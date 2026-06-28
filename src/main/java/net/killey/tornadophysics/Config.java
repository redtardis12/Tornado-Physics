package net.killey.tornadophysics;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Config {
    public enum DestructionMode {
        OFF,
        VANILLA,
        SABLE
    }

    public enum WindMode {
        OFF,
        ALL,
        SAILS_ONLY
    }

    public static final Set<TagKey<Block>> cachedBlockTags = new HashSet<>();

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue PHYSICS_ENABLED;
    public static final ModConfigSpec.IntValue TICK_SPEED;
    public static final ModConfigSpec.DoubleValue PULL_RANGE;
    public static final ModConfigSpec.DoubleValue HEIGHT_OFFSET;
    public static final ModConfigSpec.DoubleValue MASS_RESISTANCE;
    public static final ModConfigSpec.DoubleValue MAX_SPEED;
    public static final ModConfigSpec.DoubleValue ANGULAR_SPEED;
    public static final ModConfigSpec.DoubleValue ANGULAR_LIMIT;
    public static final ModConfigSpec.DoubleValue BASE_ORBIT;
    public static final ModConfigSpec.DoubleValue BASE_INWARD;
    public static final ModConfigSpec.DoubleValue BASE_LIFT;
    public static final ModConfigSpec.DoubleValue WIND_GRIP;

    public static final ModConfigSpec.EnumValue DESTRUCTION_MODE;
    public static final ModConfigSpec.IntValue DESTRUCTION_DELAY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DESTROYABLE_BLOCKS;
    public static final ModConfigSpec.IntValue DESTRUCTION_ATTEMPTS;
    public static final ModConfigSpec.IntValue CLUSTER_SIZE;
    public static final ModConfigSpec.BooleanValue DROP_BLOCKS;

    public static final ModConfigSpec.EnumValue WIND_MODE;
    public static final ModConfigSpec.DoubleValue GLOBAL_WIND_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SAIL_DRAG;
    public static final ModConfigSpec.DoubleValue WIND_DRAG;
    public static final ModConfigSpec.DoubleValue MASS_DRAG;
    public static final ModConfigSpec.DoubleValue WIND_LIMIT;
    public static final ModConfigSpec.DoubleValue WIND_GUST_ANGLE_MIN;
    public static final ModConfigSpec.DoubleValue WIND_GUST_ANGLE_MAX;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Settings for tornado physics calculations").push("physics");

        PHYSICS_ENABLED = BUILDER
                .comment("Enable tornado physics")
                .translation("tornadophysics.config.physics.enabled")
                .define("physicsEnabled", true);

        TICK_SPEED = BUILDER
                .comment("The tick rate of tornado physics calculations")
                .translation("tornadophysics.config.physics.tickSpeed")
                .defineInRange("tickSpeed", 1, 1, 10);

        PULL_RANGE = BUILDER
                .comment("Defines tornado pull range by multiplying default size of Weather2 tornado")
                .translation("tornadophysics.config.physics.pullRange")
                .defineInRange("pullRange", 0.5, 0.1, 100.0);

        HEIGHT_OFFSET = BUILDER
                .comment("Offsets tornado center's height down (because by default it's too high)")
                .translation("tornadophysics.config.physics.heightOffset")
                .defineInRange("heightOffset", 30.0, 0.0, 200.0);

        MASS_RESISTANCE = BUILDER
                .comment("Defines how much sub-level mass effected by tornado pull")
                .translation("tornadophysics.config.physics.massResistance")
                .defineInRange("massResistance", 1000.0, 0.0, 100000.0);

        MAX_SPEED = BUILDER
                .comment("Defines the maximum speed tornado can pull")
                .translation("tornadophysics.config.physics.maxSpeed")
                .defineInRange("maxSpeed", 200.0, 0.0, 10000.0);

        ANGULAR_SPEED = BUILDER
                .comment("Defines the speed of sub-level rotation when pulled by tornado")
                .translation("tornadophysics.config.physics.angularSpeed")
                .defineInRange("angularSpeed", 0.1, 0.0, 10000.0);

        ANGULAR_LIMIT = BUILDER
                .comment("Max angular impulse speed applied to sublevel")
                .translation("tornadophysics.config.physics.angularLimit")
                .defineInRange("angularLimit", 50.0, 0.0, 10000.0);

        BASE_ORBIT = BUILDER
                .comment("How wide object orbits the tornado")
                .translation("tornadophysics.config.physics.baseOrbit")
                .defineInRange("baseOrbit", 50.0, 0.0, 10000.0);

        BASE_INWARD = BUILDER
                .comment("Defines the strength of tornado's inward pull")
                .translation("tornadophysics.config.physics.baseInward")
                .defineInRange("baseInward", 20.0, 0.0, 10000.0);

        BASE_LIFT = BUILDER
                .comment("How much the tornado lifts the object")
                .translation("tornadophysics.config.physics.baseLift")
                .defineInRange("baseLift", 3.5, 0.0, 10000.0);

        WIND_GRIP = BUILDER
                .comment("Wind grip strength")
                .translation("tornadophysics.config.physics.windGrip")
                .defineInRange("windGrip", 0.15, 0, 1000.0);

        BUILDER.pop();


        BUILDER.comment("Settings for block destruction").push("destruction");

        DESTRUCTION_MODE = BUILDER
                .comment("VANILLA - breaks blocks\nSABLE - turns destroyed blocks into a sub-level")
                .translation("tornadophysics.config.destruction.mode")
                .defineEnum("destructionMode", DestructionMode.VANILLA);

        DESTRUCTION_DELAY = BUILDER
                .comment("Delay between every destruction attempts")
                .translation("tornadophysics.config.destruction.delay")
                .defineInRange("destructionDelay", 10, 0, 1000);

        DESTROYABLE_BLOCKS = BUILDER
                .comment("List of blocks that can be broken with Tornado (use # for item tags)")
                .translation("tornadophysics.config.destruction.blocks")
                .defineListAllowEmpty("destroyableBlocks", List.of("minecraft:oak_planks", "#aeronautics:envelope", "#create:windmill_sails"), () -> "", Config::validateItemName);

        DESTRUCTION_ATTEMPTS = BUILDER
                .comment("Amount of block destruction attempts per one destruction tick")
                .translation("tornadophysics.config.destruction.attempts")
                .defineInRange("destructionAttempts", 1, 0, 1000);

        CLUSTER_SIZE = BUILDER
                .comment("Maximum amount of blocks that will be detached when SABLE mode enabled")
                .translation("tornadophysics.config.destruction.clusterSize")
                .defineInRange("clusterSize", 5, 1, 100);

        DROP_BLOCKS = BUILDER
                .comment("Whether to drop destroyed blocks as items when VANILLA mode enabled")
                .translation("tornadophysics.config.destruction.dropBlocks")
                .define("dropBlocks", false);

        BUILDER.pop();

        BUILDER.comment("Settings for wind").push("wind");

        WIND_MODE = BUILDER
                .comment("Enable wind physics")
                .translation("tornadophysics.config.wind.windMode")
                .defineEnum("windMode", WindMode.ALL);

        GLOBAL_WIND_MULTIPLIER = BUILDER
                .comment("Wind speed multiplier")
                .translation("tornadophysics.config.wind.windMul")
                .defineInRange("windMul", 0.8, 0.0, 10000.0);

        SAIL_DRAG = BUILDER
                .comment("Sail's acceleration in the wind")
                .translation("tornadophysics.config.wind.sailDrag")
                .defineInRange("sailDrag", 0.4, 0.0, 10000.0);

        WIND_DRAG = BUILDER
                .comment("Global wind drag")
                .translation("tornadophysics.config.wind.windDrag")
                .defineInRange("windDrag", 0.6, 0.0, 1000000.0);

        MASS_DRAG = BUILDER
                .comment("Mass resistance to wind")
                .translation("tornadophysics.config.wind.massDrag")
                .defineInRange("massDrag", 1000.0, 0.0, 100000.0);

        WIND_LIMIT = BUILDER
                .comment("Speed at which wind is no longer considered")
                .translation("tornadophysics.config.wind.windLimit")
                .defineInRange("windLimit", 1000.0, 0.0, 10000000.0);

        WIND_GUST_ANGLE_MIN = BUILDER
                .comment("Minimum angle for random wind gust event")
                .translation("tornadophysics.config.wind.gustMin")
                .defineInRange("gustMin", 1.0, 1.0, 360);

        WIND_GUST_ANGLE_MAX = BUILDER
                .comment("Maximum angle for random wind gust event")
                .translation("tornadophysics.config.wind.gustMax")
                .defineInRange("gustMax", 1.0, 1.0, 360);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private static boolean validateItemName(final Object obj) {
        try {
            if (obj instanceof String itemName && itemName.startsWith("#")) {
                cachedBlockTags.add(TagKey.create(Registries.BLOCK, ResourceLocation.tryParse(itemName.substring(1))));
                return true;
            }
            return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
        } catch (Exception e) {
            return false;
        }
    }
}