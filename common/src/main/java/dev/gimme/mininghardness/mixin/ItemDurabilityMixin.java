package dev.gimme.mininghardness.mixin;

import dev.gimme.mininghardness.CommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin to adjust item durability loss when mining blocks.
 */
@Mixin(Item.class)
public class ItemDurabilityMixin {

    /**
     * Adjusts item durability loss when mining blocks based on adjusted block hardness and config settings.
     */
    @Redirect(method = "mineBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private void onMineBlockHurtAndBreak(ItemStack instance, int damageAmount, LivingEntity miningEntity, EquipmentSlot equipmentSlot, ItemStack itemStack, Level level, BlockState blockState, BlockPos blockPos) {
        if (CommonConfig.INSTANCE.shouldBlockBeAdjusted(blockPos, level)) {
            damageAmount = CommonConfig.INSTANCE.getAdjustedToolDamage(damageAmount, blockPos.getY());
        }
        instance.hurtAndBreak(damageAmount, miningEntity, equipmentSlot);
    }
}
