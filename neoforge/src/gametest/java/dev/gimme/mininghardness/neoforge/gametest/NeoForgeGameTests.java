package dev.gimme.mininghardness.neoforge.gametest;

import java.util.List;
import java.util.function.Consumer;

import dev.gimme.mininghardness.Constants;
import dev.gimme.mininghardness.gametest.MiningHardnessGameTests;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * NeoForge test wiring. Registers each shared test as a {@code TEST_FUNCTION} plus a matching
 * {@link FunctionGameTestInstance}. Auto-discovered via {@link EventBusSubscriber} so {@code main}
 * never references this dev-only source set. To add a test, add one entry to {@link #TESTS}.
 */
@EventBusSubscriber(modid = Constants.MOD_ID)
public final class NeoForgeGameTests {

    private record Test(String name, int maxTicks, boolean required, Consumer<GameTestHelper> body) {
        // Required by default; pass required=false for known-red TDD specs that shouldn't fail the suite.
        Test(String name, int maxTicks, Consumer<GameTestHelper> body) {
            this(name, maxTicks, true, body);
        }
    }

    private static final List<Test> TESTS = List.of(
            new Test("depth_scaling_hardens_mined_block", 20, MiningHardnessGameTests::depthScalingHardensMinedBlock),
            new Test("blacklisted_block_keeps_vanilla_hardness", 20, MiningHardnessGameTests::blacklistedBlockKeepsVanillaHardness),
            new Test("config_settings_survive_network_round_trip", 20, MiningHardnessGameTests::configSettingsSurviveNetworkRoundTrip),
            // Survival mining over real ticks (async) — digs the block twice (unhardened then hardened).
            new Test("mod_hardening_slows_survival_mining_over_ticks", 200, MiningHardnessGameTests::modHardeningSlowsSurvivalMiningOverTicks));

    private NeoForgeGameTests() {
    }

    @SubscribeEvent
    static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registry ->
                TESTS.forEach(test -> registry.register(id(test.name()), test.body())));
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"));
        TESTS.forEach(test -> {
            ResourceKey<Consumer<GameTestHelper>> function =
                    ResourceKey.create(Registries.TEST_FUNCTION, id(test.name()));
            TestData<Holder<TestEnvironmentDefinition<?>>> data =
                    new TestData<>(environment, id("empty"), test.maxTicks(), 0, test.required());
            event.registerTest(id(test.name()), new FunctionGameTestInstance(function, data));
        });
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
    }
}
