package enchant.enhancement.event;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import enchant.enhancement.config.EnchantmentConfig;
import enchant.enhancement.network.ConfigSyncPacket;
import enchant.enhancement.network.EnchantNetwork;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerPlayerJoinListener {
    private static MinecraftServer server;
    // 记录需要同步的玩家及其延迟计时器
    private static final Map<ServerPlayerEntity, Integer> pendingSyncPlayers = new ConcurrentHashMap<>();

    public static void register(MinecraftServer serverInstance) {
        server = serverInstance;
        
        // 注册玩家加入事件
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            // 40 tick 延迟后同步配置
            pendingSyncPlayers.put(player, 40);
        });

        // 注册玩家断开连接事件，清理待同步记录
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            pendingSyncPlayers.remove(handler.getPlayer());
        });
    }

    // 每tick调用，处理延迟同步队列
    public static void tickPendingSync() {
        if (server == null) return;
        
        Iterator<Map.Entry<ServerPlayerEntity, Integer>> it = pendingSyncPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ServerPlayerEntity, Integer> entry = it.next();
            ServerPlayerEntity player = entry.getKey();
            int ticks = entry.getValue();
            
            if (player.isDisconnected()) {
                it.remove();
                continue;
            }
            
            ticks--;
            if (ticks <= 0) {
                it.remove();
                sendConfigSyncPacket(player);
            } else {
                // ConcurrentHashMap entry.setValue 不支持，改用 remove + put
                it.remove();
                pendingSyncPlayers.put(player, ticks);
            }
        }
    }

    // 向所有在线玩家同步配置
    public static void syncToAllPlayers(Map<String, Object> generalConfig, Map<String, Integer> enchantmentsConfig) {
        if (server == null) return;
        
        ConfigSyncPacket packet = new ConfigSyncPacket(generalConfig, enchantmentsConfig);
        EnchantNetwork.ConfigSyncPayload payload = new EnchantNetwork.ConfigSyncPayload(packet);
        
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
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
