package enchant.enhancement.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import enchant.enhancement.config.EnchantmentConfig;
import enchant.enhancement.network.ConfigSyncPacket;
import enchant.enhancement.network.EnchantNetwork;

import java.util.*;

public class ServerPlayerJoinListener {
    private static MinecraftServer serverInstance;
    // 等待同步的玩家及其延迟（tick）
    private static final Map<UUID, Integer> pendingSyncPlayers = new HashMap<>();
    private static final int SYNC_DELAY_TICKS = 40; // 2秒后同步

    public static void register() {
        // 捕获服务器实例
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverInstance = server;
            pendingSyncPlayers.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            serverInstance = null;
            pendingSyncPlayers.clear();
        });

        // 玩家加入时加入等待队列
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            pendingSyncPlayers.put(handler.getPlayer().getUuid(), SYNC_DELAY_TICKS);
        });

        // 玩家断开时从队列移除
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            pendingSyncPlayers.remove(handler.getPlayer().getUuid());
        });

        // 每个 tick 处理等待队列
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (pendingSyncPlayers.isEmpty()) return;

            Iterator<Map.Entry<UUID, Integer>> it = pendingSyncPlayers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                int remaining = entry.getValue() - 1;
                if (remaining <= 0) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                    if (player != null) {
                        sendConfigSyncPacket(player);
                    }
                    it.remove();
                } else {
                    entry.setValue(remaining);
                }
            }
        });
    }

    // 向所有在线玩家同步配置
    public static void syncToAllPlayers() {
        if (serverInstance == null) return;
        for (ServerPlayerEntity player : serverInstance.getPlayerManager().getPlayerList()) {
            sendConfigSyncPacket(player);
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
        PacketByteBuf buf = PacketByteBufs.create();
        packet.write(buf);
        ServerPlayNetworking.send(player, EnchantNetwork.CONFIG_SYNC_ID, buf);
    }
}
