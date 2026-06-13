# Changelog

All notable changes to this project should be documented in this file.

## [3.1.0] - 2026-06-12 (26.1.2)

### Added

- Added editable `catskinCloudIp` setting to specify custom server addresses. The server URL is automatically normalized and prepended with a protocol (`http://` or `https://`) if missing.
- Added toggle settings to selectively enable or disable various toast notifications (`showConnectionToast`, `showUploadToast`, `showInfoToast`, and `showErrorToast`).
- Added the original button click sound to the Slim Model toggle switch.

### Changed

- Redesigned the client Settings screen: replaced the dropdown menu with search-filterable and collapsible accordion categories.
- Replaced the library slot thumbnail rendering with a 2D player face (matching the Tab Server List appearance) utilizing `PlayerFaceExtractor` to integrate with Minecraft's deferred PiP rendering pipeline.
- Shortened Toast messages in English and Thai language files to prevent text overflowing the toast boxes.

### Fixed

- Decoupled connection checks from skin downloads: increased client concurrent connection limit to 50 and executed pings on a separate thread pool to ensure player skins load instantly without queueing.
- Fixed the "Clear Skin" action: attached the Mojang verification session token to clear selection requests to prevent HTTP 401 Unauthorized errors on session-enforced servers.
- Fixed skin texture registration issues by utilizing a 2-argument `ResourceTexture` constructor to prevent path modification.
