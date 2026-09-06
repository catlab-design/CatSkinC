package com.sammy.catskinc.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

public final class SettingsScreen extends Screen {
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int leftW;

    private TextFieldWidget searchBox;
    private TextFieldWidget ipTextField;
    private TextFieldWidget requestSigningKeyTextField;
    private String searchQuery = "";
    private int settingsScroll;
    
    private boolean generalExpanded = true;
    private boolean myopiaExpanded = true;
    private boolean keybindsExpanded = true;
    private boolean toastsExpanded = true;
    private boolean debugExpanded = true;

    private static final class SettingItem {
        final String key;
        final String label;
        final String category;
        
        SettingItem(String key, String label, String category) {
            this.key = key;
            this.label = label;
            this.category = category;
        }
    }

    private final List<SettingItem> settings = List.of(
        new SettingItem("catskinCloudIp", "CatSkinCloud IP", "General"),
        new SettingItem("connectionMode", "Connection Mode", "General"),
        new SettingItem("requestSigningKey", "Request Signing Key", "General"),
        new SettingItem("myopiaEnabled", "Enabled", "Myopia"),
        new SettingItem("myopiaDistance", "Range", "Myopia"),
        new SettingItem("myopiaMode", "Mode", "Myopia"),
        new SettingItem("openSkinMenuKey", "Open Skin Menu Key", "Keybinds"),
        new SettingItem("myopiaIncreaseKey", "Increase Range Key", "Keybinds"),
        new SettingItem("myopiaDecreaseKey", "Decrease Range Key", "Keybinds"),
        new SettingItem("showConnectionToast", "Connection Toast", "Toasts"),
        new SettingItem("showUploadToast", "Upload Toast", "Toasts"),
        new SettingItem("showInfoToast", "Info Toast", "Toasts"),
        new SettingItem("showErrorToast", "Error Toast", "Toasts"),
        new SettingItem("debugTestServer", "Test Server", "Debug"),
        new SettingItem("debugForceMyopia", "Force Myopia", "Debug")
    );

    public SettingsScreen() {
        super(Text.literal("CatSkinC Settings"));
    }

    @Override
    protected void init() {
        float f = 1.0F;
        int n4 = scaled(12, f);
        int n5 = scaled(10, f);
        n4 = Math.min(n4, Math.max(6, this.width / 24));
        n5 = Math.min(n5, Math.max(6, this.width / 36));
        int n6 = scaled(48, f);
        int n8 = n4 + n6 + scaled(4, f);
        int n9 = this.height - n8 - n4;
        if (n9 < scaled(200, f)) {
            n9 = scaled(200, f);
            n8 = Math.max(n4, this.height - n4 - n9);
        }

        int n10 = Math.max(240, this.width - n4 * 2);
        int n11 = Math.max(180, n10 - n5 * 2);
        int n12 = Math.max(84, scaled(96, Math.min(f, 1.0f)));
        int n13 = Math.max(130, scaled(160, Math.min(f, 1.0f)));
        int n14 = clamp(Math.round((float)n11 * 0.3f), n12, 250);
        int n15 = n11 - n14 * 2;
        if (n15 < n13) {
            int n3 = n13 - n15;
            n14 = Math.max(n12, n14 - (n3 + 1) / 2);
            n15 = n11 - n14 * 2;
        }
        if (n15 < 140) {
            n15 = Math.max(140, n11 / 2);
            n14 = Math.max(64, (n11 - n15) / 2);
        }
        int n3 = n14 + n5 + n15 + n5 + n14;

        this.panelX = n4 + Math.max(0, (n10 - n3) / 2);
        this.panelY = n8;
        this.panelW = n3;
        this.panelH = n9;
        this.leftW = n14;

        int searchBoxW = this.panelW - 30;
        this.searchBox = new TextFieldWidget(this.textRenderer, this.panelX + 15, this.panelY + 26, searchBoxW, 16, Text.literal("Search..."));
        this.searchBox.setMaxLength(32);
        this.searchBox.setText(this.searchQuery);
        this.searchBox.setChangedListener(value -> {
            this.searchQuery = value;
            this.settingsScroll = 0;
        });
        this.addDrawableChild(this.searchBox);

        this.ipTextField = new TextFieldWidget(this.textRenderer, this.panelX + 120, this.panelY + 52, this.panelW - 220, 16, Text.literal("CatSkinCloud IP"));
        this.ipTextField.setMaxLength(128);
        this.ipTextField.setText(ModConfig.get().getCatskinCloudIp());
        this.ipTextField.setChangedListener(value -> {
            ModConfig.get().setCatskinCloudIp(value);
            ModConfig.save();
        });
        this.addDrawableChild(this.ipTextField);

        this.requestSigningKeyTextField = new TextFieldWidget(this.textRenderer, this.panelX + 120, this.panelY + 52, this.panelW - 220, 16, Text.literal("Request Signing Key"));
        this.requestSigningKeyTextField.setMaxLength(256);
        this.requestSigningKeyTextField.setText(ModConfig.get().getRequestSigningKey() != null ? ModConfig.get().getRequestSigningKey() : "");
        this.requestSigningKeyTextField.setChangedListener(value -> {
            ModConfig.get().setRequestSigningKey(value.isEmpty() ? null : value);
            ModConfig.save();
        });
        this.addDrawableChild(this.requestSigningKeyTextField);
    }

    private int reloadButtonX;
    private int reloadButtonY;
    private int reloadButtonW = 40;
    private int reloadButtonH = 16;
    private boolean hasRequestSigningKeySetting = false;

    @Override
    public void tick() {
        if (this.searchBox != null) {
            this.searchBox.tick();
        }
        if (this.ipTextField != null) {
            this.ipTextField.tick();
        }
        super.tick();
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        this.renderBackground(drawContext);
        GuiBackdrop.drawVignette(drawContext, this.width, this.height);
        
        // Draw top tab bar
        this.renderTabs(drawContext, mouseX, mouseY, false);

        // Draw title
        int titleY = 10;
        int subtitleY = 20;
        if (this.panelY < 40) {
            subtitleY = -999;
            titleY = 8;
        }

        float titleScale = 0.8f;
        int maxTitleW = Math.round((float)Math.max(100, (this.width / 2 - 85) - this.panelX - 10) / titleScale);
        String titleStr = this.ellipsis(this.title.getString(), maxTitleW);
        drawContext.getMatrices().push();
        drawContext.getMatrices().scale(titleScale, titleScale, 1.0f);
        int scaledTitleX = Math.round((float)this.panelX / titleScale);
        int scaledTitleY = Math.round((float)titleY / titleScale);
        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(titleStr), scaledTitleX, scaledTitleY, -1);
        drawContext.getMatrices().pop();

        if (subtitleY != -999) {
            float descScale = 0.7f;
            int maxDescW = Math.round((float)Math.max(100, (this.width / 2 - 85) - this.panelX - 10) / descScale);
            String subtitleStr = this.ellipsis("Configure settings", maxDescW);
            drawContext.getMatrices().push();
            drawContext.getMatrices().scale(descScale, descScale, 1.0f);
            int scaledDescX = Math.round((float)this.panelX / descScale);
            int scaledDescY = Math.round((float)subtitleY / descScale);
            drawContext.drawTextWithShadow(this.textRenderer, Text.literal(subtitleStr), scaledDescX, scaledDescY, -3552823);
            drawContext.getMatrices().pop();
        }

        // Draw main panel
        drawContext.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, -1860494565);
        drawContext.fill(this.panelX + 1, this.panelY + 1, this.panelX + this.panelW - 1, this.panelY + 20, 0x21202020);
        drawContext.drawTextWithShadow(this.textRenderer, Text.literal("Client Settings"), this.panelX + 10, this.panelY + 6, -1381654);

        int rightOffset = this.panelX + this.panelW - 15;
        int contentTop = this.panelY + 48;
        int contentBottom = this.panelY + this.panelH - 10;
        int contentHeight = contentBottom - contentTop;
        this.settingsScroll = Math.min(this.settingsScroll, maxSettingsScroll(contentHeight));
        int y = contentTop - this.settingsScroll;
        boolean hasIpSetting = false;

        // Render categories & items
        String[] categories = categories();
        drawContext.enableScissor(this.panelX + 8, contentTop, this.panelX + this.panelW - 8, contentBottom);
        for (String category : categories) {
            // Check if there are matching settings in this category
            List<SettingItem> categorySettings = new ArrayList<>();
            for (SettingItem item : settings) {
                if (item.category.equals(category)) {
                    if (searchQuery.isEmpty() || item.label.toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT))) {
                        categorySettings.add(item);
                    }
                }
            }

            if (categorySettings.isEmpty()) {
                continue;
            }

            boolean expanded = isCategoryExpanded(category);
            boolean forceExpand = !searchQuery.isEmpty();
            boolean showArrowDown = expanded || forceExpand;

            // Draw Category Header
            if (y > contentTop - this.settingsScroll) {
                drawContext.fill(this.panelX + 15, y - 3, this.panelX + this.panelW - 20, y - 2, 0x55444444);
            }
            drawContext.drawTextWithShadow(this.textRenderer, Text.literal((showArrowDown ? "v " : "^ ") + category), this.panelX + 15, y + 4,
                    category.equals("Debug") ? 0xFFFF5555 : -1);
            y += 18;

            if (showArrowDown) {
                for (SettingItem item : categorySettings) {
                    if (item.key.equals("catskinCloudIp")) {
                        hasIpSetting = true;
                        this.ipTextField.setX(this.panelX + 120);
                        this.ipTextField.setY(y);
                        this.ipTextField.setWidth(this.panelW - 220);
                        this.ipTextField.visible = isContentRowVisible(y, contentTop, contentBottom);

                        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(item.label), this.panelX + 25, y + 4, -1);
                        drawRowButton(drawContext, mouseX, mouseY, rightOffset - 40, y, 40, 16, "Reset");

                        // Reload button next to IP field - same style as Reset button
                        this.reloadButtonX = rightOffset - 85;
                        this.reloadButtonY = y;
                        this.reloadButtonW = 40;
                        this.reloadButtonH = 16;
                        drawRowButton(drawContext, mouseX, mouseY, this.reloadButtonX, y, this.reloadButtonW, this.reloadButtonH, "↻");
                    } else if (item.key.equals("connectionMode")) {
                        // Connection Mode dropdown
                        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(item.label), this.panelX + 25, y + 4, -1);
                        ModConfig.ConnectionMode mode = ModConfig.get().getConnectionMode();
                        String[] modeLabels = {"Auto", "WebSocket", "SSE (REST+SSE)"};
                        int modeIndex = mode.ordinal();
                        String currentLabel = modeLabels[modeIndex];

                        int dropdownX = rightOffset - 145;
                        int dropdownW = 100;
                        int dropdownH = 16;
                        boolean hover = mouseX >= dropdownX && mouseX < dropdownX + dropdownW && mouseY >= y && mouseY < y + dropdownH;
                        int bg = hover ? 0x55444444 : 0x3D232323;
                        int border = hover ? 1922734746 : 1670023818;
                        drawContext.fill(dropdownX, y, dropdownX + dropdownW, y + 16, bg);
                        drawContext.fill(dropdownX, y, dropdownX + dropdownW, y + 1, border);
                        drawContext.fill(dropdownX, y + 15, dropdownX + dropdownW, y + 16, border);
                        drawContext.fill(dropdownX, y + 1, dropdownX + 1, y + 15, border);
                        drawContext.fill(dropdownX + dropdownW - 1, y + 1, dropdownX + dropdownW, y + 15, border);
                        int textW = this.textRenderer.getWidth(currentLabel);
                        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(currentLabel), dropdownX + (dropdownW - textW) / 2, y + 4, -1);
                        // Draw dropdown arrow
                        drawContext.drawTextWithShadow(this.textRenderer, Text.literal("▼"), dropdownX + dropdownW - 16, y + 4, -1);
                    } else if (item.key.equals("requestSigningKey")) {
                        hasRequestSigningKeySetting = true;
                        this.requestSigningKeyTextField.setX(this.panelX + 120);
                        this.requestSigningKeyTextField.setY(y);
                        this.requestSigningKeyTextField.setWidth(this.panelW - 220);
                        this.requestSigningKeyTextField.visible = isContentRowVisible(y, contentTop, contentBottom);

                        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(item.label), this.panelX + 25, y + 4, -1);
                        drawRowButton(drawContext, mouseX, mouseY, rightOffset - 40, y, 40, 16, "Reset");
                    } else if (item.key.equals("myopiaEnabled")) {
                        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(item.label), this.panelX + 25, y + 4, -1);
                        drawToggleSwitch(drawContext, mouseX, mouseY, rightOffset - 90, y,
                                ModConfig.get().isMyopiaEnabled());
                        drawRowButton(drawContext, mouseX, mouseY, rightOffset - 40, y, 40, 16, "Reset");
                    } else if (item.key.equals("myopiaDistance")) {
                        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(item.label), this.panelX + 25, y + 4, -1);
                        String range = "< " + ModConfig.get().getMyopiaDistance() + " blocks >";
                        drawRowButton(drawContext, mouseX, mouseY, rightOffset - 145, y, 100, 16, range);
                        drawRowButton(drawContext, mouseX, mouseY, rightOffset - 40, y, 40, 16, "Reset");
                    } else if (item.key.equals("myopiaMode")) {
                        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(item.label), this.panelX + 25, y + 4, -1);
                        String mode = ModConfig.get().getMyopiaMode() == ModConfig.MyopiaMode.PANICKED
                                ? "Panicked" : "Normal";
                        drawRowButton(drawContext, mouseX, mouseY, rightOffset - 145, y, 100, 16, mode);
                        drawRowButton(drawContext, mouseX, mouseY, rightOffset - 40, y, 40, 16, "Reset");
                    } else if (isKeyBindingSetting(item.key)) {
                        KeyBinding keyBinding = keyBindingFor(item.key);
                        String boundKey = keyBinding == null ? "?" : keyBinding.getBoundKeyLocalizedText().getString();
                        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(item.label), this.panelX + 25, y + 4, -1);
                        drawRowButton(drawContext, mouseX, mouseY, rightOffset - 145, y, 100, 16, boundKey);
                        drawRowButton(drawContext, mouseX, mouseY, rightOffset - 40, y, 40, 16, "Edit");
                    } else if (item.key.equals("debugTestServer")) {
                        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(item.label), this.panelX + 25, y + 4, 0xFFFF5555);
                        drawToggleSwitch(drawContext, mouseX, mouseY, rightOffset - 90, y,
                                ModConfig.get().isDebugTestServerEnabled());
                        drawRowButton(drawContext, mouseX, mouseY, rightOffset - 40, y, 40, 16, "Test");
                    } else if (item.key.equals("debugForceMyopia")) {
                        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(item.label), this.panelX + 25, y + 4, 0xFFFF5555);
                        drawToggleSwitch(drawContext, mouseX, mouseY, rightOffset - 90, y,
                                ModConfig.get().isDebugForceMyopia());
                        drawRowButton(drawContext, mouseX, mouseY, rightOffset - 40, y, 40, 16, "Test");
                    } else {
                        boolean val = getSettingValue(item.key);
                        drawToggleSettingRow(drawContext, mouseX, mouseY, y, item.label,
                                val ? "ON" : "OFF", this.panelX + 25, rightOffset,
                                () -> toggleSetting(item.key),
                                () -> resetSetting(item.key));
                    }
                    y += 22;
                }
            }
        }
        drawContext.disableScissor();
        drawSettingsScrollbar(drawContext, contentTop, contentBottom, contentHeight);

        if (!hasIpSetting) {
            this.ipTextField.visible = false;
        }

        if (!hasRequestSigningKeySetting) {
            this.requestSigningKeyTextField.visible = false;
        }

        // Render widgets (searchBox, ipTextField)
        super.render(drawContext, mouseX, mouseY, delta);
        if (searchQuery.isEmpty() && !searchBox.isFocused()) {
            drawContext.drawTextWithShadow(textRenderer, Text.literal("Search settings..."), searchBox.getX() + 5, searchBox.getY() + 4, 0xFF777777);
        }
    }

    private void drawToggleSettingRow(DrawContext drawContext, int mouseX, int mouseY, int y, String label, String value, int labelX, int rightOffset, 
                                      Runnable onToggle, Runnable onReset) {
        // Label
        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(label), labelX, y + 4, -1);

        // Buttons positioning
        int resetX = rightOffset - 40;
        int toggleX = rightOffset - 145;
        int btnH = 16;

        // Toggle button
        drawRowButton(drawContext, mouseX, mouseY, toggleX, y, 100, btnH, value);

        // Reset button
        drawRowButton(drawContext, mouseX, mouseY, resetX, y, 40, btnH, "Reset");
    }

    /** Draws a compact slider-style switch used by the Myopia enable setting. */
    private void drawToggleSwitch(DrawContext context, int mouseX, int mouseY, int x, int y, boolean on) {
        int width = 40;
        int height = 16;
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int track = on ? (hovered ? 0xFF48A860 : 0xFF357C47) : (hovered ? 0xFF666666 : 0xFF454545);
        context.fill(x, y + 3, x + width, y + 13, track);
        context.fill(x + 1, y + 2, x + width - 1, y + 14, track);
        context.fill(x + 2, y + 1, x + width - 2, y + 15, track);
        int knobX = on ? x + 25 : x + 3;
        int knob = hovered ? 0xFFFFFFFF : 0xFFE0E0E0;
        context.fill(knobX, y + 3, knobX + 12, y + 13, knob);
        context.fill(knobX + 1, y + 2, knobX + 11, y + 14, knob);
        context.fill(knobX + 2, y + 1, knobX + 10, y + 15, knob);
        context.drawTextWithShadow(textRenderer, Text.literal(on ? "ON" : "OFF"), x - 22, y + 4, on ? 0xFF80E890 : 0xFFAAAAAA);
    }

    private void drawRowButton(DrawContext drawContext, int mouseX, int mouseY, int x, int y, int w, int h, String text) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int bg = hover ? 0x55444444 : 0x3D232323;
        int border = hover ? 1922734746 : 1670023818;
        drawContext.fill(x, y, x + w, y + h, bg);
        // Draw border using manual border drawing
        drawContext.fill(x, y, x + w, y + 1, border);
        drawContext.fill(x, y + h - 1, x + w, y + h, border);
        drawContext.fill(x, y + 1, x + 1, y + h - 1, border);
        drawContext.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        
        int textW = this.textRenderer.getWidth(text);
        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(text), x + (w - textW) / 2, y + 4, -1);
    }

    private boolean isContentRowVisible(int rowY, int contentTop, int contentBottom) {
        return rowY >= contentTop && rowY + 16 <= contentBottom;
    }

    private void drawSettingsScrollbar(DrawContext context, int contentTop, int contentBottom, int viewportHeight) {
        int totalHeight = settingsContentHeight();
        if (totalHeight <= viewportHeight) {
            return;
        }
        int trackX = this.panelX + this.panelW - 12;
        int trackHeight = contentBottom - contentTop;
        int thumbHeight = Math.max(14, trackHeight * viewportHeight / totalHeight);
        int travel = trackHeight - thumbHeight;
        int maxScroll = maxSettingsScroll(viewportHeight);
        int thumbY = contentTop + (maxScroll == 0 ? 0 : this.settingsScroll * travel / maxScroll);
        context.fill(trackX, contentTop, trackX + 3, contentBottom, 0x55303030);
        context.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, 0xFF8A8A8A);
    }

    private void renderTabs(DrawContext drawContext, int mouseX, int mouseY, boolean wardrobeActive) {
        int centerX = this.width / 2;
        int tabY = 6;
        int tabW = 80;
        int tabH = 18;
        int x1 = centerX - 85;
        int x2 = centerX + 5;

        // Tab 1: My Skins
        boolean hover1 = mouseX >= x1 && mouseX < x1 + tabW && mouseY >= tabY && mouseY < tabY + tabH;
        int bg1 = wardrobeActive ? -1554030753 : (hover1 ? 0x55444444 : 0x3D232323);
        drawContext.fill(x1, tabY, x1 + tabW, tabY + tabH, bg1);
        
        String text1 = "My Skins";
        int textW1 = this.textRenderer.getWidth(text1);
        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(text1), x1 + (tabW - textW1) / 2, tabY + (tabH - 8) / 2, -1);

        // Tab 2: Settings
        boolean hover2 = mouseX >= x2 && mouseX < x2 + tabW && mouseY >= tabY && mouseY < tabY + tabH;
        int bg2 = !wardrobeActive ? -1554030753 : (hover2 ? 0x55444444 : 0x3D232323);
        drawContext.fill(x2, tabY, x2 + tabW, tabY + tabH, bg2);
        
        String text2 = "Settings";
        int textW2 = this.textRenderer.getWidth(text2);
        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(text2), x2 + (tabW - textW2) / 2, tabY + (tabH - 8) / 2, -1);

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int centerX = this.width / 2;
            int rightOffset = this.panelX + this.panelW - 15;

            // 1. Tab click detection
            if (mouseX >= centerX - 85 && mouseX < centerX - 5 && mouseY >= 6 && mouseY < 24) {
                ModSounds.playClick();
                MinecraftClient.getInstance().setScreen(new SkinUploadScreen());
                return true;
            }
            // 2. Settings categories click detection
            int y = this.panelY + 48 - this.settingsScroll;
            String[] categories = categories();
            for (String category : categories) {
                List<SettingItem> categorySettings = new ArrayList<>();
                for (SettingItem item : settings) {
                    if (item.category.equals(category)) {
                        if (searchQuery.isEmpty() || item.label.toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT))) {
                            categorySettings.add(item);
                        }
                    }
                }

                if (categorySettings.isEmpty()) {
                    continue;
                }

                boolean expanded = isCategoryExpanded(category);
                boolean forceExpand = !searchQuery.isEmpty();
                boolean showArrowDown = expanded || forceExpand;

                // Category header click bounds
                if (mouseX >= this.panelX + 15 && mouseX < this.panelX + this.panelW - 15 && mouseY >= y && mouseY < y + 14) {
                    ModSounds.playClick();
                    if (!forceExpand) {
                        toggleCategory(category);
                    }
                    return true;
                }
                y += 18;

                if (showArrowDown) {
                    ModConfig config = ModConfig.get();
                    for (SettingItem item : categorySettings) {
                        if (item.key.equals("catskinCloudIp")) {
                            int resetX = rightOffset - 40;
                            if (mouseX >= resetX && mouseX < resetX + 40 && mouseY >= y && mouseY < y + 16) {
                                ModSounds.playClick();
                                config.setCatskinCloudIp("storage-api.catskin.space");
                                this.ipTextField.setText("storage-api.catskin.space");
                                saveAndApply();
                                return true;
                            }
                            // Reload button click
                            if (mouseX >= this.reloadButtonX && mouseX < this.reloadButtonX + this.reloadButtonW && mouseY >= y && mouseY < y + 16) {
                                ModSounds.playClick();
                                // Show reconnecting toast immediately (uses ConnectionToast which plays sound on result)
                                Toasts.ConnectionToast reconnectToast = Toasts.connection(
                                    Text.translatable("toast.catskinc.event.title"),
                                    Text.translatable("toast.catskinc.reconnecting"));
                                // Show result toast after reconnect attempt
                                ServerApiClient.reconnect(event -> {})
                                    .whenComplete((connected, ex) -> {
                                        MinecraftClient mc = MinecraftClient.getInstance();
                                        if (mc != null && reconnectToast != null) {
                                            mc.execute(() -> {
                                                if (Boolean.TRUE.equals(connected)) {
                                                    reconnectToast.complete(true, Text.translatable("toast.catskinc.connected").getString());
                                                } else {
                                                    reconnectToast.complete(false, Text.translatable("toast.catskinc.failed").getString());
                                                }
                                            });
                                        }
                                    });
                                return true;
                            }
                        } else if (item.key.equals("connectionMode")) {
                            int dropdownX = rightOffset - 145;
                            int dropdownW = 100;
                            int dropdownH = 16;
                            if (mouseX >= dropdownX && mouseX < dropdownX + dropdownW && mouseY >= y && mouseY < y + dropdownH) {
                                ModSounds.playClick();
                                // Cycle through connection modes
                                ModConfig.ConnectionMode currentMode = config.getConnectionMode();
                                ModConfig.ConnectionMode nextMode;
                                switch (currentMode) {
                                    case AUTO -> nextMode = ModConfig.ConnectionMode.WEBSOCKET;
                                    case WEBSOCKET -> nextMode = ModConfig.ConnectionMode.SSE;
                                    case SSE -> nextMode = ModConfig.ConnectionMode.AUTO;
                                    default -> nextMode = ModConfig.ConnectionMode.AUTO;
                                }
                                config.setConnectionMode(nextMode);
                                ServerApiClient.setConnectionMode(nextMode);
                                saveAndApply();
                                return true;
                            }
                        } else if (item.key.equals("requestSigningKey")) {
                            int resetX = rightOffset - 40;
                            if (mouseX >= resetX && mouseX < resetX + 40 && mouseY >= y && mouseY < y + 16) {
                                ModSounds.playClick();
                                config.setRequestSigningKey(null);
                                this.requestSigningKeyTextField.setText("");
                                ModConfig.save();
                                return true;
                            }
                        } else if (item.key.equals("myopiaEnabled")) {
                            int toggleX = rightOffset - 90;
                            int resetX = rightOffset - 40;
                            if (mouseX >= toggleX && mouseX < toggleX + 40 && mouseY >= y && mouseY < y + 16) {
                                config.setMyopiaEnabled(!config.isMyopiaEnabled());
                                ModSounds.playClick();
                                saveAndApply();
                                return true;
                            }
                            if (mouseX >= resetX && mouseX < resetX + 40 && mouseY >= y && mouseY < y + 16) {
                                config.setMyopiaEnabled(false);
                                ModSounds.playClick();
                                saveAndApply();
                                return true;
                            }
                        } else if (item.key.equals("myopiaDistance")) {
                            int rangeX = rightOffset - 145;
                            int resetX = rightOffset - 40;
                            if (mouseX >= rangeX && mouseX < rangeX + 100 && mouseY >= y && mouseY < y + 16) {
                                int delta = mouseX < rangeX + 50
                                        ? -ModConfig.MYOPIA_DISTANCE_STEP
                                        : ModConfig.MYOPIA_DISTANCE_STEP;
                                config.setMyopiaDistance(config.getMyopiaDistance() + delta);
                                ModSounds.playClick();
                                saveAndApply();
                                return true;
                            }
                            if (mouseX >= resetX && mouseX < resetX + 40 && mouseY >= y && mouseY < y + 16) {
                                config.setMyopiaDistance(ModConfig.MYOPIA_DISTANCE_DEFAULT);
                                ModSounds.playClick();
                                saveAndApply();
                                return true;
                            }
                        } else if (item.key.equals("myopiaMode")) {
                            int modeX = rightOffset - 145;
                            int resetX = rightOffset - 40;
                            if (mouseX >= modeX && mouseX < modeX + 100 && mouseY >= y && mouseY < y + 16) {
                                config.setMyopiaMode(config.getMyopiaMode() == ModConfig.MyopiaMode.NORMAL
                                        ? ModConfig.MyopiaMode.PANICKED : ModConfig.MyopiaMode.NORMAL);
                                ModSounds.playClick();
                                saveAndApply();
                                return true;
                            }
                            if (mouseX >= resetX && mouseX < resetX + 40 && mouseY >= y && mouseY < y + 16) {
                                config.setMyopiaMode(ModConfig.MyopiaMode.NORMAL);
                                ModSounds.playClick();
                                saveAndApply();
                                return true;
                            }
                        } else if (isKeyBindingSetting(item.key)) {
                            int keyX = rightOffset - 145;
                            int editX = rightOffset - 40;
                            if ((mouseX >= keyX && mouseX < keyX + 100 || mouseX >= editX && mouseX < editX + 40)
                                    && mouseY >= y && mouseY < y + 16) {
                                ModSounds.playClick();
                                openMinecraftKeybinds();
                                return true;
                            }
                        } else if (item.key.equals("debugTestServer")) {
                            int toggleX = rightOffset - 90;
                            int testX = rightOffset - 40;
                            if ((mouseX >= toggleX && mouseX < toggleX + 40 || mouseX >= testX && mouseX < testX + 40)
                                    && mouseY >= y && mouseY < y + 16) {
                                config.setDebugTestServerEnabled(!config.isDebugTestServerEnabled());
                                ModSounds.playClick();
                                saveAndApply();
                                MyopiaDebugPreview.setEnabled(config.isDebugTestServerEnabled());
                                ServerApiClient.reconnect(event -> {});
                                return true;
                            }
                        } else if (item.key.equals("debugForceMyopia")) {
                            int toggleX = rightOffset - 90;
                            int testX = rightOffset - 40;
                            if ((mouseX >= toggleX && mouseX < toggleX + 40 || mouseX >= testX && mouseX < testX + 40)
                                    && mouseY >= y && mouseY < y + 16) {
                                config.setDebugForceMyopia(!config.isDebugForceMyopia());
                                ModSounds.playClick();
                                saveAndApply();
                                return true;
                            }
                        } else {
                            int resetX = rightOffset - 40;
                            int toggleX = rightOffset - 145;
                            if (mouseX >= toggleX && mouseX < toggleX + 100 && mouseY >= y && mouseY < y + 16) {
                                ModSounds.playClick();
                                toggleSetting(item.key);
                                saveAndApply();
                                return true;
                            }
                            if (mouseX >= resetX && mouseX < resetX + 40 && mouseY >= y && mouseY < y + 16) {
                                ModSounds.playClick();
                                resetSetting(item.key);
                                saveAndApply();
                                return true;
                            }
                        }
                        y += 22;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int contentTop = this.panelY + 48;
        int contentBottom = this.panelY + this.panelH - 10;
        if (mouseX < this.panelX || mouseX >= this.panelX + this.panelW
                || mouseY < contentTop || mouseY >= contentBottom) {
            return super.mouseScrolled(mouseX, mouseY, amount);
        }
        int maxScroll = maxSettingsScroll(contentBottom - contentTop);
        if (maxScroll == 0) {
            return super.mouseScrolled(mouseX, mouseY, amount);
        }
        this.settingsScroll = clamp(this.settingsScroll - (int) Math.signum(amount) * 18, 0, maxScroll);
        return true;
    }

    private int maxSettingsScroll(int viewportHeight) {
        return Math.max(0, settingsContentHeight() - viewportHeight);
    }

    private int settingsContentHeight() {
        int height = 0;
        String[] categories = categories();
        for (String category : categories) {
            int count = 0;
            for (SettingItem item : settings) {
                if (item.category.equals(category)
                        && (searchQuery.isEmpty() || item.label.toLowerCase(Locale.ROOT)
                        .contains(searchQuery.toLowerCase(Locale.ROOT)))) {
                    count++;
                }
            }
            if (count == 0) {
                continue;
            }
            height += 18;
            if (isCategoryExpanded(category) || !searchQuery.isEmpty()) {
                height += count * 22;
            }
        }
        return height;
    }

    private boolean getSettingValue(String key) {
        ModConfig config = ModConfig.get();
        switch (key) {
            case "showConnectionToast": return config.isShowConnectionToast();
            case "showUploadToast": return config.isShowUploadToast();
            case "showInfoToast": return config.isShowInfoToast();
            case "showErrorToast": return config.isShowErrorToast();
            case "myopiaEnabled": return config.isMyopiaEnabled();
            default: return false;
        }
    }

    private boolean isCategoryExpanded(String category) {
        return switch (category) {
            case "General" -> generalExpanded;
            case "Myopia" -> myopiaExpanded;
            case "Keybinds" -> keybindsExpanded;
            case "Debug" -> debugExpanded;
            default -> toastsExpanded;
        };
    }

    private void toggleCategory(String category) {
        switch (category) {
            case "General" -> generalExpanded = !generalExpanded;
            case "Myopia" -> myopiaExpanded = !myopiaExpanded;
            case "Keybinds" -> keybindsExpanded = !keybindsExpanded;
            case "Debug" -> debugExpanded = !debugExpanded;
            case "Toasts" -> toastsExpanded = !toastsExpanded;
            default -> { }
        }
    }

    /** Opens Minecraft's authoritative Controls screen; its saved bindings are read directly on return. */
    private void openMinecraftKeybinds() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(new KeybindsScreen(this, client.options));
        }
    }

    private static boolean isKeyBindingSetting(String key) {
        return key.equals("openSkinMenuKey") || key.equals("myopiaIncreaseKey") || key.equals("myopiaDecreaseKey");
    }

    private static KeyBinding keyBindingFor(String key) {
        return switch (key) {
            case "openSkinMenuKey" -> CatskincClient.getOpenUiKey();
            case "myopiaIncreaseKey" -> CatskincClient.getMyopiaIncreaseKey();
            case "myopiaDecreaseKey" -> CatskincClient.getMyopiaDecreaseKey();
            default -> null;
        };
    }

    private String[] categories() {
        return CatskincClient.isDebugPreviewEnabled()
                ? new String[]{"General", "Myopia", "Keybinds", "Toasts", "Debug"}
                : new String[]{"General", "Myopia", "Keybinds", "Toasts"};
    }

    private void toggleSetting(String key) {
        ModConfig config = ModConfig.get();
        switch (key) {
            case "showConnectionToast": config.setShowConnectionToast(!config.isShowConnectionToast()); break;
            case "showUploadToast": config.setShowUploadToast(!config.isShowUploadToast()); break;
            case "showInfoToast": config.setShowInfoToast(!config.isShowInfoToast()); break;
            case "showErrorToast": config.setShowErrorToast(!config.isShowErrorToast()); break;
            case "myopiaEnabled": config.setMyopiaEnabled(!config.isMyopiaEnabled()); break;
        }
    }

    private void resetSetting(String key) {
        ModConfig config = ModConfig.get();
        switch (key) {
            case "showConnectionToast": config.setShowConnectionToast(true); break;
            case "showUploadToast": config.setShowUploadToast(true); break;
            case "showInfoToast": config.setShowInfoToast(true); break;
            case "showErrorToast": config.setShowErrorToast(true); break;
            case "myopiaEnabled": config.setMyopiaEnabled(false); break;
        }
    }

    private void saveAndApply() {
        ModConfig.save();
        CatskincClient.applyConfig();
        MyopiaRenderHook.onSettingsApplied();
    }

    private String ellipsis(String string, int n) {
        int n2;
        if (this.textRenderer.getWidth(string) <= n) {
            return string;
        }
        String string2 = "...";
        int n3 = this.textRenderer.getWidth(string2);
        for (n2 = string.length(); n2 > 0 && this.textRenderer.getWidth(string.substring(0, n2)) + n3 > n; --n2) {
        }
        return n2 <= 0 ? string2 : string.substring(0, n2) + string2;
    }

    private static int scaled(int n, float f) {
        return Math.max(1, Math.round((float)n * f));
    }

    private static int clamp(int n, int n2, int n3) {
        return Math.max(n2, Math.min(n3, n));
    }
}
