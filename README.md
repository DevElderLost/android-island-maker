# Island Editor (Android · Jetpack Compose)

Editor pulau 256×256 untuk Isle-style island pack, **sepenuhnya native** (tanpa HTML/WebView) dan dengan
**builder pulau di dalam APK** — tidak perlu lagi menjalankan `build_from_spec.py` di PC.

## Tombol export

| Tombol | Hasil | Lokasi |
|---|---|---|
| **💾 Simpan .spec.json** | `<template_id>.spec.json` — bisa diimpor lagi ke editor | `Downloads/` |
| **🏝️ Export as Island** | `<template_id>.zip` — terrain zip pulau siap pakai | `Downloads/` |

Isi zip: `info.yml`, `config.yml`, `whole.biomes`, `whole.ocean`, `whole.rivers`,
`whole.garden`\*, `whole.landmarks`\*, `pois.yml`, `herds.yml`\* (\* hanya jika ada datanya).

Pendaftaran ke `region_templates.json` / `archipelago_templates.json` tetap langkah sisi-server
(datanya tidak ada di HP).

## Struktur kode

```
domain/   logika murni, tanpa UI (mudah dites)
  Model.kt         Doc & data class
  Catalog.kt       katalog natural/landmark/bangunan/hewan (assets/*.json)
  Ops.kt           kuas, penghapus, flip, validasi, zona pemicu
  WaterLayers.kt   whole.ocean + whole.rivers dari grid biome
  SpecCodec.kt     Doc <-> .spec.json
  IslandBuilder.kt spec -> island zip (port dari build_from_spec.py)
  PyJson.kt        serializer ala Python json.dumps (info.yml / config.yml)
data/FileStore.kt  simpan ke Downloads (MediaStore), baca file (SAF), draft autosave
ui/                Compose: EditorViewModel, MapCanvas, Panels, EditorScreen, Theme
```

## Build

CI: `.github/workflows/build-apk.yml` (artifact `island-editor-debug-apk`).
Lokal: Android Studio (JDK 17) atau `gradle assembleDebug`.
Versi: AGP 8.1.4 · Gradle 8.4 · Kotlin 1.9.22 · Compose compiler 1.5.10 · Compose BOM 2024.02.00.

## Seed ke data server (opsional) — SERVER_SEED

1. **Pengaturan → langkah 4 "Folder data server"**: pilih folder `data/` server (yang berisi `islands.json`).
2. Di editor muncul saklar **Seed ke data server**. Bila aktif, **Export as Island** juga mendaftarkan pulau ke server:
   `terrains/<id>.zip`, entri baru di `islands.json` (Id `isleNN` berikutnya, port tertinggi + 100),
   template baru di `assets/region_templates.json` (salinan template sebiome dengan level terdekat),
   dan `islands/<isleNN>/config.json` (salinan config pulau sebiome; `RegionTemplateId` diganti).
3. Aman diulang: entri yang sudah ada tidak ditimpa (hanya zip terrain yang diganti). Restart server setelahnya.

Catatan: `Spawn`/`Zones` di config baru hanya salinan pulau dasar — sesuaikan hewannya bila perlu.
`archipelago_templates.json` tidak disentuh.
