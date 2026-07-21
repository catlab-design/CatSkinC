# CatSkinC — Minecraft 1.21.1 Mod

Cloud-based skin upload, skin history, and live skin sync mod for Minecraft 1.21.1.

- **Version:** 3.1.0
- **License:** GNU GPL v3.0
- **Authors:** Team CatLab Design

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 21 |
| Build System | Gradle 8.12.1 (Architectury Loom 1.13-SNAPSHOT) |
| Framework | Architectury (multi-loader) |
| Loaders | Fabric 0.18.4, NeoForge 21.1.219 (Minecraft 1.21.1) |
| Mappings | Official Mojang Mappings (no Yarn) |
| Mixin | SpongePowered Mixin 0.8 (JAVA_21 compat) |
| Testing | JUnit Jupiter 5.10.2 |
| CI/CD | GitHub Actions |
| Artifact Group | `com.sammy` |
| Artifact ID | `catskinc` |

---

## Project Structure

```
catskinc/
  common/           # All shared mod logic + resources + mixins
    src/main/java/  # 31 source files: client, mixin, voice
    src/main/resources/  # lang/, sounds/, icons, mixins.json
    src/test/java/  # 16 JUnit test classes
  fabric/           # Fabric entry points (trivial delegates)
    src/main/java/  # CatskincFabric.java, CatskincFabricClient.java
    src/main/resources/fabric.mod.json
  neoforge/         # NeoForge entry points (replaces Forge from 1.20.1)
    src/main/java/  # CatskincNeoForge.java, CatskincNeoForgeClient.java
    src/main/resources/META-INF/neoforge.mods.toml, pack.mcmeta
```

### Key Differences from 1.20.1 Version

| Aspect | 1.20.1 | 1.21.1 |
|--------|--------|--------|
| Java | 17 | 21 |
| Forge → NeoForge | Forge 47.4.16 | NeoForge 21.1.219 |
| Mappings | Yarn 1.20.1+build.10 | Official Mojang Mappings |
| Gradle | 8.14 | 8.12.1 |
| Loom | 1.9-SNAPSHOT | 1.13-SNAPSHOT |
| Fabric Loader | 0.17.2 | 0.18.4 |
| Architectury | 9.2.14 | 13.0.8 |
| Build hardening | Yes (`harden_build=true`) | Not configured |
| New classes | — | `PlayerSkinOverrideResolver`, `PreviewRemotePlayer` |
| Test count | 13 | 16 |

### Key Source Files

| File | Purpose |
|------|---------|
| `client/CatskincClient.java` | Client init: keybinds, join/quit, SSE connection, version checker |
| `client/ServerApiClient.java` | REST API client: HMAC signing, SSE, circuit breaker, upload with progress |
| `client/SkinManagerClient.java` | Skin texture cache, talking overlay, polling |
| `client/PlayerSkinOverrideResolver.java` | **New in 1.21.1** — Resolves skin overrides from store + cache |
| `client/PreviewRemotePlayer.java` | **New in 1.21.1** — Custom RemotePlayer for 3D preview |
| `client/SkinUploadScreen.java` | Full-screen upload GUI (1710 lines): 3D preview, library, drag-drop, validation |
| `client/SettingsScreen.java` | Settings GUI with search, categories, IP config, toasts |
| `client/ModConfig.java` | JSON config persistence (`config/catskinc.json`) |
| `client/ModrinthVersionChecker.java` | Auto-update via Modrinth API |
| `client/Toasts.java` | Custom toast notification system |
| `client/VoiceActivityTracker.java` | Voice activity detection |
| `client/VoiceIntegrationBootstrap.java` | Reflective voice mod bridge loader |
| `client/SkinOverrideStore.java` | Local skin overrides |
| `client/SkinTextureFactory.java` | Reflection-based `PlayerSkin` patching |
| `client/SkinHeadThumbnailFactory.java` | Isometric 3D head thumbnail gen |
| `client/PlayerHeadRendererCompat.java` | 3D spinning head preview |
| `client/InventoryEntityRendererCompat.java` | Full player entity rendering in GUI |
| `mixin/client/AbstractClientPlayerEntityMixin120.java` | Override `getSkin()` / `getModel()` |
| `mixin/client/PlayerListEntryMixin120.java` | Override tab list skin |
| `mixin/client/PlayerRendererMixin.java` | Override renderer texture + hand |
| `mixin/client/SkinManagerMixin.java` | Override `lookupInsecure()` for GUI |
| `mixin/client/PlayerEntityAccessor.java` | Access `DATA_PLAYER_MODE_CUSTOMISATION` |
| `voice/PlasmoVoiceBridgeAddon.java` | Plasmo Voice client addon |
| `voice/SimpleVoiceChatBridgePlugin.java` | Simple Voice Chat plugin |
| `voice/PlasmoVoiceServerBridgeAddon.java` | Plasmo Voice server addon |
| `voice/PlasmoVoiceServerBridgeBootstrap.java` | Server-side bootstrap |
| `voice/VoiceStateChannel.java` | Voice state networking |

---

## Build / Run / Test Commands

```bash
# Build all platforms (fabric + neoforge)
./gradlew build

# Build specific platform
./gradlew :fabric:build
./gradlew :neoforge:build

# Run tests
./gradlew :common:test

# Dev client
./gradlew :fabric:runClient
./gradlew :neoforge:runClient

# Clean
./gradlew clean
```

---

## Architecture

### Module Layout

```
common  (all logic, mixins, resources, tests)
  ^         ^
  |         |
fabric    neoforge
(trivial) (trivial)
```

### Skin Override Flow

```
Upload or SSE update
        |
        v
ServerApiClient → fetches skin from cloud API
        |
        v
SkinManagerClient → registers DynamicTexture, caches per UUID
        |
        v
PlayerSkinOverrideResolver.resolvePlayerSkin(uuid, baseSkin)
  - Checks SkinOverrideStore first (local overrides)
  - Falls back to SkinManagerClient cache (cloud skins)
  - Returns patched PlayerSkin via SkinTextureFactory reflection
        |
        v
Mixin injection: AbstractClientPlayerEntityMixin120 @Inject at getSkin() RETURN
```

---

## Configuration

**Runtime config file:** `config/catskinc.json` (same as 1.20.1)

**API endpoints (default):** `https://storage-api.catskin.space`

**Security headers:** `x-catskinc-request-id`, `x-catskinc-content-sha256`, `x-catskinc-timestamp`, `x-catskinc-nonce`, `x-catskinc-signature`

---

## Dependencies

| Dependency | Version | Type |
|-----------|---------|------|
| Minecraft | 1.21.1 | Required |
| Fabric Loader | >= 0.18.4 | Required (Fabric) |
| Fabric API | >= 0.116.9+1.21.1 | Required (Fabric) |
| NeoForge | >= 21.1.219 | Required (NeoForge) |
| Architectury | 13.0.8 | Required |
| Simple Voice Chat API | 2.5.36 | Optional |
| Plasmo Voice API | 2.1.8 | Optional |
| JUnit Jupiter | 5.10.2 | Test |

---

## Testing

16 JUnit 5 test classes in `common/src/test/java/com/sammy/catskinc/client/`:

Same 13 tests as 1.20.1 plus 3 new:
- `PlayerRendererFirstPersonSkinTest` — First-person skin assertions on `PlayerRendererMixin`
- `PlayerSkinOverrideResolverTest` — Override resolution from store
- `SkinTextureFactoryTest` — PlayerSkin reflection patching

---

## CI/CD

- **GitHub Actions** (`.github/workflows/ci.yml`): Build on push/PR to main/master, Java 21

---

## Git Context

- **Branch:** `1.21.1` (tracking `origin/1.21.1`)
- **Remote branches:** `origin/1.20.1`, `origin/1.21.1`, `origin/26.1.2`, `origin/main`
- **Status:** Clean working tree
- **Commits:** 17 total

---

## Important Notes

1. **NeoForge replaces Forge** — no Forge support in this version.
2. **Official Mojang Mappings** — not Yarn (unlike the 1.20.1 branch).
3. **PlayerSkinOverrideResolver** is the new central dispatch for skin overrides.
4. **PreviewRemotePlayer** replaces the simpler PreviewPlayerEntity from 1.20.1.
5. **Voice mod integration** is identical to 1.20.1 (reflective bridges for both voice mods).
6. **Build hardening** (stripping debug info) is NOT configured in this version.

---

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
| 13 | **SSE thread leak fix** — volatile `sseConnection` field, closed in `stopSse()` | Prevents connection leak on mod unload |
| 14 | **selectSkin forceRefresh retry** — retries session token with forceRefresh=true on null | Recovers from stale tokens |
| 15 | **NativeImage leak fix** — `refreshPreviewTexture()` closes NativeImage on exception | Frees native GPU memory on error |

> **Last updated:** 2026-07-21
