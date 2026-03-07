# Changelog

All notable changes to this project should be documented in this file.

## [Unreleased]

- No unreleased changes yet.

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
