<div align="center">

  <img src="https://nuvio.tv/assets/nuvio-app-logo-wordmark.webp" alt="Nuvio" width="320" />

  <p>
    A free, open-source media app for your phone, your desktop, and the TV you already own.
    <br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  [Website](https://nuvio.tv) · [GitHub releases](https://github.com/NuvioMedia/NuvioMobile/releases/latest) · [Support Nuvio](https://nuvio.tv/support)

</div>

## Get Nuvio Mobile

- [Android on Google Play](https://play.google.com/store/apps/details?id=com.nuvio.app)
- [Android APK](https://github.com/NuvioMedia/NuvioMobile/releases/latest)
- iOS via AltStore or SideStore: add [this source URL](https://raw.githubusercontent.com/NuvioMedia/NuvioMobile/cmp-rewrite/store.json) in the app's Sources section, then install Nuvio.

## Build from source

```bash
git clone https://github.com/NuvioMedia/NuvioMobile.git
cd NuvioMobile
```

### Android

Android development requires Android Studio and the Android SDK.

```bash
./gradlew :androidApp:assembleFullDebug
```

### iOS

iOS development requires macOS and Xcode.

```bash
env NUVIO_IOS_DISTRIBUTION=full xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath build/ios-derived-full-simulator \
  CODE_SIGNING_ALLOWED=NO \
  build
```

The shared app is built with Kotlin Multiplatform and Compose Multiplatform.

## Rilis Android

Satu perintah, dari build sampai config bridge:

```bash
scripts/release.sh --notes "apa yang berubah di rilis ini"
```

Yang dikerjakan skrip, berurutan: naikkan `MARKETING_VERSION` + `CURRENT_PROJECT_VERSION`
(patch +1, versionCode selalu monoton), `:androidApp:assembleFullRelease`, salin 3 APK
ke `dist/`, jalankan `scripts/verify-apk.sh` untuk ketiganya, tolak kalau versi di
dalam APK tidak sama dengan yang akan ditulis ke config, buat GitHub Release
`v<versi>`, unggah 3 aset, cocokkan `content-length` dan SHA-256 aset publik dengan
berkas lokal, lalu tulis `app_config.json` di bridge dan konfirmasi lewat
`GET /hf/app/config.json`.

Opsi: `--keep-min`, `--skip-build`, `--no-bump`, `--min-code <kode>`, `--no-deep-verify`, `--dry-run`.

`minCode` **naik otomatis** ke versi rilis, jadi setiap build baru mengunci klien lama
di layar "Perbarui Hoflix" tanpa langkah manual. Pembatal per rilis: `--keep-min`
(mis. perbaikan teks yang tidak perlu memaksa semua orang memasang ulang).

Pengaman yang melekat:

- `minCode` tidak pernah lebih besar dari versi rilis — kalau iya, app yang baru
  dipasang pun langsung terkunci;
- `minCode` baru ditulis setelah APK lolos verifikasi dan aset publik terbukti
  identik (SHA-256 + ukuran);
- peringatan dicetak saat ambang melewati `4.7.0`, karena klien di bawahnya belum
  punya layar perbarui dan harus dipasang manual sekali;
- salah naikkan bisa dibatalkan dari tab Aplikasi di panel admin, berlaku <60 detik,
  tanpa rilis ulang.

```bash
scripts/release.sh --keep-min          # rilis tanpa memaksa update
scripts/release.sh --min-code 4700000  # patok versi wajib ke angka tertentu
```

## License

[GNU General Public License v3.0](./LICENSE)
