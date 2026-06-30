package dev.gimme.mininghardness.network;

import dev.gimme.mininghardness.FcapServerConfig;
import dev.gimme.mininghardness.HardnessSettings;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-side driver that streams the authoritative hardness config to each capable client, so the client's block-break
 * prediction uses the server's values instead of its own config file.
 *
 * <p>Mirrors structure-protection's {@code ProtectionSync}: it runs from the player's server tick, reads the live config
 * each time, and (re)sends only when the snapshot a player last received differs from the current one. That single check
 * transparently covers both the first join (nothing sent yet) and live config reloads (the snapshot changed) — no
 * config-reload event hook needed. Vanilla/modless clients are skipped via {@link Sender#canSend}; the mod is still
 * required on the client for correct mining, this only removes the need for a matching client <em>config</em>.
 */
public final class ConfigSync {

    /** Loader-supplied bridge: whether a client can receive our payload (i.e. has the mod) and how to send it. */
    public interface Sender {
        boolean canSend(ServerPlayer player);

        void send(ServerPlayer player, ConfigSyncPayload payload);
    }

    public static Sender SENDER;

    // Last snapshot delivered to each player. WeakHashMap so logged-out players drop out without a disconnect hook;
    // only ever touched from the single server thread.
    private static final Map<ServerPlayer, HardnessSettings> SENT = new WeakHashMap<>();

    private ConfigSync() {
    }

    public static void tick(ServerPlayer player) {
        Sender sender = SENDER;
        if (sender == null || !sender.canSend(player)) return;

        HardnessSettings current = FcapServerConfig.snapshot();
        if (current.equals(SENT.get(player))) return; // already up to date — the steady-state every-tick no-op

        sender.send(player, new ConfigSyncPayload(current));
        SENT.put(player, current);
    }
}
