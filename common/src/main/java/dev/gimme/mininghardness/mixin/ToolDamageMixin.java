package dev.gimme.mininghardness.mixin;

import dev.gimme.mininghardness.Main;
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
 * Adjusts tool damage when mining blocks based on depth and neighbor enclosure.
 */
@Mixin(Item.class)
public class ToolDamageMixin {

    @Redirect(method = "mineBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private void onMineBlockHurtAndBreak(ItemStack instance, int damageAmount, LivingEntity miningEntity, EquipmentSlot equipmentSlot, ItemStack itemStack, Level level, BlockState blockState, BlockPos blockPos) {
        instance.hurtAndBreak(Main.INSTANCE.getServerConfig().getAdjustedToolDamage(damageAmount, blockPos, level), miningEntity, equipmentSlot);
    }
}
