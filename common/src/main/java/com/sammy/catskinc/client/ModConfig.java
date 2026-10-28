package com.sammy.catskinc.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;

    // Default configuration values
    // GLOBAL resolves the host from servers.json; CUSTOM uses catskinCloudIp as typed by the user.
    // Left null (not GLOBAL) so configs written before this field existed can be detected and migrated.
    private ServerMode serverMode = null;
    private String catskinCloudIp = "";
    private boolean showConnectionToast = true;
    private boolean showUploadToast = true;
    private boolean showInfoToast = true;
    private boolean showErrorToast = true;
    private String requestSigningKey = null;
    private int maxSkinResolution = 8192; // Max allowed skin resolution (64-8192)
    // How other players' HD skins are stored on the GPU. LOSSLESS keeps the original RGBA behaviour.
    private TextureQuality textureQuality = TextureQuality.LOSSLESS;
    // AUTO only: how many of the nearest players may use the lossless original at once.
    private int maxLosslessPlayers = MAX_LOSSLESS_PLAYERS_DEFAULT;

    // Connection mode: AUTO (WS with SSE fallback), WEBSOCKET (force WS), SSE (force SSE)
    private ConnectionMode connectionMode = ConnectionMode.AUTO;

    // Myopia (distance-based skin LOD). Off by default so existing users keep full-res skins.
    private boolean myopiaEnabled = false;
    private int myopiaDistance = MYOPIA_DISTANCE_DEFAULT;
    private MyopiaMode myopiaMode = MyopiaMode.NORMAL;
    private boolean debugTestServerEnabled = false;
    private boolean debugForceMyopia = false;

    public static final int MYOPIA_DISTANCE_MIN = 4;
    public static final int MYOPIA_DISTANCE_MAX = 128;
    public static final int MYOPIA_DISTANCE_DEFAULT = 20;
    public static final int MYOPIA_DISTANCE_STEP = 5;

    public static final int MAX_LOSSLESS_PLAYERS_MIN = 1;
    public static final int MAX_LOSSLESS_PLAYERS_MAX = 16;
    public static final int MAX_LOSSLESS_PLAYERS_DEFAULT = 4;

    public enum ServerMode {
        GLOBAL,     // Host published in servers.json (falls back to GlobalServerResolver.FALLBACK_HOST)
        CUSTOM      // User-provided host in catskinCloudIp
    }

    /** Hosts shipped as defaults by older versions; configs still pointing at them move to GLOBAL. */
    private static final java.util.Set<String> LEGACY_DEFAULT_HOSTS = java.util.Set.of(
            "storage-api.catskin.space",
            GlobalServerResolver.FALLBACK_HOST);

    public enum ConnectionMode {
        AUTO,       // Try WebSocket first, fallback to SSE on failure
        WEBSOCKET,  // Force WebSocket (protocol v3), no fallback
        SSE         // Force SSE (protocol v2) only
    }

    public enum MyopiaMode {
        NORMAL,     // full / half / quarter / eighth resolution at 1x / 2x / 4x base distance
        PANICKED    // half / quarter / eighth resolution at 1x / 2x base distance
    }

    public enum TextureQuality {
        LOSSLESS(null, 0),                     // RGBA8, resolution limited only by maxSkinResolution
        AUTO(DxtEncoder.Format.BC3, 2048),     // lossless when near, then DXT5 / DXT5 / DXT1 by Myopia level
        HIGH(DxtEncoder.Format.BC3, 4096),     // DXT5, 4x less VRAM, capped at 4096px
        BALANCED(DxtEncoder.Format.BC3, 2048), // DXT5, capped at 2048px
        FASTEST(DxtEncoder.Format.BC1, 1024);  // DXT1, 8x less VRAM, 1-bit alpha, capped at 1024px

        private final DxtEncoder.Format format;
        private final int maxResolution;

        TextureQuality(DxtEncoder.Format format, int maxResolution) {
            this.format = format;
            this.maxResolution = maxResolution;
        }

        /** Compression format, or null for uncompressed RGBA. */
        public DxtEncoder.Format format() {
            return format;
        }

        /** Format used for one Myopia level. AUTO drops to DXT1 for the farthest level. */
        public DxtEncoder.Format formatFor(MyopiaLod.Level level) {
            if (this == AUTO && level == MyopiaLod.Level.L3) {
                return DxtEncoder.Format.BC1;
            }
            return format;
        }

        /** True when nearby players render the lossless original (decoded on demand). */
        public boolean losslessWhenNear() {
            return this == AUTO;
        }

        /** Resolution cap for the compressed levels, combined with the user's maxSkinResolution. */
        public int effectiveMaxResolution(int userMax) {
            return maxResolution == 0 ? userMax : Math.min(userMax, maxResolution);
        }

        public TextureQuality next() {
            TextureQuality[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private static ModConfig instance = new ModConfig();

    private ModConfig() {}

    public static ModConfig get() {
        return instance;
    }

    public static void init(File gameDir) {
        configFile = new File(gameDir, "config/catskinc.json");
        load();
    }

    public static void load() {
        if (configFile == null || !configFile.exists()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(configFile)) {
            ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
            if (loaded != null) {
                instance = loaded;
                if (instance.migrateServerMode()) {
                    save();
                }
            }
        } catch (Exception e) {
            ModLog.error("Failed to load config, using defaults", e);
        }
    }

    /**
     * Configs written before server modes existed only have catskinCloudIp. Old default hosts become
     * GLOBAL; anything else the user typed is kept as CUSTOM. Returns true when the config changed.
     */
    boolean migrateServerMode() {
        if (serverMode != null) {
            return false;
        }
        if (isLegacyDefaultHost(catskinCloudIp)) {
            ModLog.info("Migrating CatSkinCloud server '{}' to Global", catskinCloudIp);
            serverMode = ServerMode.GLOBAL;
            catskinCloudIp = "";
        } else {
            ModLog.info("Keeping CatSkinCloud server '{}' as Custom", catskinCloudIp);
            serverMode = ServerMode.CUSTOM;
        }
        return true;
    }

    static boolean isLegacyDefaultHost(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String host = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (host.startsWith("https://")) {
            host = host.substring("https://".length());
        } else if (host.startsWith("http://")) {
            host = host.substring("http://".length());
        }
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        return LEGACY_DEFAULT_HOSTS.contains(host);
    }

    public static void save() {
        if (configFile == null) return;
        try {
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(instance, writer);
            }
        } catch (Exception e) {
            ModLog.error("Failed to save config", e);
        }
    }

    // Getters & Setters
    /** The host requests should go to, based on the selected server mode. */
    public String getCatskinCloudIp() {
        if (getServerMode() == ServerMode.CUSTOM && catskinCloudIp != null && !catskinCloudIp.isBlank()) {
            return catskinCloudIp;
        }
        return GlobalServerResolver.currentHost();
    }

    public ServerMode getServerMode() {
        return serverMode == null ? ServerMode.GLOBAL : serverMode;
    }

    public void setServerMode(ServerMode serverMode) {
        this.serverMode = serverMode;
    }

    /** The user-typed custom host (only used when the server mode is CUSTOM). */
    public String getCustomServerIp() {
        return catskinCloudIp == null ? "" : catskinCloudIp;
    }

    public void setCustomServerIp(String customServerIp) {
        this.catskinCloudIp = customServerIp;
    }

    public boolean isShowConnectionToast() {
        return showConnectionToast;
    }

    public void setShowConnectionToast(boolean showConnectionToast) {
        this.showConnectionToast = showConnectionToast;
    }

    public boolean isShowUploadToast() {
        return showUploadToast;
    }

    public void setShowUploadToast(boolean showUploadToast) {
        this.showUploadToast = showUploadToast;
    }

    public boolean isShowInfoToast() {
        return showInfoToast;
    }

    public void setShowInfoToast(boolean showInfoToast) {
        this.showInfoToast = showInfoToast;
    }

    public boolean isShowErrorToast() {
        return showErrorToast;
    }

    public void setShowErrorToast(boolean showErrorToast) {
        this.showErrorToast = showErrorToast;
    }

    public String getRequestSigningKey() {
        return requestSigningKey;
    }

    public void setRequestSigningKey(String requestSigningKey) {
        this.requestSigningKey = requestSigningKey;
    }

    public ConnectionMode getConnectionMode() {
        return connectionMode;
    }

    public void setConnectionMode(ConnectionMode connectionMode) {
        this.connectionMode = connectionMode;
    }

    public int getMaxSkinResolution() {
        return Math.max(64, Math.min(maxSkinResolution, 8192));
    }

    public void setMaxSkinResolution(int maxSkinResolution) {
        this.maxSkinResolution = Math.max(64, Math.min(maxSkinResolution, 8192));
    }

    public TextureQuality getTextureQuality() {
        return textureQuality == null ? TextureQuality.LOSSLESS : textureQuality;
    }

    public void setTextureQuality(TextureQuality textureQuality) {
        this.textureQuality = textureQuality == null ? TextureQuality.LOSSLESS : textureQuality;
    }

    public int getMaxLosslessPlayers() {
        return clampMaxLosslessPlayers(maxLosslessPlayers);
    }

    public void setMaxLosslessPlayers(int maxLosslessPlayers) {
        this.maxLosslessPlayers = clampMaxLosslessPlayers(maxLosslessPlayers);
    }

    public static int clampMaxLosslessPlayers(int value) {
        return Math.max(MAX_LOSSLESS_PLAYERS_MIN, Math.min(MAX_LOSSLESS_PLAYERS_MAX, value));
    }

    public boolean isMyopiaEnabled() {
        return myopiaEnabled;
    }

    public void setMyopiaEnabled(boolean myopiaEnabled) {
        this.myopiaEnabled = myopiaEnabled;
    }

    public int getMyopiaDistance() {
        return clampMyopiaDistance(myopiaDistance);
    }

    public void setMyopiaDistance(int myopiaDistance) {
        this.myopiaDistance = clampMyopiaDistance(myopiaDistance);
    }

    public MyopiaMode getMyopiaMode() {
        return myopiaMode == null ? MyopiaMode.NORMAL : myopiaMode;
    }

    public void setMyopiaMode(MyopiaMode myopiaMode) {
        this.myopiaMode = myopiaMode == null ? MyopiaMode.NORMAL : myopiaMode;
    }

    public static int clampMyopiaDistance(int value) {
        return Math.max(MYOPIA_DISTANCE_MIN, Math.min(MYOPIA_DISTANCE_MAX, value));
    }

    public boolean isDebugTestServerEnabled() { return debugTestServerEnabled; }

    public void setDebugTestServerEnabled(boolean enabled) { this.debugTestServerEnabled = enabled; }

    public boolean isDebugForceMyopia() { return debugForceMyopia; }

    public void setDebugForceMyopia(boolean enabled) { this.debugForceMyopia = enabled; }
}
