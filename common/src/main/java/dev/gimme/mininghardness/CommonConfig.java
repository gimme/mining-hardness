package dev.gimme.mininghardness;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The mod's common (client + server) config surface. Backed in production by {@link FcapCommonConfig},
 * which sources the values from the NeoForge config system; tests can swap individual values via
 * {@code ConfigTestSupport}.
 */
public interface CommonConfig {

    float getAdjustedHardness(float defaultHardness, BlockPos pos, Level level);

    float getAdjustedExhaustion(float defaultExhaustion, BlockPos pos, Level level);

    int getAdjustedToolDamage(int defaultDamage, BlockPos pos, Level level);
}
