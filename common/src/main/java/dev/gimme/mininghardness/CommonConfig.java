package dev.gimme.mininghardness;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

public abstract class CommonConfig {

    public static CommonConfig INSTANCE;

    public abstract float getStartHardnessMultiplier();
    public abstract float getEndHardnessMultiplier();
    public abstract int getStartHardnessY();
    public abstract int getEndHardnessY();
    public abstract int getHardnessSoftCap();
    public abstract float getHardnessSoftCapMultiplier();
    public abstract float getToolDamageHardnessMultiplier();
    public abstract float getExhaustionHardnessMultiplier();
    public abstract boolean isHardnessInOverworldOnly();
    public abstract String getBlockHardnessWhitelist();
    public abstract String getBlockHardnessBlacklist();

    public float getHardnessMultiplier(int y) {
        float startMultiplier = getStartHardnessMultiplier();
        float endMultiplier = getEndHardnessMultiplier();
        int startY = getStartHardnessY();
        int endY = getEndHardnessY();

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

        int hardnessSoftCap = getHardnessSoftCap();
        if (newHardness > hardnessSoftCap) {
            float softCapMultiplier = getHardnessSoftCapMultiplier();
            newHardness = hardnessSoftCap + (newHardness - hardnessSoftCap) * softCapMultiplier;
        }

        return newHardness;
    }

    public float getAdjustedExhaustion(float defaultExhaustion, int y) {
        float hardnessMultiplier = getHardnessMultiplier(y);
        float exhaustionHardnessMultiplier = getExhaustionHardnessMultiplier();
        return (defaultExhaustion * (1 + (hardnessMultiplier - 1) * exhaustionHardnessMultiplier));
    }

    public int getAdjustedToolDamage(float defaultDamage, int y) {
        float hardnessMultiplier = getHardnessMultiplier(y);
        float toolDamageHardnessMultiplier = getToolDamageHardnessMultiplier();
        return (int) Math.floor(defaultDamage * (1 + (hardnessMultiplier - 1) * toolDamageHardnessMultiplier));
    }

    /**
     * Checks if the block at the given position should be affected by mining hardness adjustments.
     */
    public boolean shouldBlockBeAdjusted(@NotNull BlockPos pos, @NotNull Level level) {
        if (isHardnessInOverworldOnly() && !level.dimension().equals(Level.OVERWORLD)) return false;

        Block block = level.getBlockState(pos).getBlock();
        RegistryAccess registryAccess = level.registryAccess();

        String whitelist = getBlockHardnessWhitelist();
        if (!whitelist.isEmpty() && !matchesBlockRegex(block, whitelist, registryAccess)) return false;

        String blacklist = getBlockHardnessBlacklist();
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
