package com.sammy.catskincRemake.client;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import java.lang.management.ManagementFactory;

public final class CatskincRemakeClient {
    private static KeyBinding openUiKey;
    private static int tickCounter;
    private static boolean initialized;

    private CatskincRemakeClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            ModLog.trace("Client init skipped: already initialized");
            return;
        }
        initialized = true;
        ModLog.info("Initializing CatSkinC-Remake client");

        ConfigManager.load();
        applyConfig();
        VoiceStateNetworkClient.init();
        VoiceIntegrationBootstrap.init();

        openUiKey = new KeyBinding(
                "key.catskinc-remake.open_ui",
                InputUtil.Type.KEYSYM,
                ConfigManager.get().openUiKey,
                "key.categories.catskinc-remake");
        KeyMappingRegistry.register(openUiKey);
        ModLog.debug("Registered keybinding with keycode={}", ConfigManager.get().openUiKey);

        ClientTickEvent.CLIENT_POST.register(client -> {
            while (openUiKey.wasPressed()) {
                ModLog.trace("Open UI key pressed");
                openUploadScreen();
            }

            if (client.world == null) {
                tickCounter = 0;
                VoiceActivityTracker.tick();
                VoiceIntegrationBootstrap.tick();
                return;
            }

            VoiceActivityTracker.tick();
            VoiceIntegrationBootstrap.tick();

            ClientConfig config = ConfigManager.get();
            tickCounter++;
            if (config.ensureIntervalTicks <= 0 || (tickCounter % config.ensureIntervalTicks) != 0) {
                return;
            }

            int count = 0;
            for (var player : client.world.getPlayers()) {
                if (player == null) {
                    continue;
                }
                SkinManagerClient.ensureFetch(player.getUuid());
                count++;
                if (count >= config.ensureLimitPerPass) {
                    break;
                }
            }
        });

        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                if (player != null) {
                    ModLog.info("Client join detected: {}", player.getUuid());
                } else {
                    ModLog.info("Client join detected (player unavailable)");
                }
                client.execute(() -> handleJoin(client));
            }
        });

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                if (player != null) {
                    ModLog.info("Client quit detected: {}", player.getUuid());
                } else {
                    ModLog.info("Client quit detected (player unavailable)");
                }
                client.execute(() -> {
                    SkinManagerClient.clearAll();
                    SkinOverrideStore.clearAll();
                    ServerApiClient.stopSse();
                    VoiceIntegrationBootstrap.shutdown();
                });
            }
        });
    }

    public static void openUploadScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            ModLog.trace("Opening skin upload screen");
            client.setScreen(new SkinUploadScreen());
        }
    }

    public static void applyConfig() {
        ClientConfig config = ConfigManager.get();
        boolean devDiagnostics = isDevDiagnosticsDefaultOn();
        boolean debugEnabled = config.debugLogging || devDiagnostics;
        boolean traceEnabled = config.traceLogging || devDiagnostics;
        ModLog.configure(debugEnabled, traceEnabled);
        if (devDiagnostics) {
            ModLog.debug("Dev diagnostics enabled (debugger/flag detected)");
        }
        ModLog.debug(
                "Applying config: refreshIntervalMs={}, ensureIntervalTicks={}, ensureLimitPerPass={}, uiScale={}, voiceThreshold={}, voiceHoldMs={}",
                config.refreshIntervalMs, config.ensureIntervalTicks, config.ensureLimitPerPass, config.uiScale,
                config.voiceAmplitudeThreshold, config.voiceHoldMs);
        SkinManagerClient.setRefreshIntervalMs(config.refreshIntervalMs);
        VoiceActivityTracker.configure(config.voiceAmplitudeThreshold, config.voiceHoldMs);

        if (openUiKey != null) {
            openUiKey.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(config.openUiKey));
            KeyBinding.updateKeysByCode();
            ModLog.trace("Updated keybinding to keycode={}", config.openUiKey);
        }
    }

    private static boolean isDevDiagnosticsDefaultOn() {
        String env = System.getenv("CATSKINC_DEV");
        if ("1".equals(env) || "true".equalsIgnoreCase(env) || Boolean.getBoolean("catskinc.dev")) {
            return true;
        }
        try {
            for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (argument != null && argument.contains("-agentlib:jdwp")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void handleJoin(MinecraftClient client) {
        try {
            VoiceIntegrationBootstrap.init();
            ModLog.debug("Handling join flow: start SSE + initial sync");
            ServerApiClient.startSse(event -> {
                if (event == null || event.uuid == null) {
                    ModLog.trace("Skipping empty SSE event");
                    return;
                }
                client.execute(() -> {
                    if (event.slim != null) {
                        SkinManagerClient.setSlim(event.uuid, event.slim);
                    }
                    SkinManagerClient.forceFetch(event.uuid);
                });
            });

            if (client.player != null) {
                SkinManagerClient.fetchAndApplyFor(client.player.getUuid());
            }

            Toasts.ConnectionToast toast = Toasts.connection(
                    Text.translatable("title.skin_cloud"),
                    Text.translatable("toast.cloud.checking"));
            ServerApiClient.pingAsyncOk().thenAccept(ok -> client.execute(() -> toast.complete(Boolean.TRUE.equals(ok),
                    Text.translatable(Boolean.TRUE.equals(ok)
                            ? "toast.cloud.connected"
                            : "toast.cloud.failed").getString())));
        } catch (Exception exception) {
            ModLog.error("Join flow failed", exception);
        }
    }
}
