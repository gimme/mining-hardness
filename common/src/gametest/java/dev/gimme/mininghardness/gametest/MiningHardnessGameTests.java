package dev.gimme.mininghardness.gametest;

import dev.gimme.mininghardness.ConfigTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Loader-agnostic game test bodies. Each {@code static void(GameTestHelper)} method is one test;
 * a test passes by calling {@link GameTestHelper#succeed()} and fails by throwing.
 *
 * <p>To add a test: write the method here, then wire it into {@code FabricGameTests} and
 * {@code NeoForgeGameTests}.
 *
 * <p>These read a placed block's hardness through {@code BlockState.getDestroySpeed} — the value
 * vanilla consults while a player mines — which the mod's {@code DestroySpeedMixin} rewrites. Depth
 * scaling is pinned to full strength at the block's own Y level so the test doesn't depend on where
 * the game-test structure happens to sit, and the unscaled (vanilla) hardness is recovered by
 * blacklisting the block, which exempts it from any adjustment.
 */
public final class MiningHardnessGameTests {

    private static final BlockPos BLOCK = new BlockPos(1, 2, 1);

    private MiningHardnessGameTests() {
    }

    /** With depth scaling pinned to full strength at the block's Y level, a mined block is hardened. */
    public static void depthScalingHardensMinedBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(BLOCK, Blocks.STONE);
        BlockPos pos = helper.absolutePos(BLOCK);

        float vanilla = vanillaHardness(helper, pos);

        try (var _ = pinFullDepthAt(pos)) {
            float hardened = level.getBlockState(pos).getDestroySpeed(level, pos);
            // depthMultiplierBonus 1.0 at full depth alone doubles hardness; enclosure can only add more.
            helper.assertTrue(hardened >= vanilla * 2f - 0.01f,
                    "full-depth scaling should at least double the mined block's hardness: expected >= "
                            + (vanilla * 2f) + " but was " + hardened);
        }
        helper.succeed();
    }

    /** A blacklisted block is exempt: even with depth pinned to full strength it keeps its vanilla hardness. */
    public static void blacklistedBlockKeepsVanillaHardness(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(BLOCK, Blocks.STONE);
        BlockPos pos = helper.absolutePos(BLOCK);

        float vanilla = vanillaHardness(helper, pos);

        try (var _ = pinFullDepthAt(pos);
             var _ = ConfigTestSupport.override(ConfigTestSupport.BLOCK_BLACKLIST, "stone")) {
            float hardness = level.getBlockState(pos).getDestroySpeed(level, pos);
            helper.assertTrue(Math.abs(hardness - vanilla) < 0.01f,
                    "a blacklisted block should keep its vanilla hardness " + vanilla + " but was " + hardness);
        }
        helper.succeed();
    }

    // ---- helpers ----

    /** Pins depth scaling so the block at {@code pos} sits exactly at full depth, with depth alone doubling hardness. */
    private static ConfigTestSupport.Scope pinFullDepthAt(BlockPos pos) {
        long y = pos.getY();
        var startY = ConfigTestSupport.override(ConfigTestSupport.DEPTH_START_Y, y + 1);
        var endY = ConfigTestSupport.override(ConfigTestSupport.DEPTH_END_Y, y);
        var bonus = ConfigTestSupport.override(ConfigTestSupport.DEPTH_MULTIPLIER_BONUS, 1.0);
        return () -> {
            bonus.close();
            endY.close();
            startY.close();
        };
    }

    /** The block's unadjusted hardness: blacklisting it exempts it, so getDestroySpeed returns the vanilla value. */
    private static float vanillaHardness(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        try (var _ = ConfigTestSupport.override(ConfigTestSupport.BLOCK_BLACKLIST, ".*")) {
            return level.getBlockState(pos).getDestroySpeed(level, pos);
        }
    }
}
