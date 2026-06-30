package dev.gimme.mininghardness.network;

import dev.gimme.mininghardness.HardnessSettings;

/**
 * Client-side store of the hardness config the server has streamed. Read on the block-break prediction path via
 * {@link dev.gimme.mininghardness.FcapServerConfig}; written by the loader client receivers and cleared on disconnect.
 *
 * <p>{@code volatile} because the network thread writes it while the client (and, in singleplayer, the integrated
 * server) threads read it. Stays {@code null} on a dedicated server and until the first sync arrives, in which case the
 * side's own local config is used as the fallback.
 */
public final class ClientConfig {

    public static final ClientConfig INSTANCE = new ClientConfig();

    private volatile HardnessSettings settings;

    private ClientConfig() {
    }

    public void apply(HardnessSettings settings) {
        this.settings = settings;
    }

    public void clear() {
        this.settings = null;
    }

    /** The server's streamed settings, or {@code null} when none have been received (use the local config then). */
    public HardnessSettings settings() {
        return settings;
    }
}
