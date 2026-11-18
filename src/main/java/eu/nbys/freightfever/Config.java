package eu.nbys.freightfever;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common mod configuration, generated as config/freightfever-common.toml.
 *
 * Values are read where they are used: a radius change is applied by the
 * next scan (within a few seconds of gameplay) and by the client wrench
 * overlay immediately, without restarting the game.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue INDUSTRY_SCAN_RADIUS = BUILDER
            .comment("Linking radius, in blocks, around an Industry block.",
                    "Platforms in this radius are claimed for output push and input pull.",
                    "Scan cost grows cubically with this value.")
            .defineInRange("industryScanRadius", 16, 1, 64);

    public static final ModConfigSpec.IntValue CONSUMER_SCAN_RADIUS = BUILDER
            .comment("Linking radius, in blocks, around a Consumer block.",
                    "Platforms in this radius feed the consumer and can be shared with others.")
            .defineInRange("consumerScanRadius", 8, 1, 64);

    public static final ModConfigSpec.IntValue PLATFORM_MAX_SIDE = BUILDER
            .comment("Maximum side length, in blocks, of a freight platform zone (4 corner posts).",
                    "Each side must be between 2 and this value.")
            .defineInRange("platformMaxSide", 8, 2, 16);

    public static final ModConfigSpec.IntValue ITEMS_PER_PLATFORM_BLOCK = BUILDER
            .comment("Storage capacity, in individual items, contributed by each Platform block",
                    "inside a freight platform zone. Total zone capacity = this x platform block count.")
            .defineInRange("itemsPerPlatformBlock", 8, 1, 64);

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Verbose INFO logging of industry and consumer activity (ticker, scans, pushes, rolled factors).",
                    "When false, those messages still go to debug.log at DEBUG level.")
            .define("debugLogging", false);

    static final ModConfigSpec SPEC = BUILDER.build();
}
