package net.killey.tornadophysics;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

public class Config {
    public enum DestructionMode {
        OFF,
        VANILLA,
        SABLE
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue PHYSICS_ENABLED;
    public static final ModConfigSpec.IntValue TICK_SPEED;
    public static final ModConfigSpec.DoubleValue PULL_RANGE;
    public static final ModConfigSpec.DoubleValue MASS_RESISTANCE;
    public static final ModConfigSpec.DoubleValue BASE_SPEED;
    public static final ModConfigSpec.DoubleValue MAX_SPEED;
    public static final ModConfigSpec.DoubleValue ANGULAR_SPEED;
    public static final ModConfigSpec.DoubleValue ANGULAR_LIMIT;
    public static final ModConfigSpec.DoubleValue BASE_ORBIT;
    public static final ModConfigSpec.DoubleValue BASE_INWARD;
    public static final ModConfigSpec.DoubleValue BASE_LIFT;

    public static final ModConfigSpec.EnumValue DESTRUCTION_MODE;
    public static final ModConfigSpec.IntValue DESTRUCTION_DELAY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DESTROYABLE_BLOCKS;
    public static final ModConfigSpec.IntValue DESTRUCTION_ATTEMPTS;

    public static final ModConfigSpec SPEC;

    // Initialize them in a static block to enforce push/pop ordering
    static {
        // === PHYSICS GROUP ===
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
                .defineInRange("pullRange", 0.75, 0.1, 100.0);

        MASS_RESISTANCE = BUILDER
                .comment("Defines how much sub-level mass effected by tornado pull")
                .translation("tornadophysics.config.physics.massResistance")
                .defineInRange("massResistance", 1000.0, 0.0, 100000.0);

        BASE_SPEED = BUILDER
                .comment("Defines the acceleration applied by tornado pull")
                .translation("tornadophysics.config.physics.baseSpeed")
                .defineInRange("baseSpeed", 0.8, 0.0, 10000.0);

        MAX_SPEED = BUILDER
                .comment("Defines the maximum speed tornado can pull")
                .translation("tornadophysics.config.physics.maxSpeed")
                .defineInRange("maxSpeed", 200.0, 0.0, 10000.0);

        ANGULAR_SPEED = BUILDER
                .comment("Defines the speed of sub-level rotation when pulled by tornado")
                .translation("tornadophysics.config.physics.angularSpeed")
                .defineInRange("angularSpeed", 0.3, 0.0, 10000.0);

        ANGULAR_LIMIT = BUILDER
                .comment("Max angular impulse speed applied to sublevel")
                .translation("tornadophysics.config.physics.angularLimit")
                .defineInRange("angularLimit", 200.0, 0.0, 10000.0);

        BASE_ORBIT = BUILDER
                .comment("How wide object orbits the tornado")
                .translation("tornadophysics.config.physics.baseOrbit")
                .defineInRange("baseOrbit", 0.5, 0.0, 10000.0);

        BASE_INWARD = BUILDER
                .comment("Defines the strength of tornado's inward pull")
                .translation("tornadophysics.config.physics.baseInward")
                .defineInRange("baseInward", 3.0, 0.0, 10000.0);

        BASE_LIFT = BUILDER
                .comment("How much the tornado lifts the object")
                .translation("tornadophysics.config.physics.baseLift")
                .defineInRange("baseLift", 3.0, 0.0, 10000.0);

        BUILDER.pop();


        // === DESTRUCTION GROUP ===
        BUILDER.comment("Settings for block destruction").push("destruction");

        DESTRUCTION_MODE = BUILDER
                .comment("Enable tornado destruction")
                .translation("tornadophysics.config.destruction.enabled")
                .defineEnum("destructionMode", DestructionMode.VANILLA);

        DESTRUCTION_DELAY = BUILDER
                .comment("Delay between each destruction attempt")
                .translation("tornadophysics.config.destruction.delay")
                .defineInRange("destructionDelay", 10, 0, 1000);

        DESTROYABLE_BLOCKS = BUILDER
                .comment("List of blocks that can be broken with Tornado")
                .translation("tornadophysics.config.destruction.blocks")
                .defineListAllowEmpty("destroyableBlocks", List.of("minecraft:oak_planks"), () -> "", Config::validateItemName);

        DESTRUCTION_ATTEMPTS = BUILDER
                .comment("Amount of block destruction attempts")
                .translation("tornadophysics.config.destruction.attempts")
                .defineInRange("destructionAttempts", 1, 0, 1000);

        BUILDER.pop();

        // Build the final spec
        SPEC = BUILDER.build();
    }

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}