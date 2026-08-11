package enchant.enhancement.mixin;

import enchant.enhancement.config.EnchantmentConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerEntityMixin {

    @Inject(method = "getProjectile", at = @At("HEAD"), cancellable = true)
    private void getProjectile(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
        // 检查是否启用无限附魔不需要箭的功能
        if (EnchantmentConfig.isInfinityWithoutArrow()) {
            // 检查武器是否为弓
            if (weapon.getItem() instanceof BowItem) {
                // 获取无限附魔的Holder
                Player self = (Player) (Object) this;
                Holder<Enchantment> infinityEntry = self.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(Enchantments.INFINITY).orElse(null);
                if (infinityEntry != null) {
                    // 检查弓是否有无限附魔
                    if (EnchantmentHelper.getItemEnchantmentLevel(infinityEntry, weapon) > 0) {
                        // 返回一个虚拟的箭堆栈，允许射击
                        // 注意：这里返回一个箭堆栈，但原版逻辑可能会消耗它
                        // 我们需要确保箭不会被消耗
                        // 返回一个虚拟的箭堆栈，数量为1
                        cir.setReturnValue(new ItemStack(Items.ARROW, 1));
                        cir.cancel();
                    }
                }
            }
        }
    }
}
