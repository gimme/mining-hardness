package dev.gimme.mininghardness;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Immutable, stateless implementation of the hardness / exhaustion / tool-damage algorithm for one fixed
 * {@link HardnessSettings} snapshot. Because nothing mutates after construction (the white/blacklist patterns are
 * compiled once), a single instance is safe to share across the client block-break-prediction thread and the server
 * thread. {@link FcapServerConfig} rebuilds it only when the effective snapshot changes.
 */
final class HardnessCalculator {

    /** Maximum possible enclosure weight: sum of 1/distanceSq for all 124 blocks in a 5x5x5 cube. */
    private static final float MAX_ENCLOSURE_WEIGHT;

    static {
        float total = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    total += 1.0f / (dx * dx + dy * dy + dz * dz);
                }
            }
        }
        MAX_ENCLOSURE_WEIGHT = total;
    }

    private final HardnessSettings settings;
    private final IdMatcher whitelist;
    private final IdMatcher blacklist;

    HardnessCalculator(@NotNull HardnessSettings settings) {
        this.settings = settings;
        this.whitelist = IdMatcher.of(settings.blockWhitelist());
        this.blacklist = IdMatcher.of(settings.blockBlacklist());
    }

    float getAdjustedHardness(float defaultHardness, @NotNull BlockPos pos, @NotNull Level level) {
        if (defaultHardness == 0.0f) return 0.0f;
        float effectFactor = getBlockEffectFactor(pos, level);
        if (effectFactor == 0) return defaultHardness;

        float enclosure = calculateEnclosure(level, pos);
        float effectiveMultiplier = getEffectiveMultiplier(pos.getY(), enclosure, level, effectFactor);
        float newHardness = defaultHardness * effectiveMultiplier;

        int hardnessSoftCap = settings.softCapThreshold();
        if (newHardness > hardnessSoftCap) {
            float softCapMultiplier = (float) settings.softCapMultiplier();
            newHardness = hardnessSoftCap + (newHardness - hardnessSoftCap) * softCapMultiplier;
        }

        return newHardness;
    }

    float getAdjustedExhaustion(float defaultExhaustion, @NotNull BlockPos pos, @NotNull Level level) {
        float effectFactor = getBlockEffectFactor(pos, level);
        if (effectFactor == 0) return defaultExhaustion;

        float enclosure = calculateEnclosure(level, pos);
        float effectiveMultiplier = getEffectiveMultiplier(pos.getY(), enclosure, level, effectFactor);
        float exhaustionHardnessMultiplier = (float) settings.exhaustionMultiplier();
        return defaultExhaustion * (1 + (effectiveMultiplier - 1) * exhaustionHardnessMultiplier);
    }

    int getAdjustedToolDamage(int defaultDamage, @NotNull BlockPos pos, @NotNull Level level) {
        float effectFactor = getBlockEffectFactor(pos, level);
        if (effectFactor == 0) return defaultDamage;

        float enclosure = calculateEnclosure(level, pos);
        float effectiveMultiplier = getEffectiveMultiplier(pos.getY(), enclosure, level, effectFactor);
        float toolDamageHardnessMultiplier = (float) settings.toolDamageMultiplier();
        return (int) Math.floor(defaultDamage * (1 + (effectiveMultiplier - 1) * toolDamageHardnessMultiplier));
    }

    /**
     * Calculates the enclosure level (0.0–1.0) for the given position by scanning a 5x5x5 area.
     * Each solid block contributes weight inversely proportional to its squared distance from the center.
     * Blacklisted blocks are not counted as solid.
     */
    private float calculateEnclosure(@NotNull Level level, @NotNull BlockPos pos) {
        boolean hasBlacklist = !blacklist.isEmpty();
        var blockRegistry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        var mutablePos = new BlockPos.MutableBlockPos();

        float solidWeight = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    BlockState state = level.getBlockState(mutablePos.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
                    if (!state.isSolidRender()) continue;

                    if (hasBlacklist) {
                        Identifier id = blockRegistry.getKey(state.getBlock());
                        if (id != null && blacklist.matches(id)) continue;
                    }

                    solidWeight += 1.0f / (dx * dx + dy * dy + dz * dz);
                }
            }
        }

        return solidWeight / MAX_ENCLOSURE_WEIGHT;
    }

    /**
     * Returns the depth factor (0–1) for the given Y level and dimension.
     * Overworld uses depth.startY/endY, Nether uses nether.startY/endY.
     */
    private float getDepthFactor(int y, @NotNull Level level) {
        int startY, endY;
        if (level.dimension().equals(Level.NETHER)) {
            startY = settings.netherStartY();
            endY = settings.netherEndY();
        } else {
            startY = settings.depthStartY();
            endY = settings.depthEndY();
        }
        if (startY <= endY) return 1.0f;
        return Mth.clamp((float) (startY - y) / (startY - endY), 0.0f, 1.0f);
    }

    /**
     * Computes the combined enclosure + depth hardness multiplier.
     */
    private float getHardnessMultiplier(int y, float enclosure, @NotNull Level level) {
        float depthFactor = getDepthFactor(y, level);

        float exponent = (float) settings.enclosureExponent();
        float maxBonus = (float) settings.enclosureMaxBonus();
        float depthMaxBonus = (float) settings.depthMaxBonus();

        float adjustedEnclosure = (float) Math.pow(enclosure, exponent);

        float enclosureEffect = 1 + maxBonus * adjustedEnclosure * depthFactor;
        float depthEffect = 1 + depthFactor * depthMaxBonus;

        return enclosureEffect * depthEffect;
    }

    private float getEffectiveMultiplier(int y, float enclosure, @NotNull Level level, float effectFactor) {
        float hardnessMultiplier = getHardnessMultiplier(y, enclosure, level);
        return 1 + (hardnessMultiplier - 1) * effectFactor;
    }

    /**
     * Returns the effect factor for the block at the given position.
     * <p>
     * Returns 0.0 for unsupported dimensions, 1.0 for fully affected blocks,
     * or the exempt multiplier for blocks excluded by the white-/blacklist.
     */
    private float getBlockEffectFactor(@NotNull BlockPos pos, @NotNull Level level) {
        if (!level.dimension().equals(Level.OVERWORLD) && !level.dimension().equals(Level.NETHER)) return 0.0f;

        var blockRegistry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        Identifier id = blockRegistry.getKey(level.getBlockState(pos).getBlock());
        if (id == null) return 1.0f;

        boolean exempt = false;
        if (!whitelist.isEmpty() && !whitelist.matches(id)) exempt = true;
        if (!exempt && blacklist.matches(id)) exempt = true;

        return exempt ? (float) settings.exemptMultiplier() : 1.0f;
    }

    /**
     * Immutable compiled white/blacklist pattern. An empty source matches nothing; a pattern containing {@code ':'}
     * is matched against the full block id, otherwise against its path. Mirrors the old {@code CachedPattern} but with
     * no mutable refresh, since the settings are fixed for this calculator's lifetime.
     */
    private record IdMatcher(Pattern pattern, boolean useFullId) {
        static IdMatcher of(@NotNull String value) {
            if (value.isEmpty()) return new IdMatcher(null, false);
            return new IdMatcher(Pattern.compile(value), value.contains(":"));
        }

        boolean isEmpty() {
            return pattern == null;
        }

        boolean matches(@NotNull Identifier id) {
            if (pattern == null) return false;
            return pattern.matcher(useFullId ? id.toString() : id.getPath()).matches();
        }
    }
}
