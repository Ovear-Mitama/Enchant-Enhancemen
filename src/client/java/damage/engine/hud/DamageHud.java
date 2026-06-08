package damage.engine.hud;

import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import java.util.ArrayList;
import java.util.List;


public class DamageHud implements HudRenderCallback {
    private float smoothProgress = 0f;
    private boolean isRefilling = false;
    private int lastComboCount = 0;
    private int infoLastTargetId = -1;
    private float infoSmoothRatio = -1f;
    private float infoLagRatio = -1f;
    private float infoHealRatio = -1f;
    private boolean infoLagHold = false;
    private boolean infoHealHold = false;
    private float infoAvatarFactor = 0f;
    private float infoAvatarAlpha = 0f;
    private boolean infoSwitching = false;
    private long infoSwitchStartMs = 0;
    private float infoPrevSmoothRatio = -1f;
    private float infoPrevLagRatio = -1f;
    private float infoPrevHealRatio = -1f;
    private boolean infoPrevDamageTailActive = false;
    private long infoLayoutLastUpdateMs = 0;
    private long infoLastUpdateMs = 0;
    private long infoHealthLastUpdateMs = 0;
    private float infoAlpha = 0f;
    private boolean infoFading = false;
    private long infoFadeStartMs = 0;
    private static final long INFO_FADE_MS = 1000;
    private static final long INFO_SWITCH_MS = 500; // Slower switch as requested
    private boolean infoDeathDrain = false;
    private boolean infoDamageTailActive = false;
    private boolean infoDamageTailPending = false;
    private boolean infoHasSnapshot = false;
    private String infoName = "";
    private boolean infoIsPlayer = false;
    private SkinTextures infoPlayerSkin = null;
    private SkinTextures infoPrevPlayerSkin = null;
    private float infoHealth = 0f;
    private float infoMaxHealth = 1f;
    private float infoAbsorption = 0f;
    


    private long historyAnimStartTime = 0;
    private long lastNewestTimestamp = 0;
    private int prevAnimSize = 0;
    private static final long HISTORY_ANIM_MS = 150;
    private float contentRightX = 20f;
    private String previewGrade = "";
    private int previewGradeColor = 0xFFFFFFFF;
    
    public void cyclePreviewGrades() {
        List<DamageEngineConfig.RatingGrade> grades = DamageEngineConfig.getInstance().ratingGrades;
        if (grades != null && !grades.isEmpty()) {
            int idx = (int)((System.currentTimeMillis() / 1500) % grades.size());
            previewGrade = grades.get(idx).text;
            previewGradeColor = grades.get(idx).color;
        }
    }

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen instanceof damage.engine.client.gui.DamageConfigScreen) return;
            if (client.currentScreen instanceof damage.engine.client.gui.HudEditorScreen) return;
            
            DamageEngineConfig config = DamageEngineConfig.getInstance();
            
            if (config.hideOnF1 && client.options.hudHidden) return;
            if (!config.showDamage) return;
            
            DamageSessionManager session = DamageSessionManager.getInstance();
            updateInfoAnimation(session, client);
            if (config.showInfo && infoAlpha > 0.01f) {
                float a = infoAlpha;
                renderModule(context, config.infoConfig, client, a, () -> {
                    renderInfo(context, session, false, a, client);
                });
            }
            
            if (!session.isActive()) {
                lastComboCount = 0;
                return;
            }
            
            if (!config.resetEnabled) {
                renderDamageContent(context, session.getTotalDamage(), session.getComboCount(), 1.0f, session.getDamageHistory(), false, 1.0f);
                return;
            }
            
            long now = System.currentTimeMillis();
            long timeSinceLast = now - session.getLastHitTime();
            long resetTimeMs = (long)(DamageEngineConfig.getInstance().resetTime * 1000);
            
            float globalAlpha = 1.0f;
            if (timeSinceLast > resetTimeMs) {
                float fadeProgress = (timeSinceLast - resetTimeMs) / 1000.0f;
                globalAlpha = 1.0f - fadeProgress;
                if (globalAlpha < 0) globalAlpha = 0;
            }
            
            globalAlpha = MathHelper.clamp(globalAlpha, 0.0f, 1.0f);
            
            if (globalAlpha <= 0.01f) {
                return;
            }
            
            if (globalAlpha > 0.01f) {
                renderDamageContent(context, session.getTotalDamage(), session.getComboCount(),
                    session.getRemainingTimeProgress(), session.getDamageHistory(), false, globalAlpha);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void renderPreview(DrawContext context, int centerX, int centerY) {
        float total = 0;
        int previewLimit = DamageEngineConfig.getInstance().historyLimit;
        List<DamageSessionManager.DamageEntry> history = new ArrayList<>();
        for (int i = 0; i < previewLimit; i++) {
            boolean isCrit = i >= previewLimit - 3;
            float dmg = 1.5f + i * 0.3f;
            total += dmg;
            history.add(new DamageSessionManager.DamageEntry(dmg, isCrit, 0));
        }
        int combo = previewLimit;
        float progress = 0.7f;
        
        // Cycle through rating grades for preview
        List<DamageEngineConfig.RatingGrade> grades = DamageEngineConfig.getInstance().ratingGrades;
        if (grades != null && !grades.isEmpty()) {
            int idx = (int)((System.currentTimeMillis() / 1500) % grades.size());
            previewGrade = grades.get(idx).text;
            previewGradeColor = grades.get(idx).color;
        }
        
        renderDamageContent(context, total, combo, progress, history, true, 1.0f);
        
        MinecraftClient client = MinecraftClient.getInstance();
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        renderModule(context, config.infoConfig, client, 1.0f, () -> {
            renderInfo(context, null, true, 1.0f, client);
        });
    }

    private void renderDamageContent(DrawContext context, float total, int combo, float targetProgress, List<DamageSessionManager.DamageEntry> history, boolean isPreview, float globalAlpha) {
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        
        // Render rating as separate module
        RatingManager rm = RatingManager.getInstance();
        if (isPreview || rm.isVisible()) {
            renderModule(context, config.ratingConfig, client, globalAlpha, () -> {
                renderRating(context, isPreview, globalAlpha, client);
            });
        }
        
        // Render total damage + history as one integrated module
        renderModule(context, config.totalDamageConfig, client, globalAlpha, () -> {
            renderTotalDamage(context, total, targetProgress, isPreview, globalAlpha, combo, client);
            
            if (config.showDamageHistory) {
                renderHistory(context, history, isPreview, globalAlpha, client);
            }
        });
    }
    
    public void renderRating(DrawContext context, boolean isPreview, float globalAlpha, MinecraftClient client) {
        TextRenderer tr = client.textRenderer;
        RatingManager rm = RatingManager.getInstance();
        int baseAlpha = (int)(255 * globalAlpha);
        
        float ratingAlpha = 0f;
        String ratingText = "";
        int ratingColor = 0;
        String imagePath = "";
        
        if (isPreview && !previewGrade.isEmpty()) {
            long t = System.currentTimeMillis() % 1500;
            float progress = MathHelper.clamp(t / 1500f, 0f, 1f);
            ratingAlpha = progress < 0.15f ? progress / 0.15f : (progress > 0.7f ? (1f - (progress - 0.7f) / 0.3f) : 1f);
            ratingText = previewGrade;
            ratingColor = previewGradeColor;
            List<DamageEngineConfig.RatingGrade> grades = DamageEngineConfig.getInstance().ratingGrades;
            if (grades != null) {
                for (DamageEngineConfig.RatingGrade g : grades) {
                    if (g.text.equals(previewGrade)) {
                        imagePath = g.imagePath;
                        break;
                    }
                }
            }
        } else if (!isPreview && rm.isVisible()) {
            long elapsed = System.currentTimeMillis() - rm.getGradeShowTime();
            float progress = MathHelper.clamp(elapsed / 2000f, 0f, 1f);
            ratingAlpha = progress > 0.6f ? (1f - (progress - 0.6f) / 0.4f) : 1f;
            ratingText = rm.getGrade();
            ratingColor = rm.getGradeColor();
            imagePath = rm.getGradeImagePath();
        }
        
        if (ratingAlpha > 0.01f) {
            int rAlpha = (int)(baseAlpha * ratingAlpha);
            int rColor = (ratingColor & 0x00FFFFFF) | (rAlpha << 24);
            
            if (DamageEngineConfig.getInstance().ratingUseImages && imagePath != null && !imagePath.isEmpty()) {
                try {
                    java.io.File imageFile = new java.io.File(client.runDirectory, "config/damage-engine/images/" + imagePath + ".png");
                    
                    if (!imageFile.exists()) {
                        throw new Exception("Image file not found: " + imageFile.getAbsolutePath());
                    }
                    
                    java.io.FileInputStream fis = new java.io.FileInputStream(imageFile);
                    net.minecraft.client.texture.NativeImage nativeImage = net.minecraft.client.texture.NativeImage.read(fis);
                    fis.close();
                    
                    if (nativeImage == null) {
                        throw new Exception("Failed to read native image");
                    }
                    
                    Identifier texId = Identifier.of("damage-engine", "rating_" + imagePath);
                    net.minecraft.client.texture.NativeImageBackedTexture backedTexture = new net.minecraft.client.texture.NativeImageBackedTexture(nativeImage);
                    client.getTextureManager().registerTexture(texId, backedTexture);
                    
                    context.getMatrices().push();
                    context.getMatrices().scale(0.15f, 0.15f, 1.0f);
                    
                    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                    com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                    com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, (float)rAlpha / 255.0f);
                    
                    int imgWidth = nativeImage.getWidth();
                    int imgHeight = nativeImage.getHeight();
                    context.drawTexture(texId, -imgWidth / 2, -imgHeight / 2, 0, 0, imgWidth, imgHeight, imgWidth, imgHeight);
                    
                    com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                    com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                    
                    context.getMatrices().pop();
                } catch (Exception e) {
                    DamageEngineClient.LOGGER.error("Failed to load rating image: " + e.getMessage());
                    float rs = 1.2f;
                    context.getMatrices().push();
                    context.getMatrices().scale(rs, rs, 1.0f);
                    int gw = tr.getWidth(ratingText);
                    context.drawTextWithShadow(tr, ratingText, -gw / 2, -tr.fontHeight / 2, rColor);
                    context.getMatrices().pop();
                }
            } else {
                float rs = 1.2f;
                context.getMatrices().push();
                context.getMatrices().scale(rs, rs, 1.0f);
                int gw = tr.getWidth(ratingText);
                context.drawTextWithShadow(tr, ratingText, -gw / 2, -tr.fontHeight / 2, rColor);
                context.getMatrices().pop();
            }
        }
    }

    public void renderModule(DrawContext context, DamageEngineConfig.ModuleConfig moduleConfig, MinecraftClient client, float globalAlpha, Runnable renderAction) {
        if (!moduleConfig.enabled) return;
        
        int x = moduleConfig.x == -1.0f ? client.getWindow().getScaledWidth() / 2 : (int)(moduleConfig.x * client.getWindow().getScaledWidth());
        int y = moduleConfig.y == -1.0f ? client.getWindow().getScaledHeight() / 2 : (int)(moduleConfig.y * client.getWindow().getScaledHeight());
        
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(moduleConfig.scale, moduleConfig.scale, 1.0f);
        
        renderAction.run();
        
        context.getMatrices().pop();
    }
    
    public void renderTotalDamage(DrawContext context, float total, float targetProgress, boolean isPreview, float globalAlpha, int combo, MinecraftClient client) {
        TextRenderer tr = client.textRenderer;
        int baseAlpha = (int)(255 * globalAlpha);

        int decimalPlaces = DamageEngineConfig.getInstance().decimalPlaces;
        String totalText = formatDamage(total, decimalPlaces);
        
        int color = 0xFFFFFFFF;
        float bestThreshold = -1f;
        
        List<DamageEngineConfig.DamageThreshold> thresholds = DamageEngineConfig.getInstance().damageThresholds;
        if (thresholds != null) {
             for (DamageEngineConfig.DamageThreshold dt : thresholds) {
                 if (total >= dt.threshold) {
                     if (dt.threshold > bestThreshold) {
                         bestThreshold = dt.threshold;
                         color = dt.color;
                     }
                 }
             }
        }
        
        int colorWithAlpha = (color & 0x00FFFFFF) | (baseAlpha << 24);
        
        // Compute dynamic content width based on label + optional combo
        boolean showCombo = DamageEngineConfig.getInstance().showCombo;
        float labelScale = 0.9f;
        int labelAlpha = baseAlpha;
        int labelColor = (0xFFFFFFFF) | (labelAlpha << 24);
        
        String damageLabel = "damage";
        String fullLabel;
        if (showCombo && combo > 0) {
            fullLabel = damageLabel + "  x" + combo;
        } else {
            fullLabel = damageLabel;
        }
        
        // Width in label-scale space
        context.getMatrices().push();
        context.getMatrices().scale(labelScale, labelScale, 1.0f);
        int fullLabelWidth = tr.getWidth(fullLabel);
        context.getMatrices().pop();
        
        // Content area half-width in module space
        float contentHalfWidth = (fullLabelWidth * labelScale) / 2.0f;
        contentRightX = contentHalfWidth;
        
        // Render "damage" label with combo
        context.getMatrices().push();
        context.getMatrices().scale(labelScale, labelScale, 1.0f);
        int lblW = tr.getWidth(damageLabel);
        if (showCombo && combo > 0) {
            // "damage" on left, combo on right
            context.drawTextWithShadow(tr, damageLabel, -fullLabelWidth / 2, -15, labelColor);
            String comboText = "x" + combo;
            int comboColorCfg = DamageEngineConfig.getInstance().comboColor;
            int comboColor = (comboColorCfg & 0x00FFFFFF) | (labelAlpha << 24);
            context.drawTextWithShadow(tr, comboText, fullLabelWidth / 2 - tr.getWidth(comboText), -15, comboColor);
        } else {
            // "damage" on right edge
            context.drawTextWithShadow(tr, damageLabel, -fullLabelWidth / 2 + (fullLabelWidth - lblW), -15, labelColor);
        }
        context.getMatrices().pop();
        
        // Progress bar matches label width
        if (DamageEngineConfig.getInstance().showProgressBar && DamageEngineConfig.getInstance().resetEnabled) {
            if (isPreview) {
                smoothProgress = targetProgress;
            } else {
                float delta = 1.0f;
                
                if (combo > lastComboCount) {
                    if (combo == 1) {
                        smoothProgress = 1.0f;
                        isRefilling = false;
                    } else {
                        isRefilling = true;
                    }
                    lastComboCount = combo;
                }
                
                if (isRefilling) {
                     float refillSpeed = 0.1f * delta;
                     smoothProgress += refillSpeed;
                     if (smoothProgress >= targetProgress) {
                         smoothProgress = targetProgress;
                         isRefilling = false;
                     }
                } else {
                    long resetTimeMs = (long)(DamageEngineConfig.getInstance().resetTime * 1000);
                     if (resetTimeMs > 0) {
                        long timeSinceLast = 0;
                         if (!isPreview) {
                            DamageSessionManager session = DamageSessionManager.getInstance();
                            timeSinceLast = System.currentTimeMillis() - session.getLastHitTime();
                        }
                        float realProgress = 1.0f - (float)timeSinceLast / (float)resetTimeMs;
                        smoothProgress = MathHelper.clamp(realProgress, 0.0f, 1.0f);
                    } else {
                        smoothProgress = 0.0f;
                    }
                }
                smoothProgress = MathHelper.clamp(smoothProgress, 0.0f, 1.0f);
            }
            
            float barWidth = contentHalfWidth * 2.0f;
            int barHeight = 1;
            float barX = -contentHalfWidth;
            int barY = -4;
            
            int bgAlpha = (int)(100 * globalAlpha);
            context.fill((int)barX, barY, (int)(barX + barWidth), barY + barHeight, (bgAlpha << 24));
            
            int barColor = DamageEngineConfig.getInstance().progressBarColor;
            
            context.getMatrices().push();
            context.getMatrices().translate(barX, barY, 0);
            context.getMatrices().scale(barWidth * smoothProgress, (float)barHeight, 1.0f);
            context.fill(0, 0, 1, 1, barColor);
            context.getMatrices().pop();
        }
        
        // Big damage number - right aligned within content area
        float damageScale = 1.5f;
        context.getMatrices().push();
        context.getMatrices().scale(damageScale, damageScale, 1.0f);
        int textWidth = tr.getWidth(totalText);
        // Right edge at contentRightX, so text starts at contentRightX/damageScale - textWidth
        float rightEdgeInScale = contentHalfWidth / damageScale;
        float textX = rightEdgeInScale - textWidth;
        context.drawTextWithShadow(tr, totalText, (int)textX, 0, colorWithAlpha);
        context.getMatrices().pop();
    }
    
    public void renderCombo(DrawContext context, int combo, float globalAlpha, MinecraftClient client) {
        TextRenderer tr = client.textRenderer;
        int baseAlpha = (int)(255 * globalAlpha);
        if (baseAlpha < 5) return;
        
        String comboText = "x" + combo;
        int comboColorCfg = DamageEngineConfig.getInstance().comboColor;
        int comboColor = (comboColorCfg & 0x00FFFFFF) | (baseAlpha << 24);
        
        int textWidth = tr.getWidth(comboText);
        context.drawTextWithShadow(tr, comboText, -textWidth / 2, 0, comboColor);
    }
    
    public void renderHistory(DrawContext context, List<DamageSessionManager.DamageEntry> history, boolean isPreview, float globalAlpha, MinecraftClient client) {
        TextRenderer tr = client.textRenderer;
        int limit = DamageEngineConfig.getInstance().historyLimit;
        int decimalPlaces = DamageEngineConfig.getInstance().decimalPlaces;

        int renderIndex = 0;
        List<DamageSessionManager.DamageEntry> renderList = (history != null) ? new java.util.ArrayList<>(history) : java.util.Collections.emptyList();
        
        // Track newest entry by timestamp for animation - only trigger on truly new entries
        long now = System.currentTimeMillis();
        long newestTs = renderList.isEmpty() ? 0 : renderList.get(renderList.size() - 1).timestamp();
        if (newestTs != lastNewestTimestamp) {
            prevAnimSize = (lastNewestTimestamp == 0) ? renderList.size() : Math.max(0, renderList.size() - 1);
            historyAnimStartTime = now;
            lastNewestTimestamp = newestTs;
            // When resetting completely, prevAnimSize should be 0
            if (renderList.size() <= 1) prevAnimSize = 0;
        }
        
        float animProgress = isPreview ? 1.0f : MathHelper.clamp((now - historyAnimStartTime) / (float)HISTORY_ANIM_MS, 0.0f, 1.0f);
        int growthAmount = Math.max(0, renderList.size() - prevAnimSize);
        
        // Base Y offset: below progress bar
        int baseY = 15;
        
        for (int i = renderList.size() - 1; i >= 0; i--) {
            if (renderIndex >= limit) break;
            DamageSessionManager.DamageEntry entry = renderList.get(i);
            if (entry == null) continue;
            
            long timeAlive = isPreview ? 0 : (now - entry.timestamp());
            float finalItemAlpha = 1.0f;
            
            if (!isPreview) {
                float disappearTime = DamageEngineConfig.getInstance().historyDisappearanceTime;
                long disappearTimeMs = (long)(disappearTime * 1000);
                long fadeStartMs = disappearTimeMs - 1000;
                if (fadeStartMs < 0) fadeStartMs = 0;
                
                if (timeAlive > fadeStartMs) {
                    finalItemAlpha = (disappearTimeMs - timeAlive) / 1000f;
                }
            }
            
            // New entry animation: fade in from right to left
            boolean isNewest = (i == renderList.size() - 1) && !isPreview && animProgress < 1.0f;
            float slideOffsetX = 0f;
            float slideAlphaMul = 1.0f;
            if (isNewest) {
                slideOffsetX = (1.0f - animProgress) * 20f;
                slideAlphaMul = animProgress;
            }
            
            finalItemAlpha *= globalAlpha * slideAlphaMul;
            if (finalItemAlpha <= 0) continue;
            finalItemAlpha = MathHelper.clamp(finalItemAlpha, 0.0f, 1.0f);
            int itemAlpha = (int)(255 * finalItemAlpha);
            if (itemAlpha < 5) continue;
            
            int critColorCfg = DamageEngineConfig.getInstance().critColor;
            int normalColorCfg = DamageEngineConfig.getInstance().normalColor;
            int entryColor = entry.isCrit() ? critColorCfg : normalColorCfg;
            int itemColorWithAlpha = (entryColor & 0x00FFFFFF) | (itemAlpha << 24);
            
            String valText = formatDamage(entry.damage(), decimalPlaces);
            int textWidth = tr.getWidth(valText);
            
            float xPos = contentRightX - textWidth + slideOffsetX;
            
            // Smooth Y: old entries slide down during animation (new entries stay at target)
            float targetY = baseY + renderIndex * 10;
            boolean isNewEntry = renderIndex < growthAmount;
            float yPos;
            if (!isPreview && growthAmount > 0 && !isNewEntry && animProgress < 1.0f) {
                float oldY = targetY - growthAmount * 10;
                yPos = MathHelper.lerp(animProgress, oldY, targetY);
            } else {
                yPos = targetY;
            }
            
            context.drawTextWithShadow(tr, valText, (int)xPos, (int)yPos, itemColorWithAlpha);
            renderIndex++;
        }
    }
    
    private void updateInfoAnimation(DamageSessionManager session, MinecraftClient client) {
        long now = System.currentTimeMillis();
        float dt = infoLastUpdateMs == 0 ? 0.016f : (now - infoLastUpdateMs) / 1000.0f;
        dt = MathHelper.clamp(dt, 0.0f, 0.1f);
        infoLastUpdateMs = now;
        
        int candidateTargetId = session.isInfoActive() ? session.getLastTargetEntityId() : infoLastTargetId;
        
        LivingEntity target = null;
        if (candidateTargetId != -1 && client.world != null) {
            Entity e = client.world.getEntityById(candidateTargetId);
            if (e instanceof LivingEntity le) {
                target = le;
            }
        }
        
        boolean shouldBeActive = session.isInfoActive() && target != null && target.isAlive();
        
        if (shouldBeActive) {
            boolean wasInactive = !infoHasSnapshot || infoAlpha <= 0.01f;
            boolean targetAvatar = target instanceof AbstractClientPlayerEntity;
            
            int targetId = session.getLastTargetEntityId();
            if (targetId != infoLastTargetId) {
                if (!wasInactive && infoSmoothRatio >= 0) {
                    infoPrevSmoothRatio = infoSmoothRatio;
                    infoPrevLagRatio = infoLagRatio;
                    infoPrevHealRatio = infoHealRatio;
                    infoPrevDamageTailActive = infoDamageTailActive;
                    infoPrevPlayerSkin = infoPlayerSkin; // Save the current skin as the previous one
                    infoSwitching = true;
                    infoSwitchStartMs = now;
                } else {
                    infoSwitching = false;
                }
                infoLastTargetId = targetId;
                infoSmoothRatio = -1f;
                infoLagRatio = -1f;
                infoHealRatio = -1f;
                infoLagHold = false;
                infoHealHold = false;
                infoDamageTailActive = false;
                infoDamageTailPending = false;
                infoHealthLastUpdateMs = 0;
            }

            captureInfoSnapshot(target); // Now capture the new snapshot
            infoFading = false;
            infoAlpha = 1.0f;
            infoDeathDrain = false;
            if (wasInactive) {
                infoAvatarFactor = targetAvatar ? 1.0f : 0.0f;
                infoAvatarAlpha = infoAvatarFactor;
                infoPrevPlayerSkin = null; // Ensure no previous skin if we were inactive
            }
            return;
        }
        
        if (!infoDeathDrain && !infoFading && candidateTargetId != -1 && (target == null || !target.isAlive()) && (session.isInfoActive() || infoHasSnapshot)) {
            if (target != null) {
                captureInfoSnapshot(target);
            } else if (!infoHasSnapshot) {
                infoHasSnapshot = true;
                infoName = "";
                infoIsPlayer = false;
                infoPlayerSkin = null;
                infoMaxHealth = 1f;
            }
            infoHealth = 0f;
            infoAbsorption = 0f;
            infoDeathDrain = true;
            infoFading = false;
            infoAlpha = 1.0f;
            session.clearInfo();
            return;
        }
        
        if (infoDeathDrain) {
            float eps = 0.0025f;
            if (infoSmoothRatio >= 0 && infoLagRatio >= 0 && infoHealRatio >= 0
                && infoSmoothRatio <= eps && infoLagRatio <= eps && infoHealRatio <= eps) {
                infoDeathDrain = false;
                infoFading = true;
                infoFadeStartMs = now;
                infoLastTargetId = -1;
            } else {
                infoFading = false;
                infoAlpha = 1.0f;
            }
        } else if (infoHasSnapshot && !infoFading && infoAlpha > 0.01f) {
            infoFading = true;
            infoFadeStartMs = now;
        }
        
        if (infoFading) {
            float t = (now - infoFadeStartMs) / (float)INFO_FADE_MS;
            infoAlpha = 1.0f - MathHelper.clamp(t, 0.0f, 1.0f);
            if (infoAlpha <= 0.01f) {
                infoAlpha = 0.0f;
                infoFading = false;
                infoHasSnapshot = false;
                infoLastTargetId = -1;
                infoLayoutLastUpdateMs = 0;
                infoSmoothRatio = -1f;
                infoLagRatio = -1f;
                infoHealRatio = -1f;
                infoLagHold = false;
                infoHealHold = false;
                infoDeathDrain = false;
                infoDamageTailActive = false;
                infoDamageTailPending = false;
                infoAvatarAlpha = 0f;
                infoSwitching = false;
                infoPrevSmoothRatio = -1f;
                infoPrevLagRatio = -1f;
                infoPrevHealRatio = -1f;
                infoPrevDamageTailActive = false;
                infoHealthLastUpdateMs = 0;
            }
        } else if (infoDeathDrain) {
            infoAlpha = 1.0f;
        } else {
            infoAlpha = 0.0f;
        }
    }
    
    private void captureInfoSnapshot(LivingEntity target) {
        infoHasSnapshot = true;
        infoName = target.getName().getString();
        infoIsPlayer = target instanceof AbstractClientPlayerEntity;
        if (infoIsPlayer) {
            infoPlayerSkin = ((AbstractClientPlayerEntity) target).getSkinTextures();
        } else {
            infoPlayerSkin = null;
        }
        infoHealth = target.getHealth();
        infoMaxHealth = target.getMaxHealth();
        if (infoMaxHealth <= 0) infoMaxHealth = 1f;
        infoAbsorption = target.getAbsorptionAmount();
    }
    
    public void renderInfo(DrawContext context, DamageSessionManager session, boolean isPreview, float globalAlpha, MinecraftClient client) {
        float health;
        float maxHealth;
        float absorption;
        String fullName;
        boolean isPlayer;
        SkinTextures playerSkin;
        
        if (isPreview) {
            health = 10.0f;
            maxHealth = 20.0f;
            absorption = 0.0f;
            isPlayer = true;
            com.mojang.authlib.GameProfile profile = client.getGameProfile();
            fullName = client.player != null ? client.player.getName().getString() : "Player";
            if (client.player != null) {
                playerSkin = client.player.getSkinTextures();
            } else if (profile != null) {
                playerSkin = client.getSkinProvider().getSkinTextures(profile);
            } else {
                playerSkin = null;
            }
            infoAvatarFactor = 1.0f;
            infoAvatarAlpha = 1.0f;
        } else {
            if (!infoHasSnapshot) return;
            health = infoHealth;
            maxHealth = infoMaxHealth;
            absorption = infoAbsorption;
            fullName = infoName;
            isPlayer = infoIsPlayer;
            playerSkin = infoPlayerSkin;
        }
        
        int hp = Math.max(0, Math.round(health + absorption));
        int maxHp = Math.max(1, Math.round(maxHealth));
        float ratio = MathHelper.clamp(health / maxHealth, 0.0f, 1.0f);
        
        int baseAlpha = (int)(255 * globalAlpha);
        if (baseAlpha < 5) return;
        
        long now = System.currentTimeMillis();
        float layoutDt = infoLayoutLastUpdateMs == 0 ? 0.016f : (now - infoLayoutLastUpdateMs) / 1000.0f;
        layoutDt = MathHelper.clamp(layoutDt, 0.0f, 0.1f);
        infoLayoutLastUpdateMs = now;
        
        float targetAvatar = isPlayer ? 1.0f : 0.0f;
        float avatarSmoothing = 1.0f - (float)Math.pow(0.0001, layoutDt); // Slightly faster smoothing
        infoAvatarFactor += (targetAvatar - infoAvatarFactor) * avatarSmoothing;
        infoAvatarFactor = MathHelper.clamp(infoAvatarFactor, 0.0f, 1.0f);
        
        // Avatar alpha should follow the factor but with a slight delay/offset for a better "pop-in" effect
        float targetAvatarAlpha = targetAvatar;
        infoAvatarAlpha += (targetAvatarAlpha - infoAvatarAlpha) * avatarSmoothing;
        infoAvatarAlpha = MathHelper.clamp(infoAvatarAlpha, 0.0f, 1.0f);
        
        int panelH = 30; // Use even number for better vertical centering
        
        int bgOpacity = MathHelper.clamp(DamageEngineConfig.getInstance().infoBackgroundOpacity, 0, 100);
        int bgBaseAlpha = (int)Math.round(255 * (bgOpacity / 100.0f));
        int bgAlpha = (int)Math.round(bgBaseAlpha * globalAlpha);
        if (globalAlpha < 0.99f && bgAlpha < 25) {
            bgAlpha = Math.min(bgBaseAlpha, 25);
        }
        // Force background alpha to match text alpha better during fade out
        if (infoFading) {
            bgAlpha = (int)(bgBaseAlpha * infoAlpha);
        }
        int bgColor = DamageEngineConfig.getInstance().infoBackgroundColor;
        int bg = (bgColor & 0x00FFFFFF) | (bgAlpha << 24);
        
        int barW = 80;
        int barX = -barW / 2;
        
        int avatarSize = 18; // 18-2 = 16 (Perfect 8*2 for player head pixels)
        int avatarSlotFull = avatarSize + 3;
        // Use an elastic-like factor for the slot width to make it feel more dynamic
        float layoutAvatarFactor = infoAvatarFactor; 
        int avatarSlot = (int)Math.round(avatarSlotFull * layoutAvatarFactor);
        int gap = 2;
        int leftPad = 2;
        int rightPad = 2;
        
        int contentLeft = barX - gap - avatarSlot;
        int baseX = contentLeft - leftPad;
        int baseRight = barX + barW + rightPad;
        int panelW = baseRight - baseX;
        int baseY = -panelH / 2;
        context.fill(baseX - 2, baseY - 2, baseX + panelW + 2, baseY + panelH + 2, bg);
        
        TextRenderer tr = client.textRenderer;
        
        // Ensure pixel-perfect rounding for all avatar positions to prevent eye distortion
        int avatarDrawX = (int)Math.round(contentLeft + avatarSlot - avatarSlotFull);
        int avatarY = (int)Math.round(baseY + (panelH - avatarSize) / 2.0);
        
        SkinTextures skinToDraw = playerSkin; // No fallback here to avoid "styles" issues
        
        float switchT = 1.0f;
        if (infoSwitching) {
            switchT = (System.currentTimeMillis() - infoSwitchStartMs) / (float)INFO_SWITCH_MS;
            if (switchT >= 1.0f) {
                switchT = 1.0f;
                infoSwitching = false;
                infoPrevPlayerSkin = null; // Clear after switch completes
            } else if (switchT < 0.0f) {
                switchT = 0.0f;
            }
        } else {
            // Ensure switchT is 1.0 when not switching to avoid flickering or stuck animation states
            switchT = 1.0f;
        }
        
        float oldAlphaMul = infoSwitching ? (1.0f - switchT) : 0.0f;
        float newAlphaMul = infoSwitching ? switchT : 1.0f;
        
        // If skins are identical, just draw once without animation
        boolean isSameSkin = (infoPrevPlayerSkin != null && skinToDraw != null && infoPrevPlayerSkin.texture().equals(skinToDraw.texture()));
        
        if (infoAvatarAlpha > 0.01f) {
            context.getMatrices().push();
            
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            
            if (isSameSkin || !infoSwitching) {
                // One avatar: either no switch or identical skins
                if (skinToDraw != null) {
                    float fade = infoAvatarAlpha * globalAlpha;
                    context.setShaderColor(1.0f, 1.0f, 1.0f, fade);
                    // TAB-style 16px head (Perfect integer scale)
                    PlayerSkinDrawer.draw(context, skinToDraw.texture(), avatarDrawX + 1, avatarY + 1, 16, true, false);
                }
            } else {
                // EXCLUSIVE SEQUENTIAL FADE: Only ONE avatar is rendered at any time.
                // No movement, just fade out then fade in "in place".
                if (switchT < 0.5f) {
                    // Phase 1: Old avatar fades out
                    if (infoPrevPlayerSkin != null) {
                        float localT = switchT * 2.0f; // 0.0 -> 1.0
                        float alpha = infoAvatarAlpha * (1.0f - localT) * globalAlpha;
                        context.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
                        PlayerSkinDrawer.draw(context, infoPrevPlayerSkin.texture(), avatarDrawX + 1, avatarY + 1, 16, true, false);
                    }
                } else {
                    // Phase 2: New avatar fades in
                    if (skinToDraw != null) {
                        float localT = (switchT - 0.5f) * 2.0f; // 0.0 -> 1.0
                        float alpha = infoAvatarAlpha * localT * globalAlpha;
                        context.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
                        PlayerSkinDrawer.draw(context, skinToDraw.texture(), avatarDrawX + 1, avatarY + 1, 16, true, false);
                    }
                }
            }
            
            context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
            context.getMatrices().pop();
        }
        
        int barH = 5;
        int barY = baseY + (panelH - barH) / 2;
        
        int textX = barX;
        int textGap = 3;
        int textYName = barY - textGap - tr.fontHeight;
        int textYHp = barY + barH + textGap;
        int maxTextWidth = barW;
        
        String name = fullName;
        if (tr.getWidth(name) > maxTextWidth) {
            String raw = name;
            while (!raw.isEmpty() && tr.getWidth(raw + "…") > maxTextWidth) {
                raw = raw.substring(0, raw.length() - 1);
            }
            name = raw.isEmpty() ? "" : raw + "…";
        }
        
        int textColor = (0x00FFFFFF) | (baseAlpha << 24);
        context.drawTextWithShadow(tr, Text.literal(name), textX, textYName, textColor);
        
        int barBg = ((int)(128 * globalAlpha) << 24);
        context.fill(barX, barY, barX + barW, barY + barH, barBg);
        
        float dt = infoHealthLastUpdateMs == 0 ? 0.016f : (now - infoHealthLastUpdateMs) / 1000.0f;
        infoHealthLastUpdateMs = now;
        dt = MathHelper.clamp(dt, 0.0f, 0.1f);
        
        if (infoSmoothRatio < 0) {
            infoSmoothRatio = ratio;
            infoLagRatio = ratio;
            infoHealRatio = ratio;
            infoLagHold = false;
            infoHealHold = false;
            infoDamageTailActive = false;
            infoDamageTailPending = false;
        } else {
            float prevSmooth = infoSmoothRatio;
            
            float downSmoothing = 1.0f - (float)Math.pow(0.001, dt);
            float upSmoothing = 1.0f - (float)Math.pow(0.05, dt);
            float smoothing = ratio < prevSmooth ? downSmoothing : upSmoothing;
            infoSmoothRatio += (ratio - infoSmoothRatio) * smoothing;
            
            if (ratio < prevSmooth - 0.0001f) {
                infoLagHold = true;
                infoDamageTailActive = true;
                infoDamageTailPending = true;
                infoLagRatio = Math.max(infoLagRatio, prevSmooth);
            }
            if (ratio > prevSmooth + 0.0001f) {
                infoHealHold = true;
            }
            
            if (infoLagHold) {
                if (Math.abs(infoSmoothRatio - ratio) < 0.0025f) {
                    infoLagHold = false;
                }
            }
            
            float tailSmoothing = 1.0f - (float)Math.pow(0.02, dt);
            if (infoDamageTailActive) {
                if (infoDamageTailPending) {
                    if (!infoLagHold || infoHealHold) {
                        infoDamageTailPending = false;
                    } else {
                        infoLagRatio = Math.max(infoLagRatio, prevSmooth);
                    }
                }
                
                if (!infoDamageTailPending) {
                    infoLagRatio += (infoSmoothRatio - infoLagRatio) * tailSmoothing;
                }
            } else if (!infoLagHold) {
                infoLagRatio += (ratio - infoLagRatio) * tailSmoothing;
            }
            
            if (infoHealHold) {
                if (infoHealRatio < 0) infoHealRatio = prevSmooth;
                infoHealRatio += (ratio - infoHealRatio) * downSmoothing;
                if (Math.abs(infoSmoothRatio - ratio) < 0.0025f) {
                    infoHealHold = false;
                    infoHealRatio = infoSmoothRatio;
                }
            } else {
                infoHealRatio = infoSmoothRatio;
            }
        }
        
        infoSmoothRatio = MathHelper.clamp(infoSmoothRatio, 0.0f, 1.0f);
        infoLagRatio = MathHelper.clamp(infoLagRatio, 0.0f, 1.0f);
        infoHealRatio = MathHelper.clamp(infoHealRatio, 0.0f, 1.0f);
        int barColor = DamageEngineConfig.getInstance().infoBarColor;
        int healColorBase = DamageEngineConfig.getInstance().infoBarHealColor;
        int damageColorBase = DamageEngineConfig.getInstance().infoBarDamageColor;
        
        if (oldAlphaMul > 0.01f && infoPrevSmoothRatio >= 0) {
            float s = MathHelper.clamp(infoPrevSmoothRatio, 0.0f, 1.0f);
            float lag = MathHelper.clamp(infoPrevLagRatio, 0.0f, 1.0f);
            float heal = MathHelper.clamp(infoPrevHealRatio, 0.0f, 1.0f);
            int a = (int)(baseAlpha * oldAlphaMul);
            
            int fillW = (int)Math.floor(barW * s);
            int barColorWithAlpha = (barColor & 0x00FFFFFF) | (a << 24);
            context.fill(barX, barY, barX + fillW, barY + barH, barColorWithAlpha);
            
            if (heal > s) {
                int healStart = barX + (int)Math.floor(barW * s);
                int healEnd = barX + (int)Math.floor(barW * heal);
                int healAlpha = (int)(a * 0.45f);
                int healColor = (healColorBase & 0x00FFFFFF) | (healAlpha << 24);
                context.fill(healStart, barY, healEnd, barY + barH, healColor);
            }
            
            if (infoPrevDamageTailActive && lag > s) {
                int lagStart = barX + (int)Math.floor(barW * s);
                int lagEnd = barX + (int)Math.floor(barW * lag);
                int lagColor = (damageColorBase & 0x00FFFFFF) | (a << 24);
                context.fill(lagStart, barY, lagEnd, barY + barH, lagColor);
            }
        }
        
        if (newAlphaMul > 0.01f) {
            int a = (int)(baseAlpha * newAlphaMul);
            int fillW = (int)Math.floor(barW * infoSmoothRatio);
            int barColorWithAlpha = (barColor & 0x00FFFFFF) | (a << 24);
            context.fill(barX, barY, barX + fillW, barY + barH, barColorWithAlpha);
            
            if (infoHealRatio > infoSmoothRatio) {
                int healStart = barX + (int)Math.floor(barW * infoSmoothRatio);
                int healEnd = barX + (int)Math.floor(barW * infoHealRatio);
                int healAlpha = (int)(a * 0.45f);
                int healColor = (healColorBase & 0x00FFFFFF) | (healAlpha << 24);
                context.fill(healStart, barY, healEnd, barY + barH, healColor);
            }
            
            if (infoDamageTailActive && infoLagRatio > infoSmoothRatio) {
                int lagStart = barX + (int)Math.floor(barW * infoSmoothRatio);
                int lagEnd = barX + (int)Math.floor(barW * infoLagRatio);
                int lagColor = (damageColorBase & 0x00FFFFFF) | (a << 24);
                context.fill(lagStart, barY, lagEnd, barY + barH, lagColor);
                if (Math.abs(infoLagRatio - infoSmoothRatio) < 0.0025f) {
                    infoDamageTailActive = false;
                }
            }
        }
        
        boolean hasAbsorption = absorption > 0.01f;
        Identifier heartTexture = Identifier.ofVanilla(hasAbsorption ? "hud/heart/absorbing_full" : "hud/heart/full");
        
        Text hpText = Text.literal(hp + "/" + maxHp);
        context.drawTextWithShadow(tr, hpText, textX + 10, textYHp, textColor);
        context.drawGuiTexture(Identifier.ofVanilla("hud/heart/container"), textX, textYHp, 9, 9);
        context.drawGuiTexture(heartTexture, textX, textYHp, 9, 9);
    }
    
    private String formatDamage(float damage, int decimalPlaces) {
        if (decimalPlaces <= 0) {
            return String.valueOf(Math.round(damage));
        }
        String format = "%." + decimalPlaces + "f";
        return String.format(format, damage);
    }
}
