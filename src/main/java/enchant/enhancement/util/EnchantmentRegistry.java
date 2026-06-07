package enchant.enhancement.util;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.util.Identifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class EnchantmentRegistry {
    
    // 线程安全的缓存
    private static final Map<Enchantment, Identifier> ID_CACHE = new ConcurrentHashMap<>();
    
    public static Identifier getId(Enchantment enchantment) {
        if (enchantment == null) {
            return null;
        }
        
        // 先查缓存
        Identifier cached = ID_CACHE.get(enchantment);
        if (cached != null) {
            return cached;
        }
        
        try {
            // 通过 getTranslationKey 获取附魔ID，格式: "enchantment.minecraft.sharpness"
            String key = enchantment.getTranslationKey();
            if (key != null && key.startsWith("enchantment.")) {
                String[] parts = key.split("\\.");
                if (parts.length >= 3) {
                    Identifier id = new Identifier(parts[1], parts[2]);
                    ID_CACHE.put(enchantment, id);
                    return id;
                }
            }
        } catch (Throwable ignored) {
        }
        
        return null;
    }
}
