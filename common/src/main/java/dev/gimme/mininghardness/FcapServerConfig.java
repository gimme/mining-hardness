package dev.gimme.mininghardness;

import dev.gimme.mininghardness.network.ClientConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import org.jetbrains.annotations.NotNull;

/**
 * Production {@link ServerConfig} backed by the NeoForge config system (via Forge Config API Port on Fabric). The config
 * is a server concern — the authoritative values live in the {@code -server.toml} file on the server and are streamed to
 * clients.
 *
 * <p>The algorithm itself lives in the immutable {@link HardnessCalculator}, keyed on a {@link HardnessSettings}
 * snapshot. On the server the snapshot is read live from the file, so the file-watcher's auto-reload takes effect with
 * no {@code /reload}; {@link dev.gimme.mininghardness.network.ConfigSync} then re-streams it. A client uses the snapshot
 * the server streamed ({@link ClientConfig}) when one has arrived, otherwise it falls back to its own local config —
 * harmless, since the server's snapshot overwrites it within a tick and (under {@code MATCH_VERSION}) a modded client
 * only ever joins servers that run the mod. The calculator is cached and rebuilt only when the snapshot changes.
 */
public class FcapServerConfig implements ServerConfig {

    public static final String FILE_NAME = Constants.MOD_ID + "-server.toml";

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // --- depth ---

    static final ConfigValue<Integer> DEPTH_START_Y = BUILDER
            .comment("Y level at which depth-based difficulty begins increasing.")
            .define("depth.startY", 62);

    static final ConfigValue<Integer> DEPTH_END_Y = BUILDER
            .comment("Y level at which depth factor reaches 1.0 (maximum).")
            .define("depth.endY", -64);

    static final ConfigValue<Number> DEPTH_MULTIPLIER_BONUS = BUILDER
            .comment("""
                Extra multiplier applied purely from depth, independent of enclosure.
                At 0.0 depth alone has no effect; at 1.0 depth alone can double hardness at max depth.""")
            .define("depth.multiplierBonus", 0.0, o -> o instanceof Number);

    // --- enclosure ---

    private static final ConfigValue<Number> ENCLOSURE_EXPONENT = BUILDER
            .comment("""
                Controls how steeply enclosure scales from 0 to 1.
                Enclosure is measured by scanning a 5x5x5 area around the block, weighting nearby solid blocks more heavily.
                The raw enclosure value (0.0-1.0) is raised to this power before applying the multiplier bonus.
                Higher values make low enclosure nearly irrelevant while high enclosure ramps up steeply.
                Example with exponent 5: 30% enclosure -> 0.2% effect, 50% -> 3.1%, 70% -> 16.8%, 90% -> 59.0%.""")
            .define("enclosure.exponent", 5.0, o -> o instanceof Number);

    private static final ConfigValue<Number> ENCLOSURE_MULTIPLIER_BONUS = BUILDER
            .comment("""
                Maximum hardness multiplier bonus when fully enclosed at max depth.
                The actual multiplier scales between 1x and (1 + this value)x based on enclosure and depth.
                E.g. 15.0 means fully enclosed blocks at max depth get a 16x multiplier.""")
            .define("enclosure.multiplierBonus", 15.0, o -> o instanceof Number);

    // --- nether ---

    private static final ConfigValue<Integer> NETHER_START_Y = BUILDER
            .comment("Start Y for the Nether dimension. Set startY == endY to always use max depth factor.")
            .define("nether.startY", 128);

    private static final ConfigValue<Integer> NETHER_END_Y = BUILDER
            .comment("End Y for the Nether dimension.")
            .define("nether.endY", 128);

    // --- hardness soft cap ---

    private static final ConfigValue<Integer> HARDNESS_SOFT_CAP = BUILDER
            .comment("Hardness value above which the soft cap multiplier is applied. Obsidian has a hardness of 50.")
            .define("softCap.threshold", 50);

    private static final ConfigValue<Double> HARDNESS_SOFT_CAP_MULTIPLIER = BUILDER
            .comment("""
                Multiplier applied to hardness values above the soft cap.
                For example, a value of 0.2 means that an excess hardness of 10 above the soft cap will only put the final hardness
                2 above the soft cap.""")
            .defineInRange("softCap.multiplier", 0.2, 0.0, 1.0);

    // --- effects ---

    private static final ConfigValue<Number> TOOL_DAMAGE_HARDNESS_MULTIPLIER = BUILDER
            .comment("""
                How much tool damage is affected by the adjusted block hardness. For example, if set to 1.0, tool damage scales
                linearly with the increase in hardness. Keep in mind that tools only take damage in whole numbers (default 1 per block),
                and this effect takes the floor of the calculated damage (i.e., 1.9 becomes 1, 2.0 becomes 2).
                Vanilla: 0.0""")
            .define("effects.toolDamageMultiplier", 1.0, o -> o instanceof Number);

    private static final ConfigValue<Number> EXHAUSTION_HARDNESS_MULTIPLIER = BUILDER
            .comment("How much exhaustion is affected by the adjusted block hardness.")
            .define("effects.exhaustionMultiplier", 2.0, o -> o instanceof Number);

    // --- scope ---

    private static final ConfigValue<String> BLOCK_WHITELIST = BUILDER
            .comment("""
                Regex pattern of block IDs to apply the hardness adjustments to. If empty, all blocks are affected.
                Example: "stone|deepslate|andesite|calcite|diorite|granite|tuff" to match the common cave blocks.""")
            .define("scope.blockWhitelist", "");

    static final ConfigValue<String> BLOCK_BLACKLIST = BUILDER
            .comment("""
                Regex pattern of block IDs to exclude from the hardness adjustments.
                Blacklisted blocks are also not counted as solid in the enclosure scan, so they don't make neighboring blocks harder.
                Example: ".*_ore" to exclude all ores.""")
            .define("scope.blockBlacklist", "");

    private static final ConfigValue<Double> EXEMPT_MULTIPLIER = BUILDER
            .comment("""
                How much of the hardness effect is applied to blocks exempted by the white-/blacklist.
                0.0 means exempted blocks are completely unaffected (default).
                1.0 means the lists have no effect (all blocks fully affected).
                0.5 means exempted blocks get half the hardness increase.""")
            .defineInRange("scope.exemptMultiplier", 0.0, 0.0, 1.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * Reads the current config values into an immutable snapshot. Cheap (plain config-value reads); called per server
     * tick by {@link dev.gimme.mininghardness.network.ConfigSync} and per server-side hardness query.
     */
    public static HardnessSettings snapshot() {
        return new HardnessSettings(
                DEPTH_START_Y.get(), DEPTH_END_Y.get(), DEPTH_MULTIPLIER_BONUS.get().doubleValue(),
                ENCLOSURE_EXPONENT.get().doubleValue(), ENCLOSURE_MULTIPLIER_BONUS.get().doubleValue(),
                NETHER_START_Y.get(), NETHER_END_Y.get(),
                HARDNESS_SOFT_CAP.get(), HARDNESS_SOFT_CAP_MULTIPLIER.get(),
                TOOL_DAMAGE_HARDNESS_MULTIPLIER.get().doubleValue(), EXHAUSTION_HARDNESS_MULTIPLIER.get().doubleValue(),
                BLOCK_WHITELIST.get(), BLOCK_BLACKLIST.get(), EXEMPT_MULTIPLIER.get());
    }

    private record CalcCache(HardnessSettings settings, HardnessCalculator calculator) {
    }

    private volatile CalcCache cache;

    /**
     * Returns the calculator for the settings in effect: the server's streamed snapshot when one has arrived (client
     * prediction), otherwise this side's live config — the dedicated/integrated server reading its {@code -server.toml},
     * or a client briefly falling back before its first sync. Cached and rebuilt only when that snapshot changes.
     */
    private HardnessCalculator calculator() {
        HardnessSettings synced = ClientConfig.INSTANCE.settings();
        HardnessSettings effective = synced != null ? synced : snapshot();

        CalcCache current = cache;
        if (current == null || !current.settings().equals(effective)) {
            current = new CalcCache(effective, new HardnessCalculator(effective));
            cache = current; // benign race: concurrent builds yield equal calculators, last write wins
        }
        return current.calculator();
    }

    @Override
    public float getAdjustedHardness(float defaultHardness, @NotNull BlockPos pos, @NotNull Level level) {
        return calculator().getAdjustedHardness(defaultHardness, pos, level);
    }

    @Override
    public float getAdjustedExhaustion(float defaultExhaustion, @NotNull BlockPos pos, @NotNull Level level) {
        return calculator().getAdjustedExhaustion(defaultExhaustion, pos, level);
    }

    @Override
    public int getAdjustedToolDamage(int defaultDamage, @NotNull BlockPos pos, @NotNull Level level) {
        return calculator().getAdjustedToolDamage(defaultDamage, pos, level);
    }
}
