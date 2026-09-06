package com.sammy.catskinc.client;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.platform.Platform;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import java.io.File;
import java.lang.management.ManagementFactory;

public final class CatskincClient {
    private static final int DEFAULT_OPEN_UI_KEY = 75;            // GLFW_KEY_K
    private static final int DEFAULT_MYOPIA_INCREASE_KEY = 265;   // GLFW_KEY_UP
    private static final int DEFAULT_MYOPIA_DECREASE_KEY = 264;   // GLFW_KEY_DOWN
    public static final String KEY_CATEGORY = "key.categories.catskinc";
    private static final long DEFAULT_REFRESH_INTERVAL_MS = 15_000L;
    private static final int DEFAULT_ENSURE_INTERVAL_TICKS = 20;
    private static final int DEFAULT_ENSURE_LIMIT_PER_PASS = 16;
    private static final int DEFAULT_VOICE_AMPLITUDE_THRESHOLD = 180;
    private static final long DEFAULT_VOICE_HOLD_MS = 420L;

    private static KeyBinding openUiKey;
    private static KeyBinding myopiaIncreaseKey;
    private static KeyBinding myopiaDecreaseKey;
    private static int tickCounter;
    private static boolean initialized;

    private CatskincClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            ModLog.trace("Client init skipped: already initialized");
            return;
        }
        initialized = true;
        System.setProperty("http.maxConnections", "50");
        ModLog.info("Initializing catskinc client");

        File gameDir = null;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                gameDir = mc.runDirectory;
            }
        } catch (Throwable ignored) {}
        ModConfig.init(gameDir);

        applyConfig();
        VoiceStateNetworkClient.init();
        VoiceIntegrationBootstrap.init();

        openUiKey = new KeyBinding(
                "key.catskinc.open_ui",
                InputUtil.Type.KEYSYM,
                DEFAULT_OPEN_UI_KEY,
                KEY_CATEGORY);
        KeyMappingRegistry.register(openUiKey);
        ModLog.debug("Registered keybinding with keycode={}", DEFAULT_OPEN_UI_KEY);

        myopiaIncreaseKey = new KeyBinding(
                "key.catskinc.myopia_increase",
                InputUtil.Type.KEYSYM,
                DEFAULT_MYOPIA_INCREASE_KEY,
                KEY_CATEGORY);
        KeyMappingRegistry.register(myopiaIncreaseKey);
        myopiaDecreaseKey = new KeyBinding(
                "key.catskinc.myopia_decrease",
                InputUtil.Type.KEYSYM,
                DEFAULT_MYOPIA_DECREASE_KEY,
                KEY_CATEGORY);
        KeyMappingRegistry.register(myopiaDecreaseKey);
        ModLog.debug("Registered Myopia range keybindings (increase={}, decrease={})",
                DEFAULT_MYOPIA_INCREASE_KEY, DEFAULT_MYOPIA_DECREASE_KEY);

        ClientTickEvent.CLIENT_POST.register(client -> {
            while (openUiKey.wasPressed()) {
                ModLog.trace("Open UI key pressed");
                openUploadScreen();
            }
            while (myopiaIncreaseKey.wasPressed()) {
                adjustMyopiaDistance(ModConfig.MYOPIA_DISTANCE_STEP);
            }
            while (myopiaDecreaseKey.wasPressed()) {
                adjustMyopiaDistance(-ModConfig.MYOPIA_DISTANCE_STEP);
            }

            if (client.world == null) {
                tickCounter = 0;
                VoiceActivityTracker.tick();
                VoiceIntegrationBootstrap.tick();
                return;
            }

            VoiceActivityTracker.tick();
            VoiceIntegrationBootstrap.tick();

            tickCounter++;
            if ((tickCounter % DEFAULT_ENSURE_INTERVAL_TICKS) != 0) {
                return;
            }

            int count = 0;
            for (var player : client.world.getPlayers()) {
                if (player == null) {
                    continue;
                }
                SkinManagerClient.ensureFetch(player.getUuid());
                count++;
                if (count >= DEFAULT_ENSURE_LIMIT_PER_PASS) {
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
                    ServerApiClient.stopWebSocket();
                    // Clear session token for local player on disconnect.
                    if (player != null) {
                        ServerApiClient.clearSessionToken(player.getUuid());
                    }
                    VoiceIntegrationBootstrap.shutdown();
                });
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
                    // Clear session token for local player on disconnect.
                    if (player != null) {
                        ServerApiClient.clearSessionToken(player.getUuid());
                    }
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

    public static KeyBinding getOpenUiKey() {
        return openUiKey;
    }

    public static KeyBinding getMyopiaIncreaseKey() {
        return myopiaIncreaseKey;
    }

    public static KeyBinding getMyopiaDecreaseKey() {
        return myopiaDecreaseKey;
    }

    /**
     * Shifts the Myopia base distance by {@code delta} blocks (clamped), persists it and
     * re-evaluates LOD levels. Bound to the Arrow Up / Arrow Down keys by default.
     */
    static void adjustMyopiaDistance(int delta) {
        ModConfig config = ModConfig.get();
        int before = config.getMyopiaDistance();
        int after = ModConfig.clampMyopiaDistance(before + delta);
        if (after == before) {
            ModLog.trace("Myopia distance unchanged at {} (bounds reached)", before);
            return;
        }
        config.setMyopiaDistance(after);
        ModConfig.save();
        MyopiaRenderHook.onSettingsApplied();
        ModLog.debug("Myopia distance {} -> {}", before, after);
        Toasts.info(
                Text.translatable("toast.catskinc.myopia.title"),
                Text.translatable("toast.catskinc.myopia.distance", after));
    }

    public static void applyConfig() {
        boolean devDiagnostics = isDevelopmentMode();
        ModLog.configure(devDiagnostics, devDiagnostics);
        if (devDiagnostics) {
            ModLog.debug("Dev diagnostics enabled (debugger/flag detected)");
        }
        SkinManagerClient.setRefreshIntervalMs(5 * 1000L);
        VoiceActivityTracker.configure(180, 420);
    }

    public static boolean isDevelopmentMode() {
        try {
            if (Platform.isDevelopmentEnvironment()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
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

    /** Debug preview tools are strictly limited to development environments. */
    public static boolean isDebugPreviewEnabled() {
        return isDevelopmentMode();
    }

    private static void handleJoin(MinecraftClient client) {
        try {
            VoiceIntegrationBootstrap.init();
            ModLog.debug("Handling join flow: start WebSocket (v3) with SSE fallback + initial sync");

            // Pre-acquire session token for the local player so upload/select
            // does not have to wait for the auth round-trip when the UI opens.
            if (client.player != null) {
                java.util.UUID localUuid = client.player.getUuid();
                ServerApiClient.acquireSessionTokenAsync(localUuid).thenAccept(token -> {
                    if (token == null) {
                        ModLog.warn("Session token acquisition failed on join for {}", localUuid);
                    } else {
                        ModLog.debug("Session token pre-acquired on join for {}", localUuid);
                    }
                });
            }

            // Try WebSocket first (protocol v3), fall back to SSE on failure
            ServerApiClient.startWebSocket(event -> {
                if (event == null || event.uuid == null) {
                    ModLog.trace("Skipping empty WS event");
                    return;
                }
                client.execute(() -> {
                    if (event.slim != null) {
                        SkinManagerClient.setSlim(event.uuid, event.slim);
                    }
                    // Detect clear events: no URL/id means skin was removed on server
                    boolean isClearEvent = event.url == null || event.url.isBlank() || event.id == null || event.id.isBlank();
                    if (isClearEvent) {
                        SkinManagerClient.clearTexture(event.uuid);
                    } else {
                        SkinManagerClient.forceFetch(event.uuid);
                    }
                });
            });

            if (client.player != null) {
                SkinManagerClient.fetchAndApplyFor(client.player.getUuid());
            }

            if (ModConfig.get().isShowConnectionToast()) {
                Toasts.ConnectionToast toast = Toasts.connection(
                        Text.translatable("title.skin_cloud"),
                        Text.translatable("toast.cloud.checking"));
                ServerApiClient.pingAsyncOk().thenAccept(ok -> client.execute(() -> {
                    if (toast != null) {
                        toast.complete(Boolean.TRUE.equals(ok),
                                Text.translatable(Boolean.TRUE.equals(ok)
                                        ? "toast.cloud.connected"
                                        : "toast.cloud.failed").getString());
                    }
                }));
            }

            ModrinthVersionChecker.checkForUpdatesAsync().thenAccept(result -> {
                if (!ModrinthVersionChecker.tryMarkNotified(result)) {
                    return;
                }
                client.execute(() -> Toasts.info(
                        Text.translatable("toast.update.available.title"),
                        Text.translatable("toast.update.available.message", result.latestVersion())));
                // New version found → resolve the exact download for this platform.
                ModrinthVersionChecker.resolveDownloadAsync(result).thenAccept(download -> client.execute(() -> {
                    if (download.hasDirectFile()) {
                        ModLog.info("Update {} download for this platform: {} ({})",
                                result.latestVersion(), download.fileName(), download.downloadUrl());
                    } else {
                        ModLog.info("Update {} available on Modrinth: {}",
                                result.latestVersion(), download.bestUrl());
                    }
                }));
            });
        } catch (Exception exception) {
            ModLog.error("Join flow failed", exception);
        }
    }
}
