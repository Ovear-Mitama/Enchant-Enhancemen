package enchant.enhancement.client;

import com.mojang.blaze3d.platform.InputConstants;
import enchant.enhancement.EnchantEnhancement;
import enchant.enhancement.config.ConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = EnchantEnhancement.MOD_ID, value = Dist.CLIENT)
public class KeyBindings {
    public static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.enchant_enhancement.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, // 默认未指定键位，可在 选项 -> 控制 -> 附魔增强 中绑定
            "category.enchant_enhancement.general"
    );

    // 由 EnchantEnhancement 构造器注册到 MOD 总线
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        while (OPEN_CONFIG_KEY.consumeClick()) {
            openConfigScreen();
        }
    }

    /**
     * 注册聊天命令 /enchant_enhancement 打开配置界面（客户端命令，作为按键之外的第二入口）。
     */
    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("enchant_enhancement")
                .executes(ctx -> {
                    openConfigScreen();
                    return 1;
                }));
    }

    private static void openConfigScreen() {
        var factory = ConfigScreen.getFactory();
        if (factory != null) {
            Minecraft mc = Minecraft.getInstance();
            var screen = factory.apply(mc.screen);
            if (screen != null) {
                mc.setScreen(screen);
            }
        }
    }
}
