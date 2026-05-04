package net.killey.tornadophysics;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;


public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue TICK_SPEED = BUILDER
            .comment("The tick rate of tornado physics calculations")
            .defineInRange("tickSpeed", 1, 1, 10);

    public static final ModConfigSpec.DoubleValue PULL_RANGE = BUILDER
            .comment("Defines tornado pull range by multiplying default size of Weather2 tornado")
            .defineInRange("pullRange", 0.75, 0.1, 100.0);

    public static final ModConfigSpec.DoubleValue MASS_RESISTANCE = BUILDER
            .comment("Defines how much sub-level mass effected by tornado pull")
            .defineInRange("massResistance", 1000.0, 0.0, 100000.0);

    public static final ModConfigSpec.DoubleValue BASE_SPEED = BUILDER
            .comment("Defines the acceleration applied by tornado pull")
            .defineInRange("baseSpeed", 0.8, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue MAX_SPEED = BUILDER
            .comment("Defines the maximum speed tornado can pull")
            .defineInRange("maxSpeed", 200.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue ANGULAR_SPEED = BUILDER
            .comment("Defines the speed of sub-level rotation when pulled by tornado")
            .defineInRange("angularSpeed", 0.3, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue BASE_ORBIT = BUILDER
            .comment("How wide object orbits the tornado")
            .defineInRange("baseOrbit", 0.5, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue BASE_INWARD = BUILDER
            .comment("Defines the strength of tornado's inward pull")
            .defineInRange("base", 3.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue BASE_LIFT = BUILDER
            .comment("How much the tornado lifts the object")
            .defineInRange("angularSpeed", 3.0, 0.0, 10000.0);


    static final ModConfigSpec SPEC = BUILDER.build();

}
