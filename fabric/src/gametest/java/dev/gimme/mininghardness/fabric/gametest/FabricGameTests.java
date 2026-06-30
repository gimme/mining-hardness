package dev.gimme.mininghardness.fabric.gametest;

import dev.gimme.mininghardness.gametest.MiningHardnessGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric test wiring, scanned via the {@code fabric-gametest} entrypoint. One {@link GameTest}
 * delegate per shared test; the structure defaults to Fabric API's built-in empty 8x8x8.
 */
public final class FabricGameTests {

    @GameTest
    public void depthScalingHardensMinedBlock(GameTestHelper helper) {
        MiningHardnessGameTests.depthScalingHardensMinedBlock(helper);
    }

    @GameTest
    public void blacklistedBlockKeepsVanillaHardness(GameTestHelper helper) {
        MiningHardnessGameTests.blacklistedBlockKeepsVanillaHardness(helper);
    }

    @GameTest
    public void configSettingsSurviveNetworkRoundTrip(GameTestHelper helper) {
        MiningHardnessGameTests.configSettingsSurviveNetworkRoundTrip(helper);
    }

    // Async (real server ticks) — digs the block twice (unhardened then hardened), so maxTicks must cover both.

    @GameTest(maxTicks = 200)
    public void modHardeningSlowsSurvivalMiningOverTicks(GameTestHelper helper) {
        MiningHardnessGameTests.modHardeningSlowsSurvivalMiningOverTicks(helper);
    }
}
