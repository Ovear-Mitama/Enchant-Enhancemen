package enchant.enhancement.event;

import enchant.enhancement.config.EnchantmentConfig;
import enchant.enhancement.network.ConfigSyncPacket;
import enchant.enhancement.network.EnchantNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerPlayerJoinListener {
    private static MinecraftServer server;
    // 记录需要同步的玩家及其延迟计时器
    private static final Map<ServerPlayer, Integer> pendingSyncPlayers = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            server = player.getServer();
            // 40 tick 延迟后同步配置
            pendingSyncPlayers.put(player, 40);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 清理待同步记录
            pendingSyncPlayers.remove(player);
        }
    }

    // 每tick调用，处理延迟同步队列
    public static void tickPendingSync() {
        if (server == null) return;

        Iterator<Map.Entry<ServerPlayer, Integer>> it = pendingSyncPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ServerPlayer, Integer> entry = it.next();
            ServerPlayer player = entry.getKey();
            int ticks = entry.getValue();

            if (player.hasDisconnected()) {
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

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static void sendConfigSyncPacket(ServerPlayer player) {
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
        PacketDistributor.sendToPlayer(player, payload);
    }
}
