package enchant.enhancement.mixin;

import enchant.enhancement.config.EnchantmentConfig;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
    
    @Inject(method = "getProjectileType", at = @At("HEAD"), cancellable = true)
    private void getProjectileType(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
        // 检查是否启用无限附魔不需要箭的功能
        if (EnchantmentConfig.isInfinityWithoutArrow()) {
            // 检查武器是否为弓
            if (weapon.getItem() instanceof BowItem) {
                // 检查弓是否有无限附魔（1.20.1中Enchantments.INFINITY直接就是Enchantment对象）
                if (EnchantmentHelper.getLevel(Enchantments.INFINITY, weapon) > 0) {
                    // 返回一个虚拟的箭堆栈，允许射击
                    cir.setReturnValue(new ItemStack(Items.ARROW, 1));
                    cir.cancel();
                }
            }
        }
    }
}
