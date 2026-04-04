package enchant.enhancement.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import enchant.enhancement.config.EnchantmentConfig;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import enchant.enhancement.util.EnchantmentRegistry;

@Mixin(Enchantment.class)
public class EnchantmentCompatibilityMixin {
    
    @ModifyReturnValue(method = "canBeCombined", at = @At("RETURN"))
    private static boolean hookCanBeCombined(boolean original, RegistryEntry<Enchantment> first, RegistryEntry<Enchantment> second) {
        Enchantment selfEnchantment = first.value();
        Enchantment otherEnchantment = second.value();
        
        // 获取两个附魔的ID
        Identifier selfId = EnchantmentRegistry.getId(selfEnchantment);
        Identifier otherId = EnchantmentRegistry.getId(otherEnchantment);
        
        if (selfId != null && otherId != null) {
            
            // 盔甲保护兼容
            if (EnchantmentConfig.isArmorProtectionCompatibility()) {
                // 检查是否都是保护类附魔
                boolean selfIsProtection = isProtectionEnchantment(selfId);
                boolean otherIsProtection = isProtectionEnchantment(otherId);
                if (selfIsProtection && otherIsProtection) {
                    // 允许保护类附魔共存
                    return true;
                }
            }
            
            // 武器附魔兼容
            if (EnchantmentConfig.isWeaponEnchantmentCompatibility()) {
                // 检查是否都是武器伤害类附魔（锋利、亡灵杀手、节肢杀手）
                boolean selfIsWeaponDamage = isWeaponDamageEnchantment(selfId);
                boolean otherIsWeaponDamage = isWeaponDamageEnchantment(otherId);
                if (selfIsWeaponDamage && otherIsWeaponDamage) {
                    // 允许武器伤害类附魔共存
                    return true;
                }
            }
            
            // 斧头附魔拓展
            if (EnchantmentConfig.isAxeEnchantmentExpansion()) {
                boolean selfIsAxeExpansion = isAxeExpansionEnchantment(selfId);
                boolean otherIsAxeExpansion = isAxeExpansionEnchantment(otherId);
                if ((selfIsAxeExpansion && otherIsAxeExpansion) ||
                    (selfIsAxeExpansion && isAllowedAxeEnchantment(otherId)) ||
                    (otherIsAxeExpansion && isAllowedAxeEnchantment(selfId))) {
                    return true;
                }
            }
            
            // 弓附魔抢夺
            if (EnchantmentConfig.isBowLootingEnchantment()) {
                boolean selfIsLooting = "minecraft:looting".equals(selfId.toString());
                boolean otherIsLooting = "minecraft:looting".equals(otherId.toString());
                if ((selfIsLooting && isBowEnchantment(otherId)) ||
                    (otherIsLooting && isBowEnchantment(selfId))) {
                    return true;
                }
            }
            
            // 三叉戟附魔拓展
            if (EnchantmentConfig.isTridentEnchantmentExpansion()) {
                // 允许三叉戟附魔锋利、亡灵杀手、节肢杀手、击退、火焰附加、抢夺、快速装填
                boolean selfIsTridentExpansion = isTridentExpansionEnchantment(selfId);
                boolean otherIsTridentExpansion = isTridentExpansionEnchantment(otherId);
                if (selfIsTridentExpansion && otherIsTridentExpansion) {
                    // 允许三叉戟拓展附魔共存
                    return true;
                }
                // 快速装填与激流、忠诚、唤雷、穿刺共存
                boolean selfIsQuickCharge = "minecraft:quick_charge".equals(selfId.toString());
                boolean otherIsQuickCharge = "minecraft:quick_charge".equals(otherId.toString());
                boolean selfIsTridentSpecific = isTridentSpecificEnchantment(selfId);
                boolean otherIsTridentSpecific = isTridentSpecificEnchantment(otherId);
                if ((selfIsQuickCharge && otherIsTridentSpecific) ||
                    (otherIsQuickCharge && selfIsTridentSpecific)) {
                    return true;
                }
            }
            
            // 特殊兼容组合（原版不允许但合理的组合）
            // 三叉戟相关附魔组合
            boolean isRiptide = "minecraft:riptide".equals(selfId.toString()) || "minecraft:riptide".equals(otherId.toString());
            boolean isLoyalty = "minecraft:loyalty".equals(selfId.toString()) || "minecraft:loyalty".equals(otherId.toString());
            boolean isChanneling = "minecraft:channeling".equals(selfId.toString()) || "minecraft:channeling".equals(otherId.toString());
            boolean isQuickCharge = "minecraft:quick_charge".equals(selfId.toString()) || "minecraft:quick_charge".equals(otherId.toString());
            boolean isImpaling = "minecraft:impaling".equals(selfId.toString()) || "minecraft:impaling".equals(otherId.toString());
            
            // 允许三叉戟附魔与快速装填共存
            if ((isRiptide || isLoyalty || isChanneling || isImpaling) && isQuickCharge) {
                return true;
            }
            
            // 允许激流与忠诚共存
            if (isRiptide && isLoyalty) {
                return true;
            }
            
            // 允许激流与唤雷共存
            if (isRiptide && isChanneling) {
                return true;
            }
            
            // 允许忠诚与唤雷共存
            if (isLoyalty && isChanneling) {
                return true;
            }
            
            // 允许穿透与多重射击共存（弩的附魔）
            boolean isPiercing = "minecraft:piercing".equals(selfId.toString()) || "minecraft:piercing".equals(otherId.toString());
            boolean isMultishot = "minecraft:multishot".equals(selfId.toString()) || "minecraft:multishot".equals(otherId.toString());
            
            if (isPiercing && isMultishot) {
                return true;
            }
        }
        
        // 如果没有特殊兼容规则，返回原始值
        return original;
    }
    
    // 辅助方法
    private static boolean isProtectionEnchantment(Identifier id) {
        String idStr = id.toString();
        return idStr.equals("minecraft:protection") ||
               idStr.equals("minecraft:fire_protection") ||
               idStr.equals("minecraft:blast_protection") ||
               idStr.equals("minecraft:projectile_protection");
    }
    
    private static boolean isWeaponDamageEnchantment(Identifier id) {
        String idStr = id.toString();
        return idStr.equals("minecraft:sharpness") ||
               idStr.equals("minecraft:smite") ||
               idStr.equals("minecraft:bane_of_arthropods");
    }
    
    private static boolean isAxeExpansionEnchantment(Identifier id) {
        String idStr = id.toString();
        return idStr.equals("minecraft:fire_aspect") ||
               idStr.equals("minecraft:looting");
    }
    
    private static boolean isAllowedAxeEnchantment(Identifier id) {
        // 允许斧头附魔的列表
        String idStr = id.toString();
        return idStr.equals("minecraft:efficiency") ||
               idStr.equals("minecraft:unbreaking") ||
               idStr.equals("minecraft:sharpness") ||
               idStr.equals("minecraft:smite") ||
               idStr.equals("minecraft:bane_of_arthropods") ||
               idStr.equals("minecraft:sweeping_edge");
    }
    
    private static boolean isBowEnchantment(Identifier id) {
        String idStr = id.toString();
        return idStr.equals("minecraft:power") ||
               idStr.equals("minecraft:punch") ||
               idStr.equals("minecraft:flame") ||
               idStr.equals("minecraft:infinity") ||
               idStr.equals("minecraft:multishot") ||
               idStr.equals("minecraft:quick_charge") ||
               idStr.equals("minecraft:piercing");
    }
    
    private static boolean isTridentExpansionEnchantment(Identifier id) {
        String idStr = id.toString();
        return idStr.equals("minecraft:sharpness") ||
               idStr.equals("minecraft:smite") ||
               idStr.equals("minecraft:bane_of_arthropods") ||
               idStr.equals("minecraft:knockback") ||
               idStr.equals("minecraft:fire_aspect") ||
               idStr.equals("minecraft:looting") ||
               idStr.equals("minecraft:quick_charge");
    }
    
    private static boolean isTridentSpecificEnchantment(Identifier id) {
        String idStr = id.toString();
        return idStr.equals("minecraft:riptide") ||
               idStr.equals("minecraft:loyalty") ||
               idStr.equals("minecraft:channeling") ||
               idStr.equals("minecraft:impaling");
    }
    
    @ModifyReturnValue(method = "isAcceptableItem", at = @At("RETURN"))
    public boolean hookIsAcceptableItem(boolean original, ItemStack stack) {
        Enchantment self = (Enchantment) (Object) this;
        Identifier selfId = EnchantmentRegistry.getId(self);
        
        if (selfId != null) {
            // 斧头附魔拓展：允许斧头附魔火焰附加和抢夺
            if (EnchantmentConfig.isAxeEnchantmentExpansion()) {
                if (stack.getItem() instanceof AxeItem) {
                    if (selfId.toString().equals("minecraft:fire_aspect") || 
                        selfId.toString().equals("minecraft:looting")) {
                        return true;
                    }
                }
            }
            
            // 弓附魔抢夺：允许弓附魔抢夺
            if (EnchantmentConfig.isBowLootingEnchantment()) {
                if (stack.getItem() instanceof BowItem || 
                    stack.getItem() instanceof CrossbowItem) {
                    if (selfId.toString().equals("minecraft:looting")) {
                        return true;
                    }
                }
            }
            
            // 三叉戟附魔拓展：允许三叉戟附魔锋利、亡灵杀手、节肢杀手、击退、火焰附加、抢夺、快速装填
            if (EnchantmentConfig.isTridentEnchantmentExpansion()) {
                if (stack.getItem() instanceof TridentItem) {
                    if (isTridentExpansionEnchantment(selfId)) {
                        return true;
                    }
                }
            }
        }
        
        return original;
    }
}