package com.sammy.catskinc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;

public final class Toasts {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 32;

    private static final int COLOR_BORDER = 0xFFF0F0F0;
    private static final int COLOR_BG = 0xFF1C1D21;
    private static final int COLOR_BG_TOP = 0xFF26272D;
    private static final int COLOR_BG_INNER = 0xFF16171B;

    private static final int COLOR_TITLE = 0xFFFFE14A;
    private static final int COLOR_TITLE_ERROR = 0xFFFF6B6B;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFDDDDDD;

    private Toasts() {
    }

    public static void info(Component title, Component description) {
        if (!ModConfig.get().isShowInfoToast()) return;
        Minecraft.getInstance().getToastManager().addToast(new SimpleToast(title, description, COLOR_TITLE));
    }

    public static void error(Component title, Component description) {
        if (!ModConfig.get().isShowErrorToast()) return;
        Minecraft.getInstance().getToastManager().addToast(new SimpleToast(title, description, COLOR_TITLE_ERROR));
    }

    public static UploadToast showUpload(Component title, Component subtitle) {
        if (!ModConfig.get().isShowUploadToast()) return null;
        UploadToast toast = new UploadToast(title, subtitle);
        Minecraft.getInstance().getToastManager().addToast(toast);
        return toast;
    }

    public static ConnectionToast connection(Component title, Component checkingMessage) {
        if (!ModConfig.get().isShowConnectionToast()) return null;
        ConnectionToast toast = new ConnectionToast(title, checkingMessage);
        Minecraft.getInstance().getToastManager().addToast(toast);
        return toast;
    }

    private static void drawAchievementBackground(GuiGraphicsExtractor context) {
        context.fill(0, 0, WIDTH, HEIGHT, COLOR_BORDER);
        context.fill(1, 1, WIDTH - 1, HEIGHT - 1, COLOR_BG);
        context.fill(2, 2, WIDTH - 2, HEIGHT - 2, COLOR_BG_INNER);
        context.fill(2, 2, WIDTH - 2, 12, COLOR_BG_TOP);
    }

    private static void drawAchievementFrame(GuiGraphicsExtractor context, Component title, int titleColor, Component description, int descriptionColor, Font renderer) {
        drawAchievementBackground(context);
        context.text(renderer, title, 8, 5, titleColor, false);
        if (description != null) {
            context.text(renderer, description, 8, 18, descriptionColor, false);
        }
    }

    public static final class SimpleToast implements Toast {
        private final Component title;
        private final Component description;
        private final int titleColor;
        private long start = -1L;
        private Visibility wantedVisibility = Visibility.SHOW;

        private SimpleToast(Component title, Component description, int titleColor) {
            this.title = title;
            this.description = description;
            this.titleColor = titleColor;
        }

        @Override
        public Visibility getWantedVisibility() {
            return this.wantedVisibility;
        }

        @Override
        public void update(ToastManager manager, long time) {
            if (start < 0L) {
                start = time;
            }
            if ((time - start) > 3_500L) {
                this.wantedVisibility = Visibility.HIDE;
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, Font font, long time) {
            drawAchievementFrame(context, title, titleColor, description, COLOR_TEXT, font);
        }

        @Override
        public int width() {
            return WIDTH;
        }

        @Override
        public int height() {
            return HEIGHT;
        }
    }

    public static final class UploadToast implements Toast {
        private final Component title;
        private Component subtitle;
        private float progress;
        private boolean done;
        private boolean success;
        private long start = -1L;
        private Visibility wantedVisibility = Visibility.SHOW;

        private UploadToast(Component title, Component subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        public void update(float progress, String subtitle) {
            this.progress = Math.max(0.0F, Math.min(1.0F, progress));
            this.subtitle = Component.literal(subtitle);
        }

        public void complete(boolean success, String subtitle) {
            this.success = success;
            this.done = true;
            this.subtitle = Component.literal(subtitle);
        }

        @Override
        public Visibility getWantedVisibility() {
            return this.wantedVisibility;
        }

        @Override
        public void update(ToastManager manager, long time) {
            if (start < 0L) {
                start = time;
            }
            if (done && (time - start) > 2_200L) {
                this.wantedVisibility = Visibility.HIDE;
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, Font font, long time) {
            int titleColor = done ? (success ? COLOR_TITLE : COLOR_TITLE_ERROR) : COLOR_TITLE;
            drawAchievementFrame(context, title, titleColor, subtitle, COLOR_TEXT_DIM, font);

            int barX = 8;
            int barY = 28;
            int barWidth = 144;
            context.fill(barX, barY, barX + barWidth, barY + 2, 0xFF444444);
            context.fill(barX, barY, barX + (int) (barWidth * progress), barY + 2, success ? 0xFF55FF55 : 0xFF55AAFF);
        }

        @Override
        public int width() {
            return WIDTH;
        }

        @Override
        public int height() {
            return HEIGHT;
        }
    }

    public static final class ConnectionToast implements Toast {
        private final Component title;
        private final Component checkingMessage;
        private Component resultMessage;
        private boolean ok;
        private boolean done;
        private boolean soundPlayed;
        private long start = -1L;
        private Visibility wantedVisibility = Visibility.SHOW;

        private ConnectionToast(Component title, Component checkingMessage) {
            this.title = title;
            this.checkingMessage = checkingMessage;
        }

        public void complete(boolean ok, String message) {
            this.ok = ok;
            this.resultMessage = Component.literal(message);
            this.done = true;
            this.soundPlayed = false;
        }

        @Override
        public Visibility getWantedVisibility() {
            return this.wantedVisibility;
        }

        @Override
        public void update(ToastManager manager, long time) {
            if (start < 0L) {
                start = time;
            }
            long elapsed = time - start;
            boolean showResult = done && elapsed >= 800L;
            if (showResult && !soundPlayed) {
                soundPlayed = true;
                ModSounds.play(ok ? ModSounds.UI_COMPLETE : ModSounds.UI_ERROR);
            }
            if (showResult && elapsed > 2_800L) {
                this.wantedVisibility = Visibility.HIDE;
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, Font font, long time) {
            long elapsed = time - start;
            boolean showResult = done && elapsed >= 800L;
            Component line = showResult
                    ? (resultMessage == null ? Component.literal(ok ? "OK" : "ERROR") : resultMessage)
                    : checkingMessage;
            int titleColor = showResult ? (ok ? COLOR_TITLE : COLOR_TITLE_ERROR) : COLOR_TITLE;
            int descriptionColor = showResult ? (ok ? 0xFF88FF88 : 0xFFFF7D7D) : COLOR_TEXT_DIM;
            drawAchievementFrame(context, title, titleColor, line, descriptionColor, font);
        }

        @Override
        public int width() {
            return WIDTH;
        }

        @Override
        public int height() {
            return HEIGHT;
        }
    }
}
