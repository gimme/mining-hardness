package dev.gimme.mininghardness.mixin;

import dev.gimme.mininghardness.CommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin to adjust food exhaustion caused by mining blocks.
 */
@Mixin(Block.class)
public class MiningExhaustionMixin {

    /**
     * Adjusts food exhaustion caused by mining blocks based on adjusted block hardness and config settings.
     */
    @Redirect(method = "playerDestroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void onPlayerDestroyCauseFoodExhaustion(Player instance, float exhaustionAmount, Level level, Player player, BlockPos blockPos, BlockState blockState) {
        if (CommonConfig.INSTANCE.shouldBlockBeAdjusted(blockPos, level)) {
            exhaustionAmount = CommonConfig.INSTANCE.getAdjustedExhaustion(exhaustionAmount, blockPos.getY());
        }
        instance.causeFoodExhaustion(exhaustionAmount);
    }
}
