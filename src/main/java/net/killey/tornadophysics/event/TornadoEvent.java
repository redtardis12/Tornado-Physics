package net.killey.tornadophysics.event;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.killey.tornadophysics.Config;
import net.killey.tornadophysics.logic.WindPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import weather2.ServerTickHandler;
import weather2.weathersystem.WeatherManagerServer;
import weather2.weathersystem.storm.StormObject;
import weather2.weathersystem.storm.WeatherObject;

import java.util.*;

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
        weatherManager.getWindManager().setWindTimeGust(1000);

        var storms = weatherManager.getStormObjects();
        if (storms == null || storms.isEmpty()) return;

        SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (!(container instanceof ServerSubLevelContainer serverContainer)) return;
        java.util.List<ServerSubLevel> subLevels = serverContainer.getAllSubLevels();

        double pullRange = Config.PULL_RANGE.get();

        if (Config.WIND_TOGGLE.get()) {
            for (ServerSubLevel subLevel : subLevels) {
                WindPhysics.processGlobalWind(subLevel, weatherManager, tickDelay);
            }
        }

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
                        processPhysics(subLevel, (StormObject) storm, subPos, falloffMultiplier, tickDelay);
                    }

                    if (runDestruction && Config.DESTRUCTION_MODE.get() != Config.DestructionMode.OFF && !Objects.equals(subLevel.getName(), "crumble-0451")) {
                        processBlockDestruction(subLevel, serverLevel);
                    }
                }

            }
        }
    }

    private void processPhysics(ServerSubLevel subLevel, StormObject storm, Vector3d subPos, double falloffMultiplier, int physicsDelay) {
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);

        double massResistance = Config.MASS_RESISTANCE.get();

        double baseAngularSpeed = Config.ANGULAR_SPEED.get() * physicsDelay;

        double mass = subLevel.getMassTracker().getMass();
        double massFactor = massResistance / (massResistance + mass);

        Vector3d toCenter = new Vector3d(storm.pos.x, storm.pos.y - Config.HEIGHT_OFFSET.get(), storm.pos.z).sub(subPos).normalize();
        Vector3d up = new Vector3d(0.0, 1.0, 0.0);
        Vector3d tangent = new Vector3d(up).cross(toCenter).normalize();

        Vector3d windVelocity = new Vector3d(0, 0, 0)
                .add(new Vector3d(tangent).mul(Config.BASE_ORBIT.get()))
                .add(new Vector3d(toCenter).mul(Config.BASE_INWARD.get()))
                .add(new Vector3d(up).mul(Config.BASE_LIFT.get()))
                .mul(falloffMultiplier);

        Vector3dc currentVel = handle.getLinearVelocity(new Vector3d());
        Vector3d velocityDifference = new Vector3d(windVelocity).sub(currentVel);

        double baseWindGrip = Config.WIND_GRIP.get();
        double gripFactor = Math.min(1.0, baseWindGrip * massFactor * physicsDelay);

        Vector3d linearVelChange = velocityDifference.mul(gripFactor);
        Vector3d futureLin = new Vector3d(handle.getAngularVelocity(new Vector3d()).add(linearVelChange));
        linearVelChange = futureLin.lengthSquared() < Config.MAX_SPEED.get() ? linearVelChange : new Vector3d(0.0, 0.0, 0.0);

        Vector3d angularVelocity = new Vector3d(0.0, 1.0, 0.0)
                .add((Math.random() - 0.5) * 0.5, 0.0, (Math.random() - 0.5) * 0.5)
                .normalize()
                .mul(baseAngularSpeed * falloffMultiplier);

        Vector3d futureAng = new Vector3d(handle.getAngularVelocity(new Vector3d())).add(angularVelocity);
        angularVelocity = futureAng.lengthSquared() < Config.ANGULAR_LIMIT.get() ? angularVelocity : new Vector3d(0.0, 0.0, 0.0);

        handle.addLinearAndAngularVelocity(linearVelChange, angularVelocity);
    }

    private void processBlockDestruction(ServerSubLevel subLevel, ServerLevel serverLevel) {
        var box = subLevel.getPlot().getBoundingBox();
        int minX = (int) Math.floor(box.minX());
        int minY = (int) Math.floor(box.minY());
        int minZ = (int) Math.floor(box.minZ());
        int maxX = (int) Math.ceil(box.maxX());
        int maxY = (int) Math.ceil(box.maxY());
        int maxZ = (int) Math.ceil(box.maxZ());

        if (maxX <= minX || maxY <= minY || maxZ <= minZ) return;

        ServerLevel shipLevel = subLevel.getPlot().getEmbeddedLevelAccessor().getLevel();
        net.minecraft.util.RandomSource random = serverLevel.random;
        List<String> destroyableBlocks = (List<String>) Config.DESTROYABLE_BLOCKS.get();
        int maxClusterSize = Config.CLUSTER_SIZE.get();

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
                BlockPos startPos = new BlockPos(x, y, z);
                BlockState startState = shipLevel.getBlockState(startPos);

                if (!startState.isAir()) {
                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(startState.getBlock());
                    boolean destroyedByTag = startState.getTags().anyMatch(Config.cachedBlockTags::contains);

                    if (destroyableBlocks.contains(blockId.toString()) || destroyedByTag) {
                        if (Config.DESTRUCTION_MODE.get() == Config.DestructionMode.VANILLA) {
                            shipLevel.destroyBlock(startPos, Config.DROP_BLOCKS.get());
                            break;
                        }

                        List<BlockPos> clusterBlocks = new ArrayList<>();
                        Queue<BlockPos> queue = new LinkedList<>();
                        Set<BlockPos> visited = new HashSet<>();

                        net.minecraft.world.level.block.Block targetBlock = startState.getBlock();

                        queue.add(startPos);
                        visited.add(startPos);

                        int bMinX = startPos.getX(), bMinY = startPos.getY(), bMinZ = startPos.getZ();
                        int bMaxX = startPos.getX(), bMaxY = startPos.getY(), bMaxZ = startPos.getZ();

                        while (!queue.isEmpty() && clusterBlocks.size() < maxClusterSize) {
                            BlockPos current = queue.poll();
                            clusterBlocks.add(current);

                            bMinX = Math.min(bMinX, current.getX()); bMinY = Math.min(bMinY, current.getY()); bMinZ = Math.min(bMinZ, current.getZ());
                            bMaxX = Math.max(bMaxX, current.getX()); bMaxY = Math.max(bMaxY, current.getY()); bMaxZ = Math.max(bMaxZ, current.getZ());

                            for (Direction dir : Direction.values()) {
                                BlockPos neighbor = current.relative(dir);
                                if (!visited.contains(neighbor)) {
                                    visited.add(neighbor);

                                    if (shipLevel.getBlockState(neighbor).getBlock() == targetBlock) {
                                        queue.add(neighbor);
                                    }
                                }
                            }
                        }

                        final BoundingBox clusterBox = BoundingBox.fromCorners(
                                new BlockPos(bMinX, bMinY, bMinZ),
                                new BlockPos(bMaxX, bMaxY, bMaxZ)
                        );
                        final BoundingBox3i bounds = new BoundingBox3i(clusterBox);

                        var assembled = SubLevelAssemblyHelper.assembleBlocks(shipLevel, startPos, clusterBlocks, bounds);
                        assembled.setName("crumble-0451");
                    }
                    break;
                }
                x += dx; y += dy; z += dz;
            }
        }
    }
}