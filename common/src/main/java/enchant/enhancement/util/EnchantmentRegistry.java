package enchant.enhancement.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class EnchantmentRegistry {
    
    // 缓存已知的附魔ID，避免重复反射调用
    private static final Map<Enchantment, ResourceLocation> ID_CACHE = new HashMap<>();
    
    // 常见附魔的中文名到ID的映射
    private static final Map<String, ResourceLocation> CHINESE_NAME_TO_ID = new HashMap<>();
    
    static {
        // 初始化中文附魔名到ID的映射
        CHINESE_NAME_TO_ID.put("激流", ResourceLocation.fromNamespaceAndPath("minecraft", "riptide"));
        CHINESE_NAME_TO_ID.put("锋利", ResourceLocation.fromNamespaceAndPath("minecraft", "sharpness"));
        CHINESE_NAME_TO_ID.put("保护", ResourceLocation.fromNamespaceAndPath("minecraft", "protection"));
        CHINESE_NAME_TO_ID.put("火焰保护", ResourceLocation.fromNamespaceAndPath("minecraft", "fire_protection"));
        CHINESE_NAME_TO_ID.put("摔落保护", ResourceLocation.fromNamespaceAndPath("minecraft", "feather_falling"));
        CHINESE_NAME_TO_ID.put("爆炸保护", ResourceLocation.fromNamespaceAndPath("minecraft", "blast_protection"));
        CHINESE_NAME_TO_ID.put("弹射物保护", ResourceLocation.fromNamespaceAndPath("minecraft", "projectile_protection"));
        CHINESE_NAME_TO_ID.put("水下呼吸", ResourceLocation.fromNamespaceAndPath("minecraft", "respiration"));
        CHINESE_NAME_TO_ID.put("水下速掘", ResourceLocation.fromNamespaceAndPath("minecraft", "aqua_affinity"));
        CHINESE_NAME_TO_ID.put("荆棘", ResourceLocation.fromNamespaceAndPath("minecraft", "thorns"));
        CHINESE_NAME_TO_ID.put("深海探索者", ResourceLocation.fromNamespaceAndPath("minecraft", "depth_strider"));
        CHINESE_NAME_TO_ID.put("冰霜行者", ResourceLocation.fromNamespaceAndPath("minecraft", "frost_walker"));
        CHINESE_NAME_TO_ID.put("绑定诅咒", ResourceLocation.fromNamespaceAndPath("minecraft", "binding_curse"));
        CHINESE_NAME_TO_ID.put("消失诅咒", ResourceLocation.fromNamespaceAndPath("minecraft", "vanishing_curse"));
        CHINESE_NAME_TO_ID.put("亡灵杀手", ResourceLocation.fromNamespaceAndPath("minecraft", "smite"));
        CHINESE_NAME_TO_ID.put("节肢杀手", ResourceLocation.fromNamespaceAndPath("minecraft", "bane_of_arthropods"));
        CHINESE_NAME_TO_ID.put("击退", ResourceLocation.fromNamespaceAndPath("minecraft", "knockback"));
        CHINESE_NAME_TO_ID.put("火焰附加", ResourceLocation.fromNamespaceAndPath("minecraft", "fire_aspect"));
        CHINESE_NAME_TO_ID.put("抢夺", ResourceLocation.fromNamespaceAndPath("minecraft", "looting"));
        CHINESE_NAME_TO_ID.put("横扫之刃", ResourceLocation.fromNamespaceAndPath("minecraft", "sweeping_edge"));
        CHINESE_NAME_TO_ID.put("效率", ResourceLocation.fromNamespaceAndPath("minecraft", "efficiency"));
        CHINESE_NAME_TO_ID.put("精准采集", ResourceLocation.fromNamespaceAndPath("minecraft", "silk_touch"));
        CHINESE_NAME_TO_ID.put("耐久", ResourceLocation.fromNamespaceAndPath("minecraft", "unbreaking"));
        CHINESE_NAME_TO_ID.put("时运", ResourceLocation.fromNamespaceAndPath("minecraft", "fortune"));
        CHINESE_NAME_TO_ID.put("力量", ResourceLocation.fromNamespaceAndPath("minecraft", "power"));
        CHINESE_NAME_TO_ID.put("冲击", ResourceLocation.fromNamespaceAndPath("minecraft", "punch"));
        CHINESE_NAME_TO_ID.put("火矢", ResourceLocation.fromNamespaceAndPath("minecraft", "flame"));
        CHINESE_NAME_TO_ID.put("无限", ResourceLocation.fromNamespaceAndPath("minecraft", "infinity"));
        CHINESE_NAME_TO_ID.put("海之眷顾", ResourceLocation.fromNamespaceAndPath("minecraft", "luck_of_the_sea"));
        CHINESE_NAME_TO_ID.put("饵钓", ResourceLocation.fromNamespaceAndPath("minecraft", "lure"));
        CHINESE_NAME_TO_ID.put("忠诚", ResourceLocation.fromNamespaceAndPath("minecraft", "loyalty"));
        CHINESE_NAME_TO_ID.put("引雷", ResourceLocation.fromNamespaceAndPath("minecraft", "channeling"));
        CHINESE_NAME_TO_ID.put("多重射击", ResourceLocation.fromNamespaceAndPath("minecraft", "multishot"));
        CHINESE_NAME_TO_ID.put("穿透", ResourceLocation.fromNamespaceAndPath("minecraft", "piercing"));
        CHINESE_NAME_TO_ID.put("快速装填", ResourceLocation.fromNamespaceAndPath("minecraft", "quick_charge"));
        CHINESE_NAME_TO_ID.put("灵魂疾行", ResourceLocation.fromNamespaceAndPath("minecraft", "soul_speed"));
        CHINESE_NAME_TO_ID.put("迅捷潜行", ResourceLocation.fromNamespaceAndPath("minecraft", "swift_sneak"));
        CHINESE_NAME_TO_ID.put("经验修补", ResourceLocation.fromNamespaceAndPath("minecraft", "mending"));
    }
    
    public static ResourceLocation getId(Enchantment enchantment) {
        if (enchantment == null) {
            return null;
        }
        
        // 检查缓存
        if (ID_CACHE.containsKey(enchantment)) {
            return ID_CACHE.get(enchantment);
        }
        
        // 方法1: 尝试多种可能的方法名获取描述ID
        // 官方映射(Mojang): getDescriptionId / toString
        // Yarn 映射: getTranslationKey / getName / toString
        String[] methodNames = {
            "getDescriptionId", "getTranslationKey", "getName",
            "getStringId", "getIdString", "getRegistryName", "getKey", "toString"
        };
        
        for (String methodName : methodNames) {
            try {
                Method method = Enchantment.class.getMethod(methodName);
                Object result = method.invoke(enchantment);
                if (result instanceof String) {
                    String descriptionId = (String) result;
                    
                    // 尝试解析ID
                    ResourceLocation id = parseIdFromDescription(descriptionId, enchantment);
                    if (id != null) {
                        ID_CACHE.put(enchantment, id);
                        return id;
                    }
                } else if (result instanceof ResourceLocation) {
                    ID_CACHE.put(enchantment, (ResourceLocation) result);
                    return (ResourceLocation) result;
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
            for (Map.Entry<String, ResourceLocation> entry : CHINESE_NAME_TO_ID.entrySet()) {
                if (enchantmentString.contains(entry.getKey())) {
                    ResourceLocation id = entry.getValue();
                    ID_CACHE.put(enchantment, id);
                    return id;
                }
            }
        } catch (Exception e) {
        }
        
        // 方法3: 尝试通过注册表查找（官方映射与兼容方式）
        ResourceLocation id = tryRegistryLookup(enchantment);
        if (id != null) {
            ID_CACHE.put(enchantment, id);
            return id;
        }
        
        return null;
    }
    
    private static ResourceLocation parseIdFromDescription(String description, Enchantment enchantment) {
        if (description == null) {
            return null;
        }
        
        // 格式1: "enchantment.minecraft.sharpness"
        if (description.startsWith("enchantment.")) {
            String[] parts = description.split("\\.");
            if (parts.length >= 3) {
                String namespace = parts[1];
                String path = parts[2];
                return ResourceLocation.fromNamespaceAndPath(namespace, path);
            }
        }
        
        // 格式2: "minecraft:sharpness" (直接就是ID)
        if (description.contains(":")) {
            try {
                return ResourceLocation.parse(description);
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
                        return ResourceLocation.parse(idStr);
                    }
                }
            } catch (Exception e) {
                // 解析失败
            }
        }
        
        return null;
    }
    
    private static ResourceLocation tryRegistryLookup(Enchantment enchantment) {
        try {
            // 尝试多种可能的注册表访问方式
            String[] registryPaths = {
                "net.minecraft.core.registries.BuiltInRegistries",
                "net.minecraft.core.Registry",
                "net.minecraft.registry.Registries"
            };
            
            for (String registryPath : registryPaths) {
                try {
                    Class<?> registriesClass = Class.forName(registryPath);
                    Field enchantmentField = null;
                    
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
                        // 官方映射: Registry.getKey(Object)；Yarn 映射: getId(Object)
                        String[] getKeyMethodNames = {"getKey", "getId"};
                        for (String getKeyMethodName : getKeyMethodNames) {
                            try {
                                Method getKeyMethod = registry.getClass().getMethod(getKeyMethodName, Object.class);
                                Object id = getKeyMethod.invoke(registry, enchantment);
                                if (id instanceof ResourceLocation) {
                                    return (ResourceLocation) id;
                                }
                            } catch (NoSuchMethodException e) {
                                // 继续尝试下一个方法名
                            }
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
