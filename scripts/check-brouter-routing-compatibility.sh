#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

LOOKUPS_FILE="${LOOKUPS_FILE:-app/src/main/assets/brouter/profiles2/lookups.dat}"
THIRD_PARTY_LOOKUPS_FILE="${THIRD_PARTY_LOOKUPS_FILE:-third_party/brouter/misc/profiles2/lookups.dat}"
COMPANION_DOWNLOADER_FILE="${COMPANION_DOWNLOADER_FILE:-glancemapcompanionapp/src/main/java/com/glancemap/glancemapcompanionapp/routing/BRouterTileDownloader.kt}"
BROUTER_SEGMENT_URL="${BROUTER_SEGMENT_URL:-https://brouter.de/brouter/segments4/E0_N40.rd5}"
CHECK_LIVE_BROUTER="${CHECK_LIVE_BROUTER:-true}"

fail() {
  echo "::error::$*"
  exit 1
}

read_lookup_major() {
  local file="$1"
  local version

  version="$(sed -n 's/^---lookupversion:\([0-9][0-9]*\).*/\1/p' "$file" | head -n 1)"
  if [[ -z "$version" ]]; then
    fail "Could not read ---lookupversion from $file"
  fi

  printf '%s\n' "$version"
}

read_companion_supported_version() {
  local file="$1"
  local version

  version="$(
    sed -n \
      's/.*SUPPORTED_ROUTING_PACK_LOOKUP_VERSION[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' \
      "$file" | head -n 1
  )"
  if [[ -z "$version" ]]; then
    fail "Could not read SUPPORTED_ROUTING_PACK_LOOKUP_VERSION from $file"
  fi

  printf '%s\n' "$version"
}

read_live_segment_lookup_version() {
  local url="$1"
  local header_hex

  set +o pipefail
  header_hex="$(curl -fsSL --max-time 45 --range 0-1 "$url" | od -An -tx1 -N2 | tr -d '[:space:]')"
  set -o pipefail

  if [[ ! "$header_hex" =~ ^[0-9a-fA-F]{4}$ ]]; then
    fail "Could not read the first two bytes from $url"
  fi

  printf '%d\n' "$((16#$header_hex))"
}

app_lookup_version="$(read_lookup_major "$LOOKUPS_FILE")"
third_party_lookup_version="$(read_lookup_major "$THIRD_PARTY_LOOKUPS_FILE")"
companion_supported_version="$(read_companion_supported_version "$COMPANION_DOWNLOADER_FILE")"

if [[ "$third_party_lookup_version" != "$app_lookup_version" ]]; then
  fail "BRouter lookup metadata mismatch: $THIRD_PARTY_LOOKUPS_FILE is $third_party_lookup_version, but $LOOKUPS_FILE is $app_lookup_version"
fi

if [[ "$companion_supported_version" != "$app_lookup_version" ]]; then
  fail "BRouter companion cache version mismatch: SUPPORTED_ROUTING_PACK_LOOKUP_VERSION is $companion_supported_version, but $LOOKUPS_FILE is $app_lookup_version"
fi

echo "Local BRouter routing metadata OK: app=$app_lookup_version third_party=$third_party_lookup_version companion=$companion_supported_version"

case "$CHECK_LIVE_BROUTER" in
  true | 1 | yes)
    live_lookup_version="$(read_live_segment_lookup_version "$BROUTER_SEGMENT_URL")"
    if [[ "$live_lookup_version" != "$app_lookup_version" ]]; then
      fail "BRouter live routing pack lookup version is $live_lookup_version, but bundled lookups.dat is $app_lookup_version. Update lookups.dat and SUPPORTED_ROUTING_PACK_LOOKUP_VERSION."
    fi
    echo "Live BRouter routing metadata OK: live_rd5=$live_lookup_version bundled=$app_lookup_version"
    ;;
  false | 0 | no)
    echo "Skipped live BRouter routing pack check."
    ;;
  *)
    fail "Unsupported CHECK_LIVE_BROUTER value: $CHECK_LIVE_BROUTER"
    ;;
esac
