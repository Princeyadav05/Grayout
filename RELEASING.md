# Releasing Grayout

Releases are cut from tags. Push `vX.Y.Z`, and `.github/workflows/release.yml` does the rest — builds, signs, publishes.

## Before you tag (sanity pass, ~2 min)

1. `git status` — clean, on `main`, up to date with `origin/main`.
2. `./gradlew preReleaseCheck` (or at minimum `./gradlew testDebugUnitTest`) — tests green locally.
3. Build a locally signed optimized release and smoke-test its core flows as described below.
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
- Release enables R8 code optimization and resource shrinking. Debug remains unoptimized.
- Each tagged release includes its R8 mapping file alongside the APK so crash traces can be retraced after build outputs are overwritten.

## Verify the optimized release locally

Run from the repository with the same JDK and Android SDK used for normal builds:

```bash
bash scripts/build_local_release.sh
```

The script builds the actual `release` variant, creates a disposable signing key,
and prints paths to its APK and R8 mapping. It deletes the key after the build.
An optional first argument selects an output directory. CI runs this command on
pull requests to catch R8 build failures without production secrets.

Use a dedicated emulator or test device with no production Grayout installation.
Each invocation uses a new key, so installing over a differently signed copy will
fail. The script does not install or remove apps. Production `assembleRelease`
still requires the configured `SIGNING_*` environment variables; it does not fall
back to this local key or debug signing.

Install the printed APK and grant the permissions used by the smoke checks:

```bash
adb install /path/printed/by/script/grayout-local-release.apk
adb shell pm grant com.princeyadav.grayout android.permission.WRITE_SECURE_SETTINGS
# Android 13 and later:
adb shell pm grant com.princeyadav.grayout android.permission.POST_NOTIFICATIONS
```

Exercise all of these on the optimized APK before tagging:

- Open Home, Schedules, Settings, and Exclusions, then navigate back. Create,
  edit, disable, re-enable, and delete a schedule. Restart the app and verify a
  retained schedule loads from Room.
- Toggle grayscale and enforcement. Confirm the foreground notification and
  scheduled enforcement run. Let a short schedule start and end, and verify
  recovery after a clock/timezone change and reboot.
- Add the Grayscale and Enforcement Quick Settings tiles. Tap both and confirm
  their state and the app's state agree. Check logcat for crashes while exercising
  these flows.

The debug instrumentation suite still supplies deeper behavior coverage. Its
success does not substitute for the optimized APK smoke test, because R8 changes
the code and resource graph.

For the repeatable black-box check, use an English Android 12 emulator with
SystemUI (a standard system image, not ATD):

```bash
python3 scripts/smoke_local_release.py \
  --serial emulator-5556 \
  --apk /path/printed/by/script/grayout-local-release.apk \
  --report /tmp/grayout-release-smoke.json
```

This script rejects physical devices. It replaces Grayout's test installation,
temporarily changes the emulator clock/timezone, and restores clock settings on
exit. It verifies UI and Room operations, start/end and enforcement alarms,
exclusions, both tiles, and exact-alarm revoke/regrant. The report records the APK
hash and five force-stopped launch timings. These timings are an emulator sample,
not a physical-device startup benchmark. `--startup-only` runs just that sample.

## Compare equivalent release sizes

Build the same app source, dependency versions, release version, and signing key
twice. In a disposable comparison checkout, set only `isMinifyEnabled` and
`isShrinkResources` to `false` for the baseline. Keep both `true` for the optimized
build. Supply the same local `SIGNING_*` variables to both `assembleRelease`
commands, and copy the first APK before the second build overwrites it. Compare
the APK byte counts and compressed DEX entries, then smoke-test the optimized APK.
Do not use debug size as a release baseline.

The standard optimized platform rules and AndroidX consumer rules handle current
manifest entry points and Room's generated implementation. Add narrowly scoped
keep rules only for a demonstrated reflection or resource lookup need. See
[Android's R8 setup guidance](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).
For a release crash, use its matching mapping file with
[R8 retrace](https://developer.android.com/topic/performance/app-optimization/troubleshoot-the-optimization).
