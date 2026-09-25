# Release Pipeline Setup Guide

## Required GitHub Secrets

Go to: **Repository Settings → Secrets and variables → Actions → New repository secret**

| Secret Name | Value Source | Required |
|-------------|--------------|----------|
| `MODRINTH_TOKEN` | Modrinth API Token (https://modrinth.com/settings/api) | ✅ Required |
| `MODRINTH_PROJECT_ID` | `catskinc` (from https://modrinth.com/mod/catskinc) | ✅ Required |
| `CURSEFORGE_TOKEN` | CurseForge API Token (https://curseforge.com/api) | ✅ Required |
| `CURSEFORGE_PROJECT_ID` | `1375958` (same for both versions) | ✅ Required |
| `DISCORD_WEBHOOK` | Discord Webhook URL (Settings → Integrations → Webhooks) | ✅ Required |
| `SLACK_WEBHOOK` | Slack Incoming Webhook URL (optional) | Optional |

## How to Run a Release

1. Go to **Actions → Release CatSkinC**
2. Click **Run workflow**
3. Select:
   - **Branch**: `main` (for 1.20.1)
   - **Version** (optional): Override version from gradle.properties
   - **Publish to Modrinth**: ✅ (default)
   - **Publish to CurseForge**: ✅ (default)
   - **Send Notifications**: ✅ (default)
4. Click **Run workflow**

## Artifact Naming

Output files will be named:
- `catskinc-fabric_1.21.1-4.0.1.jar`
- `catskinc-neoforge_1.21.1-4.0.1.jar`

## Version Source

Version is read from `gradle.properties`:
```properties
mod_version=4.0.1
```

To override: specify `version` input when running workflow.

## Failure Handling (Current)

- Failed publishes **do not** trigger alerts
- Failed publishes do **not** create draft releases
- Workflow fails fast on build/test failures
- Manual re-run required for failed publishes

## Future Enhancements (Not Implemented)

- [ ] Automated version bumping
- [ ] Rollback on failed publish
- [ ] Draft release on failure
- [ ] Automated dependency updates
- [ ] Multi-platform notifications (Slack, Twitter, etc.)
