package com.sammy.catskincRemake.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "catskinc-remake.json";

    private static ClientConfig config;

    private ConfigManager() {
    }

    public static synchronized void load() {
        Path path = configPath();
        Path absolute = path.toAbsolutePath();
        ModLog.trace("Loading config from {}", absolute);
        ClientConfig loaded = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, ClientConfig.class);
                ModLog.trace("Config file parsed successfully: {}", absolute);
            } catch (Exception exception) {
                ModLog.warn("Failed to parse config, falling back to defaults: " + absolute, exception);
            }
        }

        if (loaded == null) {
            loaded = new ClientConfig();
            ModLog.debug("Using default client config");
        } else if (loaded.voiceAmplitudeThreshold == 700) {
            // Migrate legacy default to the current baseline for better voice detection.
            loaded.voiceAmplitudeThreshold = 180;
        }
        if (loaded.voiceHoldMs == 260L) {
            loaded.voiceHoldMs = 420L;
        }
        loaded.sanitize();
        config = loaded;
        save();
    }

    public static synchronized ClientConfig get() {
        if (config == null) {
            load();
        }
        return config;
    }

    public static synchronized void save() {
        ClientConfig current = get();
        current.sanitize();
        Path path = configPath();
        Path absolute = path.toAbsolutePath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(current, writer);
            }
            ModLog.trace("Config saved: {}", absolute);
        } catch (IOException exception) {
            ModLog.error("Failed to save config: " + absolute, exception);
        }
    }

    private static Path configPath() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.runDirectory != null) {
            return client.runDirectory.toPath().resolve("config").resolve(FILE_NAME);
        }
        return Path.of("config").resolve(FILE_NAME);
    }
}

