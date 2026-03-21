/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.authlib.GameProfile
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.network.OtherClientPlayerEntity
 *  net.minecraft.client.texture.AbstractTexture
 *  net.minecraft.client.texture.NativeImage
 *  net.minecraft.client.texture.NativeImageBackedTexture
 *  net.minecraft.entity.EntityPose
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.text.MutableText
 *  net.minecraft.text.Text
 *  net.minecraft.util.Identifier
 *  org.lwjgl.util.tinyfd.TinyFileDialogs
 */
package com.sammy.catskincRemake.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.sammy.catskincRemake.client.ConfigManager;
import com.sammy.catskincRemake.client.Identifiers;
import com.sammy.catskincRemake.client.InventoryEntityRendererCompat;
import com.sammy.catskincRemake.client.ModLog;
import com.sammy.catskincRemake.client.ModSounds;
import com.sammy.catskincRemake.client.ServerApiClient;
import com.sammy.catskincRemake.client.SkinManagerClient;
import com.sammy.catskincRemake.client.SkinOverrideStore;
import com.sammy.catskincRemake.client.Toasts;
import com.sammy.catskincRemake.mixin.client.PlayerEntityAccessor;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class SkinUploadScreen
extends Screen {
    private static final UUID PREVIEW_SLIM_UUID = findPreviewProfileUuid(true, "catskinc-preview-slim");
    private static final UUID PREVIEW_WIDE_UUID = findPreviewProfileUuid(false, "catskinc-preview-wide");
    private static final int COLOR_BG_BASE = -871428337;
    private static final int COLOR_BG_TOP = 0x40222222;
    private static final int COLOR_BG_BOTTOM = 0x54141414;
    private static final int COLOR_PANEL = -1860494565;
    private static final int COLOR_PANEL_INNER = 1796543765;
    private static final int COLOR_SLOT = 0x59191919;
    private static final int COLOR_BORDER = 0x55969696;
    private static final int COLOR_DIVIDER = 979921000;
    private static final int COLOR_HOVER = 0x35525252;
    private static final int COLOR_SELECTED = 0x556A6A6A;
    private static final int COLOR_TEXT = -1;
    private static final int COLOR_TEXT_DIM = -3552823;
    private static final int COLOR_TEXT_MUTED = -5855578;
    private static final int COLOR_DANGER = -1517647222;
    private static final int COLOR_ACCENT = -1381654;
    private static final int COLOR_ACCENT_SOFT = 1922734746;
    private static final int COLOR_BTN = 0x3D232323;
    private static final int COLOR_BTN_HOVER = 0x55444444;
    private static final int COLOR_BTN_BORDER = 1670023818;
    private static final int COLOR_BTN_DISABLED = 0x23202020;
    private static final int COLOR_BTN_PRIMARY = -1554030753;
    private static final int COLOR_BTN_PRIMARY_HOVER = -1116244105;
    private static final int COLOR_BTN_PRIMARY_BORDER = -926497082;
    private static final int COLOR_BTN_ASSET = -266856424;
    private static final int COLOR_BTN_ASSET_HOVER = -13619152;
    private static final int COLOR_BTN_ASSET_BORDER = -986896;
    private static final int COLOR_BTN_ASSET_ACCENT = -4671304;
    private static final int COLOR_BTN_ASSET_ACCENT_HOVER = -1;
    private static final int COLOR_BTN_DANGER = -2042609600;
    private static final int COLOR_BTN_DANGER_HOVER = -1588045736;
    private static final int COLOR_BTN_DANGER_BORDER = -1097953650;
    private static final int COLOR_MODAL_OVERLAY = -436207616;
    private static final int COLOR_MODAL_PANEL = -15395563;
    private static final int COLOR_MODAL_HEADER = -14671840;
    private File selectedFile;
    private int selectedWidth;
    private int selectedHeight;
    private File selectedMouthOpenFile;
    private int selectedMouthOpenWidth;
    private int selectedMouthOpenHeight;
    private File selectedMouthCloseFile;
    private int selectedMouthCloseWidth;
    private int selectedMouthCloseHeight;
    private boolean slimChecked;
    private boolean slimInitialized;
    private ResourceLocation previewId;
    private DynamicTexture previewTexture;
    private RemotePlayer previewPlayer;
    private boolean previewSlim;
    private int leftX;
    private int leftY;
    private int leftW;
    private int leftH;
    private int centerX;
    private int centerY;
    private int centerW;
    private int centerH;
    private int rightX;
    private int rightY;
    private int rightW;
    private int rightH;
    private int dropX;
    private int dropY;
    private int dropW;
    private int dropH;
    private int infoX;
    private int infoY;
    private int infoW;
    private int infoH;
    private int historyX;
    private int historyY;
    private int historyW;
    private int historyH;
    private int historyRowH;
    private int historyScroll;
    private int btnBrowseX;
    private int btnBrowseY;
    private int btnBrowseW;
    private int btnBrowseH;
    private int btnMouthOpenX;
    private int btnMouthOpenY;
    private int btnMouthOpenW;
    private int btnMouthOpenH;
    private int btnMouthCloseX;
    private int btnMouthCloseY;
    private int btnMouthCloseW;
    private int btnMouthCloseH;
    private int btnSlimX;
    private int btnSlimY;
    private int btnSlimW;
    private int btnSlimH;
    private int btnUploadX;
    private int btnUploadY;
    private int btnUploadW;
    private int btnUploadH;
    private int btnClearX;
    private int btnClearY;
    private int btnClearW;
    private int btnClearH;
    private boolean clearDialogOpen;
    private int clearDialogX;
    private int clearDialogY;
    private int clearDialogW;
    private int clearDialogH;
    private int clearBtnAllX;
    private int clearBtnAllY;
    private int clearBtnAllW;
    private int clearBtnAllH;
    private int clearBtnSkinX;
    private int clearBtnSkinY;
    private int clearBtnSkinW;
    private int clearBtnSkinH;
    private int clearBtnMouthX;
    private int clearBtnMouthY;
    private int clearBtnMouthW;
    private int clearBtnMouthH;
    private int clearBtnCancelX;
    private int clearBtnCancelY;
    private int clearBtnCancelW;
    private int clearBtnCancelH;
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private final List<HistoryEntry> history = new ArrayList<HistoryEntry>();

    public SkinUploadScreen() {
        super((Component)Component.translatable((String)"screen.catskinc-remake.title"));
    }

    protected void init() {
        int n;
        int n2;
        int n3;
        float f = ConfigManager.get().uiScale;
        int n4 = SkinUploadScreen.scaled(12, f);
        int n5 = SkinUploadScreen.scaled(10, f);
        n4 = Math.min(n4, Math.max(6, this.width / 24));
        n5 = Math.min(n5, Math.max(6, this.width / 36));
        int n6 = SkinUploadScreen.scaled(36, f);
        int n7 = SkinUploadScreen.scaled(12, f);
        int n8 = n4 + n6 + SkinUploadScreen.scaled(4, f);
        int n9 = this.height - n8 - n4;
        if (n9 < SkinUploadScreen.scaled(200, f)) {
            n9 = SkinUploadScreen.scaled(200, f);
            n8 = Math.max(n4, this.height - n4 - n9);
        }
        int n10 = Math.max(240, this.width - n4 * 2);
        int n11 = Math.max(180, n10 - n5 * 2);
        int n12 = Math.max(84, SkinUploadScreen.scaled(96, Math.min(f, 1.0f)));
        int n13 = Math.max(130, SkinUploadScreen.scaled(160, Math.min(f, 1.0f)));
        int n14 = SkinUploadScreen.clamp(Math.round((float)n11 * 0.3f), n12, 250);
        int n15 = n11 - n14 * 2;
        if (n15 < n13) {
            n3 = n13 - n15;
            n14 = Math.max(n12, n14 - (n3 + 1) / 2);
            n15 = n11 - n14 * 2;
        }
        if (n15 < 140) {
            n15 = Math.max(140, n11 / 2);
            n14 = Math.max(64, (n11 - n15) / 2);
        }
        n3 = n14 + n5 + n15 + n5 + n14;
        this.leftX = n2 = n4 + Math.max(0, (n10 - n3) / 2);
        this.leftY = n8;
        this.leftW = n14;
        this.leftH = n9;
        this.centerX = this.leftX + this.leftW + n5;
        this.centerY = n8;
        this.centerW = n15;
        this.centerH = n9;
        this.rightX = this.centerX + this.centerW + n5;
        this.rightY = n8;
        this.rightW = n14;
        this.rightH = n9;
        int n16 = SkinUploadScreen.scaled(46, f);
        this.infoH = SkinUploadScreen.scaled(78, f);
        int n17 = this.centerH - SkinUploadScreen.scaled(86, f);
        if (n17 < this.infoH) {
            this.infoH = Math.max(n16, n17);
        }
        this.infoX = this.centerX;
        this.infoW = this.centerW;
        this.infoY = this.centerY + this.centerH - this.infoH;
        int n18 = n7;
        this.dropX = this.centerX + n18;
        this.dropY = this.centerY + SkinUploadScreen.scaled(30, f);
        this.dropW = this.centerW - n18 * 2;
        this.dropH = this.infoY - this.dropY - SkinUploadScreen.scaled(6, f);
        int n19 = SkinUploadScreen.scaled(64, Math.min(f, 1.0f));
        if (this.dropH < n19) {
            n = n19 - this.dropH;
            this.infoH = Math.max(n16, this.infoH - n);
            this.infoY = this.centerY + this.centerH - this.infoH;
            this.dropH = Math.max(36, this.infoY - this.dropY - SkinUploadScreen.scaled(6, f));
        }
        this.historyX = this.leftX + 1;
        this.historyY = this.leftY + SkinUploadScreen.scaled(30, f);
        this.historyW = this.leftW - 2;
        this.historyH = this.leftH - SkinUploadScreen.scaled(32, f);
        this.historyRowH = SkinUploadScreen.scaled(48, f);
        this.clearWidgets();
        this.loadHistory();
        this.historyScroll = SkinUploadScreen.clamp(this.historyScroll, 0, this.maxHistoryScroll());
        n = Math.min(n7, Math.max(8, this.rightW / 12));
        int n20 = this.rightW - n * 2;
        int n21 = SkinUploadScreen.scaled(22, f);
        int n22 = SkinUploadScreen.scaled(6, f);
        int n23 = SkinUploadScreen.scaled(20, f);
        int n24 = this.rightY + SkinUploadScreen.scaled(38, f);
        int n25 = SkinUploadScreen.scaled(8, f);
        int n26 = this.rightY + this.rightH - n25 - n24;
        int n27 = n21 * 6 + n22 * 3 + n23 * 2;
        if (n26 > 0 && n27 > n26) {
            float f2 = (float)n26 / (float)n27;
            n21 = Math.max(16, Math.round((float)n21 * f2));
            n22 = Math.max(2, Math.round((float)n22 * f2));
            n23 = Math.max(12, Math.round((float)n23 * f2));
        }
        this.btnBrowseX = this.rightX + n;
        this.btnBrowseY = n24;
        this.btnBrowseW = n20;
        this.btnBrowseH = n21;
        this.btnMouthOpenX = this.rightX + n;
        this.btnMouthOpenY = this.btnBrowseY + n21 + n22;
        this.btnMouthOpenW = n20;
        this.btnMouthOpenH = n21;
        this.btnMouthCloseX = this.rightX + n;
        this.btnMouthCloseY = this.btnMouthOpenY + n21 + n22;
        this.btnMouthCloseW = n20;
        this.btnMouthCloseH = n21;
        this.btnSlimX = this.rightX + n;
        this.btnSlimY = this.btnMouthCloseY + n21 + n23;
        this.btnSlimW = n20;
        this.btnSlimH = n21;
        this.btnUploadX = this.rightX + n;
        this.btnUploadY = this.btnSlimY + n21 + n23;
        this.btnUploadW = n20;
        this.btnUploadH = n21;
        this.btnClearX = this.rightX + n;
        this.btnClearY = this.btnUploadY + n21 + n22;
        this.btnClearW = n20;
        this.btnClearH = n21;
        this.layoutClearDialog(f, n4);
        this.initSlimState();
        this.rebuildPreviewPlayer();
    }

    public void resize(Minecraft minecraftClient, int n, int n2) {
        super.resize(minecraftClient, n, n2);
        this.init();
    }

    public void onClose() {
        this.disposePreview();
        this.disposeHistory();
        super.onClose();
    }

    public void render(GuiGraphics drawContext, int n, int n2, float f) {
        this.lastMouseX = n;
        this.lastMouseY = n2;
        this.drawBackdrop(drawContext);
        float f2 = ConfigManager.get().uiScale;
        String string = this.ellipsis(this.title.getString(), this.width - 20);
        drawContext.drawCenteredString(this.font, (Component)Component.literal((String)string), this.width / 2, SkinUploadScreen.scaled(10, f2), -1);
        String string2 = this.ellipsis("Simple workspace for skin + mouth setup", this.width - 20);
        drawContext.drawCenteredString(this.font, (Component)Component.literal((String)string2), this.width / 2, SkinUploadScreen.scaled(24, f2), -5855578);
        this.drawPanel(drawContext, this.leftX, this.leftY, this.leftW, this.leftH);
        this.drawPanel(drawContext, this.centerX, this.centerY, this.centerW, this.centerH);
        this.drawPanel(drawContext, this.rightX, this.rightY, this.rightW, this.rightH);
        drawContext.drawString(this.font, (Component)Component.literal((String)this.ellipsis("Library", this.leftW - 20)), this.leftX + 10, this.leftY + 9, -1381654);
        drawContext.drawString(this.font, (Component)Component.literal((String)this.ellipsis("Live Preview", this.centerW - 20)), this.centerX + 10, this.centerY + 9, -1381654);
        drawContext.drawString(this.font, (Component)Component.literal((String)this.ellipsis("Controls", this.rightW - 20)), this.rightX + 10, this.rightY + 9, -1381654);
        this.renderPreviewArea(drawContext, n, n2);
        this.renderInfoBar(drawContext);
        this.renderSkinSlots(drawContext, n, n2);
        this.renderRightSidebar(drawContext, n, n2);
        if (this.clearDialogOpen) {
            this.renderClearDialog(drawContext, n, n2);
        }
    }

    public void onFilesDrop(List<Path> list) {
        ArrayList<File> arrayList = new ArrayList<File>();
        for (Path path : list) {
            File file = path.toFile();
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) continue;
            arrayList.add(file);
        }
        if (arrayList.isEmpty()) {
            SkinUploadScreen.toastError("toast.error.drag_not_png", new Object[0]);
            ModSounds.play(ModSounds.UI_ERROR);
            return;
        }
        if (SkinUploadDropPlan.resolve(this.lastMouseX, this.lastMouseY, this.leftX, this.leftY, this.leftW, this.leftH, arrayList.size()) == SkinUploadDropPlan.Mode.LIBRARY_IMPORT) {
            this.importDroppedSkinsToLibrary(arrayList);
            return;
        }
        this.setSelectedFile((File)arrayList.get(0), false);
        if (this.selectedFile != null && arrayList.size() > 1) {
            this.setSelectedMouthFile((File)arrayList.get(1), MouthVariant.OPEN);
        }
        if (this.selectedFile != null && arrayList.size() > 2) {
            this.setSelectedMouthFile((File)arrayList.get(2), MouthVariant.CLOSE);
        }
    }

    public boolean mouseScrolled(double d, double d2, double d3) {
        if (this.clearDialogOpen) {
            return true;
        }
        return this.onMouseScrolled(d, d2, d3);
    }

    public boolean mouseScrolled(double d, double d2, double d3, double d4) {
        if (this.clearDialogOpen) {
            return true;
        }
        return this.onMouseScrolled(d, d2, d4);
    }

    public boolean mouseClicked(double d, double d2, int n) {
        if (n == 0) {
            int n2 = (int)d;
            int n3 = (int)d2;
            if (this.clearDialogOpen) {
                return this.handleClearDialogClick(n2, n3);
            }
            if (this.isInside(n2, n3, this.dropX, this.dropY, this.dropW, this.dropH)) {
                this.browseForSkinFile();
                return true;
            }
            if (this.isInside(n2, n3, this.btnBrowseX, this.btnBrowseY, this.btnBrowseW, this.btnBrowseH)) {
                ModSounds.play(ModSounds.UI_UPLOAD);
                this.browseForSkinFile();
                return true;
            }
            if (this.isInside(n2, n3, this.btnMouthOpenX, this.btnMouthOpenY, this.btnMouthOpenW, this.btnMouthOpenH)) {
                ModSounds.play(ModSounds.UI_UPLOAD);
                this.browseForMouthFile(MouthVariant.OPEN);
                return true;
            }
            if (this.isInside(n2, n3, this.btnMouthCloseX, this.btnMouthCloseY, this.btnMouthCloseW, this.btnMouthCloseH)) {
                ModSounds.play(ModSounds.UI_UPLOAD);
                this.browseForMouthFile(MouthVariant.CLOSE);
                return true;
            }
            if (this.isInside(n2, n3, this.btnSlimX, this.btnSlimY, this.btnSlimW, this.btnSlimH)) {
                this.slimChecked = !this.slimChecked;
                this.rebuildPreviewPlayer();
                return true;
            }
            if (this.isInside(n2, n3, this.btnUploadX, this.btnUploadY, this.btnUploadW, this.btnUploadH)) {
                this.doUpload();
                return true;
            }
            if (this.isInside(n2, n3, this.btnClearX, this.btnClearY, this.btnClearW, this.btnClearH)) {
                if (this.canApplySlimOnly()) {
                    this.clearDialogOpen = true;
                    ModSounds.play(ModSounds.UI_UPLOAD);
                }
                return true;
            }
            if (this.tryHandleSlotClick(d, d2)) {
                return true;
            }
        }
        return super.mouseClicked(d, d2, n);
    }

    public boolean keyPressed(int n, int n2, int n3) {
        if (this.clearDialogOpen && n == 256) {
            this.clearDialogOpen = false;
            ModSounds.play(ModSounds.UI_UPLOAD);
            return true;
        }
        return super.keyPressed(n, n2, n3);
    }

    private void renderPreviewArea(GuiGraphics drawContext, int n, int n2) {
        boolean bl = this.isInside(n, n2, this.dropX, this.dropY, this.dropW, this.dropH);
        drawContext.fill(this.dropX, this.dropY, this.dropX + this.dropW, this.dropY + this.dropH, 0x59191919);
        if (bl) {
            drawContext.fill(this.dropX, this.dropY, this.dropX + this.dropW, this.dropY + this.dropH, 0x35525252);
        }
        SkinUploadScreen.drawRectBorder(drawContext, this.dropX, this.dropY, this.dropW, this.dropH, bl ? 1922734746 : 0x55969696);
        SkinUploadScreen.drawRectBorder(drawContext, this.dropX + 2, this.dropY + 2, this.dropW - 4, this.dropH - 4, 0x224A4A4A);
        Minecraft minecraftClient = Minecraft.getInstance();
        if (this.previewId != null && (this.previewPlayer == null || this.previewSlim != this.slimChecked)) {
            this.rebuildPreviewPlayer();
        }
        LivingEntity livingEntity = this.previewPlayer != null ? this.previewPlayer : minecraftClient.player;
        if (livingEntity != null) {
            int n3 = this.dropX + 2;
            int n4 = this.dropY + 2;
            int n5 = this.dropX + this.dropW - 2;
            int n6 = this.dropY + this.dropH - 2;
            drawContext.enableScissor(n3, n4, n5, n6);
            drawContext.pose().pushPose();
            drawContext.pose().translate(0.0, 0.0, 1000.0);
            InventoryEntityRendererCompat.drawEntity(drawContext, n3, n4, n5, n6, n, n2, livingEntity);
            drawContext.pose().popPose();
            drawContext.disableScissor();
        }
        MutableComponent mutableText = this.selectedFile == null ? Component.translatable((String)"screen.catskinc-remake.drop_hint") : Component.literal((String)"Click to replace selected skin");
        this.drawCenteredTextFit(drawContext, mutableText.getString(), this.dropX + this.dropW / 2, this.dropY + this.dropH - 18, Math.max(12, this.dropW - 16), -3552823, 0.68f);
    }

    private void renderInfoBar(GuiGraphics drawContext) {
        drawContext.fill(this.infoX, this.infoY, this.infoX + this.infoW, this.infoY + this.infoH, 1796543765);
        SkinUploadScreen.drawRectBorder(drawContext, this.infoX, this.infoY, this.infoW, this.infoH, 0x55969696);
        if (this.selectedFile == null) {
            int n = Math.max(12, this.infoW - 16);
            this.drawCenteredTextFit(drawContext, Component.translatable((String)"screen.catskinc-remake.info.no_skin").getString(), this.infoX + this.infoW / 2, this.infoY + 8, n, -3552823, 0.72f);
            this.drawCenteredTextFit(drawContext, Component.translatable((String)"screen.catskinc-remake.info.constraints").getString(), this.infoX + this.infoW / 2, this.infoY + 22, n, -5855578, 0.64f);
            this.drawCenteredTextFit(drawContext, Component.translatable((String)"screen.catskinc-remake.info.select_skin_required").getString(), this.infoX + this.infoW / 2, this.infoY + 36, n, -5855578, 0.64f);
        } else {
            String string = this.ellipsis(this.selectedFile.getName(), this.infoW - 24);
            String string2 = this.selectedWidth + "x" + this.selectedHeight + (this.slimChecked ? " | Slim" : " | Classic");
            String string3 = this.selectedMouthOpenFile != null ? "Open: " + this.ellipsis(this.selectedMouthOpenFile.getName(), this.infoW - 64) : Component.translatable((String)"screen.catskinc-remake.info.mouth_open_none").getString();
            String string4 = this.selectedMouthCloseFile != null ? "Close: " + this.ellipsis(this.selectedMouthCloseFile.getName(), this.infoW - 64) : Component.translatable((String)"screen.catskinc-remake.info.mouth_close_none").getString();
            drawContext.drawString(this.font, (Component)Component.literal((String)string), this.infoX + 10, this.infoY + 6, -1);
            drawContext.drawString(this.font, (Component)Component.literal((String)string2), this.infoX + 10, this.infoY + 20, -1381654);
            drawContext.drawString(this.font, (Component)Component.literal((String)string3), this.infoX + 10, this.infoY + 36, -3552823);
            drawContext.drawString(this.font, (Component)Component.literal((String)string4), this.infoX + 10, this.infoY + 50, -3552823);
        }
    }

    private void renderRightSidebar(GuiGraphics drawContext, int n, int n2) {
        int n3 = this.rightX + 10;
        int n4 = this.rightW - 20;
        this.drawSectionHeader(drawContext, "Assets", n3, this.btnBrowseY - 12, n4);
        this.drawSectionHeader(drawContext, "Model", n3, this.btnSlimY - 12, n4);
        this.drawSectionHeader(drawContext, "Actions", n3, this.btnUploadY - 12, n4);
        this.drawCustomButton(drawContext, this.btnBrowseX, this.btnBrowseY, this.btnBrowseW, this.btnBrowseH, Component.translatable((String)"screen.catskinc-remake.button.browse_skin").getString(), n, n2, true, ButtonStyle.PRIMARY);
        this.drawCustomButton(drawContext, this.btnMouthOpenX, this.btnMouthOpenY, this.btnMouthOpenW, this.btnMouthOpenH, Component.translatable((String)"screen.catskinc-remake.button.browse_mouth_open").getString(), n, n2, this.selectedFile != null, ButtonStyle.PRIMARY);
        this.drawCustomButton(drawContext, this.btnMouthCloseX, this.btnMouthCloseY, this.btnMouthCloseW, this.btnMouthCloseH, Component.translatable((String)"screen.catskinc-remake.button.browse_mouth_close").getString(), n, n2, this.selectedFile != null, ButtonStyle.PRIMARY);
        this.drawSlimToggle(drawContext, this.btnSlimX, this.btnSlimY, this.btnSlimW, this.btnSlimH, n, n2);
        boolean bl = this.selectedFile != null;
        this.drawCustomButton(drawContext, this.btnUploadX, this.btnUploadY, this.btnUploadW, this.btnUploadH, Component.translatable((String)"screen.catskinc-remake.button.upload").getString(), n, n2, bl, ButtonStyle.PRIMARY);
        boolean bl2 = this.canApplySlimOnly();
        this.drawCustomButton(drawContext, this.btnClearX, this.btnClearY, this.btnClearW, this.btnClearH, Component.translatable((String)"screen.catskinc-remake.button.clear").getString(), n, n2, bl2, ButtonStyle.DANGER);
    }

    private void drawSlimToggle(GuiGraphics drawContext, int n, int n2, int n3, int n4, int n5, int n6) {
        boolean bl = this.isInside(n5, n6, n, n2, n3, n4);
        int n7 = bl ? 0x55444444 : 0x3D232323;
        drawContext.fill(n, n2, n + n3, n2 + n4, n7);
        SkinUploadScreen.drawRectBorder(drawContext, n, n2, n3, n4, bl ? 1922734746 : 1670023818);
        int n8 = 28;
        int n9 = n4 - 8;
        int n10 = n + n3 - n8 - 6;
        int n11 = n2 + 4;
        int n12 = this.slimChecked ? -1334676878 : 0x55464646;
        drawContext.fill(n10, n11, n10 + n8, n11 + n9, n12);
        SkinUploadScreen.drawRectBorder(drawContext, n10, n11, n8, n9, 2141364898);
        int n13 = n8 / 2 - 2;
        int n14 = n9 - 3;
        int n15 = this.slimChecked ? n10 + n8 - n13 - 3 : n10 + 3;
        int n16 = n11 + 1;
        drawContext.fill(n15, n16, n15 + n13, n16 + n14, -1513240);
        String string = Component.translatable((String)"screen.catskinc-remake.slim_model").getString();
        int n17 = Math.max(10, n10 - n - 10);
        drawContext.drawString(this.font, (Component)Component.literal((String)this.ellipsis(string, n17)), n + 6, n2 + (n4 - 8) / 2, -1);
    }

    private void drawCustomButton(GuiGraphics drawContext, int n, int n2, int n3, int n4, String string, int n5, int n6, boolean bl, ButtonStyle buttonStyle) {
        int n7;
        int n8;
        boolean bl2;
        boolean bl3 = bl2 = bl && this.isInside(n5, n6, n, n2, n3, n4);
        if (!bl) {
            n8 = 0x23202020;
            n7 = 1670023818;
        } else {
            switch (buttonStyle.ordinal()) {
                case 2: {
                    n8 = bl2 ? -1116244105 : -1554030753;
                    n7 = -926497082;
                    break;
                }
                case 1: {
                    n8 = bl2 ? -13619152 : -266856424;
                    n7 = -986896;
                    break;
                }
                case 3: {
                    n8 = bl2 ? -1588045736 : -2042609600;
                    n7 = -1097953650;
                    break;
                }
                default: {
                    n8 = bl2 ? 0x55444444 : 0x3D232323;
                    n7 = bl2 ? 1922734746 : 1670023818;
                }
            }
        }
        int n9 = bl ? -1 : -3552823;
        drawContext.fill(n, n2, n + n3, n2 + n4, n8);
        SkinUploadScreen.drawRectBorder(drawContext, n, n2, n3, n4, n7);
        if (bl && buttonStyle == ButtonStyle.ASSET) {
            int n10 = bl2 ? -1 : -4671304;
            drawContext.fill(n + 2, n2 + 2, n + 4, n2 + n4 - 2, n10);
            drawContext.fill(n + 5, n2 + 2, n + n3 - 2, n2 + 3, bl2 ? 0x42FFFFFF : 0x2DFFFFFF);
        }
        if (bl && bl2) {
            drawContext.fill(n + 1, n2 + 1, n + n3 - 1, n2 + 2, 0x2CFFFFFF);
        }
        this.drawCenteredTextFit(drawContext, string, n + n3 / 2, n2 + (n4 - 8) / 2, Math.max(12, n3 - 12), n9, 0.62f);
    }

    private void drawSectionHeader(GuiGraphics drawContext, String string, int n, int n2, int n3) {
        String string2 = this.ellipsis(string, Math.max(16, n3 - 4));
        drawContext.drawString(this.font, (Component)Component.literal((String)string2), n, n2, -5855578);
    }

    private void renderClearDialog(GuiGraphics drawContext, int n, int n2) {
        drawContext.pose().pushPose();
        drawContext.pose().translate(0.0, 0.0, 3000.0);
        drawContext.fill(0, 0, this.width, this.height, -436207616);
        drawContext.fill(this.clearDialogX, this.clearDialogY, this.clearDialogX + this.clearDialogW, this.clearDialogY + this.clearDialogH, -15395563);
        drawContext.fill(this.clearDialogX + 1, this.clearDialogY + 1, this.clearDialogX + this.clearDialogW - 1, this.clearDialogY + 20, -14671840);
        String string = Component.translatable((String)"screen.catskinc-remake.clear_dialog.title").getString();
        String string2 = Component.translatable((String)"screen.catskinc-remake.clear_dialog.subtitle").getString();
        drawContext.drawCenteredString(this.font, (Component)Component.literal((String)this.ellipsis(string, this.clearDialogW - 16)), this.clearDialogX + this.clearDialogW / 2, this.clearDialogY + 8, -1);
        drawContext.drawCenteredString(this.font, (Component)Component.literal((String)this.ellipsis(string2, this.clearDialogW - 16)), this.clearDialogX + this.clearDialogW / 2, this.clearDialogY + 24, -3552823);
        boolean bl = this.canApplySlimOnly();
        this.drawModalButton(drawContext, this.clearBtnAllX, this.clearBtnAllY, this.clearBtnAllW, this.clearBtnAllH, Component.translatable((String)"screen.catskinc-remake.clear_dialog.clear_all").getString(), n, n2, true, ButtonStyle.DANGER);
        this.drawModalButton(drawContext, this.clearBtnSkinX, this.clearBtnSkinY, this.clearBtnSkinW, this.clearBtnSkinH, Component.translatable((String)"screen.catskinc-remake.clear_dialog.clear_skin").getString(), n, n2, bl, ButtonStyle.SECONDARY);
        this.drawModalButton(drawContext, this.clearBtnMouthX, this.clearBtnMouthY, this.clearBtnMouthW, this.clearBtnMouthH, Component.translatable((String)"screen.catskinc-remake.clear_dialog.clear_mouth").getString(), n, n2, bl, ButtonStyle.SECONDARY);
        this.drawModalButton(drawContext, this.clearBtnCancelX, this.clearBtnCancelY, this.clearBtnCancelW, this.clearBtnCancelH, Component.translatable((String)"screen.catskinc-remake.clear_dialog.cancel").getString(), n, n2, true, ButtonStyle.PRIMARY);
        drawContext.pose().popPose();
    }

    private void drawModalButton(GuiGraphics drawContext, int n, int n2, int n3, int n4, String string, int n5, int n6, boolean bl, ButtonStyle buttonStyle) {
        int n7;
        int n8;
        boolean bl2;
        boolean bl3 = bl2 = bl && this.isInside(n5, n6, n, n2, n3, n4);
        if (!bl) {
            n8 = -14013910;
            n7 = -10724260;
        } else {
            switch (buttonStyle.ordinal()) {
                case 2: {
                    n8 = bl2 ? -10197916 : -11579569;
                    n7 = -3158065;
                    break;
                }
                case 3: {
                    n8 = bl2 ? -8763834 : -10143174;
                    n7 = -3301473;
                    break;
                }
                default: {
                    n8 = bl2 ? -11842741 : -12829636;
                    n7 = -4605511;
                }
            }
        }
        int n9 = bl ? -1 : -6513508;
        drawContext.fill(n, n2, n + n3, n2 + n4, n8);
        SkinUploadScreen.drawRectBorder(drawContext, n, n2, n3, n4, n7);
        this.drawCenteredTextFit(drawContext, string, n + n3 / 2, n2 + (n4 - 8) / 2, Math.max(12, n3 - 12), n9, 0.7f);
    }

    private boolean handleClearDialogClick(int n, int n2) {
        boolean bl = this.isInside(n, n2, this.clearDialogX, this.clearDialogY, this.clearDialogW, this.clearDialogH);
        if (bl) {
            if (this.isInside(n, n2, this.clearBtnCancelX, this.clearBtnCancelY, this.clearBtnCancelW, this.clearBtnCancelH)) {
                this.clearDialogOpen = false;
                ModSounds.play(ModSounds.UI_UPLOAD);
                return true;
            }
            if (!this.canApplySlimOnly()) {
                this.clearDialogOpen = false;
                SkinUploadScreen.toastError("toast.clear.no_player", new Object[0]);
                ModSounds.play(ModSounds.UI_ERROR);
                return true;
            }
            if (this.isInside(n, n2, this.clearBtnAllX, this.clearBtnAllY, this.clearBtnAllW, this.clearBtnAllH)) {
                this.clearSelection(ClearTarget.ALL);
                return true;
            }
            if (this.isInside(n, n2, this.clearBtnSkinX, this.clearBtnSkinY, this.clearBtnSkinW, this.clearBtnSkinH)) {
                this.clearSelection(ClearTarget.SKIN);
                return true;
            }
            if (this.isInside(n, n2, this.clearBtnMouthX, this.clearBtnMouthY, this.clearBtnMouthW, this.clearBtnMouthH)) {
                this.clearSelection(ClearTarget.MOUTH);
                return true;
            }
            return true;
        }
        this.clearDialogOpen = false;
        ModSounds.play(ModSounds.UI_UPLOAD);
        return true;
    }

    private void renderSkinSlots(GuiGraphics drawContext, int n, int n2) {
        drawContext.fill(this.historyX, this.historyY, this.historyX + this.historyW, this.historyY + this.historyH, 1796543765);
        SkinUploadScreen.drawRectBorder(drawContext, this.historyX, this.historyY, this.historyW, this.historyH, 0x55969696);
        int n3 = this.historyX + 1;
        int n4 = this.historyY + 1;
        int n5 = this.historyW - 2;
        int n6 = this.historyH - 2;
        if (this.history.isEmpty()) {
            drawContext.drawCenteredString(this.font, (Component)Component.literal((String)"No skins yet"), this.historyX + this.historyW / 2, this.historyY + this.historyH / 2 - 4, -3552823);
            return;
        }
        drawContext.enableScissor(n3, n4, n3 + n5, n4 + n6);
        for (int i = 0; i < this.history.size(); ++i) {
            boolean bl;
            int n7;
            int n8;
            int n9;
            int n10 = this.historyY + 1 + i * this.historyRowH - this.historyScroll;
            if (n10 + this.historyRowH < n4 || n10 > n4 + n6) continue;
            int n11 = n3 + 2;
            int n12 = n5 - 6;
            int n13 = this.historyRowH - 4;
            HistoryEntry historyEntry = this.history.get(i);
            boolean bl2 = this.selectedFile != null && this.selectedFile.equals(historyEntry.file);
            boolean bl3 = this.isInside(n, n2, n11, n10, n12, n13);
            int n14 = bl2 ? 0x556A6A6A : (bl3 ? 0x35525252 : 0x2B222222);
            drawContext.fill(n11, n10, n11 + n12, n10 + n13, n14);
            SkinUploadScreen.drawRectBorder(drawContext, n11, n10, n12, n13, bl2 ? 1922734746 : 979921000);
            int n15 = Math.min(n13 - 16, 28);
            int n16 = n11 + 6;
            int n17 = n10 + (n13 - n15) / 2;
            if (historyEntry.previewSkinId != null) {
                drawContext.enableScissor(n16 - 1, n17 - 1, n16 + n15 + 1, n17 + n15 + 1);
                PlayerHeadRendererCompat.drawHead(drawContext, historyEntry.previewSkinId, n16, n17, n15);
                drawContext.disableScissor();
            } else if (historyEntry.thumbId != null) {
                drawContext.blit(historyEntry.thumbId, n16, n17, 0.0f, 0.0f, n15, n15, n15, n15);
            }
            if (historyEntry.previewSkinId != null || historyEntry.thumbId != null) {
                SkinUploadScreen.drawRectBorder(drawContext, n16 - 1, n17 - 1, n15 + 2, n15 + 2, 0x36666666);
            }
            n7 = 12;
            n9 = n11 + n12 - n7 - 6;
            n8 = n10 + (n13 - n7) / 2;
            bl = this.isInside(n, n2, n9, n8, n7, n7);
            drawContext.fill(n9, n8, n9 + n7, n8 + n7, bl ? -1517647222 : 0x3A2A2A2A);
            String deleteIcon = "\u2716";
            int deleteIconW = this.font.width(deleteIcon);
            int deleteIconX = n9 + (n7 - deleteIconW) / 2;
            int deleteIconY = n8 + (n7 - this.font.lineHeight) / 2 + 1;
            drawContext.drawString(this.font, (Component)Component.literal((String)deleteIcon), deleteIconX, deleteIconY, -1);
            int n20 = n16 + n15 + 8;
            int n21 = Math.max(20, n9 - n20 - 8);
            drawContext.pose().pushPose();
            float f3 = 0.85f;
            drawContext.pose().scale(f3, f3, 1.0f);
            float f4 = 1.0f / f3;
            int n22 = (int)((float)n20 * f4);
            int n23 = (int)((float)n21 * f4);
            String string = this.ellipsis(SkinUploadScreen.stripExt(historyEntry.file.getName()), n23);
            int n24 = (int)(((float)n10 + (float)n13 / 2.0f - 7.0f) * f4);
            drawContext.drawString(this.font, (Component)Component.literal((String)string), n22, n24, bl2 ? -1 : -3552823);
            if (historyEntry.width > 0 && historyEntry.height > 0) {
                String string2 = historyEntry.width + "x" + historyEntry.height;
                int n25 = (int)(((float)n10 + (float)n13 / 2.0f + 3.0f) * f4);
                drawContext.drawString(this.font, (Component)Component.literal((String)string2), n22, n25, -3552823);
            }
            drawContext.pose().popPose();
        }
        drawContext.disableScissor();
        this.drawSlotScrollbar(drawContext);
    }

    private void drawSlotScrollbar(GuiGraphics drawContext) {
        int n = this.maxHistoryScroll();
        if (n <= 0) {
            return;
        }
        int n2 = this.historyX + this.historyW - 3;
        int n3 = this.historyY + 2;
        int n4 = this.historyH - 4;
        float f = (float)this.historyH / (float)(this.historyH + n);
        int n5 = Math.max(18, Math.round((float)n4 * f));
        int n6 = n3 + Math.round((float)this.historyScroll / (float)n * (float)(n4 - n5));
        drawContext.fill(n2, n3, n2 + 2, n3 + n4, 641021237);
        drawContext.fill(n2, n6, n2 + 2, n6 + n5, -1902404709);
    }

    private boolean onMouseScrolled(double d, double d2, double d3) {
        if (!this.isInside((int)d, (int)d2, this.historyX, this.historyY, this.historyW, this.historyH)) {
            return false;
        }
        int n = this.maxHistoryScroll();
        if (n <= 0) {
            return false;
        }
        this.historyScroll = SkinUploadScreen.clamp(this.historyScroll - (int)(d3 * (double)((float)this.historyRowH * 0.65f)), 0, n);
        return true;
    }

    private boolean tryHandleSlotClick(double d, double d2) {
        if (!this.isInside((int)d, (int)d2, this.historyX, this.historyY, this.historyW, this.historyH)) {
            return false;
        }
        int n = (int)((d2 - (double)this.historyY + (double)this.historyScroll) / (double)this.historyRowH);
        if (n < 0 || n >= this.history.size()) {
            return true;
        }
        int n2 = this.historyX + 3;
        int n3 = this.historyW - 8;
        int n4 = 12;
        int n5 = n2 + n3 - n4 - 6;
        int n6 = this.historyY + 1 + n * this.historyRowH - this.historyScroll;
        int n7 = this.historyRowH - 4;
        int n8 = n6 + (n7 - n4) / 2;
        if (this.isInside((int)d, (int)d2, n5, n8, n4, n4)) {
            this.deleteHistory(n);
            return true;
        }
        this.setSelectedFile(this.history.get((int)n).file, true);
        return true;
    }

    private void doUpload() {
        final Minecraft minecraftClient = Minecraft.getInstance();
        final boolean bl = this.slimChecked;
        if (this.selectedFile == null) {
            SkinUploadScreen.toastError("toast.error.upload_requires_skin", new Object[0]);
            ModSounds.play(ModSounds.UI_ERROR);
            return;
        }
        ModLog.debug("UI upload requested: file='{}', slim={}", this.selectedFile.getName(), this.slimChecked);
        final Toasts.UploadToast uploadToast = Toasts.showUpload((Component)Component.translatable((String)"toast.upload.title"), (Component)Component.translatable((String)"toast.upload.preparing"));
        ModSounds.play(ModSounds.UI_UPLOAD);
        minecraftClient.setScreen(null);
        UUID uUID = minecraftClient.player == null ? null : minecraftClient.player.getUUID();
        ServerApiClient.uploadSkinAsync(this.selectedFile, this.selectedMouthOpenFile, this.selectedMouthCloseFile, uUID, bl, new ServerApiClient.ProgressListener(){

            @Override
            public void onStart(long l) {
                minecraftClient.execute(() -> uploadToast.update(0.0f, Component.translatable((String)"toast.upload.start", (Object[])new Object[]{SkinUploadScreen.humanBytes(l)}).getString()));
            }

            @Override
            public void onProgress(long l, long l2) {
                float f = l2 > 0L ? (float)l / (float)l2 : 0.0f;
                minecraftClient.execute(() -> uploadToast.update(f, (int)(f * 100.0f) + "%"));
            }

            @Override
            public void onDone(boolean bl2, String string) {
                minecraftClient.execute(() -> {
                    uploadToast.complete(bl2, Component.translatable((String)(bl2 ? "toast.upload.success" : "toast.upload.failed")).getString());
                    if (!bl2) {
                        ModSounds.play(ModSounds.UI_ERROR);
                        String string2 = SkinUploadScreen.formatServerErrorMessage(string, false);
                        Toasts.error((Component)Component.translatable((String)"title.skin_management"), (Component)Component.literal((String)string2));
                        return;
                    }
                    ModSounds.play(ModSounds.UI_COMPLETE);
                    if (minecraftClient.player != null) {
                        SkinUploadScreen.this.applyImmediateLocalSkinSelection(minecraftClient, bl);
                        ServerApiClient.selectSkin(minecraftClient.player.getUUID(), string, bl);
                        SkinManagerClient.setSlim(minecraftClient.player.getUUID(), bl);
                        SkinManagerClient.refresh(minecraftClient.player.getUUID());
                    }
                });
            }
        });
    }

    private void browseForSkinFile() {
        try {
            String string = this.selectedFile != null ? this.selectedFile.getAbsolutePath() : this.historyDir().getAbsolutePath();
            String string2 = TinyFileDialogs.tinyfd_openFileDialog((CharSequence)"Select skin PNG", (CharSequence)string, null, (CharSequence)"PNG image", (boolean)false);
            if (string2 == null || string2.isBlank()) {
                ModLog.trace("File dialog cancelled", new Object[0]);
                return;
            }
            this.setSelectedFile(new File(string2), false);
        }
        catch (Exception exception) {
            ModLog.error("File picker failed", exception);
            SkinUploadScreen.toastError("toast.error.read_failed", new Object[0]);
        }
    }

    private void browseForMouthFile(MouthVariant mouthVariant) {
        if (this.selectedFile == null) {
            SkinUploadScreen.toastError("toast.error.mouth_requires_skin", new Object[0]);
            ModSounds.play(ModSounds.UI_ERROR);
            return;
        }
        try {
            File file = mouthVariant == MouthVariant.OPEN ? this.selectedMouthOpenFile : this.selectedMouthCloseFile;
            String string = file != null ? file.getAbsolutePath() : this.selectedFile.getAbsolutePath();
            String string2 = mouthVariant == MouthVariant.OPEN ? "Select mouth-open PNG" : "Select mouth-close PNG";
            String string3 = TinyFileDialogs.tinyfd_openFileDialog((CharSequence)string2, (CharSequence)string, null, (CharSequence)"PNG image", (boolean)false);
            if (string3 == null || string3.isBlank()) {
                ModLog.trace("Mouth file dialog cancelled for {}", new Object[]{mouthVariant});
                return;
            }
            this.setSelectedMouthFile(new File(string3), mouthVariant);
        }
        catch (Exception exception) {
            ModLog.error("Mouth file picker failed", exception);
            SkinUploadScreen.toastError("toast.error.read_failed", new Object[0]);
        }
    }

    private void setSelectedFile(File file, boolean bl) {
        if (file == null) {
            return;
        }
        String string = file.getName().toLowerCase(Locale.ROOT);
        if (!string.endsWith(".png")) {
            SkinUploadScreen.toastError("toast.error.not_png", new Object[0]);
            ModSounds.play(ModSounds.UI_ERROR);
            return;
        }
        SkinFileInfo skinFileInfo = this.readSkinFileInfo(file, true);
        if (skinFileInfo == null) {
            ModSounds.play(ModSounds.UI_ERROR);
            return;
        }
        try {
            File file2;
            this.selectedFile = file2 = bl ? file : this.copyToHistory(file);
            this.selectedWidth = skinFileInfo.width;
            this.selectedHeight = skinFileInfo.height;
            this.refreshPreviewTexture();
            ModLog.debug("Skin file selected: file='{}', size={}x{}, fromHistory={}", file2.getName(), skinFileInfo.width, skinFileInfo.height, bl);
            if (!bl) {
                SkinUploadScreen.toastInfo("toast.file.selected", file2.getName());
                ModSounds.play(ModSounds.UI_UPLOAD);
            }
            this.loadHistory();
            this.historyScroll = SkinUploadScreen.clamp(this.historyScroll, 0, this.maxHistoryScroll());
        }
        catch (Exception exception) {
            ModLog.error("Failed to load selected skin file: " + String.valueOf(file), exception);
            SkinUploadScreen.toastError("toast.error.read_failed", new Object[0]);
            ModSounds.play(ModSounds.UI_ERROR);
        }
    }

    private void importDroppedSkinsToLibrary(List<File> list) {
        File file = null;
        int n = 0;
        for (File file2 : list) {
            SkinFileInfo skinFileInfo = this.readSkinFileInfo(file2, false);
            if (skinFileInfo == null) {
                continue;
            }
            try {
                File file3 = this.copyToHistory(file2);
                if (file == null) {
                    file = file3;
                }
                ++n;
            }
            catch (Exception exception) {
                ModLog.warn("Failed to import dropped skin into history: " + String.valueOf(file2), exception);
            }
        }
        if (file == null) {
            SkinUploadScreen.toastError("toast.error.invalid_dimensions", new Object[0]);
            ModSounds.play(ModSounds.UI_ERROR);
            return;
        }
        this.setSelectedFile(file, true);
        ModSounds.play(ModSounds.UI_UPLOAD);
        ModLog.debug("Imported {} dropped skin(s) into history", n);
    }

    private SkinFileInfo readSkinFileInfo(File file, boolean bl) {
        try (FileInputStream fileInputStream = new FileInputStream(file);){
            NativeImage nativeImage = NativeImage.read((InputStream)fileInputStream);
            int n = nativeImage.getWidth();
            int n2 = nativeImage.getHeight();
            nativeImage.close();
            if (!SkinUploadScreen.isValidSize(n, n2)) {
                if (bl) {
                    SkinUploadScreen.toastError("toast.error.invalid_dimensions", new Object[0]);
                }
                return null;
            }
            return new SkinFileInfo(n, n2);
        }
        catch (Exception exception) {
            if (bl) {
                ModLog.error("Failed to read skin file: " + String.valueOf(file), exception);
                SkinUploadScreen.toastError("toast.error.read_failed", new Object[0]);
            } else {
                ModLog.trace("Skipped dropped skin '{}': {}", file.getName(), exception.getMessage());
            }
            return null;
        }
    }

    private void setSelectedMouthFile(File file, MouthVariant mouthVariant) {
        if (file == null) {
            return;
        }
        if (this.selectedFile == null) {
            SkinUploadScreen.toastError("toast.error.mouth_requires_skin", new Object[0]);
            ModSounds.play(ModSounds.UI_ERROR);
            return;
        }
        String string = file.getName().toLowerCase(Locale.ROOT);
        if (!string.endsWith(".png")) {
            SkinUploadScreen.toastError("toast.error.not_png", new Object[0]);
            ModSounds.play(ModSounds.UI_ERROR);
            return;
        }
        if (!this.validateMouthFile(file, mouthVariant, true)) {
            ModSounds.play(ModSounds.UI_ERROR);
            return;
        }
        if (mouthVariant == MouthVariant.OPEN) {
            this.selectedMouthOpenFile = file;
        } else {
            this.selectedMouthCloseFile = file;
        }
        this.refreshPreviewTexture();
        SkinUploadScreen.toastInfo(mouthVariant == MouthVariant.OPEN ? "toast.file.mouth_open_selected" : "toast.file.mouth_close_selected", file.getName());
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private boolean validateMouthFile(File file, MouthVariant mouthVariant, boolean bl) {
        try (FileInputStream fileInputStream = new FileInputStream(file);){
            NativeImage nativeImage = NativeImage.read((InputStream)fileInputStream);
            int n2 = nativeImage.getWidth();
            int n = nativeImage.getHeight();
            nativeImage.close();
            if (!SkinUploadScreen.isValidOverlaySize(n2, n)) {
                if (bl) {
                    SkinUploadScreen.toastError("toast.error.invalid_mouth_dimensions", new Object[0]);
                }
                boolean bl2 = false;
                return bl2;
            }
            if (mouthVariant == MouthVariant.OPEN) {
                this.selectedMouthOpenWidth = n2;
                this.selectedMouthOpenHeight = n;
            } else {
                this.selectedMouthCloseWidth = n2;
                this.selectedMouthCloseHeight = n;
            }
            boolean bl3 = true;
            return bl3;
        }
        catch (Exception exception) {
            ModLog.error("Failed to validate mouth file: " + String.valueOf(file), exception);
            if (!bl) return false;
            SkinUploadScreen.toastError("toast.error.read_failed", new Object[0]);
            return false;
        }
    }

    private void refreshPreviewTexture() {
        this.disposePreview();
        if (this.selectedFile == null) {
            return;
        }
        try (FileInputStream fileInputStream = new FileInputStream(this.selectedFile);){
            NativeImage nativeImage = NativeImage.read((InputStream)fileInputStream);
            File file = this.selectedMouthCloseFile != null ? this.selectedMouthCloseFile : this.selectedMouthOpenFile;
            NativeImage nativeImage2 = nativeImage;
            if (file != null) {
                try (FileInputStream fileInputStream2 = new FileInputStream(file);){
                    NativeImage nativeImage3 = NativeImage.read((InputStream)fileInputStream2);
                    NativeImage nativeImage4 = this.createOverlayImage("preview", nativeImage, nativeImage3);
                    if (nativeImage4 != null && nativeImage4 != nativeImage) {
                        nativeImage.close();
                        nativeImage2 = nativeImage4;
                    }
                    nativeImage3.close();
                }
            }
            this.previewTexture = new DynamicTexture(nativeImage2);
            this.previewTexture.setFilter(false, false);
            this.previewId = Identifiers.mod("preview/" + System.nanoTime());
            Minecraft.getInstance().getTextureManager().register(this.previewId, (AbstractTexture)this.previewTexture);
        }
        catch (Exception exception) {
            ModLog.error("Failed to rebuild preview texture", exception);
            SkinUploadScreen.toastError("toast.error.read_failed", new Object[0]);
            return;
        }
        this.rebuildPreviewPlayer();
    }

    private void rebuildPreviewPlayer() {
        Minecraft minecraftClient = Minecraft.getInstance();
        if (minecraftClient == null || minecraftClient.level == null || this.previewId == null) {
            this.previewPlayer = null;
            return;
        }
        if (this.previewPlayer != null) {
            SkinOverrideStore.clear(this.previewPlayer.getUUID());
        }
        this.previewSlim = this.slimChecked;
        GameProfile gameProfile = new GameProfile(previewProfileUuid(this.previewSlim), "");
        this.previewPlayer = new PreviewRemotePlayer(minecraftClient.level, gameProfile);
        this.previewPlayer.setCustomNameVisible(false);
        this.previewPlayer.setPose(Pose.STANDING);
        this.previewPlayer.setInvisible(false);
        this.previewPlayer.setYRot(180.0f);
        this.previewPlayer.yBodyRot = 180.0f;
        this.previewPlayer.yHeadRot = 180.0f;
        this.previewPlayer.setXRot(0.0f);
        try {
            this.previewPlayer.getEntityData().set(PlayerEntityAccessor.catskincRemake$getPlayerModelParts(), (byte)127);
        }
        catch (Exception exception) {
            ModLog.trace("Preview player model part update failed: {}", exception.getMessage());
        }
        SkinOverrideStore.put(this.previewPlayer.getUUID(), this.previewId, this.previewSlim);
    }

    private void applyImmediateLocalSkinSelection(Minecraft minecraftClient, boolean slim) {
        if (minecraftClient == null || minecraftClient.player == null || this.selectedFile == null) {
            return;
        }
        UUID uuid = minecraftClient.player.getUUID();
        SkinOverrideStore.clear(uuid);
        try {
            SkinOverrideStore.putManagedFromFile(uuid, this.selectedFile, slim);
        }
        catch (Exception exception) {
            ModLog.warn("Failed to apply immediate local skin selection for {}", uuid, exception);
        }
    }

    private static UUID previewProfileUuid(boolean slim) {
        return slim ? PREVIEW_SLIM_UUID : PREVIEW_WIDE_UUID;
    }

    private static UUID findPreviewProfileUuid(boolean slim, String seedPrefix) {
        PlayerSkin.Model targetModel = slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
        for (int attempt = 0; attempt < 256; attempt++) {
            UUID candidate = UUID.nameUUIDFromBytes((seedPrefix + "-" + attempt).getBytes(StandardCharsets.UTF_8));
            if (DefaultPlayerSkin.get(candidate).model() == targetModel) {
                return candidate;
            }
        }
        return UUID.nameUUIDFromBytes((seedPrefix + "-fallback").getBytes(StandardCharsets.UTF_8));
    }

    private void disposePreview() {
        Minecraft minecraftClient = Minecraft.getInstance();
        if (this.previewId != null && minecraftClient != null) {
            minecraftClient.getTextureManager().release(this.previewId);
            this.previewId = null;
        }
        if (this.previewPlayer != null) {
            SkinOverrideStore.clear(this.previewPlayer.getUUID());
            this.previewPlayer = null;
        }
        this.previewTexture = null;
    }

    private NativeImage createOverlayImage(String string, NativeImage nativeImage, NativeImage nativeImage2) {
        if (nativeImage2 == null) {
            return null;
        }
        try {
            int n = nativeImage.getWidth();
            int n2 = nativeImage.getHeight();
            int n3 = nativeImage2.getWidth();
            int n4 = nativeImage2.getHeight();
            if (n <= 0 || n2 <= 0 || n3 <= 0 || n4 <= 0) {
                return null;
            }
            int n5 = Math.max(n, n3);
            int n6 = Math.max(n2, n4);
            if (n5 != n || n6 != n2) {
                ModLog.debug("Scaling skin up to match high-res overlay ({}): {}x{} -> {}x{}", string, n, n2, n5, n6);
            }
            NativeImage nativeImage3 = new NativeImage(n5, n6, true);
            for (int i = 0; i < n6; ++i) {
                int n7 = Math.min(n2 - 1, i * n2 / n6);
                int n8 = Math.min(n4 - 1, i * n4 / n6);
                for (int j = 0; j < n5; ++j) {
                    int n9 = Math.min(n - 1, j * n / n5);
                    int n10 = Math.min(n3 - 1, j * n3 / n5);
                    int n11 = nativeImage2.getPixelRGBA(n10, n8);
                    int n12 = n11 >>> 24 & 0xFF;
                    nativeImage3.setPixelRGBA(j, i, n12 > 0 ? n11 : nativeImage.getPixelRGBA(n9, n7));
                }
            }
            return nativeImage3;
        }
        catch (Exception exception) {
            ModLog.warn("Failed to build {} texture", string, exception);
            return null;
        }
    }

    private void disposeHistory() {
        Minecraft minecraftClient = Minecraft.getInstance();
        for (HistoryEntry historyEntry : this.history) {
            if (minecraftClient == null) {
                continue;
            }
            if (historyEntry.thumbId != null) {
                minecraftClient.getTextureManager().release(historyEntry.thumbId);
            }
            if (historyEntry.previewSkinId != null) {
                minecraftClient.getTextureManager().release(historyEntry.previewSkinId);
            }
        }
        this.history.clear();
    }

    private void loadHistory() {
        this.disposeHistory();
        File[] fileArray = this.historyDir().listFiles((file, string) -> string.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (fileArray == null) {
            return;
        }
        ArrayList<File> arrayList = new ArrayList<File>(List.of(fileArray));
        arrayList.sort(Comparator.comparingLong(File::lastModified).reversed());
        for (File file2 : arrayList) {
            try (FileInputStream fileInputStream = new FileInputStream(file2);){
                NativeImage nativeImage = NativeImage.read((InputStream)fileInputStream);
                int n = nativeImage.getWidth();
                int n2 = nativeImage.getHeight();
                NativeImage nativeImage2 = SkinHeadThumbnailFactory.createIsometricHeadThumbnail(nativeImage, 64);
                DynamicTexture nativeImageBackedTexture = new DynamicTexture(nativeImage2);
                nativeImageBackedTexture.setFilter(false, false);
                ResourceLocation identifier = Identifiers.mod("thumb/" + System.nanoTime());
                Minecraft.getInstance().getTextureManager().register(identifier, (AbstractTexture)nativeImageBackedTexture);
                DynamicTexture nativeImageBackedTexture2 = new DynamicTexture(nativeImage);
                nativeImageBackedTexture2.setFilter(false, false);
                ResourceLocation identifier2 = Identifiers.mod("preview/" + System.nanoTime());
                Minecraft.getInstance().getTextureManager().register(identifier2, (AbstractTexture)nativeImageBackedTexture2);
                HistoryEntry historyEntry = new HistoryEntry(file2);
                historyEntry.thumbId = identifier;
                historyEntry.previewSkinId = identifier2;
                historyEntry.width = n;
                historyEntry.height = n2;
                this.history.add(historyEntry);
            }
            catch (Exception exception) {
                ModLog.trace("Skipped broken history skin '{}': {}", file2.getName(), exception.getMessage());
            }
        }
    }

    private void deleteHistory(int n) {
        if (n < 0 || n >= this.history.size()) {
            return;
        }
        HistoryEntry historyEntry = this.history.remove(n);
        try {
            Files.deleteIfExists(historyEntry.file.toPath());
        }
        catch (Exception exception) {
            ModLog.warn("Failed to delete history file: " + String.valueOf(historyEntry.file), exception);
        }
        if (historyEntry.thumbId != null) {
            Minecraft.getInstance().getTextureManager().release(historyEntry.thumbId);
        }
        if (historyEntry.previewSkinId != null) {
            Minecraft.getInstance().getTextureManager().release(historyEntry.previewSkinId);
        }
        if (this.selectedFile != null && this.selectedFile.equals(historyEntry.file)) {
            this.selectedFile = null;
            this.selectedWidth = 0;
            this.selectedHeight = 0;
            this.selectedMouthOpenFile = null;
            this.selectedMouthOpenWidth = 0;
            this.selectedMouthOpenHeight = 0;
            this.selectedMouthCloseFile = null;
            this.selectedMouthCloseWidth = 0;
            this.selectedMouthCloseHeight = 0;
            this.disposePreview();
        }
        this.historyScroll = SkinUploadScreen.clamp(this.historyScroll, 0, this.maxHistoryScroll());
    }

    private int maxHistoryScroll() {
        return Math.max(0, this.history.size() * this.historyRowH - this.historyH);
    }

    private boolean canApplySlimOnly() {
        Minecraft minecraftClient = Minecraft.getInstance();
        return minecraftClient != null && minecraftClient.player != null;
    }

    private void initSlimState() {
        Boolean bl;
        if (this.slimInitialized) {
            return;
        }
        Minecraft minecraftClient = Minecraft.getInstance();
        if (minecraftClient != null && minecraftClient.player != null && (bl = SkinManagerClient.isSlimOrNull(minecraftClient.player.getUUID())) != null) {
            this.slimChecked = bl;
        }
        this.slimInitialized = true;
    }

    private void layoutClearDialog(float f, int n) {
        this.clearDialogW = SkinUploadScreen.clamp(SkinUploadScreen.scaled(286, f), 200, Math.max(200, this.width - n * 2));
        this.clearDialogH = SkinUploadScreen.scaled(114, f);
        this.clearDialogX = (this.width - this.clearDialogW) / 2;
        this.clearDialogY = (this.height - this.clearDialogH) / 2;
        int n2 = SkinUploadScreen.scaled(8, f);
        int n3 = SkinUploadScreen.scaled(6, f);
        int n4 = (this.clearDialogW - n2 * 2 - n3) / 2;
        int n5 = SkinUploadScreen.scaled(20, f);
        int n6 = this.clearDialogY + this.clearDialogH - n2 - n5;
        int n7 = n6 - n3 - n5;
        int n8 = this.clearDialogX + n2;
        int n9 = n8 + n4 + n3;
        this.clearBtnAllX = n8;
        this.clearBtnAllY = n7;
        this.clearBtnAllW = n4;
        this.clearBtnAllH = n5;
        this.clearBtnSkinX = n9;
        this.clearBtnSkinY = n7;
        this.clearBtnSkinW = n4;
        this.clearBtnSkinH = n5;
        this.clearBtnMouthX = n8;
        this.clearBtnMouthY = n6;
        this.clearBtnMouthW = n4;
        this.clearBtnMouthH = n5;
        this.clearBtnCancelX = n9;
        this.clearBtnCancelY = n6;
        this.clearBtnCancelW = n4;
        this.clearBtnCancelH = n5;
    }

    private void clearSelection(ClearTarget clearTarget) {
        this.clearDialogOpen = false;
        Minecraft minecraftClient = Minecraft.getInstance();
        if (minecraftClient == null || minecraftClient.player == null) {
            SkinUploadScreen.toastError("toast.clear.no_player", new Object[0]);
            ModSounds.play(ModSounds.UI_ERROR);
            return;
        }
        UUID uUID = minecraftClient.player.getUUID();
        ModSounds.play(ModSounds.UI_UPLOAD);
        ServerApiClient.clearSelectionAsync(uUID, clearTarget.serverMode, clearResult -> minecraftClient.execute(() -> {
            if (clearResult == null || !clearResult.ok()) {
                ModSounds.play(ModSounds.UI_ERROR);
                String string = clearResult == null ? null : clearResult.message();
                string = SkinUploadScreen.formatServerErrorMessage(string, true);
                Toasts.error((Component)Component.translatable((String)"title.skin_management"), (Component)Component.literal((String)string));
                return;
            }
            this.applyLocalClearSelection(clearTarget);
            SkinOverrideStore.clear(uUID);
            SkinManagerClient.refresh(uUID);
            if (this.selectedFile == null) {
                this.disposePreview();
            } else {
                this.refreshPreviewTexture();
            }
            ModSounds.play(ModSounds.UI_COMPLETE);
            switch (clearTarget.ordinal()) {
                case 1: {
                    SkinUploadScreen.toastInfo("toast.file.skin_cleared", new Object[0]);
                    break;
                }
                case 2: {
                    SkinUploadScreen.toastInfo("toast.file.mouth_cleared", new Object[0]);
                    break;
                }
                case 0: {
                    SkinUploadScreen.toastInfo("toast.file.cleared", new Object[0]);
                    break;
                }
            }
        }));
    }

    private void applyLocalClearSelection(ClearTarget clearTarget) {
        switch (clearTarget.ordinal()) {
            case 1: {
                this.clearSkinSelection();
                break;
            }
            case 2: {
                this.clearMouthSelection();
                break;
            }
            case 0: {
                this.clearSkinSelection();
                this.clearMouthSelection();
                break;
            }
            default: {
                throw new IllegalStateException("Unexpected clear target: " + String.valueOf((Object)clearTarget));
            }
        }
    }

    private void clearSkinSelection() {
        this.selectedFile = null;
        this.selectedWidth = 0;
        this.selectedHeight = 0;
    }

    private void clearMouthSelection() {
        this.selectedMouthOpenFile = null;
        this.selectedMouthOpenWidth = 0;
        this.selectedMouthOpenHeight = 0;
        this.selectedMouthCloseFile = null;
        this.selectedMouthCloseWidth = 0;
        this.selectedMouthCloseHeight = 0;
    }

    private void drawBackdrop(GuiGraphics drawContext) {
        // Keep world background visible; no full-screen dark overlay.
    }

    private void drawPanel(GuiGraphics drawContext, int n, int n2, int n3, int n4) {
        drawContext.fill(n, n2, n + n3, n2 + n4, -1860494565);
        drawContext.fill(n + 1, n2 + 1, n + n3 - 1, n2 + 20, 0x21202020);
    }

    private static void drawRectBorder(GuiGraphics drawContext, int n, int n2, int n3, int n4, int n5) {
    }

    private boolean isInside(int n, int n2, int n3, int n4, int n5, int n6) {
        return n >= n3 && n < n3 + n5 && n2 >= n4 && n2 < n4 + n6;
    }

    private String ellipsis(String string, int n) {
        int n2;
        if (this.font.width(string) <= n) {
            return string;
        }
        String string2 = "...";
        int n3 = this.font.width(string2);
        for (n2 = string.length(); n2 > 0 && this.font.width(string.substring(0, n2)) + n3 > n; --n2) {
        }
        return n2 <= 0 ? string2 : string.substring(0, n2) + string2;
    }

    private void drawCenteredTextFit(GuiGraphics drawContext, String string, int n, int n2, int n3, int n4, float f) {
        int n5 = Math.max(8, n3);
        int n6 = this.font.width(string);
        if (n6 <= n5) {
            drawContext.drawCenteredString(this.font, (Component)Component.literal((String)string), n, n2, n4);
            return;
        }
        float f2 = Math.max(f, (float)n5 / (float)Math.max(1, n6));
        drawContext.pose().pushPose();
        drawContext.pose().scale(f2, f2, 1.0f);
        float f3 = 1.0f / f2;
        String string2 = string;
        int n7 = Math.max(6, Math.round((float)n5 / f2));
        if (this.font.width(string2) > n7) {
            string2 = this.ellipsis(string2, n7);
        }
        int n8 = Math.round((float)n * f3);
        int n9 = Math.round((float)n2 * f3);
        drawContext.drawCenteredString(this.font, (Component)Component.literal((String)string2), n8, n9, n4);
        drawContext.pose().popPose();
    }

    private File historyDir() {
        File file = new File(Minecraft.getInstance().gameDirectory, "skin_history");
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    private File copyToHistory(File file) throws Exception {
        File file2 = this.historyDir();
        File file3 = file.getParentFile();
        if (file3 != null && file3.getAbsoluteFile().equals(file2.getAbsoluteFile())) {
            return file;
        }
        String string = SkinUploadScreen.stripExt(file.getName());
        File file4 = new File(file2, string + ".png");
        int n = 2;
        while (file4.exists()) {
            file4 = new File(file2, string + "_" + n + ".png");
            ++n;
        }
        Files.copy(file.toPath(), file4.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        return file4;
    }

    private static String stripExt(String string) {
        int n = string.lastIndexOf(46);
        return n > 0 ? string.substring(0, n) : string;
    }

    private static final class SkinFileInfo {
        private final int width;
        private final int height;

        private SkinFileInfo(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static NativeImage createHeadThumbnail(NativeImage nativeImage, int n) {
        boolean bl;
        int n2 = Math.max(8, n);
        NativeImage nativeImage2 = new NativeImage(n2, n2, true);
        int n3 = nativeImage.getWidth();
        int n4 = nativeImage.getHeight();
        if (n3 <= 0 || n4 <= 0) {
            return nativeImage2;
        }
        int n5 = Math.max(1, n3 / 64);
        int n6 = Math.max(1, 8 * n5);
        int n7 = 8 * n5;
        int n8 = 8 * n5;
        int n9 = 40 * n5;
        int n10 = 8 * n5;
        boolean bl2 = bl = n7 + n6 <= n3 && n8 + n6 <= n4;
        if (!bl) {
            int n11 = Math.max(1, Math.min(n3, n4));
            for (int i = 0; i < n2; ++i) {
                int n12 = Math.min(n11 - 1, i * n11 / n2);
                for (int j = 0; j < n2; ++j) {
                    int n13 = Math.min(n11 - 1, j * n11 / n2);
                    nativeImage2.setPixelRGBA(j, i, nativeImage.getPixelRGBA(n13, n12));
                }
            }
            return nativeImage2;
        }
        boolean bl3 = n9 + n6 <= n3 && n10 + n6 <= n4;
        for (int i = 0; i < n2; ++i) {
            int n14 = Math.min(n6 - 1, i * n6 / n2);
            for (int j = 0; j < n2; ++j) {
                int n15;
                int n16 = Math.min(n6 - 1, j * n6 / n2);
                int n17 = nativeImage.getPixelRGBA(n7 + n16, n8 + n14);
                if (bl3 && ((n15 = nativeImage.getPixelRGBA(n9 + n16, n10 + n14)) >>> 24 & 0xFF) > 0) {
                    n17 = n15;
                }
                nativeImage2.setPixelRGBA(j, i, n17);
            }
        }
        return nativeImage2;
    }

    private static boolean isValidSize(int n, int n2) {
        boolean bl = n == n2;
        boolean bl2 = (n & n - 1) == 0;
        boolean bl3 = n >= 64 && n <= 4096;
        return bl && bl2 && bl3;
    }

    private static boolean isValidOverlaySize(int n, int n2) {
        return n >= 8 && n2 >= 8 && n <= 4096 && n2 <= 4096;
    }

    private static int scaled(int n, float f) {
        return Math.max(1, Math.round((float)n * f));
    }

    private static int clamp(int n, int n2, int n3) {
        return Math.max(n2, Math.min(n3, n));
    }

    private static String formatServerErrorMessage(String string, boolean bl) {
        String string2 = Component.translatable((String)(bl ? "toast.clear.failed" : "toast.upload.failed")).getString();
        if (string == null || string.isBlank()) {
            return string2;
        }
        String string3 = SkinUploadScreen.extractServerErrorField(string);
        if (string3 == null || string3.isBlank()) {
            string3 = string.trim();
        }
        if (string3.isBlank()) {
            return string2;
        }
        String string4 = string3.toLowerCase(Locale.ROOT);
        if (bl && string4.contains("uuid and skin required")) {
            return "Server API does not support Clear action yet. Rebuild and restart NewServer.";
        }
        if (!bl && (string4.contains("file required") || string4.contains("uuid and skin required"))) {
            return Component.translatable((String)"toast.error.upload_requires_skin").getString();
        }
        return string3;
    }

    private static String extractServerErrorField(String string) {
        String string2;
        String string3 = string2 = string == null ? "" : string.trim();
        if (!string2.startsWith("{")) {
            return null;
        }
        String string4 = SkinUploadScreen.extractJsonField(string2, "error");
        if (string4 == null || string4.isBlank()) {
            string4 = SkinUploadScreen.extractJsonField(string2, "message");
        }
        return string4;
    }

    private static String extractJsonField(String string, String string2) {
        String string3 = "\"" + string2 + "\"";
        int n = string.indexOf(string3);
        if (n < 0) {
            return null;
        }
        int n2 = string.indexOf(58, n + string3.length());
        if (n2 < 0) {
            return null;
        }
        int n3 = string.indexOf(34, n2 + 1);
        if (n3 < 0) {
            return null;
        }
        int n4 = -1;
        boolean bl = false;
        for (int i = n3 + 1; i < string.length(); ++i) {
            char c = string.charAt(i);
            if (bl) {
                bl = false;
                continue;
            }
            if (c == '\\') {
                bl = true;
                continue;
            }
            if (c != '\"') continue;
            n4 = i;
            break;
        }
        if (n4 < 0) {
            return null;
        }
        return string.substring(n3 + 1, n4).replace("\\n", " ").replace("\\r", " ").replace("\\\"", "\"").replace("\\\\", "\\").trim();
    }

    private static String humanBytes(long l) {
        if (l < 1024L) {
            return l + " B";
        }
        if (l < 0x100000L) {
            return String.format(Locale.ROOT, "%.1f KB", Float.valueOf((float)l / 1024.0f));
        }
        return String.format(Locale.ROOT, "%.1f MB", Float.valueOf((float)l / 1048576.0f));
    }

    private static void toastInfo(String string, Object ... objectArray) {
        Toasts.info((Component)Component.translatable((String)"title.skin_management"), (Component)Component.translatable((String)string, (Object[])objectArray));
    }

    private static void toastError(String string, Object ... objectArray) {
        Toasts.error((Component)Component.translatable((String)"title.skin_management"), (Component)Component.translatable((String)string, (Object[])objectArray));
    }

    private static enum MouthVariant {
        OPEN,
        CLOSE;

    }

    private static enum ButtonStyle {
        SECONDARY,
        ASSET,
        PRIMARY,
        DANGER;

    }

    private static enum ClearTarget {
        ALL("all"),
        SKIN("skin"),
        MOUTH("mouth");

        private final String serverMode;

        private ClearTarget(String string2) {
            this.serverMode = string2;
        }
    }

    private static class HistoryEntry {
        final File file;
        ResourceLocation thumbId;
        ResourceLocation previewSkinId;
        int width;
        int height;

        HistoryEntry(File file) {
            this.file = file;
        }
    }
}
