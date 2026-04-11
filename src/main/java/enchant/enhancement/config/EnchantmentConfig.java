package enchant.enhancement.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.util.Identifier;
import net.fabricmc.loader.api.FabricLoader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class EnchantmentConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("enchant_enhancement");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    private static final Map<String, Integer> MAX_LEVELS = new HashMap<>();
    private static final Map<String, Integer> DEFAULT_LEVELS = new HashMap<>();
    // 常规配置
    private static boolean mergeHighEnchantments = true;
    private static boolean lootHighEnchantments = true;
    private static boolean armorProtectionCompatibility = true;
    private static boolean weaponEnchantmentCompatibility = true;
    private static boolean axeEnchantmentExpansion = false;
    private static boolean bowLootingEnchantment = true;
    private static boolean tridentEnchantmentExpansion = true;
    private static boolean infinityWithoutArrow = true;
    private static boolean loaded = false;
    
    public static Integer getMaxLevel(Identifier enchantmentId) {
        loadIfNeeded();
        return MAX_LEVELS.get(enchantmentId.toString());
    }
    
    public static Integer getDefaultLevel(Identifier enchantmentId) {
        loadIfNeeded();
        return DEFAULT_LEVELS.get(enchantmentId.toString());
    }
    
    public static void setMaxLevel(Identifier enchantmentId, int level) {
        loadIfNeeded();
        MAX_LEVELS.put(enchantmentId.toString(), level);
    }
    
    private static void ensureConfigDir() throws IOException {
        if (!Files.exists(CONFIG_DIR)) {
            Files.createDirectories(CONFIG_DIR);
        }
    }
    
    public static void save() {
        try {
            ensureConfigDir();
            Map<String, Object> configMap = new HashMap<>();
            Map<String, Object> generalConfig = new HashMap<>();
            generalConfig.put("mergeHighEnchantments", mergeHighEnchantments);
            generalConfig.put("lootHighEnchantments", lootHighEnchantments);
            generalConfig.put("armorProtectionCompatibility", armorProtectionCompatibility);
            generalConfig.put("weaponEnchantmentCompatibility", weaponEnchantmentCompatibility);
            generalConfig.put("axeEnchantmentExpansion", axeEnchantmentExpansion);
            generalConfig.put("bowLootingEnchantment", bowLootingEnchantment);
            generalConfig.put("tridentEnchantmentExpansion", tridentEnchantmentExpansion);
            generalConfig.put("infinityWithoutArrow", infinityWithoutArrow);
            configMap.put("general", generalConfig);
            configMap.put("enchantments", MAX_LEVELS);
            
            try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(configMap, writer);
            }
        } catch (IOException e) {
        }
    }
    
    public static void load() {
        try {
            ensureConfigDir();
        } catch (IOException e) {
        }
        
        if (Files.exists(CONFIG_FILE)) {
            try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE)) {
                // 解析JSON为Map
                Map<String, Object> configMap = GSON.fromJson(reader, new TypeToken<Map<String, Object>>(){}.getType());
                if (configMap != null) {
                    // 加载常规配置
                    if (configMap.containsKey("general")) {
                        Object generalObj = configMap.get("general");
                        if (generalObj instanceof Map) {
                            Map<?, ?> generalMap = (Map<?, ?>) generalObj;
                            mergeHighEnchantments = getBoolean(generalMap, "mergeHighEnchantments", true);
                            lootHighEnchantments = getBoolean(generalMap, "lootHighEnchantments", true);
                            armorProtectionCompatibility = getBoolean(generalMap, "armorProtectionCompatibility", true);
                            weaponEnchantmentCompatibility = getBoolean(generalMap, "weaponEnchantmentCompatibility", true);
                            axeEnchantmentExpansion = getBoolean(generalMap, "axeEnchantmentExpansion", false);
                            bowLootingEnchantment = getBoolean(generalMap, "bowLootingEnchantment", true);
                            tridentEnchantmentExpansion = getBoolean(generalMap, "tridentEnchantmentExpansion", true);
                            infinityWithoutArrow = getBoolean(generalMap, "infinityWithoutArrow", true);
                        }
                    }
                    
                    // 加载附魔等级配置
                    if (configMap.containsKey("enchantments")) {
                        Object enchantmentsObj = configMap.get("enchantments");
                        if (enchantmentsObj instanceof Map) {
                            Map<?, ?> enchantmentsMap = (Map<?, ?>) enchantmentsObj;
                            MAX_LEVELS.clear();
                            for (Map.Entry<?, ?> entry : enchantmentsMap.entrySet()) {
                                if (entry.getKey() instanceof String && entry.getValue() instanceof Number) {
                                    MAX_LEVELS.put((String) entry.getKey(), ((Number) entry.getValue()).intValue());
                                }
                            }
                        }
                    } else {
                        // 旧格式兼容：直接加载整个Map作为附魔等级
                        MAX_LEVELS.clear();
                        for (Map.Entry<?, ?> entry : configMap.entrySet()) {
                            if (entry.getKey() instanceof String && entry.getValue() instanceof Number) {
                                MAX_LEVELS.put((String) entry.getKey(), ((Number) entry.getValue()).intValue());
                            }
                        }
                    }
                } else {
                    initializeDefaults();
                    save();
                }
            } catch (IOException e) {
                initializeDefaults();
                save();
            }
        } else {
            initializeDefaults();
            save();
        }
        
        // 如果附魔等级为空，用默认值初始化
        if (MAX_LEVELS.isEmpty()) {
            initializeDefaults();
            save();
        }
        
        loaded = true;
    }
    
    private static boolean getBoolean(Map<?, ?> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    
    private static void loadIfNeeded() {
        if (!loaded) {
            load();
        }
    }
    
    private static void initializeDefaults() {
        
        // 根据用户要求设置的默认值
        // 格式：附魔ID, 默认等级
        Object[][] enchantmentDefaults = {
            // 设置默认值的附魔
            {"minecraft:efficiency", 5},           // 效率
            {"minecraft:looting", 5},              // 抢夺
            {"minecraft:soul_speed", 3},           // 灵魂疾行
            {"minecraft:quick_charge", 3},         // 快速装填
            {"minecraft:fortune", 5},              // 时运
            {"minecraft:aqua_affinity", 5},        // 水下速掘
            {"minecraft:multishot", 5},            // 多重射击（用户未指定等级，暂定5）
            {"minecraft:loyalty", 3},              // 忠诚
            {"minecraft:knockback", 2},            // 冲击
            {"minecraft:power", 10},               // 力量
            {"minecraft:projectile_protection", 6}, // 弹射物保护
            {"minecraft:sharpness", 10},           // 锋利
            {"minecraft:frost_walker", 3},         // 冰霜行者
            {"minecraft:fire_protection", 6},      // 火焰保护
            {"minecraft:impaling", 8},             // 穿刺
            {"minecraft:luck_of_the_sea", 5},      // 海之眷顾
            {"minecraft:riptide", 3},              // 激流
            {"minecraft:mending", 3},              // 经验修补
            {"minecraft:respiration", 5},          // 水下呼吸
            {"minecraft:protection", 6},           // 保护
            {"minecraft:piercing", 4},             // 穿透
            {"minecraft:feather_falling", 5},      // 摔落缓冲
            {"minecraft:swift_sneak", 3},          // 迅捷潜行
            {"minecraft:unbreaking", 5},           // 耐久
            {"minecraft:smite", 5},                // 亡灵杀手
            {"minecraft:lure", 3},                 // 饵钓
            {"minecraft:fire_aspect", 2},          // 火焰附加
            {"minecraft:sweeping", 5},             // 横扫之刃
            {"minecraft:blast_protection", 6},     // 爆炸保护
            {"minecraft:bane_of_arthropods", 5},   // 节肢杀手
            {"minecraft:thorns", 3},               // 荆棘
            {"minecraft:protection", 6},
            {"minecraft:fire_protection", 6},
            {"minecraft:feather_falling", 5},
            {"minecraft:blast_protection", 6},
            {"minecraft:projectile_protection", 6},
            {"minecraft:respiration", 5},
            {"minecraft:aqua_affinity", 5},
            {"minecraft:thorns", 3},
            {"minecraft:soul_speed", 3},
            {"minecraft:swift_sneak", 3},
            {"minecraft:sharpness", 10},
            {"minecraft:smite", 5},
            {"minecraft:bane_of_arthropods", 5},
            {"minecraft:knockback", 2},
            {"minecraft:fire_aspect", 2},
            {"minecraft:looting", 5},
            {"minecraft:sweeping", 5},
            {"minecraft:efficiency", 5},
            {"minecraft:unbreaking", 5},
            {"minecraft:fortune", 5},
            {"minecraft:power", 10},
            {"minecraft:punch", 2},                // 冲击（弓）
            {"minecraft:luck_of_the_sea", 5},
            {"minecraft:lure", 3},
            {"minecraft:loyalty", 3},
            {"minecraft:impaling", 8},
            {"minecraft:riptide", 5},
            {"minecraft:multishot", 5},
            {"minecraft:piercing", 4},
            {"minecraft:mending", 3},
            {"minecraft:density", 5},
            {"minecraft:breach", 5},
            {"minecraft:wind_burst", 3},
        };

        // 精准采集 (silk_touch)
        // 火矢 (flame)
        // 引雷 (channeling)
        // 绑定诅咒 (binding_curse)
        // 深海探索者 (depth_strider)
        // 无限 (infinity)
        // 消失诅咒 (vanishing_curse)
        
        for (Object[] entry : enchantmentDefaults) {
            String idStr = (String) entry[0];
            int maxLevel = (Integer) entry[1];
            MAX_LEVELS.put(idStr, maxLevel);
            // 只在第一次出现时添加到默认值映射，避免重复覆盖
            if (!DEFAULT_LEVELS.containsKey(idStr)) {
                DEFAULT_LEVELS.put(idStr, maxLevel);
            }
        }
    }
    
    public static Map<Identifier, Integer> getAllLevels() {
        loadIfNeeded();
        Map<Identifier, Integer> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : MAX_LEVELS.entrySet()) {
            try {
                Identifier id = Identifier.of(entry.getKey());
                result.put(id, entry.getValue());
            } catch (Exception e) {
            }
        }
        return result;
    }
    
    // 常规配置的getter/setter方法
    public static boolean isMergeHighEnchantments() {
        loadIfNeeded();
        return mergeHighEnchantments;
    }
    
    public static void setMergeHighEnchantments(boolean value) {
        loadIfNeeded();
        mergeHighEnchantments = value;
    }
    
    public static boolean isLootHighEnchantments() {
        loadIfNeeded();
        return lootHighEnchantments;
    }
    
    public static void setLootHighEnchantments(boolean value) {
        loadIfNeeded();
        lootHighEnchantments = value;
    }
    


    public static boolean isArmorProtectionCompatibility() {
        loadIfNeeded();
        return armorProtectionCompatibility;
    }

    public static void setArmorProtectionCompatibility(boolean value) {
        loadIfNeeded();
        armorProtectionCompatibility = value;
    }

    public static boolean isWeaponEnchantmentCompatibility() {
        loadIfNeeded();
        return weaponEnchantmentCompatibility;
    }

    public static void setWeaponEnchantmentCompatibility(boolean value) {
        loadIfNeeded();
        weaponEnchantmentCompatibility = value;
    }

    public static boolean isAxeEnchantmentExpansion() {
        loadIfNeeded();
        return axeEnchantmentExpansion;
    }

    public static void setAxeEnchantmentExpansion(boolean value) {
        loadIfNeeded();
        axeEnchantmentExpansion = value;
    }

    public static boolean isBowLootingEnchantment() {
        loadIfNeeded();
        return bowLootingEnchantment;
    }

    public static void setBowLootingEnchantment(boolean value) {
        loadIfNeeded();
        bowLootingEnchantment = value;
    }

    public static boolean isTridentEnchantmentExpansion() {
        loadIfNeeded();
        return tridentEnchantmentExpansion;
    }

    public static void setTridentEnchantmentExpansion(boolean value) {
        loadIfNeeded();
        tridentEnchantmentExpansion = value;
    }



    public static boolean isInfinityWithoutArrow() {
        loadIfNeeded();
        return infinityWithoutArrow;
    }

    public static void setInfinityWithoutArrow(boolean value) {
        loadIfNeeded();
        infinityWithoutArrow = value;
    }
    
    public static void resetToDefaults() {
        loadIfNeeded();
        // 确保默认值已初始化
        if (DEFAULT_LEVELS.isEmpty()) {
            initializeDefaults();
        }
        // 将所有附魔等级重置为默认值
        MAX_LEVELS.clear();
        for (Map.Entry<String, Integer> entry : DEFAULT_LEVELS.entrySet()) {
            MAX_LEVELS.put(entry.getKey(), entry.getValue());
        }
        // 保存到配置文件
        save();
    }
    

}