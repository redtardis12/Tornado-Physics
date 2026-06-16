package net.killey.tornadophysics.logic;

import dev.ryanhcode.sable.api.physics.collider.SableCollisionContext;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.killey.tornadophysics.Config;
import net.killey.tornadophysics.TornadoPhysics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import weather2.util.WeatherUtilEntity;
import weather2.weathersystem.WeatherManagerServer;

import java.util.Map;
import java.util.WeakHashMap;

public class WindPhysics {
    private static final Map<ServerSubLevel, Boolean> outsideCache = new WeakHashMap<>();
    private static final Map<ServerSubLevel, Long> outsideScanTime = new WeakHashMap<>();

    private static boolean isOutside(ServerSubLevel subLevel, ServerLevel level, Vector3d windDir) {
        long currentTime = level.getGameTime();

        if (!outsideCache.containsKey(subLevel) || currentTime - outsideScanTime.getOrDefault(subLevel, 0L) > 40L) {

            Vector3dc subPos = subLevel.logicalPose().position();
            var box = subLevel.getPlot().getBoundingBox();

            double width = Math.abs(box.maxX() - box.minX());
            double depth = Math.abs(box.maxZ() - box.minZ());
            double halfHorizontalDiagonal = Math.sqrt(width * width + depth * depth) / 2.0;
            Vec3 startVecLocal = new Vec3(
                    subPos.x() - windDir.x * (halfHorizontalDiagonal + 2.0),
                    subPos.y(), // Keep start at ship's center Y
                    subPos.z() - windDir.z * (halfHorizontalDiagonal + 2.0)
            );

            Vec3 endVecLocal = new Vec3(
                    startVecLocal.x - windDir.x * 15.0,
                    startVecLocal.y, // Keep end at ship's center Y
                    startVecLocal.z - windDir.z * 15.0
            );
            BlockHitResult hitResult = level.clip(new ClipContext(startVecLocal, endVecLocal, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));

            boolean outside = true;

            if (hitResult.getType() != HitResult.Type.MISS) {
                outside = false;
            } else {
                Vec3 centerVec = new Vec3(subPos.x(), subPos.y(), subPos.z());
                outside = WeatherUtilEntity.isPosOutside(level, centerVec, false, true);
            }

            outsideCache.put(subLevel, outside);
            outsideScanTime.put(subLevel, currentTime);
        }
        return outsideCache.getOrDefault(subLevel, true);
    }

    public static void processGlobalWind(ServerSubLevel subLevel, WeatherManagerServer weatherManager, ServerLevel serverLevel, int physicsDelay) {

        Vector3dc subPos = subLevel.logicalPose().position();
        var windManager = weatherManager.getWindManager();
        float windAngle = windManager.getWindAngle(new Vec3(subPos.x(), subPos.y(), subPos.z()));
        float windSpeed = windManager.getWindSpeed();


        double radians = Math.toRadians(windAngle);
        Vector3d windDir = new Vector3d(-Math.sin(radians), 0, Math.cos(radians)).normalize();
        if (!isOutside(subLevel, serverLevel, windDir)) return;

        double windTargetSpeed = windSpeed * Config.GLOBAL_WIND_MULTIPLIER.get();


        var liftProviders = subLevel.getPlot().getLiftProviders();
        boolean hasSails = !liftProviders.isEmpty();
        if (Config.WIND_MODE.get() == Config.WindMode.SAILS_ONLY && !hasSails) return;

        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        Vector3dc currentVel = handle.getLinearVelocity();
        if (currentVel.lengthSquared() > Config.WIND_LIMIT.get()) return;

        double mass = subLevel.getMassTracker().getMass();
        double massResistance = Config.MASS_DRAG.get();
        double massFactor = massResistance / (massResistance + mass);

        if (!hasSails) {
            double driftTargetSpeed = windTargetSpeed * Config.WIND_DRAG.get();
            Vector3d targetVel = new Vector3d(windDir).mul(driftTargetSpeed);

            Vector3d currentHorizontalVel = new Vector3d(currentVel.x(), 0, currentVel.z());
            Vector3d speedDifference = targetVel.sub(currentHorizontalVel);

            Vector3d windAcceleration = speedDifference.mul(massFactor * physicsDelay);
            handle.addLinearAndAngularVelocity(windAcceleration, new Vector3d(0, 0, 0));
            return;
        }

        Quaterniondc shipRotation = subLevel.logicalPose().orientation();
        Vector3d shipForward = new Vector3d(0, 0, 1).rotate(shipRotation);

        shipForward.y = 0;
        if (shipForward.lengthSquared() > 0.001) {
            shipForward.normalize();
        } else {
            shipForward.set(0, 0, 1);
        }

        Vector3d totalWindForce = new Vector3d(0, 0, 0);

        for (var provider : liftProviders) {
            var localDir = provider.dir();
            Vector3d sailDir = new Vector3d(localDir.x, localDir.y, localDir.z);

            sailDir.rotate(shipRotation);
            sailDir.y = 0;
            if (sailDir.lengthSquared() > 0.001) sailDir.normalize();

            double alignment = windDir.dot(sailDir);
            Vector3d pushDirection = new Vector3d(sailDir).mul(Math.signum(alignment));
            totalWindForce.add(pushDirection.mul(Math.abs(alignment)));
        }

        double forwardForceMagnitude = totalWindForce.dot(shipForward);
        double baseWindPush = windDir.dot(shipForward) * 0.5;
        double baseDrag = 0.01;
        double sailDrag = Math.abs(forwardForceMagnitude) * Config.SAIL_MULTIPLIER.get();
        double totalDrag = Math.min(1.0, baseDrag + sailDrag);
        double targetForwardSpeed = (forwardForceMagnitude + baseWindPush) * windTargetSpeed;
        double currentForwardSpeed = new Vector3d(currentVel.x(), 0, currentVel.z()).dot(shipForward);
        double speedDifference = targetForwardSpeed - currentForwardSpeed;

        Vector3d windAcceleration = new Vector3d(shipForward).mul(speedDifference * totalDrag * massFactor * physicsDelay);

        handle.addLinearAndAngularVelocity(windAcceleration, new Vector3d(0, 0, 0));
    }
}