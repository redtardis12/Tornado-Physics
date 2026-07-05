package net.killey.tornadophysics.logic;

import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.killey.tornadophysics.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.*;
import weather2.weathersystem.WeatherManagerServer;

import java.lang.Math;
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
            double h = box.height() / 2 + 1;
            double w = box.width() / 2 + 1;
            double l = box.length() / 2 + 1;
            double rayLength = (Math.max(Math.max(l, w), h) / 2.0) + 10.0;
            double m = subPos.y() + h;

            Vec3[] localCorners = new Vec3[] {
                    new Vec3(subPos.x()-w, m, subPos.z()-l),
                    new Vec3(subPos.x()-w, m, subPos.z()+l),
                    new Vec3(subPos.x()+w, m, subPos.z()-l),
                    new Vec3(subPos.x()+w, m, subPos.z()+l)
            };

            boolean anyCornerOutside = false;
            for (Vec3 corner : localCorners) {
                Vec3 endVec = corner.add(-windDir.x * rayLength, 0, -windDir.z * rayLength);
                BlockHitResult hit = level.clip(new ClipContext(corner, endVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));

                if (hit.getType() == HitResult.Type.MISS) {
                    anyCornerOutside = true;
                    break;
                }
            }
            outsideCache.put(subLevel, anyCornerOutside);
            outsideScanTime.put(subLevel, currentTime);
        }
        return outsideCache.getOrDefault(subLevel, true);
    }

    private static Vector3d getBottom(BoundingBox3ic box) {
        return new Vector3d((box.minX() + box.maxX()) / 2, box.minY(), (box.minZ() + box.maxZ()) / 2);
    }

    public static void processGlobalWind(ServerSubLevel subLevel, WeatherManagerServer weatherManager, ServerLevel serverLevel, int physicsDelay) {
        Object2ObjectMap<ForceGroup, QueuedForceGroup> queuedForceGroups = subLevel.getQueuedForceGroups();
        if (queuedForceGroups != null) {
            for (Map.Entry<ForceGroup, QueuedForceGroup> entry : queuedForceGroups.entrySet()) {
                ResourceLocation groupId = ForceGroups.REGISTRY.getKey(entry.getKey());
                if (groupId == null) {
                    continue;
                }
                if (groupId.getPath().equals("propulsion")) {
                    queuedForceGroups.remove(entry.getKey());
                    return;
                }
            }
        }
        Vector3dc subPos = subLevel.logicalPose().position();
        var windManager = weatherManager.getWindManager();
        float windAngle = windManager.getWindAngle(new Vec3(subPos.x(), subPos.y(), subPos.z()));
        float windSpeed = windManager.getWindSpeed();

        double radians = Math.toRadians(windAngle);
        Vector3d windDir = new Vector3d(-Math.sin(radians), 0, Math.cos(radians)).normalize();
        if (Config.OBSTRUCTION_CHECK.get() && !isOutside(subLevel, serverLevel, windDir)) return;

        double windTargetSpeed = windSpeed * Config.GLOBAL_WIND_MULTIPLIER.get();

        var liftProviders = subLevel.getPlot().getLiftProviders();
        boolean hasSails = !liftProviders.isEmpty();
        if (Config.WIND_MODE.get() == Config.WindMode.SAILS_ONLY && !hasSails) return;

        RigidBodyHandle handle;
        Vector3dc currentVel;
        try {
            handle = RigidBodyHandle.of(subLevel);
            currentVel = handle.getLinearVelocity(new Vector3d());
        } catch (RuntimeException e) {
            return;
        }
        if (currentVel.lengthSquared() > Config.WIND_LIMIT.get()) return;

        double massResistance = Config.MASS_DRAG.get();
        double massFactor = massResistance / (massResistance + subLevel.getMassTracker().getMass());

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
        if (shipForward.lengthSquared() > 0.001) shipForward.normalize();
        else shipForward.set(0, 0, 1);

        Vector3d totalWindForce = new Vector3d(0, 0, 0);

        for (var provider : liftProviders) {
            var localDir = provider.dir();
            Vector3d sailDir = new Vector3d(localDir.x, localDir.y, localDir.z);

            sailDir.rotate(shipRotation);
            sailDir.y = 0;
            if (sailDir.lengthSquared() > 0.001) sailDir.normalize();

            double alignment = windDir.dot(sailDir);
            if (alignment > 0.2) {
                continue;
            }
            Vector3d reflect = new Vector3d(windDir).reflect(sailDir).mul(Math.sin(alignment) * Config.SAIL_DRAG.get());
            totalWindForce.add(reflect);
        }

        Vector3d targetVel = new Vector3d(0, 0, 0);
        if (totalWindForce.lengthSquared() > 0.001) {
            targetVel = new Vector3d(totalWindForce).normalize().mul(totalWindForce.length() * windTargetSpeed);
        }

        Vector3d currentHorizontalVel = new Vector3d(currentVel.x(), 0, currentVel.z());
        Vector3d speedDifference = targetVel.sub(currentHorizontalVel);

        Vector3d windAcceleration = speedDifference.mul(massFactor * physicsDelay);
        CompoundTag userData = subLevel.getUserDataTag();
        if (handle.isValid()) {
            if (Config.SAIL_STABILIZER.get() && userData != null && userData.getBoolean("tornadophysics:on_swivel")) {
                Quaterniond inverseRotation = new Quaterniond(shipRotation).conjugate();
                handle.applyImpulseAtPoint(getBottom(subLevel.getPlot().getBoundingBox()), windAcceleration.rotate(inverseRotation));
            }
            else {
                handle.addLinearAndAngularVelocity(windAcceleration, new Vector3d(0, 0, 0));
            }
        }
    }
}