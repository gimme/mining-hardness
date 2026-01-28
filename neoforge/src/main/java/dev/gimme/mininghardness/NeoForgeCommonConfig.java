package dev.gimme.mininghardness;

import com.electronwill.nightconfig.core.Config;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class NeoForgeCommonConfig extends CommonConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.DoubleValue START_HARDNESS_MULTIPLIER = BUILDER
            .comment("""
                Multiplier for block hardness at the starting Y level and above.
                 Vanilla: 1.0""")
            .defineInRange("startHardnessMultiplier", 1.0, 0.0, 100.0);

    private static final ModConfigSpec.DoubleValue END_HARDNESS_MULTIPLIER = BUILDER
            .comment("Multiplier for block hardness at the ending Y level and below.")
            .defineInRange("endHardnessMultiplier", 8.0, 0.0, 100.0);

    private static final ModConfigSpec.IntValue START_HARDNESS_Y = BUILDER
            .comment("Y level at which the hardness starts to increase.")
            .defineInRange("startHardnessY", 62, -64, 384);

    private static final ModConfigSpec.IntValue END_HARDNESS_Y = BUILDER
            .comment("Y level at which the hardness reaches its maximum multiplier.")
            .defineInRange("endHardnessY", -64, -64, 384);

    private static final ModConfigSpec.IntValue HARDNESS_SOFT_CAP = BUILDER
            .comment("Hardness value above which the soft cap multiplier is applied. Obsidian has a hardness of 50.")
            .defineInRange("hardnessSoftCap", 50, 1, 100);

    private static final ModConfigSpec.DoubleValue HARDNESS_SOFT_CAP_MULTIPLIER = BUILDER
            .comment("""
                Multiplier applied to hardness values above the soft cap.
                For example, a value of 0.2 means that an excess hardness of 10 above the soft cap will only put the final hardness
                2 above the soft cap.""")
            .defineInRange("hardnessSoftCapMultiplier", 0.2, 0.0, 1.0);

    private static final ModConfigSpec.DoubleValue TOOL_DAMAGE_HARDNESS_MULTIPLIER = BUILDER
            .comment("""
                    How much tool damage is affected by the adjusted block hardness from above. For example, if set to 1.0, tool damage scales
                    linearly with the increase in hardness. Keep in mind that tools only take damage in whole numbers (default 1 per block),
                    and this effect takes the floor of the calculated damage (i.e., 1.9 becomes 1, 2.0 becomes 2).
                     Vanilla: 0.0""")
            .defineInRange("toolDamageHardnessMultiplier", 1.0, 0.0, 10.0);

    private static final ModConfigSpec.DoubleValue EXHAUSTION_HARDNESS_MULTIPLIER = BUILDER
            .comment("How much exhaustion is affected by the adjusted block hardness from above.")
            .defineInRange("exhaustionHardnessMultiplier", 2.0, 0.0, 10.0);

    private static final ModConfigSpec.BooleanValue HARDNESS_IN_OVERWORLD_ONLY = BUILDER
            .comment("If hardness adjustments should only apply in the Overworld dimension.")
            .define("hardnessInOverworldOnly", true);

    private static final ModConfigSpec.ConfigValue<String> BLOCK_WHITELIST = BUILDER
            .comment("""
                    Regex pattern of block IDs to apply the hardness adjustments to. If empty, all blocks are affected.
                    Example: "stone|deepslate|andesite|calcite|diorite|granite|tuff" to match the common cave blocks.""")
            .define("blockWhitelist", "", NeoForgeCommonConfig::isValidRegex);

    private static final ModConfigSpec.ConfigValue<String> BLOCK_BLACKLIST = BUILDER
            .comment("""
                    Regex pattern of block IDs to exclude from the hardness adjustments.
                    Example: ".*_ore" to exclude all ore blocks.""")
            .define("blockBlacklist", "", NeoForgeCommonConfig::isValidRegex);

    /**
     * Checks if the given string is a valid regex pattern.
     */
    private static boolean isValidRegex(@NotNull Object regex) {
        try {
            Pattern.compile(regex.toString());
        } catch (PatternSyntaxException ex) {
            return false;
        }
        return true;
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    @Override
    public float getStartHardnessMultiplier() {
        return START_HARDNESS_MULTIPLIER.get().floatValue();
    }

    @Override
    public float getEndHardnessMultiplier() {
        return END_HARDNESS_MULTIPLIER.get().floatValue();
    }

    @Override
    public int getStartHardnessY() {
        return START_HARDNESS_Y.get();
    }

    @Override
    public int getEndHardnessY() {
        return END_HARDNESS_Y.get();
    }

    @Override
    public int getHardnessSoftCap() {
        return HARDNESS_SOFT_CAP.get();
    }

    @Override
    public float getHardnessSoftCapMultiplier() {
        return HARDNESS_SOFT_CAP_MULTIPLIER.get().floatValue();
    }

    @Override
    public float getToolDamageHardnessMultiplier() {
        return TOOL_DAMAGE_HARDNESS_MULTIPLIER.get().floatValue();
    }

    @Override
    public float getExhaustionHardnessMultiplier() {
        return EXHAUSTION_HARDNESS_MULTIPLIER.get().floatValue();
    }

    @Override
    public boolean isHardnessInOverworldOnly() {
        return HARDNESS_IN_OVERWORLD_ONLY.get();
    }

    @Override
    public String getBlockHardnessWhitelist() {
        return BLOCK_WHITELIST.get();
    }

    @Override
    public String getBlockHardnessBlacklist() {
        return BLOCK_BLACKLIST.get();
    }
}
