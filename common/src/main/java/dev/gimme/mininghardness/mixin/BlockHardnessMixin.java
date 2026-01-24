package dev.gimme.mininghardness.mixin;

import dev.gimme.mininghardness.CommonConfig;
import dev.gimme.mininghardness.Constants;
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
    private void onGetDestroySpeed(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        float originalHardness = cir.getReturnValue();
        var hardnessConfig = CommonConfig.INSTANCE;

        Constants.LOG.debug("is overworld: {}", level instanceof Level realLevel && realLevel.dimension().equals(Level.OVERWORLD));
        if (level instanceof Level realLevel && hardnessConfig.isHardnessInOverworldOnly() && !realLevel.dimension().equals(Level.OVERWORLD)) return;

        float newSpeed = hardnessConfig.getAdjustedHardness(originalHardness, pos.getY());
        cir.setReturnValue(newSpeed);
    }
}
