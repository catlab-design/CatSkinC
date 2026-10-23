package com.sammy.catskinc.client;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModConfigServerModeTest {
    private static final Gson GSON = new Gson();

    private static ModConfig parse(String json) {
        return GSON.fromJson(json, ModConfig.class);
    }

    @Test
    void oldDeadDefaultHostMigratesToGlobal() {
        ModConfig config = parse("{\"catskinCloudIp\": \"storage-api.catskin.space\"}");
        assertTrue(config.migrateServerMode());
        assertEquals(ModConfig.ServerMode.GLOBAL, config.getServerMode());
        assertEquals(GlobalServerResolver.currentHost(), config.getCatskinCloudIp());
    }

    @Test
    void oldDefaultWithSchemeAlsoMigrates() {
        ModConfig config = parse("{\"catskinCloudIp\": \"https://storage-api.catskin.space/\"}");
        assertTrue(config.migrateServerMode());
        assertEquals(ModConfig.ServerMode.GLOBAL, config.getServerMode());
    }

    @Test
    void userCustomHostIsKeptAsCustom() {
        ModConfig config = parse("{\"catskinCloudIp\": \"skins.example.org:8080\"}");
        assertTrue(config.migrateServerMode());
        assertEquals(ModConfig.ServerMode.CUSTOM, config.getServerMode());
        assertEquals("skins.example.org:8080", config.getCatskinCloudIp());
    }

    @Test
    void explicitModeIsNotTouched() {
        ModConfig config = parse("{\"serverMode\": \"CUSTOM\", \"catskinCloudIp\": \"storage-api.catskin.space\"}");
        assertFalse(config.migrateServerMode());
        assertEquals(ModConfig.ServerMode.CUSTOM, config.getServerMode());
        assertEquals("storage-api.catskin.space", config.getCatskinCloudIp());
    }

    @Test
    void blankCustomHostFallsBackToGlobalHost() {
        ModConfig config = parse("{\"serverMode\": \"CUSTOM\", \"catskinCloudIp\": \"  \"}");
        assertEquals(GlobalServerResolver.currentHost(), config.getCatskinCloudIp());
    }
}
