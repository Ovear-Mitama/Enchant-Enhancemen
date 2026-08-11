package enchant.enhancement;

import enchant.enhancement.config.ConfigScreen;
import enchant.enhancement.config.EnchantmentConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 客户端初始化：注册配置界面工厂并加载本地配置。
 */
@EventBusSubscriber(modid = EnchantEnhancement.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class EnchantEnhancementClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 注册配置界面工厂，使 Mod 列表中的配置按钮可用
        ModList.get().getModContainerById(EnchantEnhancement.MOD_ID).ifPresent(container ->
            container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, parent) -> new ConfigScreen(parent)
            )
        );

        // 加载本地配置（如果未同步）
        EnchantmentConfig.load();
    }
}
