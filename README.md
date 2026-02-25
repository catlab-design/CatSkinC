# CatSkinC-Remake

CatSkinC-Remake is a Minecraft client mod for cloud skin upload, skin history, and live skin sync.
It is built with Architectury for Fabric and Forge.

## Features

- Upload PNG skins directly in-game.
- Keep local skin history with quick re-select.
- Sync selected skin from API.
- Live updates via SSE (server-sent events).

## Supported Versions

- Minecraft: `1.20.1`
- Loaders: `Fabric`, `Forge`
- Java: `17` (recommended for Gradle + runtime)

## Runtime Dependencies

- Fabric:
  - `fabric-api`
  - `architectury`
- Forge:
  - `architectury`

## Project Layout

- `common/` shared mod logic
- `fabric/` Fabric entrypoints and packaging
- `forge/` Forge entrypoints and packaging

## Build

Windows:

```powershell
.\gradlew.bat :common:compileJava :fabric:remapJar :forge:remapJar
```

Linux/macOS:

```bash
./gradlew :common:compileJava :fabric:remapJar :forge:remapJar
```

Output jars are generated under each module `build/libs/` directory.

## Dev Run

Fabric client:

```powershell
.\gradlew.bat :fabric:runClient
```

Forge client:

```powershell
.\gradlew.bat :forge:runClient
```

## Configuration

Client config file is created at:

- `<minecraft>/config/catskinc-remake.json`

Important values:

- `apiBaseUrl`
- `pathUpload`, `pathSelect`, `pathSelected`, `pathPublic`, `pathEvents`
- `timeoutMs`
- `debugLogging`, `traceLogging`

## Development Notes

- This repository contains version-specific mixins for 1.20/1.21 method differences with graceful fallback behavior.
- If you see Architectury transformer instability in IDE run tasks, prefer `:forge:runClient` / `:fabric:runClient` with JDK 17.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [SECURITY.md](SECURITY.md).

## License

See [LICENSE](LICENSE).
