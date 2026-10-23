package com.sammy.catskinc.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/** Resolves the "Global" CatSkinCloud server from the published servers.json, with a built-in fallback. */
public final class GlobalServerResolver {
    public static final String FALLBACK_HOST = "storage-skin.catlabdesign.space";
    private static final String SERVERS_JSON_URL =
            "https://raw.githubusercontent.com/catlab-design/CatSkinC-Data/refs/heads/main/servers.json";
    private static final int TIMEOUT_MS = 5_000;
    private static final int MAX_BYTES = 16 * 1024;

    private static volatile String resolvedHost;
    private static volatile CompletableFuture<String> inFlight;

    private GlobalServerResolver() {
    }

    /** The global host to use right now: the last fetched value, or the fallback. */
    public static String currentHost() {
        String host = resolvedHost;
        return host != null ? host : FALLBACK_HOST;
    }

    /** Fetches servers.json in the background; concurrent callers share one request. */
    public static synchronized CompletableFuture<String> refreshAsync() {
        CompletableFuture<String> running = inFlight;
        if (running != null && !running.isDone()) {
            return running;
        }
        CompletableFuture<String> created = CompletableFuture.supplyAsync(GlobalServerResolver::fetch);
        inFlight = created;
        return created;
    }

    private static String fetch() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(SERVERS_JSON_URL).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code / 100 != 2) {
                ModLog.warn("servers.json returned HTTP {}; using {}", code, currentHost());
                return currentHost();
            }
            String body;
            try (InputStream in = connection.getInputStream()) {
                body = new String(in.readNBytes(MAX_BYTES), StandardCharsets.UTF_8);
            }
            String host = parseGlobalHost(body);
            if (host == null) {
                ModLog.warn("servers.json has no usable globalIp; using {}", currentHost());
                return currentHost();
            }
            resolvedHost = host;
            ModLog.debug("Global CatSkinCloud server resolved to {}", host);
            return host;
        } catch (Exception e) {
            ModLog.warn("Failed to fetch servers.json ({}); using {}", e.toString(), currentHost());
            return currentHost();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Returns the trimmed {@code globalIp} value, or null when the JSON is missing or unusable. */
    static String parseGlobalHost(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject object = root.getAsJsonObject();
            JsonElement value = object.get("globalIp");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                return null;
            }
            String host = value.getAsString().trim();
            if (host.isEmpty() || host.length() > 255 || host.chars().anyMatch(Character::isWhitespace)) {
                return null;
            }
            return host;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static void resetForTesting() {
        resolvedHost = null;
        inFlight = null;
    }
}
