package enchant.enhancement;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import enchant.enhancement.config.EnchantmentConfig;
import enchant.enhancement.event.ServerPlayerJoinListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnchantEnhancement implements ModInitializer {
	public static final String MOD_ID = "enchant-enhancement";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 服务端启动时初始化（专用服务器和LAN集成服务器统一处理）
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			EnchantmentConfig.setIsServer(true);
			EnchantmentConfig.load();
			ServerPlayerJoinListener.register(server);
		});

		// 每tick处理玩家的延迟配置同步队列
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerPlayerJoinListener.tickPendingSync();
		});
	}
}
