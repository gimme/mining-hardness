package dev.gimme.mininghardness;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The mod's server-authoritative config surface. Backed in production by {@link FcapServerConfig}, which sources the
 * values from the NeoForge config system (the {@code -server.toml} file) on the server, and — for client-side
 * block-break prediction — from the snapshot the server streams. Tests can swap individual values via
 * {@code ConfigTestSupport}.
 */
public interface ServerConfig {

    float getAdjustedHardness(float defaultHardness, BlockPos pos, Level level);

    float getAdjustedExhaustion(float defaultExhaustion, BlockPos pos, Level level);

    int getAdjustedToolDamage(int defaultDamage, BlockPos pos, Level level);
}
