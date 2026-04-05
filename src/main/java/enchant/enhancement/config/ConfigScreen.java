package enchant.enhancement.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.sound.PositionedSoundInstance;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private EnchantmentListWidget listWidget;
    private TextFieldWidget searchField;
    private final Map<Identifier, Integer> enchantmentLevels;
    private final List<EnchantmentEntry> allEntries = new ArrayList<>();
    private String searchQuery = "";
    private int listTop;
    private int listHeight;
    
    private enum Tab { GENERAL, CUSTOM }
    private Tab currentTab = Tab.GENERAL;
    private ButtonWidget generalTabButton;
    private ButtonWidget customTabButton;
    
    // 常规标签页组件状态
    private int resetButtonState = 0; // 0=重置, 1=确认?, 2=已重置!
    private long resetButtonTime = 0;
    
    // 自定义标签页组件（已移至RestoreDefaultsEntry内部）
    private TextFieldWidget searchFieldFixed; // 固定的搜索框
    
    // 恢复默认值按钮状态
    private int restoreDefaultsButtonState = 0; // 0=恢复, 1=确认?, 2=已恢复!
    private long restoreDefaultsButtonTime = 0;
    
    // 附魔分组
    private final Map<String, List<Identifier>> enchantmentGroups = new java.util.LinkedHashMap<>();
    private final Map<String, Boolean> groupExpandedStates = new java.util.LinkedHashMap<>();

    
    public ConfigScreen(Screen parent) {
        super(Text.translatable("config.enchant_enhancement.title"));
        this.parent = parent;
        this.enchantmentLevels = EnchantmentConfig.getAllLevels();
        
        // 初始化所有附魔条目，用于保存配置
        for (Map.Entry<Identifier, Integer> entry : enchantmentLevels.entrySet()) {
            allEntries.add(new EnchantmentEntry(entry.getKey(), entry.getValue()));
        }
    }
    
    // 静态工厂方法，供Mod Menu和按键绑定使用
    public static Function<Screen, Screen> getFactory() {
        return ConfigScreen::new;
    }
    
    private void switchTab(Tab tab) {
        currentTab = tab;
        updateTabButtonColors();
        // 更新固定搜索框占位符
        if (searchFieldFixed != null) {
            if (currentTab == Tab.GENERAL) {
                searchFieldFixed.setPlaceholder(Text.translatable("config.enchant_enhancement.search.placeholder_settings"));
            } else {
                searchFieldFixed.setPlaceholder(Text.translatable("config.enchant_enhancement.search.placeholder_enchantments"));
            }
            // 清除搜索查询和过滤
            searchFieldFixed.setText("");
            searchQuery = "";
        }
        updateListContent();
        // 重置滚动位置，确保每个标签页有独立的滚动状态
        if (listWidget != null) {
            listWidget.setScrollAmount(0);
        }
    }
    
    private void updateTabButtonColors() {
        if (generalTabButton != null) {
            int color = currentTab == Tab.GENERAL ? 0xFFB1EAC2 : 0xFFAAAAAA;
            generalTabButton.setMessage(Text.translatable("config.enchant_enhancement.tab.general").withColor(color));
        }
        if (customTabButton != null) {
            int color = currentTab == Tab.CUSTOM ? 0xFFB1EAC2 : 0xFFAAAAAA;
            customTabButton.setMessage(Text.translatable("config.enchant_enhancement.tab.enchantments").withColor(color));
        }
    }
    
    private Text getToggleText(boolean enabled) {
        String key = enabled ? "config.enchant_enhancement.toggle.on" : "config.enchant_enhancement.toggle.off";
        return Text.translatable(key).withColor(enabled ? 0xFFB1EAC2 : 0xFFF9867D);
    }
    
    private void handleResetButton() {
        if (resetButtonState == 0) {
            // 第一步：显示"确认?"
            resetButtonState = 1;
            // 刷新列表以更新按钮文本
            updateListContent();
        } else if (resetButtonState == 1) {
            // 第二步：执行重置并显示"已重置!"
            resetButtonState = 2;
            resetButtonTime = System.currentTimeMillis();
            
            // 执行重置操作
            EnchantmentConfig.setMergeHighEnchantments(true);
            EnchantmentConfig.setLootHighEnchantments(true);
            EnchantmentConfig.setArmorProtectionCompatibility(true);
            EnchantmentConfig.setWeaponEnchantmentCompatibility(true);
            EnchantmentConfig.setAxeEnchantmentExpansion(false);
            EnchantmentConfig.setBowLootingEnchantment(true);
            EnchantmentConfig.setTridentEnchantmentExpansion(true);
            EnchantmentConfig.setInfinityWithoutArrow(true);
            
            // 重置所有附魔等级为默认值
            EnchantmentConfig.resetToDefaults();
            
            // 重新从配置获取所有等级
            ConfigScreen.this.enchantmentLevels.clear();
            ConfigScreen.this.enchantmentLevels.putAll(EnchantmentConfig.getAllLevels());
            
            // 重新初始化附魔分组
            ConfigScreen.this.initializeGroups();
            
            // 重新初始化所有附魔条目，用于保存配置
            ConfigScreen.this.allEntries.clear();
            for (Map.Entry<Identifier, Integer> entry : ConfigScreen.this.enchantmentLevels.entrySet()) {
                ConfigScreen.this.allEntries.add(new EnchantmentEntry(entry.getKey(), entry.getValue()));
            }
            
            // 保存所有配置更改到文件
            EnchantmentConfig.save();
            
            // 刷新列表以更新UI组件
            updateListContent();
        }
    }
    

    
    @Override
    protected void init() {
        super.init();
        
        // 创建固定的选项卡按钮（横着放在黑色背景的顶部靠左位置）
        int generalTabButtonWidth = 40; // 常规按钮宽度，刚好比文字长一点
        int customTabButtonWidth = 70; // 附魔等级按钮宽度，刚好比文字长一点
        int tabButtonHeight = 20;
        int tabButtonY = 30; // 标题下方
        int tabButtonSpacing = 5;
        int tabStartX = 10; // 靠左位置
        
        generalTabButton = ButtonWidget.builder(
            Text.translatable("config.enchant_enhancement.tab.general"),
            button -> switchTab(Tab.GENERAL)
        ).dimensions(tabStartX, tabButtonY, generalTabButtonWidth, tabButtonHeight).build();
        
        customTabButton = ButtonWidget.builder(
            Text.translatable("config.enchant_enhancement.tab.enchantments"),
            button -> switchTab(Tab.CUSTOM)
        ).dimensions(tabStartX + generalTabButtonWidth + tabButtonSpacing, tabButtonY, customTabButtonWidth, tabButtonHeight).build();
        
        addDrawableChild(generalTabButton);
        addDrawableChild(customTabButton);
        
        // 更新按钮颜色以反映当前选中的标签页
        updateTabButtonColors();
        
        // 列表位置和大小 - 从选项卡按钮下方开始，到底部按钮上方结束
        listTop = tabButtonY + tabButtonHeight + 5; // 选项卡按钮下方留5像素间距，增加列表高度
        int listBottom = height - 40; // 为底部按钮留出空间，增加列表高度
        
        // 创建固定的搜索框（位于列表顶部，不随列表滚动）
        int searchBoxHeight = 20;
        int searchBoxTop = listTop;
        int searchBoxWidth = width - 20;
        int searchBoxLeft = 10;
        
        searchFieldFixed = new TextFieldWidget(textRenderer, searchBoxLeft + 4, searchBoxTop + 12, searchBoxWidth, searchBoxHeight, Text.literal("搜索")); // 向右4像素，向下12像素补偿文本偏移
        // 根据当前标签页设置占位符
        if (currentTab == Tab.GENERAL) {
            searchFieldFixed.setPlaceholder(Text.translatable("config.enchant_enhancement.search.placeholder_settings"));
        } else {
            searchFieldFixed.setPlaceholder(Text.translatable("config.enchant_enhancement.search.placeholder_enchantments"));
        }
        searchFieldFixed.setChangedListener(text -> {
            searchQuery = text.toLowerCase();
            filterEntries();
        });
        searchFieldFixed.setDrawsBackground(false);
        searchFieldFixed.setEditable(true);
        addSelectableChild(searchFieldFixed);
        // 初始可见性：两个标签页都显示（保持布局一致）
        searchFieldFixed.visible = true;
        // 更新类级别的searchField引用，以便其他方法使用
        this.searchField = searchFieldFixed;
        
        // 列表实际起始位置在搜索框下方（留5像素间距）
        int actualListTop = listTop + searchBoxHeight + 5;
        listHeight = listBottom - actualListTop;
        
        // 创建列表部件 - 宽度为屏幕宽度，使用自定义的滚动条
        this.listWidget = new EnchantmentListWidget(client, width, listHeight, actualListTop, 28); // 减少项目高度到28以缩小间隔
        addSelectableChild(this.listWidget);
        
        // 初始化常规和附魔等级标签页的列表内容
        updateListContent();
        
        // 按钮宽度和位置设置（保存和取消按钮，始终显示）
        int buttonWidth = 80; // 统一宽度
        int buttonHeight = 20;
        int rightMargin = 5; // 更靠近右侧边缘
        int bottomMargin = 10; // 更靠近底部边缘
        int buttonSpacing = 5;
        
        // 保存并退出按钮（右下角右边）
        int saveX = width - rightMargin - buttonWidth;
        int saveY = height - bottomMargin - buttonHeight;
        ButtonWidget saveButton = ButtonWidget.builder(
            Text.translatable("config.enchant_enhancement.button.save_exit").withColor(0xFFB1EAC2),
            button -> saveChanges()
        ).dimensions(saveX, saveY, buttonWidth, buttonHeight).build();
        addDrawableChild(saveButton);
        
        // 取消按钮（右下角左边）颜色：#F9867D
        int cancelX = saveX - buttonSpacing - buttonWidth;
        int cancelY = saveY;
        ButtonWidget cancelButton = ButtonWidget.builder(
            Text.translatable("gui.cancel").withColor(0xFFF9867D),
            button -> close()
        ).dimensions(cancelX, cancelY, buttonWidth, buttonHeight).build();
        addDrawableChild(cancelButton);
        
        // 初始化附魔分组
        initializeGroups();
    }
    
    private void initializeGroups() {
        // 保存当前展开状态
        Map<String, Boolean> savedExpandedStates = new java.util.HashMap<>(groupExpandedStates);
        
        enchantmentGroups.clear();
        groupExpandedStates.clear();
        
        // 初始化默认展开状态，保留用户之前的设置
        String[] groupNames = {"protection", "armor", "weapons", "bows", "tridents", "general"};
        for (String groupName : groupNames) {
            enchantmentGroups.put(groupName, new ArrayList<>());
            // 如果之前有展开状态，保留它；否则默认收起
            boolean expanded = savedExpandedStates.getOrDefault(groupName, false);
            groupExpandedStates.put(groupName, expanded);
        }
        
        // 将附魔分配到各组（基于ID关键字）
        for (Identifier enchantmentId : enchantmentLevels.keySet()) {
            String id = enchantmentId.toString();
            
            if (id.contains("protection")) {
                enchantmentGroups.get("protection").add(enchantmentId);
            } else if (id.contains("falling") || id.contains("feather") || id.contains("aqua") || 
                       id.contains("respiration") || id.contains("depth") || id.contains("frost") ||
                       id.contains("thorns") || id.contains("soul") || id.contains("swift")) {
                enchantmentGroups.get("armor").add(enchantmentId);
            } else if (id.contains("sharpness") || id.contains("smite") || id.contains("bane") ||
                       id.contains("sweeping") || id.contains("fire_aspect") || id.contains("density") ||
                       id.contains("breach") || id.contains("wind")) {
                enchantmentGroups.get("weapons").add(enchantmentId);
            } else if (id.contains("power") || id.contains("flame") || id.contains("punch") ||
                       id.contains("infinity") || id.contains("piercing") || id.contains("multishot") ||
                       id.contains("quick_charge")) {
                enchantmentGroups.get("bows").add(enchantmentId);
            } else if (id.contains("riptide") || id.contains("loyalty") || id.contains("channeling") ||
                       id.contains("impaling")) {
                enchantmentGroups.get("tridents").add(enchantmentId);
            } else if (id.contains("unbreaking") || id.contains("fortune") || id.contains("efficiency") ||
                       id.contains("mending") || id.contains("binding") || id.contains("vanishing")) {
                enchantmentGroups.get("general").add(enchantmentId);
            } else {
                // 未分类的附魔放入通用组
                enchantmentGroups.get("general").add(enchantmentId);
            }
        }
        

    }
    
    private void updateListContent() {
        if (listWidget == null) return;
        
        listWidget.clearEntriesPublic();
        
        if (currentTab == Tab.GENERAL) {
            // 常规标签页：添加常规设置条目，支持搜索过滤
            String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
            
            // 只有匹配搜索词或搜索词为空时才添加条目
            if (query.isEmpty() || "合并高级附魔".toLowerCase().contains(query)) {
                listWidget.addListEntry(new GeneralSettingEntry(
                    Text.translatable("config.enchant_enhancement.setting.merge_high_enchantments"),
                    EnchantmentConfig.isMergeHighEnchantments(),
                    value -> {
                        EnchantmentConfig.setMergeHighEnchantments(value);
                        return getToggleText(value);
                    },
                    Text.translatable("config.enchant_enhancement.tooltip.merge_high_enchantments")
                ));
            }
            
            if (query.isEmpty() || "战利品生产高级附魔书".toLowerCase().contains(query)) {
                listWidget.addListEntry(new GeneralSettingEntry(
                    Text.translatable("config.enchant_enhancement.setting.loot_high_enchantments"),
                    EnchantmentConfig.isLootHighEnchantments(),
                    value -> {
                        EnchantmentConfig.setLootHighEnchantments(value);
                        return getToggleText(value);
                    }
                ));
            }
            


            // 新增功能：盔甲保护兼容
            if (query.isEmpty() || "盔甲保护兼容".toLowerCase().contains(query)) {
                listWidget.addListEntry(new GeneralSettingEntry(
                    Text.translatable("config.enchant_enhancement.setting.armor_protection_compatibility"),
                    EnchantmentConfig.isArmorProtectionCompatibility(),
                    value -> {
                        EnchantmentConfig.setArmorProtectionCompatibility(value);
                        return getToggleText(value);
                    },
                    Text.translatable("config.enchant_enhancement.tooltip.armor_protection_compatibility")
                ));
            }

            // 武器附魔兼容
            if (query.isEmpty() || "武器附魔兼容".toLowerCase().contains(query)) {
                listWidget.addListEntry(new GeneralSettingEntry(
                    Text.translatable("config.enchant_enhancement.setting.weapon_enchantment_compatibility"),
                    EnchantmentConfig.isWeaponEnchantmentCompatibility(),
                    value -> {
                        EnchantmentConfig.setWeaponEnchantmentCompatibility(value);
                        return getToggleText(value);
                    },
                    Text.translatable("config.enchant_enhancement.tooltip.weapon_enchantment_compatibility")
                ));
            }

            // 三叉戟附魔拓展
            if (query.isEmpty() || "三叉戟附魔拓展".toLowerCase().contains(query)) {
                listWidget.addListEntry(new GeneralSettingEntry(
                    Text.translatable("config.enchant_enhancement.setting.trident_enchantment_expansion"),
                    EnchantmentConfig.isTridentEnchantmentExpansion(),
                    value -> {
                        EnchantmentConfig.setTridentEnchantmentExpansion(value);
                        return getToggleText(value);
                    },
                    Text.translatable("config.enchant_enhancement.tooltip.trident_enchantment_expansion")
                ));
            }



            // 斧头附魔拓展
            if (query.isEmpty() || "斧头附魔拓展".toLowerCase().contains(query)) {
                listWidget.addListEntry(new GeneralSettingEntry(
                    Text.translatable("config.enchant_enhancement.setting.axe_enchantment_expansion"),
                    EnchantmentConfig.isAxeEnchantmentExpansion(),
                    value -> {
                        EnchantmentConfig.setAxeEnchantmentExpansion(value);
                        return getToggleText(value);
                    },
                    Text.translatable("config.enchant_enhancement.tooltip.axe_enchantment_expansion")
                ));
            }

            // 弓附魔拓展和兼容
            if (query.isEmpty() || "弓附魔拓展".toLowerCase().contains(query) || "弓附魔兼容".toLowerCase().contains(query)) {
                listWidget.addListEntry(new GeneralSettingEntry(
                    Text.translatable("config.enchant_enhancement.setting.bow_enchantment_expansion"),
                    EnchantmentConfig.isBowLootingEnchantment(),
                    value -> {
                        EnchantmentConfig.setBowLootingEnchantment(value);
                        return getToggleText(value);
                    },
                    Text.translatable("config.enchant_enhancement.tooltip.bow_enchantment_expansion")
                ));
            }

            // 无限附魔不需要一支箭
            if (query.isEmpty() || "无限附魔不需要一支箭".toLowerCase().contains(query)) {
                listWidget.addListEntry(new GeneralSettingEntry(
                    Text.translatable("config.enchant_enhancement.setting.infinity_without_arrow"),
                    EnchantmentConfig.isInfinityWithoutArrow(),
                    value -> {
                        EnchantmentConfig.setInfinityWithoutArrow(value);
                        return getToggleText(value);
                    },
                    Text.translatable("config.enchant_enhancement.tooltip.infinity_without_arrow")
                ));
            }

            if (query.isEmpty() || "重置设置".toLowerCase().contains(query)) {
                // 添加重置按钮条目
                listWidget.addListEntry(new ResetButtonEntry());
            }
            
        } else {
            // 附魔等级标签页：使用分组显示附魔条目
            
            // 确保分组已初始化
            if (enchantmentGroups.isEmpty()) {
                initializeGroups();
            }
            
            // 应用搜索过滤（如果有搜索词，则显示平铺结果；否则显示分组）
            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                // 无搜索词：显示分组
                for (Map.Entry<String, List<Identifier>> groupEntry : enchantmentGroups.entrySet()) {
                    String groupName = groupEntry.getKey();
                    List<Identifier> ids = groupEntry.getValue();
                    if (!ids.isEmpty()) {
                        // 添加分组条目
                        listWidget.addListEntry(new GroupEntry(groupName, ids));
                        
                        // 如果分组展开，添加该组的附魔条目
                        if (groupExpandedStates.getOrDefault(groupName, false)) {
                            for (Identifier enchantmentId : ids) {
                                Integer level = enchantmentLevels.get(enchantmentId);
                                if (level != null) {
                                    listWidget.addListEntry(new EnchantmentEntry(enchantmentId, level));
                                }
                            }
                        }
                    }
                }
            } else {
                // 有搜索词：显示平铺的过滤结果
                String query = searchQuery.toLowerCase();
                for (Map.Entry<Identifier, Integer> entry : enchantmentLevels.entrySet()) {
                    EnchantmentEntry enchantmentEntry = new EnchantmentEntry(entry.getKey(), entry.getValue());
                    String displayName = enchantmentEntry.getEnchantmentDisplayName().toLowerCase();
                    if (displayName.contains(query)) {
                        listWidget.addListEntry(enchantmentEntry);
                    }
                }
            }
            

            
            // 添加恢复默认按钮条目
            listWidget.addListEntry(new RestoreDefaultsEntry());
        }
    }
    
    private void filterEntries() {
        if (currentTab == Tab.GENERAL) {
            // 常规标签页：直接更新列表内容以反映搜索过滤
            updateListContent();
        } else {
            // 附魔等级标签页：直接更新列表内容以反映搜索过滤（分组模式下）
            updateListContent();
        }
    }
    

    
    private void saveChanges() {
        for (EnchantmentEntry entry : allEntries) {
            try {
                EnchantmentConfig.setMaxLevel(entry.enchantmentId, entry.getLevel());
            } catch (Exception e) {
            }
        }
        EnchantmentConfig.save();
        close();
    }
    
    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
    
    @Override
    public void tick() {
        super.tick();
        
        // 处理重置按钮状态恢复（3秒后）
        if (resetButtonState == 2 && System.currentTimeMillis() - resetButtonTime > 3000) {
            resetButtonState = 0;
            // 按钮文本将在ResetButtonEntry.render()中自动更新
        }
        
        // 处理恢复默认值按钮状态恢复（3秒后）
        if (restoreDefaultsButtonState == 2 && System.currentTimeMillis() - restoreDefaultsButtonTime > 3000) {
            restoreDefaultsButtonState = 0;
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 先调用super.render()渲染背景和所有子组件（按钮等）
        super.render(context, mouseX, mouseY, delta);
        
        // 渲染列表和搜索框（两个标签页使用相同的布局）
        renderCustomTab(context, mouseX, mouseY, delta);
        
        // 渲染标题在左上角
        context.drawTextWithShadow(textRenderer, Text.translatable("config.enchant_enhancement.title.main"), 10, 15, 0xFFFFFF);
    }
    

    
    private void renderCustomTab(DrawContext context, int mouseX, int mouseY, float delta) {
        // 渲染列表（addSelectableChild添加的需要手动渲染）
        if (listWidget != null) {
            listWidget.render(context, mouseX, mouseY, delta);
        }
        
        // 渲染搜索框
        if (searchField != null) {

            

            
            // 然后渲染搜索框（背景透明）
            searchField.render(context, mouseX, mouseY, delta);
        }
    }
    
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // 如果不在游戏内，使用原版全景图背景
        if (client != null && client.world == null) {
            // 调用父类的renderBackground，这会渲染原版全景图背景
            // 不添加任何额外背景层，避免模糊效果
            super.renderBackground(context, mouseX, mouseY, delta);
        } else {
            // 在游戏内，使用半透明深色背景，隐藏背后的Mod Menu界面
            // 0x22000000 = 大约13%透明度的黑色，足够暗以隐藏Mod Menu但不会太暗
            context.fillGradient(0, 0, width, height, 0x22000000, 0x22000000);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 在附魔等级标签页时将点击事件传递给列表
        if (currentTab == Tab.CUSTOM && listWidget != null && listWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // 检查是否有输入框获得焦点，如果点击事件没有被处理，则取消所有输入框焦点
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!handled) {
            // 取消所有输入框焦点
            for (EnchantmentEntry entry : allEntries) {
                if (entry.levelField != null && entry.levelField.isFocused()) {
                    entry.levelField.setFocused(false);
                }
            }
        }
        return handled;
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (listWidget != null && listWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (listWidget != null && listWidget.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 将键盘事件传递给列表中的所有输入框
        if (listWidget != null) {
            for (EnchantmentEntry entry : allEntries) {
                if (entry.levelField != null && entry.levelField.isFocused()) {
                    if (entry.levelField.keyPressed(keyCode, scanCode, modifiers)) {
                        return true;
                    }
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        // 将字符输入事件传递给列表中的所有输入框
        if (listWidget != null) {
            for (EnchantmentEntry entry : allEntries) {
                if (entry.levelField != null && entry.levelField.isFocused()) {
                    if (entry.levelField.charTyped(chr, modifiers)) {
                        return true;
                    }
                }
            }
        }
        return super.charTyped(chr, modifiers);
    }
    
    // 附魔条目类
    public class EnchantmentEntry extends AlwaysSelectedEntryListWidget.Entry<EnchantmentEntry> {
        private final Identifier enchantmentId;

        private TextFieldWidget levelField;
        private int level;
        private float hoverProgress = 0.0f;

        private boolean initialized = false;
        
        public EnchantmentEntry(Identifier enchantmentId, int initialLevel) {
            this.enchantmentId = enchantmentId;
            this.level = initialLevel;

        }
        
        @Override
        public Text getNarration() {
            return Text.translatable("config.enchant_enhancement.narration", enchantmentId);
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // 平滑动画：淡白色背景
            float targetAlpha = hovered ? 0.1f : 0.0f;
            float speed = 0.8f; // 增加动画速度，使过渡更平滑快速
            hoverProgress += (targetAlpha - hoverProgress) * speed * tickDelta; // 使用tickDelta进行时间平滑插值
            hoverProgress = Math.max(0.0f, Math.min(1.0f, hoverProgress));
            
            if (hoverProgress > 0.001f) {
                int alpha = (int)(hoverProgress * 0x66) & 0xFF;
                int color = (alpha << 24) | 0xFFFFFF; // ARGB格式，白色带透明度
                context.fill(x, y, x + entryWidth, y + entryHeight, color);
            }
            
            // 渲染附魔名称
            String displayName = getEnchantmentDisplayName(enchantmentId);
            // 对于30像素高度的列表项，文本垂直居中位置调整为y + 11（字体高度9像素）
            int fontHeight = textRenderer.fontHeight; // 通常为9
            int textY = y + Math.round((entryHeight - fontHeight) / 2f); // 使用浮点计算以正确居中
            context.drawTextWithShadow(textRenderer, displayName, x + 10, textY, 0xFFFFFF);
            
            // 创建或更新等级输入框
            if (!initialized) {
                int fieldWidth = 78; // 内部宽度78，加上边框后总宽度80
                int fieldHeight = 18; // 内部高度18，加上边框后总高度20
                int fieldX = x + entryWidth - 80 - 15; // 总宽度80，右侧留15像素边距，确保完全可见
                int fieldY = y + (entryHeight - 20) / 2; // 总高度20，垂直居中
                
                levelField = new TextFieldWidget(textRenderer, fieldX, fieldY, fieldWidth, fieldHeight, Text.empty());
                levelField.setText(String.valueOf(level));
                levelField.setChangedListener(text -> {
                    try {
                        int newLevel = Integer.parseInt(text);
                        if (newLevel >= 1 && newLevel <= 255) {
                            // 有效输入，更新等级并显示白色文本
                            level = newLevel;
                            // 更新enchantmentLevels映射
                            ConfigScreen.this.enchantmentLevels.put(enchantmentId, newLevel);
                            levelField.setEditableColor(0xFFFFFF);
                        } else {
                            // 超出范围，保持原值但显示红色文本提示
                            levelField.setEditableColor(0xFF6666); // 红色提示
                            // 不更新level值，允许用户继续编辑
                        }
                    } catch (NumberFormatException e) {
                        // 非法输入（包括空字符串），保持原值但显示红色文本提示
                        levelField.setEditableColor(0xFF6666); // 红色提示
                        // 不更新level值，允许用户继续编辑
                    }
                });
                // 使用原版输入框样式
                levelField.setEditableColor(0xFFFFFF);
                levelField.setUneditableColor(0xFFFFFF);
                levelField.setMaxLength(3); // 最多3位数（如255）

                levelField.setEditable(true);
                levelField.setFocusUnlocked(true);
                levelField.setFocused(false);
                
                // 将输入框添加到屏幕的可交互组件中
                ConfigScreen.this.addSelectableChild(levelField);
                
                initialized = true;
            }
            
            // 更新输入框位置和大小
            int fieldX = x + entryWidth - 80 - 15; // 总宽度80，右侧留15像素边距，确保完全可见
            int fieldY = y + (entryHeight - 20) / 2; // 总高度20，垂直居中
            levelField.setPosition(fieldX, fieldY);
            
            // 渲染输入框（使用原版样式）
            levelField.render(context, mouseX, mouseY, tickDelta);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (levelField != null && levelField.mouseClicked(mouseX, mouseY, button)) {
                // 移除所有其他输入框的焦点
                for (EnchantmentEntry entry : ConfigScreen.this.allEntries) {
                    if (entry != this && entry.levelField != null) {
                        entry.levelField.setFocused(false);
                    }
                }
                levelField.setFocused(true);
                // 确保屏幕的焦点设置正确
                ConfigScreen.this.setFocused(levelField);
                return true;
            }
            return false;
        }
        
        public int getLevel() {
            return level;
        }
        
        private String getEnchantmentDisplayName(Identifier id) {
            // 尝试使用Minecraft的翻译系统获取中文名称
            // 附魔的翻译键格式: "enchantment.minecraft.sharpness"
            String translationKey = "enchantment." + id.getNamespace() + "." + id.getPath();
            Text translated = Text.translatable(translationKey);
            
            // 如果翻译键存在且不是原键名，使用翻译结果
            if (!translated.getString().equals(translationKey)) {
                return translated.getString();
            }
            
            // 否则使用英文名称（首字母大写，下划线替换为空格）
            String path = id.getPath();
            String[] words = path.split("_");
            StringBuilder result = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) {
                    result.append(Character.toUpperCase(word.charAt(0)))
                           .append(word.substring(1))
                           .append(" ");
                }
            }
            return result.toString().trim();
        }
        
        // 公共方法，供外部类访问显示名称
        public String getEnchantmentDisplayName() {
            return getEnchantmentDisplayName(enchantmentId);
        }
    }
    
    // 选项卡按钮条目
    public class TabButtonEntry extends AlwaysSelectedEntryListWidget.Entry<TabButtonEntry> {
        private final Text buttonText;
        private final Tab targetTab;
        private final boolean isActive;
        private ButtonWidget buttonWidget;
        
        public TabButtonEntry(Text buttonText, Tab targetTab, boolean isActive) {
            this.buttonText = buttonText;
            this.targetTab = targetTab;
            this.isActive = isActive;
        }
        
        @Override
        public Text getNarration() {
            return Text.translatable("config.enchant_enhancement.tab_narration", buttonText);
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // 创建或更新按钮
            if (buttonWidget == null) {
                int buttonWidth = 60; // 较短按钮
                int buttonHeight = 18;
                int buttonX = x + (entryWidth - buttonWidth) / 2;
                int buttonY = y + (entryHeight - buttonHeight) / 2;
                
                buttonWidget = ButtonWidget.builder(
                    buttonText,
                    button -> switchTab(targetTab)
                ).dimensions(buttonX, buttonY, buttonWidth, buttonHeight).build();
                
                // 设置按钮颜色：激活状态为绿色，非激活状态为灰色
                int color = isActive ? 0xFFB1EAC2 : 0xFFAAAAAA;
                buttonWidget.setMessage(buttonText.copy().withColor(color));
            }
            
            // 更新按钮位置
            int buttonWidth = 60;
            int buttonHeight = 18;
            int buttonX = x + (entryWidth - buttonWidth) / 2;
            int buttonY = y + (entryHeight - buttonHeight) / 2;
            buttonWidget.setPosition(buttonX, buttonY);
            
            buttonWidget.render(context, mouseX, mouseY, tickDelta);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (buttonWidget != null && buttonWidget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return false;
        }
    }
    
    // 常规设置条目（开关）
    public class GeneralSettingEntry extends AlwaysSelectedEntryListWidget.Entry<GeneralSettingEntry> {
        private final Text label;
        private boolean value;
        private final java.util.function.Function<Boolean, Text> onToggle;
        private final List<Text> tooltipLines;
        private ButtonWidget toggleButton;
        private float hoverProgress = 0.0f;
        
        public GeneralSettingEntry(Text label, boolean initialValue, java.util.function.Function<Boolean, Text> onToggle) {
            this(label, initialValue, onToggle, null);
        }
        
        public GeneralSettingEntry(Text label, boolean initialValue, java.util.function.Function<Boolean, Text> onToggle, Text tooltip) {
            this.label = label;
            this.value = initialValue;
            this.onToggle = onToggle;
            if (tooltip != null) {
                String tooltipStr = tooltip.getString();
                if (tooltipStr.contains("\\n")) {
                    String[] lines = tooltipStr.split("\\\\n");
                    this.tooltipLines = new java.util.ArrayList<>();
                    for (String line : lines) {
                        this.tooltipLines.add(Text.literal(line));
                    }
                } else if (tooltipStr.contains("\n")) {
                    String[] lines = tooltipStr.split("\n");
                    this.tooltipLines = new java.util.ArrayList<>();
                    for (String line : lines) {
                        this.tooltipLines.add(Text.literal(line));
                    }
                } else {
                    this.tooltipLines = java.util.Collections.singletonList(tooltip);
                }
            } else {
                this.tooltipLines = null;
            }
        }
        
        @Override
        public Text getNarration() {
            return Text.translatable("config.enchant_enhancement.setting_narration", label);
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // 平滑动画：淡白色背景
            float targetAlpha = hovered ? 0.1f : 0.0f;
            float speed = 0.8f; // 增加动画速度，使过渡更平滑快速
            hoverProgress += (targetAlpha - hoverProgress) * speed * tickDelta; // 使用tickDelta进行时间平滑插值
            hoverProgress = Math.max(0.0f, Math.min(1.0f, hoverProgress));
            
            if (hoverProgress > 0.001f) {
                int alpha = (int)(hoverProgress * 0x66) & 0xFF;
                int color = (alpha << 24) | 0xFFFFFF; // ARGB格式，白色带透明度
                context.fill(x, y, x + entryWidth, y + entryHeight, color);
            }
            
            // 渲染标签（垂直居中）
            int fontHeight = textRenderer.fontHeight; // 通常为9
            int textY = y + Math.round((entryHeight - fontHeight) / 2f); // 使用浮点计算以正确居中
            context.drawTextWithShadow(textRenderer, label, x + 10, textY, 0xFFFFFF);
            
            // 创建或更新开关按钮（尺寸统一为宽80高20）
            if (toggleButton == null) {
                int buttonWidth = 80;
                int buttonHeight = 20;
                int buttonX = x + entryWidth - buttonWidth - 5; // 右侧留5像素边距
                int buttonY = y + (entryHeight - buttonHeight) / 2; // 垂直居中
                
                toggleButton = ButtonWidget.builder(
                    onToggle.apply(value),
                    button -> {
                        value = !value;
                        toggleButton.setMessage(onToggle.apply(value));
                    }
                ).dimensions(buttonX, buttonY, buttonWidth, buttonHeight).build();
            }
            
            // 更新按钮位置（尺寸统一为宽80高20）
            int buttonWidth = 80;
            int buttonHeight = 20;
            int buttonX = x + entryWidth - buttonWidth - 5; // 右侧留5像素边距
            int buttonY = y + (entryHeight - buttonHeight) / 2; // 垂直居中
            toggleButton.setPosition(buttonX, buttonY);
            
            toggleButton.render(context, mouseX, mouseY, tickDelta);
            
            // 渲染工具提示（如果存在且鼠标悬停）
            if (hovered && tooltipLines != null) {
                context.drawTooltip(ConfigScreen.this.textRenderer, tooltipLines, mouseX, mouseY);
            }
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (toggleButton != null && toggleButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return false;
        }
    }
    
    // 经验等级滑块条目
    public class ExperienceSliderEntry extends AlwaysSelectedEntryListWidget.Entry<ExperienceSliderEntry> {
        private int value;

        private SliderWidget sliderWidget;
        private float hoverProgress = 0.0f;
        
        public ExperienceSliderEntry(int initialValue) {
            this.value = Math.max(20, Math.min(50, initialValue));
        }
        
        @Override
        public Text getNarration() {
            return Text.translatable("config.enchant_enhancement.experience_narration");
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // 平滑动画：淡白色背景
            float targetAlpha = hovered ? 0.1f : 0.0f;
            float speed = 0.8f; // 增加动画速度，使过渡更平滑快速
            hoverProgress += (targetAlpha - hoverProgress) * speed * tickDelta; // 使用tickDelta进行时间平滑插值
            hoverProgress = Math.max(0.0f, Math.min(1.0f, hoverProgress));
            
            if (hoverProgress > 0.001f) {
                int alpha = (int)(hoverProgress * 0x66) & 0xFF;
                int color = (alpha << 24) | 0xFFFFFF; // ARGB格式，白色带透明度
                context.fill(x, y, x + entryWidth, y + entryHeight, color);
            }
            
            // 渲染标签（垂直居中）
            int fontHeight = textRenderer.fontHeight; // 通常为9
            int textY = y + Math.round((entryHeight - fontHeight) / 2f); // 使用浮点计算以正确居中
            context.drawTextWithShadow(textRenderer, Text.translatable("config.enchant_enhancement.slider.max_experience_cost"), x + 10, textY, 0xFFFFFF);
            

            
            // 创建或更新滑块
            if (sliderWidget == null) {
                int sliderWidth = 80; // 统一宽度
                int sliderHeight = 20; // 统一高度
                int sliderX = x + entryWidth - sliderWidth - 5; // 右侧留5像素边距
                int sliderY = y + (entryHeight - sliderHeight) / 2; // 垂直居中
                double sliderValue = (value - 20.0) / 30.0;
                
                sliderWidget = new SliderWidget(sliderX, sliderY, sliderWidth, sliderHeight, 
                    Text.empty(), sliderValue) {
                    @Override
                    protected void updateMessage() {
                        // 确保value在0-1范围内
                        double normalizedValue = Math.max(0.0, Math.min(1.0, this.value));
                        int currentValue = 20 + (int)(normalizedValue * 30.0);
                        currentValue = Math.max(20, Math.min(50, currentValue));
                        setMessage(Text.literal(String.valueOf(currentValue)).withColor(0xFFFFFF));
                    }
                    
                    @Override
                    protected void applyValue() {
                        // 确保value在0-1范围内
                        double normalizedValue = Math.max(0.0, Math.min(1.0, this.value));
                        int newValue = 20 + (int)(normalizedValue * 30.0);
                        newValue = Math.max(20, Math.min(50, newValue));
                        value = newValue;

                        // 更新滑块内部值以保持同步
                        this.value = Math.max(0.0, Math.min(1.0, (newValue - 20.0) / 30.0));
                    }
                    

                    

                    


                    

                };
                // 初始化消息
                sliderWidget.setMessage(Text.literal(String.valueOf(value)).withColor(0xFFFFFF));
            }
            
            // 更新滑块位置和值（尺寸统一为宽80高20）
            int sliderWidth = 80; // 统一宽度
            int sliderHeight = 20; // 统一高度
            int sliderX = x + entryWidth - sliderWidth - 5; // 右侧留5像素边距
            int sliderY = y + (entryHeight - sliderHeight) / 2; // 垂直居中
            sliderWidget.setPosition(sliderX, sliderY);
            // 注意：SliderWidget.setValue()是private的，不能直接调用
            // 滑块值在创建时已设置，通过applyValue方法更新
            
            sliderWidget.render(context, mouseX, mouseY, tickDelta);
            

        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (sliderWidget != null && sliderWidget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return false;
        }
        
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (sliderWidget != null && sliderWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
            return false;
        }
        
        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (sliderWidget != null && sliderWidget.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
            return false;
        }
    }
    
    // 经验消耗比例滑块条目
    public class ExperienceMultiplierSliderEntry extends AlwaysSelectedEntryListWidget.Entry<ExperienceMultiplierSliderEntry> {
        private float value;
        private SliderWidget sliderWidget;
        private float hoverProgress = 0.0f;
        
        public ExperienceMultiplierSliderEntry(float initialValue) {
            this.value = Math.max(0.8F, Math.min(1.0F, initialValue));
        }
        
        @Override
        public Text getNarration() {
            return Text.translatable("config.enchant_enhancement.experience_multiplier_narration");
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // 平滑动画：淡白色背景
            float targetAlpha = hovered ? 0.1f : 0.0f;
            float speed = 0.8f; // 增加动画速度，使过渡更平滑快速
            hoverProgress += (targetAlpha - hoverProgress) * speed * tickDelta; // 使用tickDelta进行时间平滑插值
            hoverProgress = Math.max(0.0f, Math.min(1.0f, hoverProgress));
            
            if (hoverProgress > 0.001f) {
                int alpha = (int)(hoverProgress * 0x55) & 0xFF;
                int color = (alpha << 24) | 0xFFFFFF; // ARGB格式，白色带透明度
                context.fill(x, y, x + entryWidth, y + entryHeight, color);
            }
            
            // 渲染标签
            context.drawTextWithShadow(textRenderer, Text.translatable("config.enchant_enhancement.slider.experience_multiplier"), x + 10, y + 8, 0xFFFFFF);
            
            // 创建或更新滑块
            if (sliderWidget == null) {
                int sliderWidth = 80; // 统一宽度
                int sliderHeight = 20; // 统一高度
                int sliderX = x + entryWidth - sliderWidth - 5; // 右侧留5像素边距
                int sliderY = y + (entryHeight - sliderHeight) / 2; // 垂直居中
                double sliderValue = (value - 0.8) / 0.2; // 映射到0-1范围（0.8-1 -> 0-1）
                
                sliderWidget = new SliderWidget(sliderX, sliderY, sliderWidth, sliderHeight, 
                    Text.empty(), sliderValue) {
                    @Override
                    protected void updateMessage() {
                        // 确保value在0-1范围内
                        double normalizedValue = Math.max(0.0, Math.min(1.0, this.value));
                        float currentValue = 0.8F + (float)(normalizedValue * 0.2);
                        currentValue = Math.max(0.8F, Math.min(1.0F, currentValue));
                        setMessage(Text.literal(String.format("%.2f", currentValue)).withColor(0xFFFFFF));
                    }
                    
                    @Override
                    protected void applyValue() {
                        // 确保value在0-1范围内
                        double normalizedValue = Math.max(0.0, Math.min(1.0, this.value));
                        float newValue = 0.8F + (float)(normalizedValue * 0.2);
                        newValue = Math.max(0.8F, Math.min(1.0F, newValue));
                        value = newValue;

                        // 更新滑块内部值以保持同步
                        this.value = Math.max(0.0, Math.min(1.0, (newValue - 0.8) / 0.2));
                    }
                    

                    

                    

                };
                // 初始化消息
                sliderWidget.setMessage(Text.literal(String.format("%.2f", value)).withColor(0xFFFFFF));
            }
            
            // 更新滑块位置和值（尺寸统一为宽80高20）
            int sliderWidth = 80; // 统一宽度
            int sliderHeight = 20; // 统一高度
            int sliderX = x + entryWidth - sliderWidth - 5; // 右侧留5像素边距
            int sliderY = y + (entryHeight - sliderHeight) / 2; // 垂直居中
            sliderWidget.setPosition(sliderX, sliderY);
            // 注意：SliderWidget.setValue()是private的，不能直接调用
            // 滑块值在创建时已设置，通过applyValue方法更新
            
            sliderWidget.render(context, mouseX, mouseY, tickDelta);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (sliderWidget != null && sliderWidget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return false;
        }
        
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (sliderWidget != null && sliderWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
            return false;
        }
        
        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (sliderWidget != null && sliderWidget.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
            return false;
        }
    }
    
    // 重置按钮条目
    public class ResetButtonEntry extends AlwaysSelectedEntryListWidget.Entry<ResetButtonEntry> {
        private ButtonWidget resetButton;
        
        @Override
        public Text getNarration() {
            return Text.translatable("config.enchant_enhancement.reset_narration");
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // 在左侧渲染"重置设置"文本（垂直居中）
            int fontHeight = textRenderer.fontHeight; // 通常为9
            int textY = y + Math.round((entryHeight - fontHeight) / 2f); // 使用浮点计算以正确居中
            context.drawTextWithShadow(textRenderer, Text.translatable("config.enchant_enhancement.button.reset"), x + 10, textY, 0xFFFFFF);
            
            // 创建或更新重置按钮（放在最右侧，尺寸统一为宽80高20）
            if (resetButton == null) {
                int buttonWidth = 80; // 统一宽度
                int buttonHeight = 20; // 统一高度
                int buttonX = x + entryWidth - buttonWidth - 5; // 右侧留5像素边距，与上方按钮统一
                int buttonY = y + (entryHeight - buttonHeight) / 2; // 垂直居中，与上方按钮统一
                
                resetButton = ButtonWidget.builder(
                    Text.translatable("config.enchant_enhancement.button.reset").withColor(0xFFF9867D), // 初始文本为"重置"
                    button -> handleResetButton()
                ).dimensions(buttonX, buttonY, buttonWidth, buttonHeight).build();
            }
            
            // 更新按钮位置（统一宽度）
            int buttonWidth = 80; // 统一宽度
            int buttonHeight = 20; // 统一高度
            int buttonX = x + entryWidth - buttonWidth - 5; // 右侧留5像素边距，与上方按钮统一
            int buttonY = y + (entryHeight - buttonHeight) / 2; // 垂直居中，与上方按钮统一
            resetButton.setPosition(buttonX, buttonY);
            
            // 根据状态更新按钮文本
            if (resetButtonState == 1) {
                resetButton.setMessage(Text.translatable("config.enchant_enhancement.button.reset_confirm").withColor(0xFFF9867D));
            } else if (resetButtonState == 2) {
                resetButton.setMessage(Text.translatable("config.enchant_enhancement.button.reset_done").withColor(0xFFB1EAC2));
            } else {
                resetButton.setMessage(Text.translatable("config.enchant_enhancement.button.reset").withColor(0xFFF9867D)); // 正常状态显示"重置"
            }
            
            resetButton.render(context, mouseX, mouseY, tickDelta);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (resetButton != null && resetButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return false;
        }
    }
    
    // 搜索条目
    public class SearchEntry extends AlwaysSelectedEntryListWidget.Entry<SearchEntry> {
        private TextFieldWidget searchFieldLocal;
        private float hoverProgress = 0.0f;
        
        @Override
        public Text getNarration() {
            return Text.translatable("config.enchant_enhancement.search_narration");
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // 平滑动画：淡白色背景
            float targetAlpha = hovered ? 0.1f : 0.0f;
            float speed = 0.8f; // 增加动画速度，使过渡更平滑快速
            hoverProgress += (targetAlpha - hoverProgress) * speed * tickDelta; // 使用tickDelta进行时间平滑插值
            hoverProgress = Math.max(0.0f, Math.min(1.0f, hoverProgress));
            
            if (hoverProgress > 0.001f) {
                int alpha = (int)(hoverProgress * 0x55) & 0xFF;
                int color = (alpha << 24) | 0xFFFFFF; // ARGB格式，白色带透明度
                context.fill(x, y, x + entryWidth, y + entryHeight, color);
            }
            
            // 创建或更新搜索框
            if (searchFieldLocal == null) {
    
                int searchHeight = 20; // 增加高度
                int searchWidth = entryWidth - 20;
                int searchX = x + 10;
                int searchY = y + (entryHeight - searchHeight) / 2;
                
                searchFieldLocal = new TextFieldWidget(textRenderer, searchX + 4, searchY + 12, searchWidth, searchHeight, Text.translatable("config.enchant_enhancement.search.placeholder")); // 向右4像素，向下12像素补偿文本偏移
                searchFieldLocal.setPlaceholder(Text.translatable("config.enchant_enhancement.search.placeholder_enchantments"));
                searchFieldLocal.setChangedListener(text -> {
                    searchQuery = text.toLowerCase();
                    filterEntries();
                });
                searchFieldLocal.setDrawsBackground(false);
                searchFieldLocal.setEditable(true);

                
                // 将搜索框设置为类级别的引用，以便其他方法访问
                ConfigScreen.this.searchField = searchFieldLocal;
                ConfigScreen.this.addSelectableChild(searchFieldLocal);
            }
            
            // 更新搜索框位置
            int searchHeight = 20; // 增加高度
            int searchX = x + 10 + 4;
            int searchY = y + (entryHeight - searchHeight) / 2 + 12;
            searchFieldLocal.setPosition(searchX, searchY);
            
            searchFieldLocal.render(context, mouseX, mouseY, tickDelta);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (searchFieldLocal != null && searchFieldLocal.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return false;
        }
    }
    

    
    // 恢复默认按钮条目
    public class RestoreDefaultsEntry extends AlwaysSelectedEntryListWidget.Entry<RestoreDefaultsEntry> {
        private ButtonWidget restoreButton;
        private float hoverProgress = 0.0f;
        
        @Override
        public Text getNarration() {
            return Text.translatable("config.enchant_enhancement.restore_narration");
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // 平滑动画：淡白色背景
            float targetAlpha = hovered ? 0.1f : 0.0f;
            float speed = 0.8f;
            hoverProgress += (targetAlpha - hoverProgress) * speed * tickDelta;
            hoverProgress = Math.max(0.0f, Math.min(1.0f, hoverProgress));
            
            if (hoverProgress > 0.001f) {
                int alpha = (int)(hoverProgress * 0x66) & 0xFF;
                int color = (alpha << 24) | 0xFFFFFF;
                context.fill(x, y, x + entryWidth, y + entryHeight, color);
            }
            
            // 渲染左侧文字（垂直居中）
            int fontHeight = textRenderer.fontHeight; // 通常为9
            int textY = y + Math.round((entryHeight - fontHeight) / 2f); // 使用浮点计算以正确居中
            context.drawTextWithShadow(textRenderer, Text.translatable("config.enchant_enhancement.button.restore"), x + 10, textY, 0xFFFFFF);
            
            // 创建或更新恢复默认按钮（右侧）
            if (restoreButton == null) {
                int buttonWidth = 80; // 统一宽度80像素
                int buttonHeight = 20; // 统一高度
                int buttonX = x + entryWidth - buttonWidth - 15; // 右侧留15像素边距，与输入框统一
                int buttonY = y + (entryHeight - buttonHeight) / 2; // 垂直居中，与上方按钮统一
                
                restoreButton = ButtonWidget.builder(
                    getButtonText(),
                    button -> handleButtonClick()
                ).dimensions(buttonX, buttonY, buttonWidth, buttonHeight).build();
            }
            
            // 更新按钮位置和文本
            int buttonWidth = 80; // 统一宽度80像素
            int buttonHeight = 20;
            int buttonX = x + entryWidth - buttonWidth - 15; // 右侧留15像素边距，与输入框统一
            int buttonY = y + (entryHeight - buttonHeight) / 2; // 垂直居中，与上方按钮统一
            restoreButton.setPosition(buttonX, buttonY);
            restoreButton.setMessage(getButtonText());
            
            restoreButton.render(context, mouseX, mouseY, tickDelta);
        }
        
        private Text getButtonText() {
            if (ConfigScreen.this.restoreDefaultsButtonState == 1) {
                return Text.translatable("config.enchant_enhancement.button.restore_confirm").withColor(0xFFF9867D);
            } else if (ConfigScreen.this.restoreDefaultsButtonState == 2) {
                return Text.translatable("config.enchant_enhancement.button.restore_done").withColor(0xFFB1EAC2); // 绿色
            } else {
                return Text.translatable("config.enchant_enhancement.button.restore").withColor(0xFFF9867D);
            }
        }
        
        private void handleButtonClick() {
            if (ConfigScreen.this.restoreDefaultsButtonState == 0) {
                // 第一步：显示确认提示
                ConfigScreen.this.restoreDefaultsButtonState = 1;
                // 刷新列表以更新按钮文本
                updateListContent();
            } else if (ConfigScreen.this.restoreDefaultsButtonState == 1) {
                // 第二步：执行恢复默认操作并显示"已恢复!"
                ConfigScreen.this.restoreDefaultsButtonState = 2;
                ConfigScreen.this.restoreDefaultsButtonTime = System.currentTimeMillis();
                
                // 执行恢复默认操作 - 调用 EnchantmentConfig 的方法
                EnchantmentConfig.resetToDefaults();
                
                // 重新从配置获取所有等级
                ConfigScreen.this.enchantmentLevels.clear();
                ConfigScreen.this.enchantmentLevels.putAll(EnchantmentConfig.getAllLevels());
                
                // 重新初始化附魔分组
                ConfigScreen.this.initializeGroups();
                
                // 重新初始化所有附魔条目，用于保存配置
                ConfigScreen.this.allEntries.clear();
                for (Map.Entry<Identifier, Integer> entry : ConfigScreen.this.enchantmentLevels.entrySet()) {
                    ConfigScreen.this.allEntries.add(new EnchantmentEntry(entry.getKey(), entry.getValue()));
                }
                
                // 刷新UI列表以显示更新后的值
                updateListContent();
            }
            // 状态2时不处理点击，等待计时器重置
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (restoreButton != null && restoreButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return false;
        }
    }
    
    // 分组条目
    public class GroupEntry extends AlwaysSelectedEntryListWidget.Entry<GroupEntry> {
        private final String groupName;
        private final List<Identifier> enchantmentIds;
        private float hoverProgress = 0.0f;
        
        public GroupEntry(String groupName, List<Identifier> enchantmentIds) {
            this.groupName = groupName;
            this.enchantmentIds = enchantmentIds;
        }
        
        @Override
        public Text getNarration() {
            return Text.translatable("config.enchant_enhancement.group_narration", groupName);
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // 平滑动画：淡白色背景
            float targetAlpha = hovered ? 0.1f : 0.0f;
            float speed = 0.8f;
            hoverProgress += (targetAlpha - hoverProgress) * speed * tickDelta;
            hoverProgress = Math.max(0.0f, Math.min(1.0f, hoverProgress));
            
            if (hoverProgress > 0.001f) {
                int alpha = (int)(hoverProgress * 0x66) & 0xFF;
                int color = (alpha << 24) | 0xFFFFFF;
                context.fill(x, y, x + entryWidth, y + entryHeight, color);
            }
            
            // 渲染组名（鼠标悬停时变淡黄色）
            int groupNameColor = hovered ? 0xFFFFAA : 0xFFFFFF; // 悬停时淡黄色，否则白色
            context.drawTextWithShadow(textRenderer, Text.translatable("config.enchant_enhancement.group." + groupName), x + 10, y + 8, groupNameColor);
            
            // 渲染切换按钮文本（"+"或"-"），鼠标悬停时变淡黄色
            boolean expanded = groupExpandedStates.getOrDefault(groupName, false);
            String buttonText = expanded ? "-" : "+";
            int textWidth = textRenderer.getWidth(buttonText);
            int buttonX = x + entryWidth - textWidth - 10; // 右侧留10像素边距
            int buttonY = y + 9; // 与左侧文本对齐（y + 8 + 1像素微调）
            
            int buttonColor = hovered ? 0xFFFFAA : 0xFFFFFF; // 悬停时淡黄色，否则白色
            context.drawTextWithShadow(textRenderer, Text.literal(buttonText), buttonX, buttonY, buttonColor);
        }
        

        
        private void toggleGroup() {
            boolean current = groupExpandedStates.getOrDefault(groupName, false);
            groupExpandedStates.put(groupName, !current);
            // 播放按钮点击音效
            if (ConfigScreen.this.client != null) {
                ConfigScreen.this.client.getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                );
            }
            // 刷新列表内容以反映展开/收起状态
            updateListContent();
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // 点击整个条目区域切换分组
            toggleGroup();
            return true;
        }
        
        public boolean isExpanded() {
            return groupExpandedStates.getOrDefault(groupName, false);
        }
        
        public List<Identifier> getEnchantmentIds() {
            return enchantmentIds;
        }
    }
    
    // 通用列表部件，支持多种类型的条目
    @SuppressWarnings({"rawtypes", "unchecked"})
    public class EnchantmentListWidget extends AlwaysSelectedEntryListWidget {
        private static final double SCROLL_FACTOR = 3.0; // 增加滚动幅度，提高灵敏度
        private final int itemHeight;
        // 自定义滚动条拖动状态
        private boolean customScrollbarDragging = false;
        private int customScrollbarDragStartY = 0;
        private double customScrollbarDragStartScroll = 0.0;
        private int customScrollbarLeft, customScrollbarRight, customScrollbarTop, customScrollbarBottom; // 保存当前滚动条位置用于鼠标检测
        
        public EnchantmentListWidget(net.minecraft.client.MinecraftClient client, int width, int height, int top, int itemHeight) {
            super(client, width, height, top, itemHeight);
            this.itemHeight = itemHeight;
            // 初始化滚动条位置
            this.customScrollbarLeft = 0;
            this.customScrollbarRight = 0;
            this.customScrollbarTop = 0;
            this.customScrollbarBottom = 0;
        }
        
        @SuppressWarnings("unchecked")
        public void addListEntry(AlwaysSelectedEntryListWidget.Entry<?> entry) {
            super.addEntry(entry);
        }
        
        // 公共方法，用于从外部类清除条目
        public void clearEntriesPublic() {
            this.clearEntries();
        }
        
        @Override
        public int getRowWidth() {
            return width - 40;
        }
        
        @Override
        protected int getScrollbarX() {
            return getX() + getWidth() + 100; // 将滚动条移出屏幕外，隐藏原版滚动条
        }
        
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            // 应用滚动因子，提高滚动灵敏度
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount * SCROLL_FACTOR);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // 检测是否点击在自定义滚动条上
            if (getMaxScroll() > 0 && mouseX >= customScrollbarLeft && mouseX <= customScrollbarRight && 
                mouseY >= customScrollbarTop && mouseY <= customScrollbarBottom) {
                customScrollbarDragging = true;
                customScrollbarDragStartY = (int)mouseY;
                customScrollbarDragStartScroll = getScrollAmount();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (customScrollbarDragging) {
                int deltaYInt = (int)mouseY - customScrollbarDragStartY;
                int visibleHeight = getHeight();
                double maxScroll = getMaxScroll();
                double newScroll = customScrollbarDragStartScroll + (deltaYInt * maxScroll / (visibleHeight - (customScrollbarBottom - customScrollbarTop)));
                setScrollAmount(newScroll);
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (customScrollbarDragging) {
                customScrollbarDragging = false;
                return true;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        protected void renderScrollBar(DrawContext context, int mouseX, int mouseY, float delta) {
            // 空实现，禁用原版滚动条渲染
        }
        
        protected void renderSelection(DrawContext context, int y, int entryHeight, int borderColor, int fillColor) {
            // 空实现，禁用选中状态渲染
        }
        
        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            // 先调用父类渲染列表内容（包括原版滚动条）
            super.renderWidget(context, mouseX, mouseY, delta);
            

            
            // 自定义滚动条渲染：白色半透明，靠右，宽度增加
            if (getMaxScroll() > 0) {
                int customScrollbarWidth = 6; // 增加宽度
                int customScrollbarRight = getX() + getWidth() - 2; // 最右侧，留2像素边距
                int customScrollbarLeft = customScrollbarRight - customScrollbarWidth;
                
                // 计算滚动条高度和位置
                int contentHeight = getEntryCount() * this.itemHeight; // 使用实际的项目高度
                int visibleHeight = getHeight();
                int scrollbarHeight = Math.max(10, (int)((float)visibleHeight * visibleHeight / contentHeight));
                // 限制滚动条最大高度不超过可见区域的1/3
                scrollbarHeight = Math.min(visibleHeight / 3, scrollbarHeight);
                int customScrollbarTop = getY() + (int)((float)getScrollAmount() * (visibleHeight - scrollbarHeight) / getMaxScroll());
                int customScrollbarBottom = customScrollbarTop + scrollbarHeight;
                
                // 保存滚动条位置用于鼠标检测
                this.customScrollbarLeft = customScrollbarLeft;
                this.customScrollbarRight = customScrollbarRight;
                this.customScrollbarTop = customScrollbarTop;
                this.customScrollbarBottom = customScrollbarBottom;
                
                // 绘制白色半透明滚动条
                int scrollbarColor = 0x88FFFFFF; // 半透明白色
                context.fill(customScrollbarLeft, customScrollbarTop, customScrollbarRight, customScrollbarBottom, scrollbarColor);
            }
        }
        


    }
}