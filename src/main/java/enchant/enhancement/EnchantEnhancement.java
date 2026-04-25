package enchant.enhancement;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import enchant.enhancement.config.EnchantmentConfig;
import enchant.enhancement.network.EnchantNetwork;
import enchant.enhancement.event.ServerPlayerJoinListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnchantEnhancement implements ModInitializer {
	public static final String MOD_ID = "enchant-enhancement";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 通用初始化
	}
}

class EnchantEnhancementClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 客户端初始化
		EnchantNetwork.registerClientPackets();
		// 加载客户端配置（如果未同步）
		EnchantmentConfig.load();
	}
}

class EnchantEnhancementServer implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		// 服务端初始化
		EnchantmentConfig.setIsServer(true);
		EnchantmentConfig.load();
		ServerPlayerJoinListener.register();
	}
}