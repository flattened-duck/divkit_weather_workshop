#!/usr/bin/env bash
set -euo pipefail

# Regenerates the bundled zero-skeleton asset (android/src/main/assets/document.json) from a running
# backend. Usage:
#   1. Start the backend: (cd backend && ./gradlew run)
#   2. ./scripts/update-bundled-layout.sh
# Overrides: LANG_CODE=en BASE_URL=http://localhost:8080 ./scripts/update-bundled-layout.sh

BASE_URL="${BASE_URL:-http://localhost:8080}"
LANG_CODE="${LANG_CODE:-ru}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$SCRIPT_DIR/../android/src/main/assets/document.json"

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

if ! curl -fsS "$BASE_URL/zero?lang=$LANG_CODE" -o "$TMP"; then
    echo "ERROR: failed to fetch $BASE_URL/zero?lang=$LANG_CODE — is the backend running?" >&2
    exit 1
fi

if [ ! -s "$TMP" ] || ! grep -q '"screens"' "$TMP" || ! grep -q 'zero_skeleton' "$TMP"; then
    echo "ERROR: response from $BASE_URL/zero?lang=$LANG_CODE doesn't look like a valid zero envelope (missing 'screens' or 'zero_skeleton') — leaving $OUT untouched" >&2
    exit 1
fi

mv "$TMP" "$OUT"
trap - EXIT
echo "Updated $OUT ($(wc -c < "$OUT" | tr -d ' ') bytes) from $BASE_URL/zero?lang=$LANG_CODE"
