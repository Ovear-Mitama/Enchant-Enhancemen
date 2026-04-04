package enchant.enhancement.client;

import enchant.enhancement.config.ConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyBindings implements ClientModInitializer {
    public static KeyBinding OPEN_CONFIG_KEY;

    @Override
    public void onInitializeClient() {
        OPEN_CONFIG_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.enchant_enhancement.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, // 默认未指定键位
                "category.enchant_enhancement.general"
        ));

        // 注册按键处理
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_CONFIG_KEY.wasPressed()) {
                var factory = ConfigScreen.getFactory();
                if (factory != null) {
                    var screen = factory.apply(client.currentScreen);
                    if (screen != null) {
                        client.setScreen(screen);
                    }
                }
            }
        });
    }
}