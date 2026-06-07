package enchant.enhancement.mixin;

import enchant.enhancement.util.EnchantmentMaxLevelHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.PiercingEnchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(PiercingEnchantment.class)
public class PiercingEnchantmentMixin {
    @ModifyReturnValue(method = "getMaxLevel", at = @At("RETURN"))
    private int getMaxLevel(int original) {
        return EnchantmentMaxLevelHelper.getCustomMaxLevel((Enchantment)(Object)this, original);
    }
}
