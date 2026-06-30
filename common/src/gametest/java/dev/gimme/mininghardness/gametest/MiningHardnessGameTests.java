package dev.gimme.mininghardness.gametest;

import com.mojang.authlib.GameProfile;
import dev.gimme.mininghardness.ConfigTestSupport;
import dev.gimme.mininghardness.HardnessSettings;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Loader-agnostic game test bodies. Each {@code static void(GameTestHelper)} method is one test;
 * a test passes by calling {@link GameTestHelper#succeed()} and fails by throwing.
 *
 * <p>To add a test: write the method here, then wire it into {@code FabricGameTests} and
 * {@code NeoForgeGameTests}.
 *
 * <p>The hardness tests read a placed block's hardness through {@code BlockState.getDestroySpeed} —
 * the value vanilla consults while a player mines — which the mod's {@code DestroySpeedMixin}
 * rewrites. Depth scaling is pinned to full strength at the block's own Y level so the test doesn't
 * depend on where the game-test structure happens to sit, and the unscaled (vanilla) hardness is
 * recovered by blacklisting the block, which exempts it from any adjustment. These read synchronously
 * within one tick, so their try-with-resources config overrides are atomic and safe.
 *
 * <p>The mining test instead drives the live break pipeline with a survival mock player and lets the
 * server tick: the {@code handleBlockBreakAction} packets only start the destruction, and the block
 * actually breaks later, once {@code ServerPlayerGameMode#tick()} (run each real server tick) has
 * accumulated enough destroy progress — whose per-tick rate is derived from the mod-rewritten
 * {@code getDestroySpeed}. It times the same survival player digging the same block twice with the same
 * tool and asserts the mod-hardened dig takes relatively longer; the comparison is immune to the
 * absolute mining rate (e.g. the mock player's off-ground penalty) and per-loader timing. Crucially the
 * hardening is <em>positional</em> (the mod's enclosure factor, from blocks placed around the target),
 * NOT a global config override: gametests in a batch tick concurrently against the one config singleton,
 * so an override held open across ticks would race the synchronous hardness tests above. Block placement
 * is local to this test's region, so there is no cross-test interference and the shipped config is used
 * unchanged. This needs a real survival player over real ticks, only expressible with
 * {@code makeMockServerPlayer(GameType.SURVIVAL)} — the deprecated {@code makeMockServerPlayerInLevel}
 * could only build creative players, who ignore hardness and break instantly.
 */
public final class MiningHardnessGameTests {

    private static final BlockPos BLOCK = new BlockPos(1, 2, 1);

    /** Where the mock player stands to mine {@link #BLOCK}: a few blocks to the side, within reach. */
    private static final BlockPos STAND = BLOCK.offset(3, 0, 0);

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

    // ---- survival mining over real ticks: the mod's hardness scaling measurably slows it ----

    /**
     * The mod's hardness scaling measurably slows real survival mining. The same survival player digs the
     * same dirt block twice with the same diamond shovel, over real server ticks: first isolated (no solid
     * neighbours, so the mod's enclosure factor leaves it near-vanilla), then encased in stone (the mod's
     * enclosure factor multiplies its hardness). The encased dig taking relatively longer pins the slowdown
     * on the mod. The contrast is positional (block placement, local to this test's region) rather than a
     * global config override, so it never races the concurrent synchronous hardness tests; and being
     * relative, it is immune to the mock player's off-ground mining penalty and to per-loader tick timing.
     * Driven end to end through the live break pipeline and {@code ServerPlayerGameMode#tick()}.
     */
    public static void modHardeningSlowsSurvivalMiningOverTicks(GameTestHelper helper) {
        ServerPlayer player = placeMiningSurvivalPlayer(helper);
        long[] isolatedStart = {helper.getTick()};
        long[] isolatedDigTicks = {0};
        long[] encasedStart = {0};

        startMining(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockPresent(Blocks.AIR, BLOCK)) // isolated (near-vanilla) block breaks
                .thenExecute(() -> {
                    isolatedDigTicks[0] = helper.getTick() - isolatedStart[0];
                    encaseBlock(helper);                  // positional hardening: the mod's enclosure factor
                    encasedStart[0] = helper.getTick();
                    startMining(helper, player);
                })
                .thenWaitUntil(() -> helper.assertBlockPresent(Blocks.AIR, BLOCK)) // encased (hardened) block breaks, later
                .thenExecute(() -> {
                    long encasedDigTicks = helper.getTick() - encasedStart[0];
                    helper.assertTrue(encasedDigTicks > isolatedDigTicks[0] * 3 / 2,
                            "the mod's enclosure hardening should make the dig clearly longer: isolated "
                                    + isolatedDigTicks[0] + " ticks vs encased " + encasedDigTicks + " ticks");
                })
                .thenSucceed();
    }

    // ---- config sync: the streamed snapshot survives the wire ----

    /**
     * The streamed {@link HardnessSettings} survives a network encode→decode unchanged. Guards the hand-written
     * {@code STREAM_CODEC} against silent field drift — in particular a reorder of two same-typed fields (there are five
     * {@code int}s and several {@code double}s), which the compiler can't catch but which would swap their values on
     * the wire and desync client prediction. Every field gets a distinct value, so any such swap fails the round-trip;
     * the leftover-bytes check additionally catches a read/write width mismatch (e.g. {@code readInt} vs {@code writeDouble}).
     */
    public static void configSettingsSurviveNetworkRoundTrip(GameTestHelper helper) {
        HardnessSettings original = new HardnessSettings(
                11, -22, 3.5, 4.5, 5.5, 66, -77, 88, 9.5, 10.5, 11.5, "stone|deepslate", ".*_ore", 12.5);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        HardnessSettings.STREAM_CODEC.encode(buf, original);
        HardnessSettings decoded = HardnessSettings.STREAM_CODEC.decode(buf);

        helper.assertTrue(decoded.equals(original),
                "config settings changed across a network round-trip: " + original + " -> " + decoded);
        helper.assertTrue(!buf.isReadable(),
                "codec left " + buf.readableBytes() + " unread byte(s) — read and write are out of sync");
        helper.succeed();
    }

    // ---- helpers ----

    /**
     * Places an (initially isolated) dirt block and a survival mock player standing beside it (on footing,
     * within reach) holding a diamond shovel — the correct tool, fast enough that even with the mock
     * player's off-ground penalty the near-vanilla block breaks quickly — then returns the player.
     */
    private static ServerPlayer placeMiningSurvivalPlayer(GameTestHelper helper) {
        helper.setBlock(BLOCK, Blocks.DIRT);
        helper.setBlock(STAND.below(), Blocks.STONE); // footing, 3 blocks away so it isn't counted as enclosure
        BlockPos standAbs = helper.absolutePos(STAND);
        ServerPlayer player = placeMockPlayer(helper, GameType.SURVIVAL);
        player.snapTo(standAbs.getX() + 0.5, standAbs.getY(), standAbs.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SHOVEL));
        return player;
    }

    /** Fills the 5x5x5 shell around {@link #BLOCK} with stone (re-placing the target as dirt) to maximise the mod's enclosure factor. */
    private static void encaseBlock(GameTestHelper helper) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    helper.setBlock(BLOCK.offset(dx, dy, dz), Blocks.STONE);
                }
            }
        }
        helper.setBlock(BLOCK, Blocks.DIRT);
    }

    /**
     * Has the player mine {@link #BLOCK} through the vanilla mining packets: START then STOP. The block is
     * not yet broken at STOP, so the server defers it and {@code ServerPlayerGameMode#tick()} completes the
     * break over the following real ticks once accumulated progress (rate from the mod-adjusted
     * {@code getDestroySpeed}) crosses the threshold — the same path as releasing the dig key early.
     */
    private static void startMining(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(BLOCK);
        int ceiling = helper.getLevel().getMaxY();
        player.gameMode.handleBlockBreakAction(
                pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.WEST, ceiling, 0);
        player.gameMode.handleBlockBreakAction(
                pos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.WEST, ceiling, 1);
    }

    /**
     * Creates a mock {@link ServerPlayer} of the requested gamemode and registers it in the test level.
     * {@link GameTestHelper#makeMockServerPlayer(GameType)} (unlike the deprecated, creative-locked
     * {@code makeMockServerPlayerInLevel}) only builds the player object, so we place it with a dummy
     * connection here — the break pipeline runs through the player's {@code gameMode}, which acks
     * block-break actions back over the connection.
     */
    private static ServerPlayer placeMockPlayer(GameTestHelper helper, GameType gameType) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(gameType);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        GameProfile profile = player.getGameProfile();
        helper.getLevel().getServer().getPlayerList()
                .placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
        player.setGameMode(gameType); // sync the ServerPlayerGameMode the break pipeline reads
        return player;
    }

    /** Pins depth scaling so the block at {@code pos} sits exactly at full depth, with depth alone doubling hardness. */
    private static ConfigTestSupport.Scope pinFullDepthAt(BlockPos pos) {
        int y = pos.getY();
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
