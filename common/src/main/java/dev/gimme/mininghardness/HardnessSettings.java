package dev.gimme.mininghardness;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable snapshot of the config values that drive the hardness algorithm. Built from the live config on the server
 * (see {@link FcapServerConfig#snapshot()}) and streamed to clients so their block-break prediction matches the server
 * exactly, regardless of the client's own config file. Compared by value to detect config reloads (see
 * {@link dev.gimme.mininghardness.network.ConfigSync}) and to avoid rebuilding the {@link HardnessCalculator} when
 * nothing changed.
 *
 * <p>Carries every value the algorithm reads — including the server-only exhaustion/tool-damage multipliers — so one
 * snapshot fully describes the config and a single code path serves both sides.
 */
public record HardnessSettings(
        int depthStartY,
        int depthEndY,
        double depthMaxBonus,
        double enclosureExponent,
        double enclosureMaxBonus,
        int netherStartY,
        int netherEndY,
        int softCapThreshold,
        double softCapMultiplier,
        double toolDamageMultiplier,
        double exhaustionMultiplier,
        String blockWhitelist,
        String blockBlacklist,
        double exemptMultiplier) {

    public static final StreamCodec<FriendlyByteBuf, HardnessSettings> STREAM_CODEC =
            StreamCodec.of(HardnessSettings::write, HardnessSettings::read);

    private static void write(FriendlyByteBuf buf, HardnessSettings s) {
        buf.writeInt(s.depthStartY);
        buf.writeInt(s.depthEndY);
        buf.writeDouble(s.depthMaxBonus);
        buf.writeDouble(s.enclosureExponent);
        buf.writeDouble(s.enclosureMaxBonus);
        buf.writeInt(s.netherStartY);
        buf.writeInt(s.netherEndY);
        buf.writeInt(s.softCapThreshold);
        buf.writeDouble(s.softCapMultiplier);
        buf.writeDouble(s.toolDamageMultiplier);
        buf.writeDouble(s.exhaustionMultiplier);
        buf.writeUtf(s.blockWhitelist);
        buf.writeUtf(s.blockBlacklist);
        buf.writeDouble(s.exemptMultiplier);
    }

    private static HardnessSettings read(FriendlyByteBuf buf) {
        return new HardnessSettings(
                buf.readInt(), buf.readInt(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(),
                buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(),
                buf.readUtf(), buf.readUtf(), buf.readDouble());
    }
}
