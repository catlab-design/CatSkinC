# Contributing to CatSkinC-Remake

Thanks for contributing.

## Before You Start

- Use Java 17 for Gradle and local runs.
- Keep changes focused (one topic per PR).
- Open an issue first for large changes.

## Local Setup

Windows:

```powershell
.\gradlew.bat :common:compileJava :fabric:compileJava :forge:compileJava
```

Run clients:

```powershell
.\gradlew.bat :fabric:runClient
.\gradlew.bat :forge:runClient
```

## Coding Guidelines

- Keep logic in `common/` when possible.
- Use platform modules only for loader-specific glue.
- Avoid silent exception swallowing; log with context.
- Preserve backward compatibility for existing config fields.

## Pull Request Checklist

- [ ] Code compiles for `common`, `fabric`, and `forge`.
- [ ] No unrelated files changed.
- [ ] User-facing behavior is described in PR.
- [ ] Added or updated docs when needed.

## Commit Style

Suggested format:

- `feat: ...`
- `fix: ...`
- `refactor: ...`
- `docs: ...`
- `chore: ...`
