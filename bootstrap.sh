#!/bin/sh
set -e

echo "→ Configuring git hooks path..."
git config core.hooksPath .githooks

echo "→ Making hooks executable..."
chmod +x .githooks/*

echo "→ Done. Pre-push hook is active."
echo ""
echo "  The pre-push hook will run ./gradlew testDebugUnitTest before every git push."
echo "  To skip in an emergency (not recommended): git push --no-verify"
