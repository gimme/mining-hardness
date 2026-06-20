package dev.gimme.mininghardness;

import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;

/**
 * Test-only handles to {@link FcapCommonConfig} values. Lives in the gametest source set's package
 * alongside the config so it can reach the package-private config fields; production code still
 * exposes only the read-only getters.
 */
public final class ConfigTestSupport {

    public static final ConfigValue<String> BLOCK_BLACKLIST = FcapCommonConfig.BLOCK_BLACKLIST;
    public static final ConfigValue<Long> DEPTH_START_Y = FcapCommonConfig.DEPTH_START_Y;
    public static final ConfigValue<Long> DEPTH_END_Y = FcapCommonConfig.DEPTH_END_Y;
    public static final ConfigValue<Double> DEPTH_MULTIPLIER_BONUS = FcapCommonConfig.DEPTH_MULTIPLIER_BONUS;

    private ConfigTestSupport() {
    }

    /**
     * A restore handle whose {@code close()} throws nothing, so it reads cleanly in try-with-resources.
     */
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Mutates the live config and returns a scope that restores the previous value on close, keeping tests isolated.
     */
    public static <T> Scope override(ConfigValue<T> config, T value) {
        T previous = config.get();
        config.set(value);
        return () -> config.set(previous);
    }
}
