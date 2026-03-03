package com.sammy.catskincRemake.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class Toasts {
    private static final Identifier TOAST_TEXTURE = Identifiers.parse("minecraft:textures/gui/toasts.png");

    private Toasts() {
    }

    public static void info(Text title, Text description) {
        MinecraftClient.getInstance().getToastManager().add(new SimpleToast(title, description, 0xFFFFFFFF));
    }

    public static void error(Text title, Text description) {
        MinecraftClient.getInstance().getToastManager().add(new SimpleToast(title, description, 0xFFFF5555));
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

    private static void drawBackground(DrawContext context) {
        context.drawTexture(TOAST_TEXTURE, 0, 0, 0, 0, 160, 32, 256, 256);
    }

    public static final class SimpleToast implements Toast {
        private final Text title;
        private final Text description;
        private final int color;
        private long start = -1L;

        private SimpleToast(Text title, Text description, int color) {
            this.title = title;
            this.description = description;
            this.color = color;
        }

        @Override
        public Visibility draw(DrawContext context, ToastManager manager, long time) {
            if (start < 0L) {
                start = time;
            }
            drawBackground(context);
            var renderer = MinecraftClient.getInstance().textRenderer;
            context.drawText(renderer, title, 8, 7, color, false);
            if (description != null) {
                context.drawText(renderer, description, 8, 18, 0xFFDDDDDD, false);
            }
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
            drawBackground(context);
            var renderer = MinecraftClient.getInstance().textRenderer;
            context.drawText(renderer, title, 8, 7, 0xFFFFFFFF, false);
            if (subtitle != null) {
                context.drawText(renderer, subtitle, 8, 18, 0xFFDDDDDD, false);
            }

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
            drawBackground(context);
            var renderer = MinecraftClient.getInstance().textRenderer;
            context.drawText(renderer, title, 8, 7, 0xFFFFFFFF, false);

            long elapsed = time - start;
            boolean showResult = done && elapsed >= 800L;
            Text line = showResult
                    ? (resultMessage == null ? Text.literal(ok ? "OK" : "ERROR") : resultMessage)
                    : checkingMessage;
            int color = showResult ? (ok ? 0xFF55FF55 : 0xFFFF5555) : 0xFFDDDDDD;
            context.drawText(renderer, line, 8, 18, color, false);

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

