# Island Editor (Android offline)

Wrapper WebView offline buat `island_editor.html` (editor pulau Durango) — target
**Android 10 (API 29)**, minSdk=targetSdk=29. Semua HTML/JS-nya ke-bundle di
`app/src/main/assets/island_editor.html`, jadi jalan tanpa internet sama sekali.

## Struktur
```
app/
  build.gradle                 - config Gradle module, minSdk/targetSdk 29
  src/main/
    AndroidManifest.xml
    java/.../MainActivity.java - WebView + jembatan simpan/buka file
    assets/island_editor.html  - editor-nya (salinan dari artifact Claude)
.github/workflows/build-apk.yml - CI, hasil APK jadi artifact download
```

## Setup repo
```
git init
git add .
git commit -m "island editor android wrapper"
git remote add origin <url repo kamu>
git push -u origin main
```
Lalu buka tab **Actions** di GitHub → "Build Island Editor APK" → Run workflow.
Hasil APK-nya ada di artifact run itu (debug-signed, cukup buat sideload sendiri).

## Kenapa perlu wrapper native (bukan cuma buka HTML-nya di Chrome offline)
- WebView default **gak dukung** `<input type=file>` tanpa `onShowFileChooser`
  di-override manual → makanya ada `MainActivity.java`.
- WebView **gak reliable** buat blob download (`<a download>`) → tombol
  "Export spec.json" di HTML manggil `AndroidBridge.saveSpec()` yang nulis
  langsung ke folder Downloads lewat `MediaStore` (aman buat scoped storage
  Android 10, gak butuh permission storage).

## Alur kerja penuh
1. Buka app ini di HP (offline, gak perlu koneksi).
2. Lukis pulau → **Export spec.json** → kesimpen di `Downloads/`.
3. Pindahin file itu ke folder `island_specs/` di **repo gen-island** kamu
   (langkah manual — transfer file lewat kabel/cloud/git app, gak otomatis).
4. Di repo gen-island, jalanin workflow `gen-island-from-spec.yml` (atau
   `build_from_spec.py` lokal) buat bikin terrain zip + daftarin ke
   `region_templates.json`/`archipelago_templates.json`.

## Update HTML editor
Kalau editor `island_editor.html`-nya direvisi lagi, tinggal timpa file di
`app/src/main/assets/island_editor.html`, commit, workflow otomatis build ulang
(trigger `push` udah diset di `.github/workflows/build-apk.yml`).

## Kalau AGP/Gradle version di build.gradle root udah ketinggalan
`com.android.tools.build:gradle:8.1.4` + Gradle 8.4 dipilih karena stabil dan
kompatibel JDK 17 di titik ini dibikin. Kalau workflow gagal karena versi Android
Gradle Plugin ketinggalan (Google sering update tahunan), naikin versi classpath
di `build.gradle` root + `gradle-version` di workflow barengan — cek kombinasi
kompatibelnya di halaman rilis AGP.
