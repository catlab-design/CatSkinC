package com.sammy.catskincRemake.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;

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

    public static void info(Text title, Text description) {
        MinecraftClient.getInstance().getToastManager().add(new SimpleToast(title, description, COLOR_TITLE));
    }

    public static void error(Text title, Text description) {
        MinecraftClient.getInstance().getToastManager().add(new SimpleToast(title, description, COLOR_TITLE_ERROR));
    }

    public static UploadToast showUpload(Text title, Text subtitle) {
        UploadToast toast = new UploadToast(title, subtitle);
        MinecraftClient.getInstance().getToastManager().add(toast);
        return toast;
    }

    public static ConnectionToast connection(Text title, Text checkingMessage) {
        ConnectionToast toast = new ConnectionToast(title, checkingMessage);
        MinecraftClient.getInstance().getToastManager().add(toast);
        return toast;
    }

    private static void drawAchievementBackground(DrawContext context) {
        context.fill(0, 0, WIDTH, HEIGHT, COLOR_BORDER);
        context.fill(1, 1, WIDTH - 1, HEIGHT - 1, COLOR_BG);
        context.fill(2, 2, WIDTH - 2, HEIGHT - 2, COLOR_BG_INNER);
        context.fill(2, 2, WIDTH - 2, 12, COLOR_BG_TOP);
    }

    private static void drawAchievementFrame(DrawContext context, Text title, int titleColor, Text description, int descriptionColor) {
        drawAchievementBackground(context);
        var renderer = MinecraftClient.getInstance().textRenderer;
        context.drawText(renderer, title, 8, 5, titleColor, false);
        if (description != null) {
            context.drawText(renderer, description, 8, 18, descriptionColor, false);
        }
    }

    public static final class SimpleToast implements Toast {
        private final Text title;
        private final Text description;
        private final int titleColor;
        private long start = -1L;

        private SimpleToast(Text title, Text description, int titleColor) {
            this.title = title;
            this.description = description;
            this.titleColor = titleColor;
        }

        @Override
        public Visibility draw(DrawContext context, ToastManager manager, long time) {
            if (start < 0L) {
                start = time;
            }
            drawAchievementFrame(context, title, titleColor, description, COLOR_TEXT);
            return (time - start) > 3_500L ? Visibility.HIDE : Visibility.SHOW;
        }
    }

    public static final class UploadToast implements Toast {
        private final Text title;
        private Text subtitle;
        private float progress;
        private boolean done;
        private boolean success;
        private long start = -1L;

        private UploadToast(Text title, Text subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        public void update(float progress, String subtitle) {
            this.progress = Math.max(0.0F, Math.min(1.0F, progress));
            this.subtitle = Text.literal(subtitle);
        }

        public void complete(boolean success, String subtitle) {
            this.success = success;
            this.done = true;
            this.subtitle = Text.literal(subtitle);
        }

        @Override
        public Visibility draw(DrawContext context, ToastManager manager, long time) {
            if (start < 0L) {
                start = time;
            }
            int titleColor = done ? (success ? COLOR_TITLE : COLOR_TITLE_ERROR) : COLOR_TITLE;
            drawAchievementFrame(context, title, titleColor, subtitle, COLOR_TEXT_DIM);

            int barX = 8;
            int barY = 28;
            int barWidth = 144;
            context.fill(barX, barY, barX + barWidth, barY + 2, 0xFF444444);
            context.fill(barX, barY, barX + (int) (barWidth * progress), barY + 2, success ? 0xFF55FF55 : 0xFF55AAFF);

            if (done) {
                return (time - start) > 2_200L ? Visibility.HIDE : Visibility.SHOW;
            }
            return Visibility.SHOW;
        }
    }

    public static final class ConnectionToast implements Toast {
        private final Text title;
        private final Text checkingMessage;
        private Text resultMessage;
        private boolean ok;
        private boolean done;
        private boolean soundPlayed;
        private long start = -1L;

        private ConnectionToast(Text title, Text checkingMessage) {
            this.title = title;
            this.checkingMessage = checkingMessage;
        }

        public void complete(boolean ok, String message) {
            this.ok = ok;
            this.resultMessage = Text.literal(message);
            this.done = true;
            this.soundPlayed = false;
        }

        @Override
        public Visibility draw(DrawContext context, ToastManager manager, long time) {
            if (start < 0L) {
                start = time;
            }
            long elapsed = time - start;
            boolean showResult = done && elapsed >= 800L;
            Text line = showResult
                    ? (resultMessage == null ? Text.literal(ok ? "OK" : "ERROR") : resultMessage)
                    : checkingMessage;
            int titleColor = showResult ? (ok ? COLOR_TITLE : COLOR_TITLE_ERROR) : COLOR_TITLE;
            int descriptionColor = showResult ? (ok ? 0xFF88FF88 : 0xFFFF7D7D) : COLOR_TEXT_DIM;
            drawAchievementFrame(context, title, titleColor, line, descriptionColor);

            if (showResult && !soundPlayed) {
                soundPlayed = true;
                ModSounds.play(ok ? ModSounds.UI_COMPLETE : ModSounds.UI_ERROR);
            }
            if (showResult && elapsed > 2_800L) {
                return Visibility.HIDE;
            }
            return Visibility.SHOW;
        }
    }
}

