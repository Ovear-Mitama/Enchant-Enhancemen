package enchant.enhancement.mixin;

import enchant.enhancement.config.EnchantmentConfig;
import enchant.enhancement.util.EnchantmentRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public class EnchantmentMixin {
    @Inject(method = "getMaxLevel", at = @At("HEAD"), cancellable = true)
    private void getMaxLevel(CallbackInfoReturnable<Integer> cir) {
        Enchantment self = (Enchantment) (Object) this;

        // 使用EnchantmentRegistry获取附魔ID
        ResourceLocation id = EnchantmentRegistry.getId(self);

        if (id != null) {
            Integer customMaxLevel = EnchantmentConfig.getMaxLevel(id);
            if (customMaxLevel != null) {
                // 检查是否允许合并高级附魔
                if (EnchantmentConfig.isMergeHighEnchantments()) {
                    cir.setReturnValue(customMaxLevel);
                    cir.cancel();
                    return;
                }
            }
        }
    }
}
