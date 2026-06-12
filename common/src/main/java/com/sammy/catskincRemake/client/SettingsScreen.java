package com.sammy.catskincRemake.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
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
    private String searchQuery = "";
    
    private boolean generalExpanded = true;
    private boolean toastsExpanded = true;

    private static final class SettingItem {
        final String key;
        final String label;
        final String category; // "General" or "Toasts"
        
        SettingItem(String key, String label, String category) {
            this.key = key;
            this.label = label;
            this.category = category;
        }
    }

    private final List<SettingItem> settings = List.of(
        new SettingItem("catskinCloudIp", "CatSkinCloud IP", "General"),
        new SettingItem("showConnectionToast", "Connection Toast", "Toasts"),
        new SettingItem("showUploadToast", "Upload Toast", "Toasts"),
        new SettingItem("showInfoToast", "Info Toast", "Toasts"),
        new SettingItem("showErrorToast", "Error Toast", "Toasts")
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
        this.searchBox.setChangedListener(value -> this.searchQuery = value);
        this.addDrawableChild(this.searchBox);

        this.ipTextField = new TextFieldWidget(this.textRenderer, this.panelX + 120, this.panelY + 52, this.panelW - 180, 16, Text.literal("CatSkinCloud IP"));
        this.ipTextField.setMaxLength(128);
        this.ipTextField.setText(ModConfig.get().getCatskinCloudIp());
        this.ipTextField.setChangedListener(value -> {
            ModConfig.get().setCatskinCloudIp(value);
            ModConfig.save();
        });
        this.addDrawableChild(this.ipTextField);
    }

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
        int y = this.panelY + 48;
        boolean hasIpSetting = false;

        // Render categories & items
        String[] categories = {"General", "Toasts"};
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

            boolean expanded = category.equals("General") ? generalExpanded : toastsExpanded;
            boolean forceExpand = !searchQuery.isEmpty();
            boolean showArrowDown = expanded || forceExpand;

            // Draw Category Header
            drawContext.drawTextWithShadow(this.textRenderer, Text.literal((showArrowDown ? "v " : "^ ") + category), this.panelX + 15, y + 4, -1);
            y += 18;

            if (showArrowDown) {
                for (SettingItem item : categorySettings) {
                    if (item.key.equals("catskinCloudIp")) {
                        hasIpSetting = true;
                        this.ipTextField.setX(this.panelX + 120);
                        this.ipTextField.setY(y);
                        this.ipTextField.setWidth(this.panelW - 180);
                        this.ipTextField.visible = true;

                        drawContext.drawTextWithShadow(this.textRenderer, Text.literal(item.label), this.panelX + 25, y + 4, -1);
                        drawRowButton(drawContext, mouseX, mouseY, rightOffset - 40, y, 40, 16, "Reset");
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

        if (!hasIpSetting) {
            this.ipTextField.visible = false;
        }

        // Render widgets (searchBox, ipTextField)
        super.render(drawContext, mouseX, mouseY, delta);
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
            int y = this.panelY + 48;
            String[] categories = {"General", "Toasts"};
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

                boolean expanded = category.equals("General") ? generalExpanded : toastsExpanded;
                boolean forceExpand = !searchQuery.isEmpty();
                boolean showArrowDown = expanded || forceExpand;

                // Category header click bounds
                if (mouseX >= this.panelX + 15 && mouseX < this.panelX + this.panelW - 15 && mouseY >= y && mouseY < y + 14) {
                    ModSounds.playClick();
                    if (!forceExpand) {
                        if (category.equals("General")) {
                            generalExpanded = !generalExpanded;
                        } else {
                            toastsExpanded = !toastsExpanded;
                        }
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

    private boolean getSettingValue(String key) {
        ModConfig config = ModConfig.get();
        switch (key) {
            case "showConnectionToast": return config.isShowConnectionToast();
            case "showUploadToast": return config.isShowUploadToast();
            case "showInfoToast": return config.isShowInfoToast();
            case "showErrorToast": return config.isShowErrorToast();
            default: return false;
        }
    }

    private void toggleSetting(String key) {
        ModConfig config = ModConfig.get();
        switch (key) {
            case "showConnectionToast": config.setShowConnectionToast(!config.isShowConnectionToast()); break;
            case "showUploadToast": config.setShowUploadToast(!config.isShowUploadToast()); break;
            case "showInfoToast": config.setShowInfoToast(!config.isShowInfoToast()); break;
            case "showErrorToast": config.setShowErrorToast(!config.isShowErrorToast()); break;
        }
    }

    private void resetSetting(String key) {
        ModConfig config = ModConfig.get();
        switch (key) {
            case "showConnectionToast": config.setShowConnectionToast(true); break;
            case "showUploadToast": config.setShowUploadToast(true); break;
            case "showInfoToast": config.setShowInfoToast(true); break;
            case "showErrorToast": config.setShowErrorToast(true); break;
        }
    }

    private void saveAndApply() {
        ModConfig.save();
        CatskincRemakeClient.applyConfig();
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
