#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME}"
: "${KEEPR_LEGACY_KEYSTORE:?Set KEEPR_LEGACY_KEYSTORE}"
: "${KEEPR_RELEASE_KEYSTORE:?Set KEEPR_RELEASE_KEYSTORE}"
: "${KEEPR_RELEASE_PASSWORD:?Set KEEPR_RELEASE_PASSWORD}"
input=${1:?Unsigned APK path required}
output=${2:?Signed APK path required}
signer="$ANDROID_HOME/build-tools/36.0.0/apksigner"
"$signer" sign --ks "$KEEPR_LEGACY_KEYSTORE" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android \
    --next-signer --ks "$KEEPR_RELEASE_KEYSTORE" --ks-key-alias keepr-release --ks-pass env:KEEPR_RELEASE_PASSWORD \
    --lineage release/signing-lineage.bin --rotation-min-sdk-version 28 --v4-signing-enabled false --out "$output" "$input"
report=$("$signer" verify --min-sdk-version 28 --print-certs "$output")
expected=$(cat release/certificate-sha256.txt)
[[ "$report" == *"certificate SHA-256 digest: $expected"* ]] || { echo 'Unexpected production certificate'; exit 1; }
"$signer" verify --min-sdk-version 26 --max-sdk-version 27 "$output"
python3 scripts/verify-release.py "$output"
