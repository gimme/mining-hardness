package dev.gimme.mininghardness.fabric;

import dev.gimme.mininghardness.Constants;
import dev.gimme.mininghardness.FcapServerConfig;
import dev.gimme.mininghardness.Main;
import dev.gimme.mininghardness.network.ConfigSync;
import dev.gimme.mininghardness.network.ConfigSyncPayload;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.config.ModConfig;

public class FabricMod implements ModInitializer {

    @Override
    public void onInitialize() {
        ConfigRegistry.INSTANCE.register(Constants.MOD_ID, ModConfig.Type.COMMON, FcapServerConfig.SPEC, FcapServerConfig.FILE_NAME);

        // Register the clientbound payload on both sides (server encodes, client decodes). The client receiver itself is
        // wired separately in the client entrypoint (FabricClientMod).
        PayloadTypeRegistry.clientboundPlay().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.STREAM_CODEC);

        // Bridge the loader-agnostic config sync to Fabric networking. canSend() is false for clients without the mod,
        // so the sync skips them. (The mod is still required on the client for correct mining — this only frees the
        // client from needing a *matching config*.)
        ConfigSync.SENDER = new ConfigSync.Sender() {
            @Override
            public boolean canSend(ServerPlayer player) {
                return ServerPlayNetworking.canSend(player, ConfigSyncPayload.TYPE);
            }

            @Override
            public void send(ServerPlayer player, ConfigSyncPayload payload) {
                ServerPlayNetworking.send(player, payload);
            }
        };

        Main.init();
    }
}
