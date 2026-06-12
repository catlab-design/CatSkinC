package com.sammy.catskincRemake.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;

    // Default configuration values
    private String catskinCloudIp = "storage-api.catskin.space";
    private boolean showConnectionToast = true;
    private boolean showUploadToast = true;
    private boolean showInfoToast = true;
    private boolean showErrorToast = true;

    private static ModConfig instance = new ModConfig();

    private ModConfig() {}

    public static ModConfig get() {
        return instance;
    }

    public static void init(File gameDir) {
        configFile = new File(gameDir, "config/catskinc-remake.json");
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
            }
        } catch (Exception e) {
            ModLog.error("Failed to load config, using defaults", e);
        }
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
    public String getCatskinCloudIp() {
        return catskinCloudIp == null ? "https://storage-api.catskin.space" : catskinCloudIp;
    }

    public void setCatskinCloudIp(String catskinCloudIp) {
        this.catskinCloudIp = catskinCloudIp;
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
}
