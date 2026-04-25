package enchant.enhancement.util;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.util.Identifier;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class EnchantmentRegistry {
    
    // 缓存已知的附魔ID，避免重复反射调用
    private static final Map<Enchantment, Identifier> ID_CACHE = new HashMap<>();
    
    // 常见附魔的中文名到ID的映射
    private static final Map<String, Identifier> CHINESE_NAME_TO_ID = new HashMap<>();
    
    static {
        // 初始化中文附魔名到ID的映射
        CHINESE_NAME_TO_ID.put("激流", Identifier.of("minecraft", "riptide"));
        CHINESE_NAME_TO_ID.put("锋利", Identifier.of("minecraft", "sharpness"));
        CHINESE_NAME_TO_ID.put("保护", Identifier.of("minecraft", "protection"));
        CHINESE_NAME_TO_ID.put("火焰保护", Identifier.of("minecraft", "fire_protection"));
        CHINESE_NAME_TO_ID.put("摔落保护", Identifier.of("minecraft", "feather_falling"));
        CHINESE_NAME_TO_ID.put("爆炸保护", Identifier.of("minecraft", "blast_protection"));
        CHINESE_NAME_TO_ID.put("弹射物保护", Identifier.of("minecraft", "projectile_protection"));
        CHINESE_NAME_TO_ID.put("水下呼吸", Identifier.of("minecraft", "respiration"));
        CHINESE_NAME_TO_ID.put("水下速掘", Identifier.of("minecraft", "aqua_affinity"));
        CHINESE_NAME_TO_ID.put("荆棘", Identifier.of("minecraft", "thorns"));
        CHINESE_NAME_TO_ID.put("深海探索者", Identifier.of("minecraft", "depth_strider"));
        CHINESE_NAME_TO_ID.put("冰霜行者", Identifier.of("minecraft", "frost_walker"));
        CHINESE_NAME_TO_ID.put("绑定诅咒", Identifier.of("minecraft", "binding_curse"));
        CHINESE_NAME_TO_ID.put("消失诅咒", Identifier.of("minecraft", "vanishing_curse"));
        CHINESE_NAME_TO_ID.put("亡灵杀手", Identifier.of("minecraft", "smite"));
        CHINESE_NAME_TO_ID.put("节肢杀手", Identifier.of("minecraft", "bane_of_arthropods"));
        CHINESE_NAME_TO_ID.put("击退", Identifier.of("minecraft", "knockback"));
        CHINESE_NAME_TO_ID.put("火焰附加", Identifier.of("minecraft", "fire_aspect"));
        CHINESE_NAME_TO_ID.put("抢夺", Identifier.of("minecraft", "looting"));
        CHINESE_NAME_TO_ID.put("横扫之刃", Identifier.of("minecraft", "sweeping_edge"));
        CHINESE_NAME_TO_ID.put("效率", Identifier.of("minecraft", "efficiency"));
        CHINESE_NAME_TO_ID.put("精准采集", Identifier.of("minecraft", "silk_touch"));
        CHINESE_NAME_TO_ID.put("耐久", Identifier.of("minecraft", "unbreaking"));
        CHINESE_NAME_TO_ID.put("时运", Identifier.of("minecraft", "fortune"));
        CHINESE_NAME_TO_ID.put("力量", Identifier.of("minecraft", "power"));
        CHINESE_NAME_TO_ID.put("冲击", Identifier.of("minecraft", "punch"));
        CHINESE_NAME_TO_ID.put("火矢", Identifier.of("minecraft", "flame"));
        CHINESE_NAME_TO_ID.put("无限", Identifier.of("minecraft", "infinity"));
        CHINESE_NAME_TO_ID.put("海之眷顾", Identifier.of("minecraft", "luck_of_the_sea"));
        CHINESE_NAME_TO_ID.put("饵钓", Identifier.of("minecraft", "lure"));
        CHINESE_NAME_TO_ID.put("忠诚", Identifier.of("minecraft", "loyalty"));
        CHINESE_NAME_TO_ID.put("引雷", Identifier.of("minecraft", "channeling"));
        CHINESE_NAME_TO_ID.put("多重射击", Identifier.of("minecraft", "multishot"));
        CHINESE_NAME_TO_ID.put("穿透", Identifier.of("minecraft", "piercing"));
        CHINESE_NAME_TO_ID.put("快速装填", Identifier.of("minecraft", "quick_charge"));
        CHINESE_NAME_TO_ID.put("灵魂疾行", Identifier.of("minecraft", "soul_speed"));
        CHINESE_NAME_TO_ID.put("迅捷潜行", Identifier.of("minecraft", "swift_sneak"));
        CHINESE_NAME_TO_ID.put("经验修补", Identifier.of("minecraft", "mending"));
    }
    
    public static Identifier getId(Enchantment enchantment) {
        if (enchantment == null) {
            return null;
        }
        
        // 检查缓存
        if (ID_CACHE.containsKey(enchantment)) {
            return ID_CACHE.get(enchantment);
        }
        
        // 方法1: 尝试多种可能的方法名获取描述ID
        String[] methodNames = {
            "getTranslationKey", "getDescriptionId", "getName", 
            "getStringId", "getIdString", "getRegistryName", "getKey", "toString"
        };
        
        for (String methodName : methodNames) {
            try {
                Method method = Enchantment.class.getMethod(methodName);
                Object result = method.invoke(enchantment);
                if (result instanceof String) {
                    String descriptionId = (String) result;
                    
                    // 尝试解析ID
                    Identifier id = parseIdFromDescription(descriptionId, enchantment);
                    if (id != null) {
                        ID_CACHE.put(enchantment, id);
                        return id;
                    }
                } else if (result instanceof Identifier) {
                    ID_CACHE.put(enchantment, (Identifier) result);
                    return (Identifier) result;
                }
            } catch (NoSuchMethodException e) {
                // 继续尝试下一个方法
            } catch (Exception e) {
            }
        }
        
        // 方法2: 尝试从附魔对象字符串表示中提取中文名
        try {
            String enchantmentString = enchantment.toString();
            
            // 尝试匹配中文名
            for (Map.Entry<String, Identifier> entry : CHINESE_NAME_TO_ID.entrySet()) {
                if (enchantmentString.contains(entry.getKey())) {
                    Identifier id = entry.getValue();
                    ID_CACHE.put(enchantment, id);
                    return id;
                }
            }
        } catch (Exception e) {
        }
        
        // 方法3: 尝试通过注册表查找（Minecraft 1.21.1兼容方式）
        Identifier id = tryRegistryLookup(enchantment);
        if (id != null) {
            ID_CACHE.put(enchantment, id);
            return id;
        }
        
        return null;
    }
    
    private static Identifier parseIdFromDescription(String description, Enchantment enchantment) {
        if (description == null) {
            return null;
        }
        
        // 格式1: "enchantment.minecraft.sharpness"
        if (description.startsWith("enchantment.")) {
            String[] parts = description.split("\\.");
            if (parts.length >= 3) {
                String namespace = parts[1];
                String path = parts[2];
                return Identifier.of(namespace, path);
            }
        }
        
        // 格式2: "minecraft:sharpness" (直接就是ID)
        if (description.contains(":")) {
            try {
                return Identifier.of(description);
            } catch (Exception e) {
                // 无效的ID格式
            }
        }
        
        // 格式3: 从对象字符串中提取
        String str = enchantment.toString();
        if (str.contains(":")) {
            try {
                // 尝试从类似 "Enchantment[minecraft:sharpness]" 的格式中提取
                int start = str.indexOf("minecraft:");
                if (start != -1) {
                    int end = str.indexOf("]", start);
                    if (end != -1) {
                        String idStr = str.substring(start, end);
                        return Identifier.of(idStr);
                    }
                }
            } catch (Exception e) {
                // 解析失败
            }
        }
        
        return null;
    }
    
    private static Identifier tryRegistryLookup(Enchantment enchantment) {
        try {
            // 尝试多种可能的注册表访问方式
            String[] registryPaths = {
                "net.minecraft.registry.Registries",
                "net.minecraft.core.Registry",
                "net.minecraft.core.registries.BuiltInRegistries"
            };
            
            for (String registryPath : registryPaths) {
                try {
                    Class<?> registriesClass = Class.forName(registryPath);
                    java.lang.reflect.Field enchantmentField = null;
                    
                    // 尝试不同的字段名
                    String[] fieldNames = {"ENCHANTMENT", "ENCHANTMENTS", "enchantment"};
                    for (String fieldName : fieldNames) {
                        try {
                            enchantmentField = registriesClass.getField(fieldName);
                            break;
                        } catch (NoSuchFieldException e) {
                            // 继续尝试下一个字段名
                        }
                    }
                    
                    if (enchantmentField != null) {
                        Object registry = enchantmentField.get(null);
                        Method getIdMethod = registry.getClass().getMethod("getId", Object.class);
                        Object id = getIdMethod.invoke(registry, enchantment);
                        if (id instanceof Identifier) {
                            return (Identifier) id;
                        }
                    }
                } catch (ClassNotFoundException e) {
                    // 继续尝试下一个注册表路径
                }
            }
        } catch (Exception e) {
        }
        
        return null;
    }
}