package enchant.enhancement;

import enchant.enhancement.client.KeyBindings;
import enchant.enhancement.config.EnchantmentConfig;
import enchant.enhancement.event.ServerPlayerJoinListener;
import enchant.enhancement.network.EnchantNetwork;
import enchant.enhancement.platform.EnchantEnhancementPlatform;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(EnchantEnhancement.MOD_ID)
public class EnchantEnhancement {
    public static final String MOD_ID = "enchant_enhancement";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public EnchantEnhancement(IEventBus modEventBus, ModContainer modContainer) {
        // 注入平台实现（配置目录等）
        EnchantEnhancementPlatform.setInstance(new EnchantEnhancementPlatformImpl());

        // 注册网络 payload（MOD 总线）
        modEventBus.addListener(EnchantNetwork::register);

        // 注册按键映射（MOD 总线）
        modEventBus.addListener(KeyBindings::registerKeyMappings);

        // 服务端事件（GAME 总线）
        NeoForge.EVENT_BUS.register(ServerPlayerJoinListener.class);
        NeoForge.EVENT_BUS.addListener(EnchantEnhancement::onServerStarting);
        NeoForge.EVENT_BUS.addListener(EnchantEnhancement::onServerTick);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // 服务端启动时初始化（专用服务器和LAN集成服务器统一处理）
        EnchantmentConfig.setIsServer(true);
        EnchantmentConfig.load();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 每tick处理玩家的延迟配置同步队列
        ServerPlayerJoinListener.tickPendingSync();
    }
}
