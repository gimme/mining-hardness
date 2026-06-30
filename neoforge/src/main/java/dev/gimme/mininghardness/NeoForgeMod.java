package dev.gimme.mininghardness;

import dev.gimme.mininghardness.client.NeoForgeClient;
import dev.gimme.mininghardness.network.ClientConfig;
import dev.gimme.mininghardness.network.ConfigSync;
import dev.gimme.mininghardness.network.ConfigSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(Constants.MOD_ID)
public class NeoForgeMod {

    public NeoForgeMod(ModContainer container, IEventBus modEventBus) {
        container.registerConfig(ModConfig.Type.COMMON, FcapServerConfig.SPEC, FcapServerConfig.FILE_NAME);

        modEventBus.addListener(NeoForgeMod::registerPayloads);

        // Bridge the loader-agnostic config sync to NeoForge networking. hasChannel() is false for clients without the
        // mod, so the sync skips them. (The mod is still required on the client for correct mining — this only frees the
        // client from needing a *matching config*.)
        ConfigSync.SENDER = new ConfigSync.Sender() {
            @Override
            public boolean canSend(ServerPlayer player) {
                return ((ICommonPacketListener) player.connection).hasChannel(ConfigSyncPayload.TYPE);
            }

            @Override
            public void send(ServerPlayer player, ConfigSyncPayload payload) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        };

        // Client-only: clear the synced config on disconnect so a later singleplayer/LAN world uses its own config.
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeoForgeClient.init();
        }

        Main.init();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional(): keeps hasChannel() meaningful. Required clients always negotiate it; a stray modless connection
        // simply never receives the payload instead of being refused at the channel level.
        PayloadRegistrar registrar = event.registrar(Constants.MOD_ID).optional();
        registrar.playToClient(ConfigSyncPayload.TYPE, ConfigSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientConfig.INSTANCE.apply(payload.settings()));
    }
}
