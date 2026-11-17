#!/usr/bin/env bash
# Verifikasi APK sebelum dikirim ke perangkat.
# Gagal (exit 1) kalau APK tidak bisa dipasang lewat installer biasa:
#   - manifest membawa android:testOnly="true"  -> INSTALL_FAILED_INVALID_APK
#   - signature v2 tidak valid
#   - zipalign rusak
# Pakai: scripts/verify-apk.sh dist/HOFLIX-V2-arm64-X.Y.Z.apk
set -euo pipefail

APK="${1:?pakai: $0 <apk>}"
[ -f "$APK" ] || { echo "APK tidak ada: $APK"; exit 1; }

BT="${ANDROID_BUILD_TOOLS:-}"
if [ -z "$BT" ]; then
  BT=$(ls -d "${ANDROID_SDK_ROOT:-$HOME/android-sdk}"/build-tools/* 2>/dev/null | sort -V | tail -1)
fi
[ -x "$BT/aapt2" ] || { echo "build-tools tidak ditemukan"; exit 1; }

fail=0
say() { printf '%-22s %s\n' "$1" "$2"; }

say "file" "$APK"
say "ukuran" "$(stat -c %s "$APK") byte"
say "sha256" "$(sha256sum "$APK" | cut -d' ' -f1)"

badging=$("$BT/aapt2" dump badging "$APK")
say "package" "$(printf '%s\n' "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
say "versi" "$(printf '%s\n' "$badging" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)"
say "versionCode" "$(printf '%s\n' "$badging" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -1)"
say "minSdk / targetSdk" "$(printf '%s\n' "$badging" | sed -n "s/^minSdkVersion:'\([^']*\)'/\1/p") / $(printf '%s\n' "$badging" | sed -n "s/^targetSdkVersion:'\([^']*\)'/\1/p")"
say "abi" "$(printf '%s\n' "$badging" | sed -n "s/^native-code: \(.*\)/\1/p")"

# android:testOnly -> APK hanya bisa dipasang lewat "adb install -t"
manifest_xml=$("$BT/aapt2" dump xmltree --file AndroidManifest.xml "$APK")
if grep -q "testOnly" <<<"$manifest_xml"; then
  say "testOnly" "ADA (APK tidak bisa dipasang manual)"
  fail=1
else
  say "testOnly" "tidak ada"
fi

sign_info=$("$BT/apksigner" verify --verbose --print-certs "$APK" 2>&1 || true)
if grep -q "Verified using v2 scheme (APK Signature Scheme v2): true" <<<"$sign_info"; then
  say "signature v2" "ok — $(sed -n 's/^V2 Signer: certificate DN: //p' <<<"$sign_info" | head -1)"
else
  say "signature v2" "GAGAL"
  fail=1
fi

if "$BT/zipalign" -c -p 4 "$APK" >/dev/null 2>&1; then
  say "zipalign" "ok"
else
  say "zipalign" "GAGAL"
  fail=1
fi

exit $fail
