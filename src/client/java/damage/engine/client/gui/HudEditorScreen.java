package damage.engine.client.gui;

import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageHud;
import damage.engine.hud.DamageSessionManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class HudEditorScreen extends Screen {
    private final Screen parent;
    private final DamageEngineConfig config;
    private final DamageHud damageHud;
    
    private EditorModule selectedModule = null;
    private boolean dragging = false;
    private boolean resizing = false;
    private float dragOffsetX, dragOffsetY;
    
    private final List<EditorModule> modules = new ArrayList<>();
    
    private final Deque<Runnable> undoStack = new ArrayDeque<>();
    private final Deque<Runnable> redoStack = new ArrayDeque<>();
    
    private int resetButtonState = 0;
    private long resetButtonActionTime = 0;
    
    private DamageConfigScreen.StyledButton undoBtn;
    private DamageConfigScreen.StyledButton redoBtn;
    private DamageConfigScreen.StyledButton resetBtn;


    public HudEditorScreen(Screen parent) {
        super(Text.translatable("title.damage-engine.hud_editor"));
        this.parent = parent;
        this.config = DamageEngineConfig.getInstance();
        this.damageHud = new DamageHud();
        
        modules.add(new EditorModule(config.totalDamageConfig, ModuleType.TOTAL));
        modules.add(new EditorModule(config.ratingConfig, ModuleType.RATING));
        modules.add(new EditorModule(config.infoConfig, ModuleType.INFO));
    }
    
    public void playClickSound() {
        try {
             net.minecraft.client.sound.PositionedSoundInstance sound = net.minecraft.client.sound.PositionedSoundInstance.ambient(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value());
             
             try {
                 java.lang.reflect.Field volField = net.minecraft.client.sound.AbstractSoundInstance.class.getDeclaredField("volume");
                 volField.setAccessible(true);
                 volField.setFloat(sound, 0.25F);
             } catch (Exception ignored) {}
             
             MinecraftClient.getInstance().getSoundManager().play(sound);
        } catch (Exception e) {
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
    
    @Override
    protected void init() {

        int btnY = 10;
        int btnX = 10;
        int spacing = 5;
        
        undoBtn = new DamageConfigScreen.StyledButton(btnX, btnY, 40, 20, Text.translatable("hud.editor.undo"), this::undo);
        
        redoBtn = new DamageConfigScreen.StyledButton(btnX + 40 + spacing, btnY, 40, 20, Text.translatable("hud.editor.redo"), this::redo);
            
        resetBtn = new DamageConfigScreen.StyledButton(btnX + 40 + spacing + 40 + spacing, btnY, 40, 20, Text.translatable("hud.editor.reset").withColor(0xFFFC887E), this::handleResetClick);
            
        this.addDrawableChild(undoBtn);
        this.addDrawableChild(redoBtn);
        this.addDrawableChild(resetBtn);
        
        updateButtons();
    }

    private void handleResetClick() {
        if (resetButtonState == 0) {
            resetButtonState = 1;
            resetBtn.setMessage(Text.translatable("gui.confirm").append("?").withColor(0xFFFC887E));
            resetButtonActionTime = System.currentTimeMillis();
        } else if (resetButtonState == 1) {
            resetButtonState = 2;
            resetButtonActionTime = System.currentTimeMillis();
            resetAllModules();
            resetBtn.setMessage(Text.translatable("text.damage-engine.reset_done").withColor(0xFFB5F0C6));
        }
    }
    
    private void updateButtons() {
        undoBtn.active = !undoStack.isEmpty();
        redoBtn.active = !redoStack.isEmpty();
        
        if (resetButtonState == 2) {
            if (System.currentTimeMillis() - resetButtonActionTime > 3000) {
                resetButtonState = 0;
                resetBtn.setMessage(Text.translatable("hud.editor.reset").withColor(0xFFFC887E));
            }
        } else if (resetButtonState == 1) {
             if (System.currentTimeMillis() - resetButtonActionTime > 5000) {
                 resetButtonState = 0;
                 resetBtn.setMessage(Text.translatable("hud.editor.reset").withColor(0xFFFC887E));
             }
        }
    }
    
    @Override
    public void tick() {
        super.tick();
        updateButtons();
    }
    
    private void saveStateForUndo() {
        final List<ModuleSnapshot> snapshot = captureState();
        undoStack.push(() -> restoreState(snapshot, true));
        redoStack.clear();
        updateButtons();
    }
    
    private void restoreState(List<ModuleSnapshot> stateToRestore, boolean isUndo) {
        final List<ModuleSnapshot> currentState = captureState();
        
        if (isUndo) {
            redoStack.push(() -> restoreState(currentState, false));
        } else {
            undoStack.push(() -> restoreState(currentState, true));
        }
        
        for (int i = 0; i < modules.size(); i++) {
             if (i < stateToRestore.size()) {
                 stateToRestore.get(i).apply(modules.get(i).config);
             }
        }
        updateButtons();
    }
    
    private void undo() {
        if (undoStack.isEmpty()) return;
        undoStack.pop().run();
    }
    
    private void redo() {
        if (redoStack.isEmpty()) return;
        redoStack.pop().run();
    }
    
    private List<ModuleSnapshot> captureState() {
        List<ModuleSnapshot> list = new ArrayList<>();
        for (EditorModule m : modules) list.add(new ModuleSnapshot(m.config));
        return list;
    }

    private void resetAllModules() {
        saveStateForUndo();
        config.totalDamageConfig.x = 0.8160156f;
        config.totalDamageConfig.y = 0.37278107f;
        config.totalDamageConfig.scale = 0.95652175f;
        
        config.infoConfig.x = 0.61875f;
        config.infoConfig.y = 0.59909815f;
        config.infoConfig.scale = 0.5539445f;
        
        config.ratingConfig.x = 0.7867187f;
        config.ratingConfig.y = 0.31508327f;
        config.ratingConfig.scale = 1.4066461f;
        config.save();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        
        for (EditorModule m : modules) {
            renderModule(context, m);
        }
        
        if (selectedModule != null) {
            drawSelection(context, selectedModule);
        }
        
        for (net.minecraft.client.gui.Element element : this.children()) {
            if (element instanceof net.minecraft.client.gui.Drawable) {
                ((net.minecraft.client.gui.Drawable) element).render(context, mouseX, mouseY, delta);
            }
        }
        

    }
    

    

    
    private void renderModule(DrawContext context, EditorModule m) {
        damageHud.renderModule(context, m.config, this.client, 1.0f, () -> {
            switch (m.type) {
                case TOTAL:
                    int previewLimit = DamageEngineConfig.getInstance().historyLimit;
                    List<DamageSessionManager.DamageEntry> history = new ArrayList<>();
                    float previewTotal = 0;
                    for (int j = 0; j < previewLimit; j++) {
                        boolean isCrit = j >= previewLimit - 3;
                        float dmg = 1.5f + j * 0.3f;
                        previewTotal += dmg;
                        history.add(new DamageSessionManager.DamageEntry(dmg, isCrit, 0));
                    }
                    damageHud.renderTotalDamage(context, previewTotal, 0.7f, true, 1.0f, previewLimit, this.client);
                    damageHud.renderHistory(context, history, true, 1.0f, this.client);
                    break;
                case RATING:
                    damageHud.cyclePreviewGrades();
                    damageHud.renderRating(context, true, 1.0f, this.client);
                    break;
                case INFO:
                    damageHud.renderInfo(context, null, true, 1.0f, this.client);
                    break;
            }
        });
    }
    
    private void drawSelection(DrawContext context, EditorModule m) {
        int[] b = getBounds(m);
        int greenColor = 0xFFB5F0C6;
        
        drawBorder(context, b[0], b[1], b[2], b[3], greenColor);
        
        int handleSize = 8;
        int hx = b[0] + b[2] - handleSize;
        int hy = b[1] + b[3] - handleSize;
        context.fill(hx, hy, hx + handleSize, hy + handleSize, greenColor);
    }
    
    private void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
    
    private int[] getBounds(EditorModule m) {
        int cx = m.config.x == -1.0f ? width / 2 : (int)(m.config.x * width);
        int cy = m.config.y == -1.0f ? height / 2 : (int)(m.config.y * height);
        float s = m.config.scale;
        
        int w = 0, h = 0;
        int ox = 0, oy = 0;
        
        switch (m.type) {
            case TOTAL:
                w = (int)(80 * s); h = (int)(55 * s);
                
                ox = (int)(-w/2 + 2 * s);
                oy = (int)(-h/2 + 12 * s);
                break;
            case RATING:
                w = (int)(30 * s); h = (int)(18 * s);
                ox = -w/2; oy = -h/2 - (int)(2 * s);
                break;
            case INFO:
                w = (int)(106 * s); h = (int)(34 * s);
                ox = (int)(-65 * s); oy = (int)(-17 * s);
                break;
        }
        return new int[]{cx + ox - 2, cy + oy - 2, w + 4, h + 4};
    }
    
    private boolean isOverHandle(double mx, double my, EditorModule m) {
        int[] b = getBounds(m);
        int handleSize = 8;
        int hx = b[0] + b[2] - handleSize;
        int hy = b[1] + b[3] - handleSize;
        return mx >= hx && mx <= hx + handleSize && my >= hy && my <= hy + handleSize;
    }
    
    private boolean isOverModule(double mx, double my, EditorModule m) {
        int[] b = getBounds(m);
        return mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        
        this.setFocused(null);
        
        if (button == 0) {
            double mx = client.mouse.getX() * (double)client.getWindow().getScaledWidth() / (double)client.getWindow().getWidth();
            double my = client.mouse.getY() * (double)client.getWindow().getScaledHeight() / (double)client.getWindow().getHeight();
            
            if (selectedModule != null && isOverHandle(mx, my, selectedModule)) {
                saveStateForUndo();
                resizing = true;
                dragOffsetX = (float)mx;
                dragOffsetY = (float)my;
                return true;
            }
            
            for (EditorModule m : modules) {
                if (isOverModule(mx, my, m)) {
                    if (selectedModule != m) {
                         selectedModule = m;
                         updateButtons();
                    }
                    saveStateForUndo();
                    dragging = true;
                    
                    int cx = m.config.x == -1.0f ? this.width / 2 : (int)(m.config.x * this.width);
                    int cy = m.config.y == -1.0f ? this.height / 2 : (int)(m.config.y * this.height);
                    
                    dragOffsetX = (float)mx - cx;
                    dragOffsetY = (float)my - cy;
                    return true;
                }
            }
            
            if (selectedModule != null) {
                selectedModule = null;
                updateButtons();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (super.mouseReleased(mouseX, mouseY, button)) return true;
        
        dragging = false;
        resizing = false;
        config.save();
        return false;
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (resizing && selectedModule != null) {
            float sensitivity = 0.01f;
            selectedModule.config.scale += (float)(deltaX + deltaY) * sensitivity; 
            if (selectedModule.config.scale < 0.1f) selectedModule.config.scale = 0.1f;
            if (selectedModule.config.scale > 5.0f) selectedModule.config.scale = 5.0f;
            return true;
        }
        
        if (dragging && selectedModule != null) {
            float newX = ((float)mouseX - dragOffsetX) / (float)width;
            float newY = ((float)mouseY - dragOffsetY) / (float)height;
            
            selectedModule.config.x = newX;
            selectedModule.config.y = newY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY); 
    }
    
    @Override
    public void close() {
        config.save();
        client.setScreen(parent);
    }
    
    private static class EditorModule {
        DamageEngineConfig.ModuleConfig config;
        ModuleType type;
        public EditorModule(DamageEngineConfig.ModuleConfig c, ModuleType t) { this.config = c; this.type = t; }
    }
    
    private enum ModuleType { TOTAL, RATING, INFO }
    
    private record ModuleSnapshot(float x, float y, float scale, boolean enabled) {
        public ModuleSnapshot(DamageEngineConfig.ModuleConfig c) { this(c.x, c.y, c.scale, c.enabled); }
        public void apply(DamageEngineConfig.ModuleConfig c) { c.x = x; c.y = y; c.scale = scale; c.enabled = enabled; }
    }
}
