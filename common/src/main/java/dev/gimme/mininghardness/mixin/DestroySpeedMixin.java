package dev.gimme.mininghardness.mixin;

import dev.gimme.mininghardness.Main;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adjusts block destroy speed based on depth and neighbor enclosure.
 */
@Mixin(BlockStateBase.class)
public class DestroySpeedMixin {

    @Inject(at = @At("RETURN"), method = "getDestroySpeed", cancellable = true)
    private void onGetDestroySpeed(BlockGetter level, BlockPos blockPos, CallbackInfoReturnable<Float> cir) {
        if (!(level instanceof Level realLevel)) return;
        cir.setReturnValue(Main.INSTANCE.getServerConfig().getAdjustedHardness(cir.getReturnValue(), blockPos, realLevel));
    }
}
