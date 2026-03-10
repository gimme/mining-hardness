package dev.gimme.mininghardness;

import dev.gimme.config.ModConfigSpec;
import dev.gimme.config.ModConfigSpec.ConfigValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

public class CommonConfig {

    public static final ModConfigSpec SPEC = new ModConfigSpec();

    private static final ConfigValue<Number> START_HARDNESS_MULTIPLIER = SPEC.variable()
            .comment("""
                Multiplier for block hardness at the starting Y level and above.
                Vanilla: 1.0""")
            .define("hardness.startMultiplier", 1.0);

    private static final ConfigValue<Number> END_HARDNESS_MULTIPLIER = SPEC.variable()
            .comment("Multiplier for block hardness at the ending Y level and below.")
            .define("hardness.endMultiplier", 8.0);

    private static final ConfigValue<Number> START_HARDNESS_Y = SPEC.variable()
            .comment("Y level at which the hardness starts to increase.")
            .define("hardness.startY", 62);

    private static final ConfigValue<Number> END_HARDNESS_Y = SPEC.variable()
            .comment("Y level at which the hardness reaches its maximum multiplier.")
            .define("hardness.endY", -64);

    private static final ConfigValue<Number> HARDNESS_SOFT_CAP = SPEC.variable()
            .comment("Hardness value above which the soft cap multiplier is applied. Obsidian has a hardness of 50.")
            .define("hardness.softCap.threshold", 50);

    private static final ConfigValue<Number> HARDNESS_SOFT_CAP_MULTIPLIER = SPEC.variable()
            .comment("""
                Multiplier applied to hardness values above the soft cap.
                For example, a value of 0.2 means that an excess hardness of 10 above the soft cap will only put the final hardness
                2 above the soft cap.""")
            .define("hardness.softCap.multiplier", 0.2);

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

    private static final ConfigValue<Boolean> HARDNESS_IN_OVERWORLD_ONLY = SPEC.variable()
            .comment("If hardness adjustments should only apply in the Overworld dimension.")
            .define("scope.overworldOnly", true);

    private static final ConfigValue<String> BLOCK_WHITELIST = SPEC.variable()
            .comment("""
                Regex pattern of block IDs to apply the hardness adjustments to. If empty, all blocks are affected.
                Example: "stone|deepslate|andesite|calcite|diorite|granite|tuff" to match the common cave blocks.""")
            .define("scope.blockWhitelist", "");

    private static final ConfigValue<String> BLOCK_BLACKLIST = SPEC.variable()
            .comment("""
                Regex pattern of block IDs to exclude from the hardness adjustments.
                Example: ".*_ore" to exclude all ore blocks.""")
            .define("scope.blockBlacklist", "");

    public float getHardnessMultiplier(int y) {
        float startMultiplier = START_HARDNESS_MULTIPLIER.get().floatValue();
        float endMultiplier = END_HARDNESS_MULTIPLIER.get().floatValue();
        int startY = START_HARDNESS_Y.get().intValue();
        int endY = END_HARDNESS_Y.get().intValue();

        if (y >= startY) {
            return startMultiplier;
        } else if (y <= endY) {
            return endMultiplier;
        } else {
            float factor = (float) (startY - y) / (startY - endY);
            return startMultiplier + factor * (endMultiplier - startMultiplier);
        }
    }

    public float getAdjustedHardness(float defaultHardness, int y) {
        if (defaultHardness == 0.0f) return 0.0f;

        float hardnessMultiplier = getHardnessMultiplier(y);
        float newHardness = defaultHardness * hardnessMultiplier;

        int hardnessSoftCap = HARDNESS_SOFT_CAP.get().intValue();
        if (newHardness > hardnessSoftCap) {
            float softCapMultiplier = HARDNESS_SOFT_CAP_MULTIPLIER.get().floatValue();
            newHardness = hardnessSoftCap + (newHardness - hardnessSoftCap) * softCapMultiplier;
        }

        return newHardness;
    }

    public float getAdjustedExhaustion(float defaultExhaustion, int y) {
        float hardnessMultiplier = getHardnessMultiplier(y);
        float exhaustionHardnessMultiplier = EXHAUSTION_HARDNESS_MULTIPLIER.get().floatValue();
        return (defaultExhaustion * (1 + (hardnessMultiplier - 1) * exhaustionHardnessMultiplier));
    }

    public int getAdjustedToolDamage(float defaultDamage, int y) {
        float hardnessMultiplier = getHardnessMultiplier(y);
        float toolDamageHardnessMultiplier = TOOL_DAMAGE_HARDNESS_MULTIPLIER.get().floatValue();
        return (int) Math.floor(defaultDamage * (1 + (hardnessMultiplier - 1) * toolDamageHardnessMultiplier));
    }

    /**
     * Checks if the block at the given position should be affected by mining hardness adjustments.
     */
    public boolean shouldBlockBeAdjusted(@NotNull BlockPos pos, @NotNull Level level) {
        if (HARDNESS_IN_OVERWORLD_ONLY.get() && !level.dimension().equals(Level.OVERWORLD)) return false;

        Block block = level.getBlockState(pos).getBlock();
        RegistryAccess registryAccess = level.registryAccess();

        String whitelist = BLOCK_WHITELIST.get();
        if (!whitelist.isEmpty() && !matchesBlockRegex(block, whitelist, registryAccess)) return false;

        String blacklist = BLOCK_BLACKLIST.get();
        if (matchesBlockRegex(block, blacklist, registryAccess)) return false;

        return true;
    }

    /**
     * Checks if the given block matches the specified block regex.
     */
    private static boolean matchesBlockRegex(@NotNull Block block, @NotNull String blockRegex, @NotNull RegistryAccess registryAccess) {
        var blockRegistry = registryAccess.lookupOrThrow(Registries.BLOCK);
        Identifier blockResourceLocation = blockRegistry.getKey(block);
        if (blockResourceLocation == null) return false;

        if (blockRegex.contains(":")) {
            return blockResourceLocation.toString().matches(blockRegex);
        } else {
            return blockResourceLocation.getPath().matches(blockRegex);
        }
    }
}
