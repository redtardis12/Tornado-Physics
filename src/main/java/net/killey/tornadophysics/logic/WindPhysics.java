package net.killey.tornadophysics.logic;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.killey.tornadophysics.Config;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import weather2.weathersystem.WeatherManagerServer;

public class WindPhysics {

    public static void processGlobalWind(ServerSubLevel subLevel, WeatherManagerServer weatherManager, int physicsDelay) {
        var windManager = weatherManager.getWindManager();
        float windAngle = windManager.getWindAngle(new Vec3(subLevel.logicalPose().position().x, subLevel.logicalPose().position().y, subLevel.logicalPose().position().z));
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

        var liftProviders = subLevel.getPlot().getLiftProviders();
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

        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        Vector3dc currentVel = handle.getLinearVelocity();

        double currentForwardSpeed = new Vector3d(currentVel.x(), 0, currentVel.z()).dot(shipForward);
        double speedDifference = targetForwardSpeed - currentForwardSpeed;

        double mass = subLevel.getMassTracker().getMass();
        double massResistance = Config.MASS_RESISTANCE.get();
        double massFactor = massResistance / (massResistance + mass);

        Vector3d windAcceleration = new Vector3d(shipForward).mul(speedDifference * totalDrag * massFactor * physicsDelay);

        handle.addLinearAndAngularVelocity(windAcceleration, new Vector3d(0, 0, 0));
    }
}