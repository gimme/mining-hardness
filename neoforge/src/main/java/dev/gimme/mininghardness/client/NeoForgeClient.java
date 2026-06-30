package dev.gimme.mininghardness.client;

import dev.gimme.mininghardness.network.ClientConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only NeoForge wiring. Kept in its own class so the dedicated server never loads the client-only event types;
 * {@link dev.gimme.mininghardness.NeoForgeMod} calls {@link #init()} solely under a {@code Dist.CLIENT} guard.
 */
public final class NeoForgeClient {

    private NeoForgeClient() {
    }

    public static void init() {
        // Drop the server's synced config when leaving a server, so the next singleplayer/LAN session starts from this
        // client's own config rather than a stale remote snapshot.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientConfig.INSTANCE.clear());
    }
}
