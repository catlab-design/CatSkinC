# Changelog

All notable changes to this project should be documented in this file.

## [3.1.0] - 2026-06-12

### Added

- Added editable `catskinCloudIp` setting to specify custom server addresses. The server URL is automatically normalized and prepended with a protocol (`http://` or `https://`) if missing.
- Added toggle settings to selectively enable or disable various toast notifications (`showConnectionToast`, `showUploadToast`, `showInfoToast`, and `showErrorToast`).
- Added the original button click sound to the Slim Model toggle switch.

### Changed

- Redesigned the client Settings screen: replaced the dropdown menu with search-filterable and collapsible accordion categories.

### Fixed

- Decoupled connection checks from skin downloads: increased client concurrent connection limit to 50 and executed pings on a separate thread pool to ensure player skins load instantly without queueing.
- Fixed the "Clear Skin" action: attached the Mojang verification session token to clear selection requests to prevent HTTP 401 Unauthorized errors on session-enforced servers.

## [3.0.5] - 2026-06-03

### Changed

- CatSkinC now takes precedence over Figura, matching the 1.21.1 release. When a
  player has both a Figura avatar and a CatSkinC skin, the CatSkinC skin (and its
  slim/wide model) is applied instead of yielding to Figura. The reflective
  Figura-yield probe added in 3.0.3 has been removed from the texture/model
  mixins.

### Fixed

- Uploaded skins no longer silently fail to apply when Figura is installed.
  CatSkinC only overrides players who actually have a CatSkinC skin selected, so
  players using only Figura (with no CatSkinC skin) are unaffected.

## [3.0.4] - 2026-06-03

### Changed

- Version bump to keep the 1.20.1 and 1.21.1 release lines in lockstep. There are
  no functional changes in this tree: 1.20.1 (Fabric and Forge) was not affected
  by the Fabric packaging issue fixed in the 1.21.1 tree's 3.0.4, because it
  overrides texture and model through separate methods and its jars were remapped
  correctly.

## [3.0.3] - 2026-06-03

### Fixed

- Figura compatibility: CatSkinC now yields to an active Figura avatar. When a
  player has a loaded Figura avatar, CatSkinC performs no skin/model override for
  that player so the two mods no longer compete over the same skin lookups.
  Detection is reflective and Figura remains an optional dependency. (1.20.1
  already overrides texture and model through separate methods and did not show
  the 1.21.1 `getSkin()` conflict; this keeps behaviour consistent and
  future-proof.)

## [3.0.2] - 2026-05-31

### Fixed

- Player skins no longer flicker or randomly swap between the remote skin,
  vanilla, and Steve. A failed skin fetch (HTTP 404, a cache-TTL race, or an
  open circuit breaker) used to destroy the live texture on every poll; the
  currently rendered skin is now kept until a real update or clear arrives.
- A failed texture download no longer marks the URL as "applied", so the next
  poll retries instead of getting stuck on a half-applied state.

## [3.0.0] - 2026-05-30

### Added

- Auto-update now resolves the exact download for your platform: when a newer
  version is detected, it queries Modrinth for that version filtered by your
  loader and Minecraft version and surfaces the matching file (falling back to
  the project page when no direct file matches).

### Changed

- Upload errors caused by session/identity checks now show clear, actionable
  messages (verify Minecraft session, premium-account-only) instead of raw
  server text.

### Fixed

- Mouth PNGs that do not match the base skin size are now rejected up front with
  a clear message, instead of failing later during upload.
- Fixed a native-image leak and double-free on the skin texture apply error path.

## [2.0.0] - 2026-03-08

### Added

- New in-game upload workspace with three main panels: `Library`, `Live Preview`, and `Controls`.
- Live 3D player preview with support for model rotation from mouse movement and a dedicated `Slim Model` toggle.
- Support for separate `mouth_open` and `mouth_close` PNG overlays during preview and upload.
- Drag-and-drop support for PNG files, including bulk import into the local Library/history panel.
- Automatic update notification using the Modrinth API for the `catskinc` project.

### Changed

- Default storage backend now points to `https://storage-api.catskin.space`.
- Skin uploads now keep a reusable local Library/history with thumbnails, quick reselect, and delete actions.
- Join flow now reports cloud connection status in-game and refreshes player skin state more reliably.
- CatSkinC `2.0.0` is now aligned across Minecraft `1.20.1` and `1.21.1` release lines.

### Fixed

- Fixed release-jar runtime issues caused by remapped identifiers and resource-location creation.
- Fixed voice-state network channel initialization for production builds.
- Fixed 3D preview rendering in packaged builds.
- Fixed preview nameplate rendering so the 3D preview no longer shows a floating name or empty label space.
- Fixed multi-file drag-and-drop so Library imports can handle multiple PNGs instead of only using the first few files.
- Improved general runtime stability around preview texture registration, preview player creation, and upload UI behavior.
