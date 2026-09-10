#!/usr/bin/env bash
# Build the actual optimized release variant with a disposable verification key.
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_dir="${1:-$(mktemp -d "${TMPDIR:-/tmp}/grayout-local-release.XXXXXX")}"
mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd)"
if [[ -e "$output_dir/grayout-local-release.apk" || -e "$output_dir/mapping.txt" ]]; then
    echo "Choose an output directory without an existing local release APK or mapping." >&2
    exit 1
fi

signing_dir="$(mktemp -d "${TMPDIR:-/tmp}/grayout-local-signing.XXXXXX")"
trap 'rm -rf "$signing_dir"' EXIT
keytool -genkeypair -noprompt -keystore "$signing_dir/verification.jks" \
    -storepass android -keypass android -alias grayout-local \
    -dname 'CN=Grayout Local Verification' -keyalg RSA -keysize 2048 -validity 7

cd "$repo_dir"
SIGNING_KEYSTORE_PATH="$signing_dir/verification.jks" \
SIGNING_STORE_PASSWORD=android \
SIGNING_KEY_ALIAS=grayout-local \
SIGNING_KEY_PASSWORD=android \
    ./gradlew :app:assembleRelease

cp app/build/outputs/apk/release/app-release.apk "$output_dir/grayout-local-release.apk"
cp app/build/outputs/mapping/release/mapping.txt "$output_dir/mapping.txt"
echo "Local verification APK: $output_dir/grayout-local-release.apk"
echo "R8 mapping: $output_dir/mapping.txt"
echo "Use a dedicated test device. This key cannot update an existing production installation."
