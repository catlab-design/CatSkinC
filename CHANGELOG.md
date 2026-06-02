# Changelog

All notable changes to this project should be documented in this file.

## [Unreleased]

### Fixed

- Figura compatibility: CatSkinC now yields to an active Figura avatar instead of
  fighting it. On 1.21.1 the texture, model (slim/wide), cape and elytra all flow
  through a single `AbstractClientPlayer#getSkin()` call, which Figura also hooks.
  CatSkinC used to rewrite that result unconditionally, so depending on mixin
  order a player ended up with the CatSkinC texture but the wrong (wide) arm model
  *and* with Figura body parts overwritten. CatSkinC now detects a loaded Figura
  avatar (reflective, optional dependency) and performs no skin/model override for
  that player.
- Slim skins uploaded while Figura is installed now render with the correct slim
  arms again. The symptom ("uploaded a slim skin but it stays wide") was caused by
  the same `getSkin()` conflict above; when no Figura avatar is active the slim
  model is applied as before.

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
