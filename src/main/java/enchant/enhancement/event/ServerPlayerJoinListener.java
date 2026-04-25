package enchant.enhancement.event;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import enchant.enhancement.config.EnchantmentConfig;
import enchant.enhancement.network.ConfigSyncPacket;
import enchant.enhancement.network.EnchantNetwork;

import java.util.HashMap;
import java.util.Map;

public class ServerPlayerJoinListener {
    public static void register() {
        // 注册玩家加入事件
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ServerPlayerEntity player) {
                sendConfigSyncPacket(player);
            }
        });
    }

    private static void sendConfigSyncPacket(ServerPlayerEntity player) {
        // 构建常规配置
        Map<String, Object> generalConfig = new HashMap<>();
        generalConfig.put("mergeHighEnchantments", EnchantmentConfig.isMergeHighEnchantments());
        generalConfig.put("lootHighEnchantments", EnchantmentConfig.isLootHighEnchantments());
        generalConfig.put("creatureHighEnchantmentArmor", EnchantmentConfig.isCreatureHighEnchantmentArmor());
        generalConfig.put("armorProtectionCompatibility", EnchantmentConfig.isArmorProtectionCompatibility());
        generalConfig.put("weaponEnchantmentCompatibility", EnchantmentConfig.isWeaponEnchantmentCompatibility());
        generalConfig.put("axeEnchantmentExpansion", EnchantmentConfig.isAxeEnchantmentExpansion());
        generalConfig.put("bowLootingEnchantment", EnchantmentConfig.isBowLootingEnchantment());
        generalConfig.put("tridentEnchantmentExpansion", EnchantmentConfig.isTridentEnchantmentExpansion());
        generalConfig.put("infinityWithoutArrow", EnchantmentConfig.isInfinityWithoutArrow());
        
        // 构建附魔配置
        Map<String, Integer> enchantmentsConfig = new HashMap<>();
        for (var entry : EnchantmentConfig.getAllLevels().entrySet()) {
            enchantmentsConfig.put(entry.getKey().toString(), entry.getValue());
        }

        // 创建并发送数据包
        ConfigSyncPacket packet = new ConfigSyncPacket(generalConfig, enchantmentsConfig);
        EnchantNetwork.ConfigSyncPayload payload = new EnchantNetwork.ConfigSyncPayload(packet);
        ServerPlayNetworking.send(player, payload);
    }
}
