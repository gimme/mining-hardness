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
            .comment("""
                Y level where mining starts getting harder in the Overworld.
                Above this, everything is normal.""")
            .define("depth.startY", 62);

    static final ConfigValue<Integer> DEPTH_END_Y = BUILDER
            .comment("""
                Y level where depth's effect on hardness maxes out in the Overworld.
                At or below this, depth contributes its full bonus.""")
            .define("depth.endY", -64);

    static final ConfigValue<Number> DEPTH_MAX_BONUS = BUILDER
            .comment("""
                How much harder blocks get purely from being deep, regardless of how boxed in they are.
                0.0 disables this depth-alone effect entirely; 1.5 makes blocks at max depth 150% harder from depth alone
                (before enclosure is factored in).
                The default is 0 because Deepslate already kind of does this naturally.""")
            .define("depth.maxBonus", 0.0, o -> o instanceof Number);

    // --- enclosure ---

    private static final ConfigValue<Number> ENCLOSURE_EXPONENT = BUILDER
            .comment("""
                Enclosure is how boxed-in a block is by nearby solid blocks, scanned in a 5x5x5 area centered on it,
                from 0% (floating in the air) to 100% (fully surrounded). Along with depth, it's one of the two inputs
                that drive the hardness bonus. When mining into a plain wall the block is about 65% enclosed; digging
                straight down in a plain 1-wide shaft (where only the blocks straight above are air) reaches about 96%
                (realistic worst case).
                
                This setting shapes how enclosure turns into extra hardness. Low values ramp the bonus up gradually as
                enclosure increases; high values keep it at almost no effect until a block is very enclosed, then starts
                spiking sharply, so open caverns stay easy while tight tunnels get much harder.
                Default 7.0:
                  - mining into a wall (~65% enclosed) gets you ~5% of the max bonus;
                  - mining straight down (~96% enclosed) gets you ~75% of the max bonus.
                The actual formula (at max depth) is `maxBonus * enclosure^exponent = bonus hardness`.""")
            .define("enclosure.exponent", 7.0, o -> o instanceof Number);

    private static final ConfigValue<Number> ENCLOSURE_MAX_BONUS = BUILDER
            .comment("""
                How much harder a fully enclosed block is, at maximum depth.
                Note: this bonus still scales (linearly) with depth. Above `depth.startY` it has no effect at all,
                no matter how boxed in a block is.
                Default 15.0: a fully enclosed block at max depth is 1500% harder to mine.""")
            .define("enclosure.maxBonus", 15.0, o -> o instanceof Number);

    // --- nether ---

    private static final ConfigValue<Integer> NETHER_START_Y = BUILDER
            .comment("""
                Same as `depth.startY`, but for the Nether.""")
            .define("nether.startY", 128);

    private static final ConfigValue<Integer> NETHER_END_Y = BUILDER
            .comment("""
                Same as `depth.endY`, but for the Nether.
                Set equal to `nether.startY` to make depth always maxed out, which is recommended since the Nether
                doesn't really have a natural shallow level like the Overworld's surface.""")
            .define("nether.endY", 128);

    // --- hardness soft cap ---

    private static final ConfigValue<Integer> HARDNESS_SOFT_CAP = BUILDER
            .comment("""
                Hardness value above which extra hardness gets dampened (see `softCap.multiplier`), so a few extremely
                hard block types don't become absurdly slow to mine.
                For reference, Obsidian's natural hardness is 50; Deepslate is 3.""")
            .define("softCap.threshold", 50);

    private static final ConfigValue<Double> HARDNESS_SOFT_CAP_MULTIPLIER = BUILDER
            .comment("""
                How much of the hardness above `softCap.threshold` actually counts.
                1.0 = no dampening; 0.0 = hardness can never exceed the threshold.
                Default 0.2: Every 10 points of hardness above the threshold only adds 2 points to the final hardness.""")
            .defineInRange("softCap.multiplier", 0.2, 0.0, 1.0);

    // --- effects ---

    private static final ConfigValue<Number> TOOL_DAMAGE_HARDNESS_MULTIPLIER = BUILDER
            .comment("""
                How much extra durability damage tools take on hardness-boosted blocks.
                0.0 = vanilla behavior, tools always take normal damage regardless of hardness.
                1.0 = tool damage scales up right along with hardness, e.g. a block that's 2x as hard (from this mod's
                effect) deals 2x the durability damage. Damage is still whole numbers, rounded down.""")
            .define("effects.toolDamageMultiplier", 1.0, o -> o instanceof Number);

    private static final ConfigValue<Number> EXHAUSTION_HARDNESS_MULTIPLIER = BUILDER
            .comment("""
                How much extra hunger (exhaustion) mining hardness-boosted blocks costs.
                Works the same way as `effects.toolDamageMultiplier`: 0.0 = vanilla exhaustion, 1.0 = exhaustion scales
                up right along with hardness.""")
            .define("effects.exhaustionMultiplier", 2.0, o -> o instanceof Number);

    // --- scope ---

    private static final ConfigValue<String> BLOCK_WHITELIST = BUILDER
            .comment("""
                Only blocks whose ID matches this regex get the hardness adjustment; every other block is left at vanilla
                hardness (or scaled down, see `scope.exemptMultiplier`). Leave empty to affect all blocks.
                Example: "stone|deepslate|andesite|calcite|diorite|granite|tuff" to only affect common cave blocks.""")
            .define("scope.blockWhitelist", "");

    static final ConfigValue<String> BLOCK_BLACKLIST = BUILDER
            .comment("""
                All blocks whose ID matches this regex are excluded from the hardness adjustment (see
                `scope.exemptMultiplier`) and are also ignored by the enclosure scan, so they won't make neighboring
                blocks count as more enclosed.
                Example: ".*_ore" to leave all ores at their vanilla hardness.""")
            .define("scope.blockBlacklist", "");

    private static final ConfigValue<Double> EXEMPT_MULTIPLIER = BUILDER
            .comment("""
                How much of the hardness bonus still applies to blocks excluded by scope.blockWhitelist /
                scope.blockBlacklist.
                0.0 = fully exempt, vanilla hardness (default). 1.0 = the lists don't actually exempt anything.
                0.5 = exempt blocks get half the usual bonus.""")
            .defineInRange("scope.exemptMultiplier", 0.0, 0.0, 1.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * Reads the current config values into an immutable snapshot. Cheap (plain config-value reads); called per server
     * tick by {@link dev.gimme.mininghardness.network.ConfigSync} and per server-side hardness query.
     */
    public static HardnessSettings snapshot() {
        return new HardnessSettings(
                DEPTH_START_Y.get(), DEPTH_END_Y.get(), DEPTH_MAX_BONUS.get().doubleValue(),
                ENCLOSURE_EXPONENT.get().doubleValue(), ENCLOSURE_MAX_BONUS.get().doubleValue(),
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
