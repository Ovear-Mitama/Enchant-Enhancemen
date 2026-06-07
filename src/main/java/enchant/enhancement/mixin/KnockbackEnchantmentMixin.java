package enchant.enhancement.mixin;

import enchant.enhancement.util.EnchantmentMaxLevelHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.KnockbackEnchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(KnockbackEnchantment.class)
public class KnockbackEnchantmentMixin {
    @ModifyReturnValue(method = "getMaxLevel", at = @At("RETURN"))
    private int getMaxLevel(int original) {
        return EnchantmentMaxLevelHelper.getCustomMaxLevel((Enchantment)(Object)this, original);
    }
}
