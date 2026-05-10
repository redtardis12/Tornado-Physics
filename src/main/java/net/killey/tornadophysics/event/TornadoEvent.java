package net.killey.tornadophysics.event;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.killey.tornadophysics.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;
import weather2.ServerTickHandler;
import weather2.weathersystem.WeatherManagerServer;
import weather2.weathersystem.storm.StormObject;
import weather2.weathersystem.storm.WeatherObject;
import java.util.List;

import static weather2.weathersystem.storm.StormObject.STATE_FORMING;

public class TornadoEvent {

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        int tickDelay = Config.TICK_SPEED.get();
        int destructionDelay = Config.DESTRUCTION_DELAY.get();
        boolean runPhysics = (serverLevel.getGameTime() % tickDelay == 0);
        boolean runDestruction = (serverLevel.getGameTime() % destructionDelay == 0);

        WeatherManagerServer weatherManager = ServerTickHandler.getWeatherManagerFor(serverLevel.dimension());
        if (weatherManager == null) return;

        var storms = weatherManager.getStormObjects();
        if (storms == null || storms.isEmpty()) return;

        SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (!(container instanceof ServerSubLevelContainer serverContainer)) return;
        java.util.List<ServerSubLevel> subLevels = serverContainer.getAllSubLevels();

        double pullRange = Config.PULL_RANGE.get();

        for (WeatherObject storm : storms) {
            if (!(storm instanceof StormObject)) continue;

            int intensityStage = ((StormObject) storm).levelCurIntensityStage;
            if (intensityStage <= STATE_FORMING) continue;

            double actualRadiusInBlocks = storm.size * pullRange;
            double pullRadiusSq = actualRadiusInBlocks * actualRadiusInBlocks;

            for (ServerSubLevel subLevel : subLevels) {
                org.joml.Vector3d subPos = new org.joml.Vector3d(subLevel.logicalPose().position());
                double stormDistSq = subPos.distanceSquared(storm.pos.x, subPos.y, storm.pos.z);

                if (stormDistSq <= pullRadiusSq) {
                    double falloffMultiplier = 1.0 - (stormDistSq / pullRadiusSq);
                    if (runPhysics && Config.PHYSICS_ENABLED.get()) {
                        processPhysics(subLevel, (StormObject) storm, subPos, falloffMultiplier, intensityStage, tickDelay);
                    }

                    if (runDestruction && Config.DESTRUCTION_MODE.get() != Config.DestructionMode.OFF) {
                        processBlockDestruction(subLevel, serverLevel);
                    }
                }

            }
        }
    }

    private void processPhysics(ServerSubLevel subLevel, StormObject storm, Vector3d subPos, double falloffMultiplier, int intensityStage, int physicsDelay) {
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);

        double massResistance = Config.MASS_RESISTANCE.get();
        double maxSpeedRaw = Config.MAX_SPEED.get();
        double maxSpeedSq = maxSpeedRaw * maxSpeedRaw;

        double baseLinearSpeed = Config.BASE_SPEED.get() * physicsDelay;
        double baseAngularSpeed = Config.ANGULAR_SPEED.get() * physicsDelay;

        double orbitStrength = Config.BASE_ORBIT.get() * intensityStage;
        double inwardStrength = Config.BASE_INWARD.get() * intensityStage;
        double liftStrength = Config.BASE_LIFT.get() * intensityStage;

        double mass = subLevel.getMassTracker().getMass();
        double massFactor = massResistance / (massResistance + mass);

        Vector3d toCenter = new Vector3d(storm.pos.x, storm.pos.y, storm.pos.z).sub(subPos).normalize();
        Vector3d up = new Vector3d(0.0, 1.0, 0.0);
        Vector3d tangent = new Vector3d(up).cross(toCenter).normalize();

        Vector3d linearVelChange = new Vector3d(0, 0, 0)
                .add(new Vector3d(tangent).mul(orbitStrength))
                .add(new Vector3d(toCenter).mul(inwardStrength))
                .add(new Vector3d(up).mul(liftStrength))
                .normalize()
                .mul(baseLinearSpeed * falloffMultiplier * massFactor);

        Vector3d futureVel = new Vector3d(handle.getLinearVelocity()).add(linearVelChange);
        linearVelChange = futureVel.lengthSquared() < maxSpeedSq ? linearVelChange : new Vector3d(0.0, 0.0, 0.0);

        Vector3d angularVelocity = new Vector3d(0.0, 1.0, 0.0)
                .add((Math.random() - 0.5) * 0.5, 0.0, (Math.random() - 0.5) * 0.5)
                .normalize()
                .mul(baseAngularSpeed * falloffMultiplier);

        Vector3d futureAng = new Vector3d(handle.getAngularVelocity()).add(angularVelocity);
        angularVelocity = futureAng.lengthSquared() < Config.ANGULAR_LIMIT.get() ? angularVelocity : new Vector3d(0.0, 0.0, 0.0);

        handle.addLinearAndAngularVelocity(linearVelChange, angularVelocity);
    }

    private void processBlockDestruction(ServerSubLevel subLevel, ServerLevel serverLevel) {
        var box = subLevel.getPlot().getBoundingBox();
        int minX = box.minX();
        int minY = box.minY();
        int minZ = box.minZ();
        int maxX = box.maxX();
        int maxY = box.maxY();
        int maxZ = box.maxZ();

        if (maxX <= minX || maxY <= minY || maxZ <= minZ) return;

        ServerLevel shipLevel = subLevel.getPlot().getEmbeddedLevelAccessor().getLevel();
        RandomSource random = serverLevel.random;
        List<String> destroyableBlocks = (List<String>) Config.DESTROYABLE_BLOCKS.get();
        if ((destroyableBlocks.isEmpty())) {return;}

        for (int i = 0; i < Config.DESTRUCTION_ATTEMPTS.get(); i++) {
            int face = random.nextInt(6);
            int x = 0, y = 0, z = 0, dx = 0, dy = 0, dz = 0, maxSteps = 0;

            switch (face) {
                case 0 -> { x = maxX; dx = -1; y = minY + random.nextInt(maxY - minY + 1); z = minZ + random.nextInt(maxZ - minZ + 1); maxSteps = maxX - minX + 1; }
                case 1 -> { x = minX; dx = 1;  y = minY + random.nextInt(maxY - minY + 1); z = minZ + random.nextInt(maxZ - minZ + 1); maxSteps = maxX - minX + 1; }
                case 2 -> { y = maxY; dy = -1; x = minX + random.nextInt(maxX - minX + 1); z = minZ + random.nextInt(maxZ - minZ + 1); maxSteps = maxY - minY + 1; }
                case 3 -> { y = minY; dy = 1;  x = minX + random.nextInt(maxX - minX + 1); z = minZ + random.nextInt(maxZ - minZ + 1); maxSteps = maxY - minY + 1; }
                case 4 -> { z = maxZ; dz = -1; x = minX + random.nextInt(maxX - minX + 1); y = minY + random.nextInt(maxY - minY + 1); maxSteps = maxZ - minZ + 1; }
                case 5 -> { z = minZ; dz = 1;  x = minX + random.nextInt(maxX - minX + 1); y = minY + random.nextInt(maxY - minY + 1); maxSteps = maxZ - minZ + 1; }
            }

            for (int step = 0; step < maxSteps; step++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = shipLevel.getBlockState(pos);

                if (!state.isAir()) {
                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());

                    if (destroyableBlocks.contains(blockId.toString())) {
                        if (Config.DESTRUCTION_MODE.get() == Config.DestructionMode.SABLE) {
                            final BoundingBox boundingBox = BoundingBox.fromCorners(pos, pos);
                            final BoundingBox3i bounds = new BoundingBox3i(boundingBox);
                            var assembled = SubLevelAssemblyHelper.assembleBlocks(shipLevel, pos, List.of(pos), bounds);
                        }
                        else { shipLevel.destroyBlock(pos, true); }
                    }
                    break;
                }
                x += dx;
                y += dy;
                z += dz;
            }
        }
    }
}