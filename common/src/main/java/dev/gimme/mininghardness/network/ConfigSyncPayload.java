package dev.gimme.mininghardness.network;

import dev.gimme.mininghardness.Constants;
import dev.gimme.mininghardness.HardnessSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client snapshot of the authoritative hardness config. Sent only when it changes for a given player (see
 * {@link ConfigSync}) and never to clients without the mod. The client mirrors it into {@link ClientConfig}. The wire
 * shape is owned by {@link HardnessSettings#STREAM_CODEC}, so the exact same encoding works on both loaders.
 */
public record ConfigSyncPayload(HardnessSettings settings) implements CustomPacketPayload {

    public static final Type<ConfigSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "config_sync"));

    public static final StreamCodec<FriendlyByteBuf, ConfigSyncPayload> STREAM_CODEC =
            StreamCodec.of(ConfigSyncPayload::write, ConfigSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, ConfigSyncPayload payload) {
        HardnessSettings.STREAM_CODEC.encode(buf, payload.settings);
    }

    private static ConfigSyncPayload read(FriendlyByteBuf buf) {
        return new ConfigSyncPayload(HardnessSettings.STREAM_CODEC.decode(buf));
    }
}
