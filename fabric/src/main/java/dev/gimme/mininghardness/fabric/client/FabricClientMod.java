package dev.gimme.mininghardness.fabric.client;

import dev.gimme.mininghardness.network.ClientConfig;
import dev.gimme.mininghardness.network.ConfigSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client entrypoint: receives the server's hardness-config sync into {@link ClientConfig} so block-break prediction
 * matches the server, and clears it on disconnect. Only runs on a physical client; on a vanilla server (or one without
 * this mod) no sync arrives and the client falls back to its own config.
 */
public class FabricClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) ->
                ClientConfig.INSTANCE.apply(payload.settings()));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientConfig.INSTANCE.clear());
    }
}
