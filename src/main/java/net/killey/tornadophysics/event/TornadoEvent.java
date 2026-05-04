package net.killey.tornadophysics.event;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.killey.tornadophysics.Config;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import weather2.ServerTickHandler;
import weather2.weathersystem.WeatherManagerServer;
import weather2.weathersystem.storm.StormObject;
import weather2.weathersystem.storm.WeatherObject;

import static weather2.weathersystem.storm.StormObject.STATE_FORMING;

public class TornadoEvent {

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        int tickDelay = Config.TICK_SPEED.get();
        if (serverLevel.getGameTime() % tickDelay != 0) return;

        WeatherManagerServer weatherManager = ServerTickHandler.getWeatherManagerFor(serverLevel.dimension());
        if (weatherManager == null) return;

        var storms = weatherManager.getStormObjects();
        if (storms == null || storms.isEmpty()) return;

        SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (!(container instanceof ServerSubLevelContainer serverContainer)) return;
        java.util.List<ServerSubLevel> subLevels = serverContainer.getAllSubLevels();

        double pullRange = Config.PULL_RANGE.get();
        double massResistance = Config.MASS_RESISTANCE.get();

        double baseLinearSpeed = Config.BASE_SPEED.get() * tickDelay;
        double baseAngularSpeed = Config.ANGULAR_SPEED.get() * tickDelay;

        double configOrbit = Config.BASE_ORBIT.get();
        double configInward = Config.BASE_INWARD.get();
        double configLift = Config.BASE_LIFT.get();

        for (WeatherObject storm : storms) {
            if (!(storm instanceof StormObject)) continue;

            int intensityStage = ((StormObject) storm).levelCurIntensityStage;
            if (intensityStage <= STATE_FORMING) continue;

            double orbitStrength = configOrbit * intensityStage;
            double inwardStrength = configInward * intensityStage;
            double liftStrength = configLift * intensityStage;

            double actualRadiusInBlocks = storm.size * pullRange;
            double pullRadiusSq = actualRadiusInBlocks * actualRadiusInBlocks;

            for (ServerSubLevel subLevel : subLevels) {
                org.joml.Vector3d subPos = new org.joml.Vector3d(subLevel.logicalPose().position());
                double stormDistSq = subPos.distanceSquared(storm.pos.x, subPos.y, storm.pos.z);

                if (stormDistSq <= pullRadiusSq) {
                    double falloffMultiplier = 1.0 - (stormDistSq / pullRadiusSq);
                    RigidBodyHandle handle = RigidBodyHandle.of(subLevel);

                    double mass = subLevel.getMassTracker().getMass();
                    double massFactor = massResistance / (massResistance + mass);

                    org.joml.Vector3d toCenter = new org.joml.Vector3d(storm.pos.x, storm.pos.y, storm.pos.z);
                    toCenter.sub(subPos).normalize();

                    org.joml.Vector3d up = new org.joml.Vector3d(0.0, 1.0, 0.0);
                    org.joml.Vector3d tangent = new org.joml.Vector3d(up).cross(toCenter).normalize();

                    org.joml.Vector3d linearVelChange = new org.joml.Vector3d(0, 0, 0);


                    linearVelChange.add(new org.joml.Vector3d(tangent).mul(orbitStrength));
                    linearVelChange.add(new org.joml.Vector3d(toCenter).mul(inwardStrength));
                    linearVelChange.add(new org.joml.Vector3d(up).mul(liftStrength));

                    linearVelChange.normalize();
                    linearVelChange.mul(baseLinearSpeed * falloffMultiplier * massFactor);

                    org.joml.Vector3d futureVel = new org.joml.Vector3d(handle.getLinearVelocity()).add(linearVelChange);

                    boolean applyLinear = futureVel.lengthSquared() < Config.MAX_SPEED.get();

                    org.joml.Vector3d angularImpulse = new org.joml.Vector3d(0.0, 1.0, 0.0);
                    double randomTumbleX = (Math.random() - 0.5) * 0.5;
                    double randomTumbleZ = (Math.random() - 0.5) * 0.5;
                    angularImpulse.add(randomTumbleX, 0.0, randomTumbleZ).normalize();

                    angularImpulse.mul(baseAngularSpeed * falloffMultiplier * intensityStage);

                    if (applyLinear) {
                        handle.addLinearAndAngularVelocity(linearVelChange, new org.joml.Vector3d(0.0, 0.0, 0.0));
                    }
                    handle.applyAngularImpulse(angularImpulse);
                }
            }
        }
    }
}