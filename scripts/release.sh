#!/usr/bin/env bash
# Rilis Hoflix: build -> verifikasi -> GitHub Release -> config bridge.
#
# Kenapa satu perintah: langkah manual (naikkan versi, build, salin, unggah,
# hitung sidik, isi panel) punya satu titik gagal yang mahal — sidik salah
# ketik berarti setiap pemasangan ditolak dan pengguna terjebak di layar wajib
# update tanpa jalan keluar. Jadi sidik tidak pernah diketik tangan.
#
# Versi wajib minimum (`minCode`) NAIK OTOMATIS mengikuti setiap rilis, supaya
# build lama berhenti di layar "Perbarui Hoflix" tanpa ada langkah manual.
# Pembatal per rilis: --keep-min (mis. perbaikan kecil yang tidak perlu memaksa).
#
# Yang tetap dijaga skrip ini:
#   - minCode tidak pernah > latestCode (kalau iya, app yang baru dipasang pun
#     langsung terkunci dan tidak ada jalan keluar);
#   - minCode hanya ditulis SETELAH APK lolos verifikasi dan aset publik
#     terbukti identik — verifikasi dulu, baru naikkan;
#   - peringatan dicetak saat ambang melewati 4.7.0, karena klien di bawahnya
#     belum punya layar perbarui dan harus dipasang manual sekali.
#
# Pakai:
#   scripts/release.sh                        # naik patch, build, rilis, minCode=latestCode
#   scripts/release.sh --notes "..."          # catatan rilis sendiri
#   scripts/release.sh --keep-min             # rilis ini TIDAK memaksa update
#   scripts/release.sh --min-code 4700000     # naikkan versi wajib ke angka tertentu
#   scripts/release.sh --skip-build           # pakai APK yang sudah ada di dist/
#   scripts/release.sh --no-bump              # pakai versi yang tertulis sekarang
#   scripts/release.sh --dry-run              # tampilkan rencana, tidak mengubah apa pun
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
DIST="$ROOT/dist"
VERSION_FILE="$ROOT/iosApp/Configuration/Version.xcconfig"
GRADLE_PROPS="$ROOT/gradle.properties"

REPO_SLUG="${REPO_SLUG:-dimasilhamhasyim/hoflix}"
BRIDGE_DIR="${BRIDGE_DIR:-/home/ubuntu/hoflix-bridge}"
PUBLIC_BASE="${PUBLIC_BASE:-https://hoflix-api.siho.my.id}"
# `appconfig` diimpor dari direktori bridge, bukan dari cwd skrip ini.
export PYTHONPATH="$BRIDGE_DIR${PYTHONPATH:+:$PYTHONPATH}"

BUMP=1
DO_BUILD=1
DRY_RUN=0
DEEP_VERIFY=1
NOTES=""
MIN_CODE=""
KEEP_MIN=0

say() { printf '%-18s %s\n' "$1" "$2"; }
step() { printf '\n\033[1m== %s\033[0m\n' "$1"; }
die() { printf 'GAGAL: %s\n' "$1" >&2; exit 1; }

# Berkas sementara + pemulihan versi kalau rilis gagal sebelum terbit.
NOTES_FILE=""
PATCH_FILE=""
VERSION_CHANGED=0
STAGE="prepare"   # prepare -> built -> released -> published
cleanup() {
  local status=$?
  [ -n "$NOTES_FILE" ] && rm -f "$NOTES_FILE"
  [ -n "$PATCH_FILE" ] && rm -f "$PATCH_FILE"
  # Build gagal = nomor versi tidak boleh "terpakai" tanpa rilis.
  # Sesudah rilis terbit, nomor versi justru harus ikut tertulis di sumber.
  if [ "$status" -ne 0 ] && [ "$VERSION_CHANGED" -eq 1 ] \
     && [ "$STAGE" != "released" ] && [ "$STAGE" != "published" ]; then
    sed -i "s/^MARKETING_VERSION *=.*/MARKETING_VERSION = $CUR_VER/" "$VERSION_FILE"
    sed -i "s/^CURRENT_PROJECT_VERSION *=.*/CURRENT_PROJECT_VERSION = $CUR_CODE/" "$VERSION_FILE"
    sed -i "s/^nuvio\.app\.versionName=.*/nuvio.app.versionName=$CUR_VER/" "$GRADLE_PROPS"
    printf 'versi sumber dikembalikan ke %s (%s)\n' "$CUR_VER" "$CUR_CODE" >&2
  fi
  return $status
}
trap cleanup EXIT

while [ $# -gt 0 ]; do
  case "$1" in
    --notes) NOTES="${2:?--notes butuh argumen}"; shift 2 ;;
    --notes-file) NOTES=$(cat "${2:?--notes-file butuh argumen}"); shift 2 ;;
    --min-code) MIN_CODE="${2:?--min-code butuh argumen}"; shift 2 ;;
    --keep-min) KEEP_MIN=1; shift ;;
    --skip-build) DO_BUILD=0; shift ;;
    --no-bump) BUMP=0; shift ;;
    --no-deep-verify) DEEP_VERIFY=0; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    --repo) REPO_SLUG="${2:?--repo butuh argumen}"; shift 2 ;;
    -h|--help) sed -n '2,29p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "argumen tidak dikenal: $1" ;;
  esac
done

if [ "$KEEP_MIN" -eq 1 ] && [ -n "$MIN_CODE" ]; then
  die "--keep-min dan --min-code tidak bisa dipakai bersamaan"
fi

[ -f "$VERSION_FILE" ] || die "Version.xcconfig tidak ada: $VERSION_FILE"
[ -f "$GRADLE_PROPS" ] || die "gradle.properties tidak ada: $GRADLE_PROPS"
command -v gh >/dev/null || die "gh CLI tidak ada"
command -v sha256sum >/dev/null || die "sha256sum tidak ada"

# ---------- versi ----------
read_ver() { sed -n 's/^MARKETING_VERSION *= *//p' "$VERSION_FILE" | tr -d '[:space:]'; }
read_code() { sed -n 's/^CURRENT_PROJECT_VERSION *= *//p' "$VERSION_FILE" | tr -d '[:space:]'; }

CUR_VER=$(read_ver)
CUR_CODE=$(read_code)
[ -n "$CUR_VER" ] || die "MARKETING_VERSION kosong"
[ -n "$CUR_CODE" ] || die "CURRENT_PROJECT_VERSION kosong"

if [ "$BUMP" -eq 1 ]; then
  IFS=. read -r MA MI PA <<<"$CUR_VER"
  [[ "$MA" =~ ^[0-9]+$ && "$MI" =~ ^[0-9]+$ && "$PA" =~ ^[0-9]+$ ]] \
    || die "versi bukan semver tiga angka: $CUR_VER"
  PA=$((PA + 1))
  NEW_VER="$MA.$MI.$PA"
  NEW_CODE=$((MA * 1000000 + MI * 100000 + PA))
  # Jaga monoton: jangan pernah menerbitkan versionCode yang lebih kecil.
  [ "$NEW_CODE" -gt "$CUR_CODE" ] || NEW_CODE=$((CUR_CODE + 1))
else
  NEW_VER="$CUR_VER"
  NEW_CODE="$CUR_CODE"
fi

APK_2ABI="HOFLIX-V2-2abi-$NEW_VER.apk"
APK_ARM64="HOFLIX-V2-arm64-$NEW_VER.apk"
APK_ARMV7="HOFLIX-V2-armv7-$NEW_VER.apk"
APK_URL="https://github.com/$REPO_SLUG/releases/download/v$NEW_VER/$APK_2ABI"

# ---------- versi wajib minimum ----------
# Baca dulu supaya peringatan bisa menyebut angkanya.
PREV_MIN=$("$BRIDGE_DIR/venv/bin/python" - <<'PY' 2>/dev/null || echo 0
import appconfig
print(appconfig.read().get("minCode") or 0)
PY
)
PREV_MIN=${PREV_MIN:-0}
[[ "$PREV_MIN" =~ ^[0-9]+$ ]] || PREV_MIN=0

if [ -n "$MIN_CODE" ]; then
  [[ "$MIN_CODE" =~ ^[0-9]+$ ]] || die "--min-code harus angka: $MIN_CODE"
  [ "$MIN_CODE" -le "$NEW_CODE" ] \
    || die "--min-code $MIN_CODE > versi rilis $NEW_CODE: app yang baru dipasang pun akan terkunci"
  MIN_LABEL="eksplisit"
  MIN_SEND=1
elif [ "$KEEP_MIN" -eq 1 ]; then
  # Config tak terbaca (PREV_MIN=0) → jangan tulis apa pun. Menulis 0 akan
  # membuka kunci semua klien, itu kegagalan yang lebih mahal daripada lewat.
  MIN_CODE="$PREV_MIN"
  MIN_LABEL="dipertahankan (--keep-min)"
  MIN_SEND=$([ "$PREV_MIN" -gt 0 ] && echo 1 || echo 0)
  [ "$MIN_SEND" -eq 1 ] || MIN_LABEL="tidak disentuh (config tak terbaca)"
else
  MIN_CODE="$NEW_CODE"
  MIN_LABEL="otomatis = versi rilis"
  MIN_SEND=1
fi

step "Rilis $NEW_VER ($NEW_CODE)"
say "versi sekarang" "$CUR_VER ($CUR_CODE)"
say "versi rilis" "$NEW_VER ($NEW_CODE)"
say "repo" "$REPO_SLUG"
say "APK utama" "$APK_2ABI"
say "versi wajib" "$MIN_CODE ($MIN_LABEL)"

# Ambang 4.7.0 = rilis pertama yang punya layar "Perbarui Hoflix".
# Klien di bawahnya tidak punya jalan masuk kalau dikunci, jadi harus dipasang manual.
if [ "$MIN_CODE" -gt 4700000 ] && [ "$PREV_MIN" -le 4700000 ]; then
  printf '\n\033[33mPERHATIAN:\033[0m minCode naik melewati 4.7.0.\n'
  printf '  Klien di bawah 4.7.0 belum punya layar perbarui — mereka terkunci tanpa\n'
  printf '  jalan keluar sampai APK dipasang manual sekali. Pastikan semua perangkat\n'
  printf '  yang kamu pakai sudah >= 4.7.0 sebelum melanjutkan.\n'
fi

if [ "$NEW_CODE" -le "$CUR_CODE" ] && [ "$BUMP" -eq 1 ]; then
  die "versionCode baru tidak lebih besar dari yang sekarang"
fi

# Tolak menerbitkan ulang versi yang sudah ada di GitHub dengan isi berbeda.
if gh release view "v$NEW_VER" --repo "$REPO_SLUG" >/dev/null 2>&1; then
  if [ "$BUMP" -eq 1 ]; then
    die "rilis v$NEW_VER sudah ada di GitHub; naikkan versi (tanpa --no-bump)"
  fi
  say "peringatan" "rilis v$NEW_VER sudah ada — aset akan ditimpa"
fi

if [ "$DRY_RUN" -eq 1 ]; then
  printf '\nDry run: tidak ada berkas, rilis, atau config yang diubah.\n'
  exit 0
fi

# ---------- versi ditulis ke sumber SEBELUM build ----------
if [ "$BUMP" -eq 1 ]; then
  sed -i "s/^MARKETING_VERSION *=.*/MARKETING_VERSION = $NEW_VER/" "$VERSION_FILE"
  sed -i "s/^CURRENT_PROJECT_VERSION *=.*/CURRENT_PROJECT_VERSION = $NEW_CODE/" "$VERSION_FILE"
  sed -i "s/^nuvio\.app\.versionName=.*/nuvio.app.versionName=$NEW_VER/" "$GRADLE_PROPS"
  VERSION_CHANGED=1
  say "sumber" "versi dinaikkan ke $NEW_VER ($NEW_CODE)"
fi

# ---------- build ----------
if [ "$DO_BUILD" -eq 1 ]; then
  step "Build release (tanpa properti injeksi)"
  # Tanpa properti `android.injected.*`: AGP akan menstempel testOnly=true dan
  # APK ditolak installer biasa.
  ( cd "$ROOT" && ./gradlew --no-daemon --console=plain :androidApp:assembleFullRelease )
else
  step "Build dilewati (--skip-build)"
fi

OUT="$ROOT/androidApp/build/outputs/apk/full/release"
[ -d "$OUT" ] || die "folder output build tidak ada: $OUT"

mkdir -p "$DIST"
cp "$OUT/androidApp-full-universal-release.apk" "$DIST/$APK_2ABI"
cp "$OUT/androidApp-full-arm64-v8a-release.apk" "$DIST/$APK_ARM64"
cp "$OUT/androidApp-full-armeabi-v7a-release.apk" "$DIST/$APK_ARMV7"

# ---------- verifikasi ----------
step "Verifikasi APK"
for apk in "$APK_2ABI" "$APK_ARM64" "$APK_ARMV7"; do
  "$ROOT/scripts/verify-apk.sh" "$DIST/$apk" >/dev/null || die "APK tidak lolos verifikasi: $apk"
done
say "verify-apk.sh" "3/3 lolos (testOnly tidak ada, sig v2, zipalign)"

# Versi di dalam APK harus sama dengan yang akan ditulis ke config; kalau beda,
# gate versi akan mengunci pengguna yang baru memasang.
badging_dir=$(ls -d "${ANDROID_SDK_ROOT:-$HOME/android-sdk}"/build-tools/* | sort -V | tail -1)
built_code=$("$badging_dir/aapt2" dump badging "$DIST/$APK_2ABI" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -1)
built_ver=$("$badging_dir/aapt2" dump badging "$DIST/$APK_2ABI" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)
[ "$built_code" = "$NEW_CODE" ] || die "versionCode di APK ($built_code) != $NEW_CODE"
[ "$built_ver" = "$NEW_VER" ] || die "versionName di APK ($built_ver) != $NEW_VER"
STAGE="built"
say "isi APK" "$built_ver ($built_code)"

SHA=$(sha256sum "$DIST/$APK_2ABI" | cut -d' ' -f1)
SIZE=$(stat -c %s "$DIST/$APK_2ABI")
say "sha256" "$SHA"
say "ukuran" "$SIZE byte"

# ---------- GitHub Release ----------
step "GitHub Release v$NEW_VER"
NOTES_FILE=$(mktemp)
if [ -n "$NOTES" ]; then
  printf '%s\n' "$NOTES" >"$NOTES_FILE"
else
  printf 'Rilis %s (%s).\n' "$NEW_VER" "$NEW_CODE" >"$NOTES_FILE"
fi

if gh release view "v$NEW_VER" --repo "$REPO_SLUG" >/dev/null 2>&1; then
  gh release upload "v$NEW_VER" --repo "$REPO_SLUG" --clobber \
    "$DIST/$APK_2ABI" "$DIST/$APK_ARM64" "$DIST/$APK_ARMV7"
else
  gh release create "v$NEW_VER" --repo "$REPO_SLUG" \
    --title "Hoflix $NEW_VER" --notes-file "$NOTES_FILE" \
    "$DIST/$APK_2ABI" "$DIST/$APK_ARM64" "$DIST/$APK_ARMV7"
fi
STAGE="released"
say "aset" "3 berkas terunggah"

# ---------- sidik dari URL yang benar-benar dipakai app ----------
step "Verifikasi aset publik"
remote_size=$(curl -sIL "$APK_URL" | awk 'tolower($1)=="content-length:"{v=$2} END{gsub(/\r/,"",v); print v+0}')
[ "$remote_size" = "$SIZE" ] || die "ukuran aset publik ($remote_size) != lokal ($SIZE)"
say "content-length" "$remote_size byte"

if [ "$DEEP_VERIFY" -eq 1 ]; then
  [ -x "$BRIDGE_DIR/venv/bin/python" ] || die "venv bridge tidak ada: $BRIDGE_DIR/venv"
  remote_sha=$(cd "$BRIDGE_DIR" && venv/bin/python - "$APK_URL" <<'PY'
import sys
import appconfig
digest, _ = appconfig.digest_url(sys.argv[1])
print(digest)
PY
)
  [ "$remote_sha" = "$SHA" ] || die "sidik aset publik ($remote_sha) != lokal ($SHA)"
  say "sha256 aset" "sama dengan lokal"
else
  say "sha256 aset" "dilewati (--no-deep-verify)"
fi

# ---------- config bridge ----------
step "Config bridge"
[ -x "$BRIDGE_DIR/venv/bin/python" ] || die "venv bridge tidak ada: $BRIDGE_DIR/venv"
PATCH_FILE=$(mktemp)
NEW_VER="$NEW_VER" NEW_CODE="$NEW_CODE" APK_URL="$APK_URL" SHA="$SHA" SIZE="$SIZE" \
  NOTES="$NOTES" MIN_CODE="$MIN_CODE" MIN_LABEL="$MIN_LABEL" MIN_SEND="$MIN_SEND" \
  PATCH_FILE="$PATCH_FILE" \
  "$BRIDGE_DIR/venv/bin/python" - <<'PY'
import json
import os

import appconfig

patch = {
    "latest": os.environ["NEW_VER"],
    "latestCode": int(os.environ["NEW_CODE"]),
    "apkUrl": os.environ["APK_URL"],
    "sha256": os.environ["SHA"],
    "sizeBytes": int(os.environ["SIZE"]),
}
notes = os.environ.get("NOTES") or "Rilis {} ({}).".format(
    os.environ["NEW_VER"], os.environ["NEW_CODE"]
)
patch["notes"] = notes
min_code = (os.environ.get("MIN_CODE") or "").strip()
if os.environ.get("MIN_SEND") == "1":
    if not min_code:
        raise SystemExit("MIN_CODE kosong padahal MIN_SEND=1")
    patch["minCode"] = int(min_code)
    # Pengaman terakhir sebelum menyentuh config hidup.
    if patch["minCode"] > patch["latestCode"]:
        raise SystemExit(
            "minCode {} > latestCode {}: app yang baru dipasang akan langsung terkunci".format(
                patch["minCode"], patch["latestCode"]
            )
        )

before = appconfig.read()
cfg = appconfig.stamp_publish(patch)
json.dump({"before": before, "after": cfg}, open(os.environ["PATCH_FILE"], "w"))
PY

MIN_LABEL="$MIN_LABEL" "$BRIDGE_DIR/venv/bin/python" - "$PATCH_FILE" <<'PY'
import json
import os
import sys
data = json.load(open(sys.argv[1]))
before, after = data["before"], data["after"]
print("{:<18} {}".format("latest", after["latest"]))
print("{:<18} {}".format("latestCode", after["latestCode"]))
print("{:<18} {} ({})".format(
    "minCode", after["minCode"], os.environ.get("MIN_LABEL") or "-"
))
print("{:<18} {}".format("blockUnversioned", after["blockUnversioned"]))
if before["minCode"] != after["minCode"]:
    print("versi wajib berubah: {} -> {} (klien lama di bawah {} akan diminta memperbarui)".format(
        before["minCode"], after["minCode"], after["minCode"]
    ))
PY

# ---------- konfirmasi dari endpoint publik ----------
step "Konfirmasi endpoint publik"
live=$(curl -fsS "$PUBLIC_BASE/hf/app/config.json")
APK_URL="$APK_URL" SHA="$SHA" NEW_VER="$NEW_VER" NEW_CODE="$NEW_CODE" SIZE="$SIZE" \
  LIVE="$live" "$BRIDGE_DIR/venv/bin/python" - <<'PY'
import json
import os
import sys

cfg = json.loads(os.environ["LIVE"])
checks = {
    "latest": (cfg.get("latest"), os.environ["NEW_VER"]),
    "latestCode": (cfg.get("latestCode"), int(os.environ["NEW_CODE"])),
    "apkUrl": (cfg.get("apkUrl"), os.environ["APK_URL"]),
    "sha256": (cfg.get("sha256"), os.environ["SHA"]),
    "sizeBytes": (cfg.get("sizeBytes"), int(os.environ["SIZE"])),
}
bad = [(k, a, b) for k, (a, b) in checks.items() if a != b]
for key, actual, _want in bad:
    print("BEDA {}: live={!r}".format(key, actual))
if bad:
    sys.exit(1)
print("semua cocok dengan config yang ditulis")
print("minCode live: {}".format(cfg.get("minCode")))
PY

STAGE="published"

step "Selesai"
say "APK" "$DIST/$APK_2ABI"
say "sha256" "$SHA"
say "unduh" "$APK_URL"
say "versi wajib" "$MIN_CODE"
printf '\nSemua klien di bawah %s akan berhenti di layar "Perbarui Hoflix" sampai dipasang\n' "$MIN_CODE"
printf 'ulang. Salah naikkan? Turunkan lewat %s/admin -> tab Aplikasi; berlaku <60 detik,\n' "$PUBLIC_BASE"
printf 'tanpa perlu rilis ulang.\n'
