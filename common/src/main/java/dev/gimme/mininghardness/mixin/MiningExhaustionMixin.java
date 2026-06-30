package dev.gimme.mininghardness.mixin;

import dev.gimme.mininghardness.Main;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Adjusts food exhaustion caused by mining blocks based on depth and neighbor enclosure.
 */
@Mixin(Block.class)
public class MiningExhaustionMixin {

    @Redirect(method = "playerDestroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void onPlayerDestroyCauseFoodExhaustion(Player instance, float exhaustionAmount, Level level, Player player, BlockPos blockPos, BlockState blockState) {
        instance.causeFoodExhaustion(Main.INSTANCE.getServerConfig().getAdjustedExhaustion(exhaustionAmount, blockPos, level));
    }
}
