package com.sammy.catskincRemake.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModrinthVersionCheckerTest {
    @Test
    void parseResponseReadsServerVersionCheckPayload() {
        String response = """
                {
                  "project": "catskinc",
                  "current": "2.0.0",
                  "latest": "2.0.2",
                  "latestName": "CatSkinC 2.0.2",
                  "updateAvailable": true,
                  "url": "https://modrinth.com/mod/catskinc/version/demo"
                }
                """;

        ModrinthVersionChecker.UpdateCheckResult result = ModrinthVersionChecker.parseResponse(response, "2.0.0");

        assertTrue(result.updateAvailable());
        assertEquals("2.0.0", result.currentVersion());
        assertEquals("2.0.2", result.latestVersion());
        assertEquals("CatSkinC 2.0.2", result.latestName());
    }

    @Test
    void parseResponsePrefersNewestListedRelease() {
        String response = """
                [
                  {
                    "name": "CatSkinC 2.0.0",
                    "version_number": "2.0.0",
                    "version_type": "release",
                    "status": "listed",
                    "date_published": "2026-03-01T00:00:00Z"
                  },
                  {
                    "name": "CatSkinC 2.1.0 Beta",
                    "version_number": "2.1.0-beta.1",
                    "version_type": "beta",
                    "status": "listed",
                    "date_published": "2026-03-03T00:00:00Z"
                  },
                  {
                    "name": "CatSkinC 2.0.2",
                    "version_number": "2.0.2",
                    "version_type": "release",
                    "status": "listed",
                    "date_published": "2026-03-02T00:00:00Z"
                  }
                ]
                """;

        ModrinthVersionChecker.UpdateCheckResult result = ModrinthVersionChecker.parseResponse(response, "2.0.0");

        assertTrue(result.updateAvailable());
        assertEquals("2.0.0", result.currentVersion());
        assertEquals("2.0.2", result.latestVersion());
        assertEquals("CatSkinC 2.0.2", result.latestName());
    }

    @Test
    void parseResponseReturnsNoUpdateWhenCurrentVersionMatchesLatestRelease() {
        String response = """
                [
                  {
                    "name": "CatSkinC 2.0.0",
                    "version_number": "2.0.0",
                    "version_type": "release",
                    "status": "listed",
                    "date_published": "2026-03-01T00:00:00Z"
                  },
                  {
                    "name": "CatSkinC 1.9.9",
                    "version_number": "1.9.9",
                    "version_type": "release",
                    "status": "listed",
                    "date_published": "2026-02-20T00:00:00Z"
                  }
                ]
                """;

        ModrinthVersionChecker.UpdateCheckResult result = ModrinthVersionChecker.parseResponse(response, "2.0.0");

        assertFalse(result.updateAvailable());
        assertEquals("2.0.0", result.latestVersion());
    }

    @Test
    void compareVersionsHandlesNumericAndPrereleaseSegments() {
        assertTrue(ModrinthVersionChecker.compareVersions("2.10.0", "2.9.9") > 0);
        assertTrue(ModrinthVersionChecker.compareVersions("2.0.0", "2.0.0-beta.1") > 0);
        assertEquals(0, ModrinthVersionChecker.compareVersions("v2.0.0+build.5", "2.0.0"));
    }

    @Test
    void buildRequestPathTargetsServerVersionEndpoint() {
        assertEquals(
                "/version/check?current=2.0.0&minecraft=1.20.1&loader=fabric",
                ModrinthVersionChecker.buildRequestPath("2.0.0", "1.20.1", "fabric"));
    }
}
