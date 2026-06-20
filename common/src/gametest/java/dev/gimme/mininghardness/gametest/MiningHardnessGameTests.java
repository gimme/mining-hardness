package dev.gimme.mininghardness.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Loader-agnostic game test bodies. Each {@code static void(GameTestHelper)} method is one test;
 * a test passes by calling {@link GameTestHelper#succeed()} and fails by throwing.
 *
 * <p>To add a test: write the method here, then wire it into {@code FabricGameTests} and
 * {@code NeoForgeGameTests}.
 */
public final class MiningHardnessGameTests {

    private MiningHardnessGameTests() {
    }

    /** Bootstrap smoke test: the harness boots, places a block, and reads it back. */
    public static void smoke(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, Blocks.STONE);
        helper.assertBlockPresent(Blocks.STONE, pos);
        helper.succeed();
    }
}
