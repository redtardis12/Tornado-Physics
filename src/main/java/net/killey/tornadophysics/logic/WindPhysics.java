package net.killey.tornadophysics.logic;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.killey.tornadophysics.Config;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import weather2.weathersystem.WeatherManagerServer;

public class WindPhysics {

    public static void processGlobalWind(ServerSubLevel subLevel, WeatherManagerServer weatherManager, int physicsDelay) {
        ServerLevelPlot plot = subLevel.getPlot();
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        Vector3dc currentVel = handle.getLinearVelocity();
        var liftProviders = plot.getLiftProviders();
        if (liftProviders.isEmpty() || currentVel.lengthSquared() > Config.WIND_LIMIT.get()) return;

        var windManager = weatherManager.getWindManager();
        Vector3d subPos = subLevel.logicalPose().position();
        float windAngle = windManager.getWindAngle(new Vec3(subPos.x, subPos.y, subPos.z));
        float windSpeed = windManager.getWindSpeed();

        double radians = Math.toRadians(windAngle);
        Vector3d windDir = new Vector3d(-Math.sin(radians), 0, Math.cos(radians)).normalize();
        double windTargetSpeed = windSpeed * Config.GLOBAL_WIND_MULTIPLIER.get();

        Quaterniondc shipRotation = subLevel.logicalPose().orientation();
        Vector3d shipForward = new Vector3d(0, 0, 1).rotate(shipRotation);

        shipForward.y = 0;
        if (shipForward.lengthSquared() > 0.001) {
            shipForward.normalize();
        } else {
            shipForward.set(0, 0, 1);
        }

        double pointOfSail = windDir.dot(shipForward.negate());
        double basePower = (pointOfSail > -0.75) ? Math.sqrt((pointOfSail + 0.75) / 1.75) : 0.0;

        Vector3d idealNormal = new Vector3d(windDir).add(shipForward);
        if (idealNormal.lengthSquared() > 0.001) {
            idealNormal.normalize();
        } else {
            idealNormal.set(windDir);
        }

        double totalForwardThrust = 0;

        Vector3d sailNormal = new Vector3d();

        for (var provider : liftProviders) {
            var localDir = provider.dir();
            sailNormal.set(localDir.x, localDir.y, localDir.z);

            sailNormal.rotate(shipRotation);
            sailNormal.y = 0;
            if (sailNormal.lengthSquared() < 0.001) continue;
            sailNormal.normalize();

            double trimEfficiency = sailNormal.dot(idealNormal);

            trimEfficiency = Math.abs(trimEfficiency);
            totalForwardThrust += trimEfficiency * basePower;
        }

        double baseDrag = 0.01;
        double sailDrag = totalForwardThrust * Config.SAIL_MULTIPLIER.get();
        double totalDrag = Math.min(1.0, baseDrag + sailDrag);

        double hullPush = Math.max(0, pointOfSail) * 0.05;
        double targetForwardSpeed = (totalForwardThrust + hullPush) * windTargetSpeed;

        double currentForwardSpeed = new Vector3d(currentVel.x(), 0, currentVel.z()).dot(shipForward);
        double speedDifference = targetForwardSpeed - currentForwardSpeed;

        double mass = subLevel.getMassTracker().getMass();
        double massResistance = Config.MASS_RESISTANCE.get();
        double massFactor = massResistance / (massResistance + mass);

        Vector3d windImpulse = new Vector3d(shipForward).mul(speedDifference * totalDrag * massFactor * physicsDelay);

        Vector3d applicationPoint = new Vector3d(plot.getCenterBlock().getX(), plot.getBoundingBox().minY(), plot.getCenterBlock().getZ());
        handle.applyImpulseAtPoint(applicationPoint, windImpulse.negate());
    }
}