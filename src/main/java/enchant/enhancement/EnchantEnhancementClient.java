package enchant.enhancement;

import enchant.enhancement.config.EnchantmentConfig;
import enchant.enhancement.network.EnchantNetwork;
import net.fabricmc.api.ClientModInitializer;

public class EnchantEnhancementClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 注册网络包接收器
        EnchantNetwork.registerClientPackets();
        // 加载本地配置（如果未同步）
        EnchantmentConfig.load();
    }
}
