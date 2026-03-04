# CatSkinC

CatSkinC is a Minecraft client mod for cloud skin upload, skin history, and live skin sync.
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

## CI / CD

- GitHub Actions: `.github/workflows/ci.yml`
- GitLab CI: `.gitlab-ci.yml`
- Jenkins: `Jenkinsfile`

All pipelines build using Java 17 and run:

- `:common:compileJava`
- `:fabric:compileJava`
- `:forge:compileJava`
- `:fabric:remapJar`
- `:forge:remapJar`

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
- `selectedCacheTtlMs`, `pingCacheTtlMs`
- `allowInsecureHttp` (default `false`, allows only HTTPS except localhost)
- `requestSigningKey` (optional shared HMAC key with server)
- `tlsPinSha256` (optional SHA-256 public-key pin for HTTPS)
- `allowedAssetHosts` (comma/semicolon-separated trusted host[:port] list for absolute texture URLs)
- `maxJsonBytes`, `maxImageBytes` (response guard rails)
- `debugLogging`, `traceLogging`

Sensitive values can be injected without writing to config file:

- `CATSKINC_REQUEST_SIGNING_KEY`
- `CATSKINC_TLS_PIN_SHA256`
- JVM properties `catskinc.requestSigningKey` / `catskinc.tlsPinSha256`

Security request headers sent by client API calls:

- `x-catskinc-request-id`
- `x-catskinc-content-sha256`
- `x-catskinc-timestamp`
- `x-catskinc-nonce`
- `x-catskinc-signature`

## Release Hardening

- Build hardening is enabled by default via `harden_build=true` (strips Java debug metadata and makes jars reproducible).
- To disable for local debugging: `-Pharden_build=false`

## Development Notes

- This repository contains version-specific mixins for 1.20/1.21 method differences with graceful fallback behavior.
- If you see Architectury transformer instability in IDE run tasks, prefer `:forge:runClient` / `:fabric:runClient` with JDK 17.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [SECURITY.md](SECURITY.md).

## License

See [LICENSE](LICENSE).
