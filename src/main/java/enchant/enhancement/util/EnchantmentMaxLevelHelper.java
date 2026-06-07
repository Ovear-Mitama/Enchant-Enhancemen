package enchant.enhancement.util;

import enchant.enhancement.config.EnchantmentConfig;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.util.Identifier;

public class EnchantmentMaxLevelHelper {
    public static int getCustomMaxLevel(Enchantment self, int original) {
        try {
            String key = self.getTranslationKey();
            if (key != null && key.startsWith("enchantment.")) {
                String[] parts = key.split("\\.");
                if (parts.length >= 3) {
                    Identifier id = new Identifier(parts[1], parts[2]);
                    Integer customMaxLevel = EnchantmentConfig.getMaxLevel(id);
                    if (customMaxLevel != null && EnchantmentConfig.isMergeHighEnchantments()) {
                        return customMaxLevel;
                    }
                }
            }
        } catch (Throwable t) {
        }
        return original;
    }
}
