package enchant.enhancement;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import enchant.enhancement.config.EnchantmentConfig;
import enchant.enhancement.event.ServerPlayerJoinListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnchantEnhancement implements ModInitializer {
	public static final String MOD_ID = "enchant-enhancement";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 服务端初始化（专用服务器和集成服务器均触发）
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			EnchantmentConfig.setIsServer(true);
			EnchantmentConfig.load();
			ServerPlayerJoinListener.register();
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			EnchantmentConfig.setIsServer(false);
		});
	}
}