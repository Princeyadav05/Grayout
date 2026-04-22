# Releasing Grayout

Releases are cut from tags. Push `vX.Y.Z`, and `.github/workflows/release.yml` does the rest — builds, signs, publishes.

## Before you tag (sanity pass, ~2 min)

1. `git status` — clean, on `main`, up to date with `origin/main`.
2. `./gradlew preReleaseCheck` (or at minimum `./gradlew testDebugUnitTest`) — tests green locally.
3. Smoke-test on a real device: `./gradlew installDebug` and tap through the core flows.
4. Skim `git log <last-tag>..HEAD` — does this warrant a release? Is the bump size right?

## Pick the version bump (semver)

| Bump | When |
|---|---|
| **patch** (`1.0.0` → `1.0.1`) | Bug fixes, polish, copy tweaks, non-behavioral refactors. |
| **minor** (`1.0.1` → `1.1.0`) | New user-visible feature, new setting, new screen. Backward compatible. |
| **major** (`1.1.0` → `2.0.0`) | Breaking change — data migration, incompatible settings rewrite, removed feature. |

First-ever GitHub release is `v1.0.0`.

## Cut the release

```bash
git tag v1.1.0
git push origin v1.1.0
gh run watch     # optional: follow the workflow live
```

~3 minutes later: signed APK + SHA-256 checksum + auto-generated release notes on the Releases page.

## After the workflow finishes

1. Open the release page, skim the auto-generated notes. Edit in the GitHub UI if a commit message didn't convey enough context (e.g., "this release also requires re-granting WRITE_SECURE_SETTINGS").
2. Download the APK on your personal device; confirm it installs and opens. This catches signing-config bugs unit tests can't.

## Failure modes

| Scenario | Fix |
|---|---|
| Workflow failed before release was created | Fix the cause, delete the tag (`git tag -d vX.Y.Z && git push origin :refs/tags/vX.Y.Z`), push a new commit, re-tag. |
| Release published but APK is broken | Delete the broken release in the GitHub UI. Cut a patch (`vX.Y.Z+1`) with the fix. Do not amend a published release. |
| Keystore or secrets leaked | Rotate: generate a new keystore, update all 4 GitHub secrets, bump major version, document uninstall-reinstall requirement for existing users in the README. |

## What's under the hood

- `versionCode` and `versionName` are derived from the tag by `versionFromTag()` in `app/build.gradle.kts`. Never bump them manually.
- Keystore lives in GitHub Secrets (`SIGNING_KEYSTORE_BASE64` + alias/store/key passwords), decoded into a runner-scoped temp file at build time.
- Release notes group conventional commits between tags into Features / Fixes / Other.
- Universal APK only. No AAB, no per-ABI splits.
