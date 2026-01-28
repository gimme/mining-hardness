package dev.gimme.mininghardness.mixin;

import dev.gimme.mininghardness.CommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to adjust block hardness.
 */
@Mixin(BlockStateBase.class)
public class BlockHardnessMixin {

    /**
     * Adjusts block hardness based on Y level and config settings.
     */
    @Inject(at = @At("RETURN"), method = "getDestroySpeed", cancellable = true)
    private void onGetDestroySpeed(BlockGetter level, BlockPos blockPos, CallbackInfoReturnable<Float> cir) {
        if (!(level instanceof Level realLevel)) return;

        if (CommonConfig.INSTANCE.shouldBlockBeAdjusted(blockPos, realLevel)) {
            float originalHardness = cir.getReturnValue();
            float adjustedHardness = CommonConfig.INSTANCE.getAdjustedHardness(originalHardness, blockPos.getY());
            cir.setReturnValue(adjustedHardness);
        }
    }
}
