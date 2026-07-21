# CatSkinC — Minecraft 1.20.1 Mod

Cloud-based skin upload, skin history, and live skin sync mod for Minecraft 1.20.1.

- **Version:** 3.1.0
- **License:** GPL-3.0-or-later
- **Authors:** Team CatLab Design (originally Q Team Studio)

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17 |
| Build System | Gradle 8.14 (Architectury Loom 1.9-SNAPSHOT) |
| Framework | Architectury (multi-loader) |
| Loaders | Fabric 0.17.2, Forge 47.4.16 (Minecraft 1.20.1) |
| Mappings | Yarn 1.20.1+build.10 |
| Mixin | SpongePowered Mixin 0.8 |
| Testing | JUnit Jupiter 5.10.2 |
| CI/CD | GitHub Actions, GitLab CI, Jenkins |

---

## Project Structure

```
catskinc/
  common/           # 100% of mod logic + resources + mixins
    src/main/java/  # 30 source files: client, mixin, voice
    src/main/resources/  # lang/, sounds/, icons, mixins.json
    src/test/java/  # 13 JUnit test classes
  fabric/           # Fabric entry points (trivial delegates)
    src/main/java/  # CatskincFabric.java, CatskincFabricClient.java
    src/main/resources/fabric.mod.json
  forge/            # Forge entry points (trivial delegates)
    src/main/java/  # CatskincForge.java, CatskincForgeClient.java
    src/main/resources/META-INF/mods.toml, pack.mcmeta
```

### Key Source Files

| File | Purpose |
|------|---------|
| `client/CatskincClient.java` | Client init: keybinds, join/quit, SSE connection, version checker |
| `client/ServerApiClient.java` | Full REST API client (1790 lines): HMAC signing, SSE, circuit breaker, upload with progress |
| `client/SkinManagerClient.java` | Skin texture cache, talking overlay, polling, slim model tracking |
| `client/SkinUploadScreen.java` | Full-screen upload GUI (1684 lines): 3D preview, library, drag-drop, validation |
| `client/SettingsScreen.java` | Settings GUI with search, categories, IP config, toasts |
| `client/ModConfig.java` | JSON config persistence (`config/catskinc.json`) |
| `client/ModrinthVersionChecker.java` | Auto-update notification via Modrinth API |
| `client/Toasts.java` | Custom toast notification system |
| `client/VoiceActivityTracker.java` | Voice activity detection (local mic + server packets) |
| `client/VoiceIntegrationBootstrap.java` | Reflective voice mod bridge loader |
| `client/SkinOverrideStore.java` | Local skin overrides (uploaded but not yet synced) |
| `client/SkinTextureFactory.java` | Reflection-based `PlayerSkin` construction with custom texture/model |
| `client/SkinHeadThumbnailFactory.java` | Isometric 3D head thumbnails from skin textures |
| `client/PlayerHeadRendererCompat.java` | 3D spinning head preview in GUI |
| `client/InventoryEntityRendererCompat.java` | Full player entity rendering in inventory |
| `mixin/client/AbstractClientPlayerEntityMixin120.java` | Override `getSkinTexture()` / `getModel()` |
| `mixin/client/PlayerListEntryMixin120.java` | Override tab list / GUI skin |
| `mixin/client/PlayerRendererMixin.java` | Override renderer texture |
| `mixin/client/SkinManagerMixin.java` | Override `loadSkin()` for GUI compat |
| `mixin/client/PlayerEntityAccessor.java` | Access `PLAYER_MODEL_PARTS` tracked data |
| `voice/PlasmoVoiceBridgeAddon.java` | Plasmo Voice client addon |
| `voice/SimpleVoiceChatBridgePlugin.java` | Simple Voice Chat plugin |
| `voice/PlasmoVoiceServerBridgeAddon.java` | Plasmo Voice server addon |
| `voice/PlasmoVoiceServerBridgeBootstrap.java` | Server-side bootstrap |
| `voice/VoiceStateChannel.java` | Server-to-client voice state networking |

---

## Build / Run / Test Commands

```bash
# Build all platforms (output: fabric/build/libs/, forge/build/libs/)
./gradlew :common:compileJava :fabric:remapJar :forge:remapJar

# Build without hardening (for local debugging)
./gradlew :common:compileJava :fabric:remapJar :forge:remapJar -Pharden_build=false

# Run tests
./gradlew :common:test

# Dev client
./gradlew :fabric:runClient
./gradlew :forge:runClient

# Compile only (fast)
./gradlew :common:compileJava :fabric:compileJava :forge:compileJava

# Clean
./gradlew clean
```

---

## Architecture

### Multi-Loader Pattern (Architectury)

```
common  (all logic, mixins, resources)
  ^         ^
  |         |
fabric    forge
(trivial) (trivial)
```

### Skin Override Pipeline

```
Player joins / skin uploaded
        |
        v
ServerApiClient.uploadSkinAsync()
  - HTTP multipart POST to /upload
  - HMAC-signed with session token
  - Returns skin ID
        |
        v
SkinManagerClient.fetchAndApplyFor()
  - Downloads texture from cloud
  - Registers DynamicTexture in BASE_CACHE
  - Updates SkinOverrideStore for local display
        |
        v
Mixin injection points:
  AbstractClientPlayerEntityMixin120  → getSkinTexture() return override
  PlayerListEntryMixin120             → tab list skin override
  PlayerRendererMixin                 → entity renderer texture
  SkinManagerMixin                    → GUI skin provider override
```

### Voice Integration Flow

```
VoiceIntegrationBootstrap (reflective load)
  -> PlasmoVoiceBridgeAddon / SimpleVoiceChatBridgePlugin
       -> VoiceActivityTracker.markSpeaking(uuid)
            -> SkinManagerClient returns TALKING_CACHE variant
```

### SSE Live Updates

```
ServerApiClient.startSse()
  -> Opens long-lived HTTP connection to /events
  -> Receives UUID + skin URL updates in real-time
  -> Calls forceFetch() for each event
  -> Circuit breaker: 3 failures = 30s cooldown
```

---

## Configuration

**Runtime config file:** `config/catskinc.json`

```json
{
  "catskinCloudIp": "storage-api.catskin.space",
  "showConnectionToast": true,
  "showUploadToast": true,
  "showInfoToast": true,
  "showErrorToast": true
}
```

**Environment variable overrides:**
- `CATSKINC_REQUEST_SIGNING_KEY` — shared HMAC key
- `CATSKINC_TLS_PIN_SHA256` — HTTPS public key pin
- `CATSKINC_DEV=1` — debug logging

**JVM property overrides:**
- `-Dcatskinc.requestSigningKey=...`
- `-Dcatskinc.tlsPinSha256=...`
- `-Dcatskinc.dev=true`

**API endpoints (hardcoded defaults):**
- `https://storage-api.catskin.space/upload` — skin upload
- `https://storage-api.catskin.space/select` — skin selection
- `https://storage-api.catskin.space/selected` — get selected
- `https://storage-api.catskin.space/public/{id}/skin.png` — public asset
- `https://storage-api.catskin.space/events` — SSE stream
- `https://storage-api.catskin.space/version/check` — update check

---

## Dependencies

| Dependency | Version | Type |
|-----------|---------|------|
| Minecraft | 1.20.1 | Required |
| Fabric Loader | >= 0.17.2 | Required (Fabric) |
| Fabric API | >= 0.92.7+1.20.1 | Required (Fabric) |
| Architectury (Fabric) | >= 9.2.14 | Required (Fabric) |
| Forge | >= 47.4.16 | Required (Forge) |
| Architectury (Forge) | >= 9.2.14 | Required (Forge) |
| Simple Voice Chat API | 2.5.36 | Optional (compile-only) |
| Plasmo Voice API | 2.1.8 | Optional (compile-only) |
| JUnit Jupiter | 5.10.2 | Test |

---

## Testing

13 JUnit 5 test classes in `common/src/test/java/com/sammy/catskinc/client/`:

- `IdentifiersTest` — ResourceLocation creation
- `InventoryEntityRendererCompatTest` — No reflection dependency
- `ModrinthVersionCheckerTest` — 9 tests: version parsing, comparison, Modrinth resolution
- `PlayerHeadRendererCompatTest` — Head layout math, animation
- `PreviewPlayerEntityTest` — Name rendering override
- `SkinHeadThumbnailFactoryTest` — Isometric rendering, HD scaling
- `SkinManagerClientManagedOverrideTest` — Managed override logic
- `SkinManagerClientTextureOwnershipTest` — Ownership tracking
- `SkinManagerGuiCompatTest` — GUI compat mixin
- `SkinUploadDropPlanTest` — Drop target resolution
- `SkinUploadScreenHistoryPreviewTest` — 3D head preview
- `SkinUploadScreenLocalApplyTest` — Immediate local apply
- `SkinUploadScreenMouthValidationTest` — Mouth dimension validation

---

## Build Hardening

Enabled by default (`harden_build=true` in `gradle.properties`):
- Strips Java debug metadata (`-g:none`)
- Reproducible JARs (no timestamps, deterministic ordering)
- Disable for debugging: `-Pharden_build=false`

---

## CI/CD

- **GitHub Actions** (`.github/workflows/ci.yml`): Build on push/PR to main/master, Java 17
- **GitLab CI** (`.gitlab-ci.yml`): Validate + build stages, Gradle caching
- **Jenkins** (`Jenkinsfile`): Pipeline with checkout, validate, build JARs

---

## Git Context

- **Branch:** `main`
- **Remote:** `origin` configured
- **Latest commit:** `e62befa` — "authors Q Team Studio changed Name to Team CatLab Design"
- **Working tree:** Clean

---

## Important Notes

1. **Voice mods are optional** — loaded reflectively at runtime. No hard dependency.
2. **Figura compatibility** — careful yield/priority management to avoid conflicts.
3. **Some code decompiled** — `SkinUploadScreen.java` has a "Decompiled with CFR 0.152" header.
4. **Thai localization** — only non-English locale is Thai (`th_th.json`).
5. **Mixins use `defaultRequire: 0`** — tolerant of missing targets across mod environments.
6. **Security headers** sent on every API call: `x-catskinc-request-id`, `x-catskinc-content-sha256`, `x-catskinc-timestamp`, `x-catskinc-nonce`, `x-catskinc-signature`.

---

## Recent Fixes (2026-07-21)

| # | Fix | Impact |
|---|-----|--------|
| 1 | **Select-Fetch race fixed** — `selectSkin().thenRun(refresh)` chains POST before GET | Eliminates 4-8s skin delay after upload |
| 2 | **Pending selections cache** — `PENDING_SELECTIONS` returns uploaded skin immediately | No more old-skin flashing |
| 3 | **LAST_CHECK on success only** — failed fetches no longer reset the 5s poll timer | Prevents 15s blackout window |
| 4 | **Fast-retry after null** — retries fetch in 2s when server has no skin | Reduces initial sync from 15s to 2s |
| 5 | **Poll interval reduced** — 15s → 5s | Faster periodic sync |
| 6 | **SSE clear-skin** — clears `BASE_CACHE`/`TALKING_CACHE` on null URL | Player reverts to default skin |
| 7 | **SSE reconnect delay** — added 1.5s backoff on stream close (was instant) | Prevents server flooding |
| 8 | **Circuit breaker in SSE** — SSE checks `circuitOpenUntilMs` before connecting | No useless retries during outage |
| 9 | **Exponential backoff** — SSE retries scale from 1.5s to 60s max | Thundering herd prevention |
| 10 | **AtomicInteger** — `consecutiveFailures` is now atomic | Correct failure counting |
| 11 | **Always destroy old textures** — removed identity check gate | Prevents GPU memory leak |
| 12 | **SSE clear event handling** — distinguishes clear vs update events | Correct behavior on skin removal |
| 13 | **Texture self-destroy check** — added `!equals` guard before `destroyTexture()` | Prevents destroying identical texture IDs |
| 14 | **SSE thread leak fix** — volatile `sseConnection` field, closed in `stopSse()` | Prevents connection leak on mod unload |
| 15 | **selectSkin forceRefresh retry** — retries session token with forceRefresh=true on null | Recovers from stale tokens |
| 16 | **NativeImage leak fix** — `refreshPreviewTexture()` closes NativeImage on exception | Frees native GPU memory on error |

> **Last updated:** 2026-07-21
