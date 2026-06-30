package dev.gimme.mininghardness.mixin;

import dev.gimme.mininghardness.network.ConfigSync;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives the per-player config sync from the server tick. All the work and throttling live in {@link ConfigSync}, which
 * no-ops for players already up to date or whose client cannot receive the data.
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {

    @Inject(method = "tick", at = @At("TAIL"), require = 1)
    private void onServerPlayerTick(CallbackInfo ci) {
        ConfigSync.tick((ServerPlayer) (Object) this);
    }
}
