package dev.gimme.mininghardness;

import dev.gimme.config.ModConfigSpec;
import dev.gimme.config.ModConfigSpec.ConfigValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class CommonConfig {

    public static final ModConfigSpec SPEC = new ModConfigSpec();

    // --- depth ---

    private static final ConfigValue<Number> DEPTH_START_Y = SPEC.variable()
            .comment("Y level at which depth-based difficulty begins increasing.")
            .define("depth.startY", 62);

    private static final ConfigValue<Number> DEPTH_END_Y = SPEC.variable()
            .comment("Y level at which depth factor reaches 1.0 (maximum).")
            .define("depth.endY", -64);

    private static final ConfigValue<Number> DEPTH_MULTIPLIER_BONUS = SPEC.variable()
            .comment("""
                Extra multiplier applied purely from depth, independent of enclosure.
                At 0.0 depth alone has no effect; at 1.0 depth alone can double hardness at max depth.""")
            .define("depth.multiplierBonus", 0.0);

    // --- enclosure ---

    private static final ConfigValue<Number> ENCLOSURE_EXPONENT = SPEC.variable()
            .comment("""
                Controls how steeply enclosure scales from 0 to 1.
                Enclosure is measured by scanning a 5x5x5 area around the block, weighting nearby solid blocks more heavily.
                The raw enclosure value (0.0-1.0) is raised to this power before applying the multiplier bonus.
                Higher values make low enclosure nearly irrelevant while high enclosure ramps up steeply.
                Example with exponent 5: 30% enclosure -> 0.2% effect, 50% -> 3.1%, 70% -> 16.8%, 90% -> 59.0%.""")
            .define("enclosure.exponent", 5.0);

    private static final ConfigValue<Number> ENCLOSURE_MULTIPLIER_BONUS = SPEC.variable()
            .comment("""
                Maximum hardness multiplier bonus when fully enclosed at max depth.
                The actual multiplier scales between 1x and (1 + this value)x based on enclosure and depth.
                E.g. 15.0 means fully enclosed blocks at max depth get a 16x multiplier.""")
            .define("enclosure.multiplierBonus", 15.0);

    // --- nether ---

    private static final ConfigValue<Number> NETHER_START_Y = SPEC.variable()
            .comment("Start Y for the Nether dimension. Set startY == endY to always use max depth factor.")
            .define("nether.startY", 128);

    private static final ConfigValue<Number> NETHER_END_Y = SPEC.variable()
            .comment("End Y for the Nether dimension.")
            .define("nether.endY", 128);

    // --- hardness soft cap ---

    private static final ConfigValue<Number> HARDNESS_SOFT_CAP = SPEC.variable()
            .comment("Hardness value above which the soft cap multiplier is applied. Obsidian has a hardness of 50.")
            .define("softCap.threshold", 50);

    private static final ConfigValue<Number> HARDNESS_SOFT_CAP_MULTIPLIER = SPEC.variable()
            .comment("""
                Multiplier applied to hardness values above the soft cap.
                For example, a value of 0.2 means that an excess hardness of 10 above the soft cap will only put the final hardness
                2 above the soft cap.""")
            .define("softCap.multiplier", 0.2);

    // --- effects ---

    private static final ConfigValue<Number> TOOL_DAMAGE_HARDNESS_MULTIPLIER = SPEC.variable()
            .comment("""
                How much tool damage is affected by the adjusted block hardness. For example, if set to 1.0, tool damage scales
                linearly with the increase in hardness. Keep in mind that tools only take damage in whole numbers (default 1 per block),
                and this effect takes the floor of the calculated damage (i.e., 1.9 becomes 1, 2.0 becomes 2).
                Vanilla: 0.0""")
            .define("effects.toolDamageMultiplier", 1.0);

    private static final ConfigValue<Number> EXHAUSTION_HARDNESS_MULTIPLIER = SPEC.variable()
            .comment("How much exhaustion is affected by the adjusted block hardness.")
            .define("effects.exhaustionMultiplier", 2.0);

    // --- scope ---

    private static final ConfigValue<String> BLOCK_WHITELIST = SPEC.variable()
            .comment("""
                Regex pattern of block IDs to apply the hardness adjustments to. If empty, all blocks are affected.
                Example: "stone|deepslate|andesite|calcite|diorite|granite|tuff" to match the common cave blocks.""")
            .define("scope.blockWhitelist", "");

    private static final ConfigValue<String> BLOCK_BLACKLIST = SPEC.variable()
            .comment("""
                Regex pattern of block IDs to exclude from the hardness adjustments.
                Blacklisted blocks are also not counted as solid in the enclosure scan, so they don't make neighboring blocks harder.
                Example: ".*_ore" to exclude all ores.""")
            .define("scope.blockBlacklist", "");

    private static final ConfigValue<Number> EXEMPT_MULTIPLIER = SPEC.variable()
            .comment("""
                How much of the hardness effect is applied to blocks exempted by the white-/blacklist.
                0.0 means exempted blocks are completely unaffected (default).
                1.0 means the lists have no effect (all blocks fully affected).
                0.5 means exempted blocks get half the hardness increase.""")
            .define("scope.exemptMultiplier", 0.0);

    // --- pre-computed constants and cached state ---

    /**
     * Maximum possible enclosure weight: sum of 1/distanceSq for all 124 blocks in a 5x5x5 cube.
     */
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

    private final CachedPattern cachedWhitelist = new CachedPattern(BLOCK_WHITELIST);
    private final CachedPattern cachedBlacklist = new CachedPattern(BLOCK_BLACKLIST);

    private static class CachedPattern {
        private final ConfigValue<String> source;
        private String lastValue = "";
        private Pattern pattern = null;
        private boolean useFullId = false;

        CachedPattern(ConfigValue<String> source) {
            this.source = source;
        }

        boolean matches(@NotNull Identifier id) {
            refresh();
            if (pattern == null) return false;
            String target = useFullId ? id.toString() : id.getPath();
            return pattern.matcher(target).matches();
        }

        boolean isEmpty() {
            refresh();
            return pattern == null;
        }

        private void refresh() {
            String value = source.get();
            if (!value.equals(lastValue)) {
                lastValue = value;
                pattern = value.isEmpty() ? null : Pattern.compile(value);
                useFullId = value.contains(":");
            }
        }
    }

    // --- instance methods ---

    /**
     * Calculates the enclosure level (0.0–1.0) for the given position by scanning a 5x5x5 area.
     * Each solid block contributes weight inversely proportional to its squared distance from the center.
     * Blacklisted blocks are not counted as solid.
     */
    public float calculateEnclosure(@NotNull Level level, @NotNull BlockPos pos) {
        boolean hasBlacklist = !cachedBlacklist.isEmpty();
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
                        if (id != null && cachedBlacklist.matches(id)) continue;
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
    public float getDepthFactor(int y, @NotNull Level level) {
        int startY, endY;
        if (level.dimension().equals(Level.NETHER)) {
            startY = NETHER_START_Y.get().intValue();
            endY = NETHER_END_Y.get().intValue();
        } else {
            startY = DEPTH_START_Y.get().intValue();
            endY = DEPTH_END_Y.get().intValue();
        }
        if (startY <= endY) return 1.0f;
        return Mth.clamp((float) (startY - y) / (startY - endY), 0.0f, 1.0f);
    }

    /**
     * Computes the combined enclosure + depth hardness multiplier.
     */
    private float getHardnessMultiplier(int y, float enclosure, @NotNull Level level) {
        float depthFactor = getDepthFactor(y, level);

        float exponent = ENCLOSURE_EXPONENT.get().floatValue();
        float multiplierBonus = ENCLOSURE_MULTIPLIER_BONUS.get().floatValue();
        float depthMultiplierBonus = DEPTH_MULTIPLIER_BONUS.get().floatValue();

        float adjustedEnclosure = (float) Math.pow(enclosure, exponent);

        float enclosureEffect = 1 + multiplierBonus * adjustedEnclosure * depthFactor;
        float depthEffect = 1 + depthFactor * depthMultiplierBonus;

        return enclosureEffect * depthEffect;
    }

    public float getAdjustedHardness(float defaultHardness, @NotNull BlockPos pos, @NotNull Level level) {
        if (defaultHardness == 0.0f) return 0.0f;
        float effectFactor = getBlockEffectFactor(pos, level);
        if (effectFactor == 0) return defaultHardness;

        float enclosure = calculateEnclosure(level, pos);
        float effectiveMultiplier = getEffectiveMultiplier(pos.getY(), enclosure, level, effectFactor);
        float newHardness = defaultHardness * effectiveMultiplier;

        int hardnessSoftCap = HARDNESS_SOFT_CAP.get().intValue();
        if (newHardness > hardnessSoftCap) {
            float softCapMultiplier = HARDNESS_SOFT_CAP_MULTIPLIER.get().floatValue();
            newHardness = hardnessSoftCap + (newHardness - hardnessSoftCap) * softCapMultiplier;
        }

        return newHardness;
    }

    public float getAdjustedExhaustion(float defaultExhaustion, @NotNull BlockPos pos, @NotNull Level level) {
        float effectFactor = getBlockEffectFactor(pos, level);
        if (effectFactor == 0) return defaultExhaustion;

        float enclosure = calculateEnclosure(level, pos);
        float effectiveMultiplier = getEffectiveMultiplier(pos.getY(), enclosure, level, effectFactor);
        float exhaustionHardnessMultiplier = EXHAUSTION_HARDNESS_MULTIPLIER.get().floatValue();
        return defaultExhaustion * (1 + (effectiveMultiplier - 1) * exhaustionHardnessMultiplier);
    }

    public int getAdjustedToolDamage(int defaultDamage, @NotNull BlockPos pos, @NotNull Level level) {
        float effectFactor = getBlockEffectFactor(pos, level);
        if (effectFactor == 0) return defaultDamage;

        float enclosure = calculateEnclosure(level, pos);
        float effectiveMultiplier = getEffectiveMultiplier(pos.getY(), enclosure, level, effectFactor);
        float toolDamageHardnessMultiplier = TOOL_DAMAGE_HARDNESS_MULTIPLIER.get().floatValue();
        return (int) Math.floor(defaultDamage * (1 + (effectiveMultiplier - 1) * toolDamageHardnessMultiplier));
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
    public float getBlockEffectFactor(@NotNull BlockPos pos, @NotNull Level level) {
        if (!level.dimension().equals(Level.OVERWORLD) && !level.dimension().equals(Level.NETHER)) return 0.0f;

        var blockRegistry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        Identifier id = blockRegistry.getKey(level.getBlockState(pos).getBlock());
        if (id == null) return 1.0f;

        boolean exempt = false;
        if (!cachedWhitelist.isEmpty() && !cachedWhitelist.matches(id)) exempt = true;
        if (!exempt && cachedBlacklist.matches(id)) exempt = true;

        return exempt ? EXEMPT_MULTIPLIER.get().floatValue() : 1.0f;
    }
}
