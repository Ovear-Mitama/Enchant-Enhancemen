package damage.engine;

import damage.engine.client.gui.DamageConfigScreen;
import damage.engine.network.DamagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

import damage.engine.hud.DamageHud;
import damage.engine.hud.DamageIndicator;
import damage.engine.hud.DamageSessionManager;
import damage.engine.hud.RatingManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.text.Text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DamageEngineClient implements ClientModInitializer {
    public static KeyBinding configKeyBinding;
    public static KeyBinding toggleHudKeyBinding;
    public static KeyBinding clearDamageKeyBinding;
    
    public static final Logger LOGGER = LoggerFactory.getLogger("damage-engine");
    private static final String KEYBIND_CATEGORY = "key.category.damage-engine.general";

    private static KeyBinding registerKeyBinding(String translationKey, int defaultKeyCode) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
            translationKey,
            defaultKeyCode,
            KEYBIND_CATEGORY
        ));
    }
    
    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(new DamageHud());
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            DamageIndicator.render(context, tickCounter.getTickDelta(false));
        });
        
        // Capture world projection matrix for FOV/zoom compatibility
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            DamageIndicator.captureProjection(new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix()));
        });

        configKeyBinding = registerKeyBinding("key.damage_engine.config", GLFW.GLFW_KEY_UNKNOWN);
        toggleHudKeyBinding = registerKeyBinding("key.damage_engine.toggle_hud", GLFW.GLFW_KEY_UNKNOWN);
        clearDamageKeyBinding = registerKeyBinding("key.damage_engine.clear_damage", GLFW.GLFW_KEY_UNKNOWN);
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                // 首次进入世界时显示提示信息
                DamageEngineConfig config = DamageEngineConfig.getInstance();
                if (!config.hasShownWelcomeMessage) {
                    client.player.sendMessage(
                        Text.translatable("text.damage-engine.welcome_message").withColor(0xB1EAC2),
                        false
                    );
                    config.hasShownWelcomeMessage = true;
                    config.save();
                }
                
                if (configKeyBinding != null) {
                    while (configKeyBinding.wasPressed()) {
                        client.setScreen(new DamageConfigScreen(client.currentScreen));
                    }
                }
                if (toggleHudKeyBinding != null) {
                    while (toggleHudKeyBinding.wasPressed()) {
                        config.showDamage = !config.showDamage;
                        config.save();
                    }
                }
                if (clearDamageKeyBinding != null) {
                    while (clearDamageKeyBinding.wasPressed()) {
                        DamageSessionManager.getInstance().reset();
                        DamageIndicator.clearAll();
                    }
                }
            }

            if (!client.isPaused()) {
                DamageSessionManager.getInstance().tick();
                DamageIndicator.tickAndCleanup();
            }
        });
        
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("damage_engine")
                .then(ClientCommandManager.literal("clear")
                    .executes(ctx -> {
                        DamageSessionManager.getInstance().reset();
                        DamageIndicator.clearAll();
                        if (ctx.getSource().getPlayer() != null) {
                            ctx.getSource().getPlayer().sendMessage(
                                Text.literal("[Damage Engine] " ).withColor(0xB3EDC4).append(Text.translatable("text.damage-engine.cleared").withColor(0xFFFFFF)),
                                false
                            );
                        }
                        return 1;
                    }))

            );
            

        });

        ClientPlayNetworking.registerGlobalReceiver(DamagePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().world == null || context.client().player == null) return;
                
                DamageEngineConfig config = DamageEngineConfig.getInstance();
                
                // Debug: show damage info (includes server-side debugInfo)
                if (config.debugShowDamageInfo) {
                    if (payload.debugInfo() != null && !payload.debugInfo().isEmpty()) {
                        context.client().player.sendMessage(
                            Text.literal("[DE Debug] ").withColor(0xB3EDC4).append(Text.literal(payload.debugInfo()).withColor(0xFFFFFF)),
                            false
                        );
                    }
                    context.client().player.sendMessage(
                        Text.literal("[DE Debug] ").withColor(0xB3EDC4)
                            .append(Text.literal("伤害: " + String.format("%.1f", payload.amount()) 
                            + (payload.isCrit() ? " 暴击" : "") + " | 实体: " + payload.entityId()
                            + " | 投射物: " + (payload.isProjectile() ? "是" : "否")).withColor(0xFFFFFF)),
                        false
                    );
                }

                if (payload.attackerId() != context.client().player.getId()) {
                    return;
                }
                
                boolean preferSwitchTarget = false;
                try {
                    if (context.client().crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult ehr) {
                        preferSwitchTarget = ehr.getEntity() != null && ehr.getEntity().getId() == payload.entityId();
                    }
                } catch (Exception ignored) {
                }
                
                DamageSessionManager.getInstance().addDamage(payload.amount(), payload.isCrit(), payload.entityId(), preferSwitchTarget);
                
                // Add damage indicator
                if (config.showDamageIndicator && payload.amount() > 0) {
                    DamageIndicator.addIndicator(payload.posX(), payload.posY(), payload.posZ(),
                        payload.amount(), payload.isCrit(), false);
                }
                if (config.showKillIndicator && payload.killed()) {
                    DamageIndicator.addIndicator(payload.posX(), payload.posY(), payload.posZ(),
                        payload.amount(), false, true);
                }
                
                // Debug: show rating
                if (config.debugShowRating && context.client().player != null) {
                    RatingManager rm = RatingManager.getInstance();
                    if (rm.isVisible()) {
                        context.client().player.sendMessage(
                            Text.literal("[DE Debug] ").withColor(0xB3EDC4)
                                .append(Text.literal("当前评分: " + rm.getGrade() + " | 分数: " + String.format("%.1f", rm.getScore())).withColor(0xFFFFFF)),
                            true
                        );
                    }
                }
            });
        });
        

    }
}
