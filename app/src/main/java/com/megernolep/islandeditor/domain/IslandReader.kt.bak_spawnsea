package com.megernolep.islandeditor.domain

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.ZipInputStream

/**
 * Kebalikan [IslandBuilder]: terrain zip pulau -> spec JSON (format .spec.json versi 2, siap di-[SpecCodec.decode]).
 *
 * Isi zip yang dibaca: whole.biomes, whole.garden (6 byte/natural), whole.landmarks (16 byte/landmark),
 * info.yml + config.yml (JSON/flow-style), pois.yml, herds.yml. whole.ocean / whole.rivers diabaikan
 * (dihitung ulang dari biome).
 */
object IslandReader {
    class Result(val spec: JSONObject, val suggestedId: String, val details: List<String>, val warnings: List<String>)

    private const val MAX_ENTRY = 32 * 1024 * 1024

    private val TYPE_BY_THEME = mapOf(
        "temperate" to "temperate_forest", "tropical" to "tropical_forest", "desert" to "desert",
        "tundra" to "tundra", "snow" to "snow_field", "snowfields" to "snow_field",
        "grassland" to "grassland", "swamp" to "swamp_mud", "volcanic" to "volcanic",
    )
    private val TYPE_BY_CODE = listOf(
        "temperate_forest", "tropical_forest", "desert", "tundra", "snow_field", "grassland", "swamp_mud", "volcanic",
    )

    private val PAIR = Regex("""\[\s*(\d+)\s*,\s*(\d+)\s*]""")
    private val CAMP = Regex("""entity_type:\s*(\d+)\s*\n\s*tile:\s*\[\s*(\d+)\s*,\s*(\d+)\s*]""")
    private val GROUP = Regex("""^ {2}(\w+):\s*$""")

    /** @throws IllegalArgumentException dengan pesan siap tampil. */
    fun readZip(input: InputStream, zipName: String, cat: Catalog): Result {
        // ---- baca semua entri ke memori (dengan batas ukuran) ----
        val files = HashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val name = e.name.substringAfterLast('/').substringAfterLast('\\')
                val bos = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = z.read(buf)
                    if (n < 0) break
                    bos.write(buf, 0, n)
                    total += n
                    require(bos.size() <= MAX_ENTRY && total <= 4L * MAX_ENTRY) { "Isi zip terlalu besar." }
                }
                files[name] = bos.toByteArray()
            }
        }
        require(files.isNotEmpty()) { "File ini bukan zip yang valid (atau kosong)." }

        val biomes = files["whole.biomes"] ?: throw IllegalArgumentException("Bukan zip pulau: whole.biomes tidak ada.")
        require(biomes.size == W * H) { "whole.biomes berukuran ${biomes.size} byte, seharusnya ${W * H}." }
        val info = parseJsonLike(files["info.yml"] ?: throw IllegalArgumentException("Bukan zip pulau: info.yml tidak ada."), "info.yml")
        val config = files["config.yml"]?.let { try { parseJsonLike(it, "config.yml") } catch (e: Exception) { null } }

        val warnings = ArrayList<String>()

        // ---- id, level, tipe ----
        val id = info.optString("region_template", "").ifBlank { zipName.removeSuffix(".zip").removeSuffix(".ZIP") }
            .trim().ifEmpty { "pulau_import" }
        val level = Regex("^ri(\\d+)").find(id)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 99)
        warnings.add("Level & peran tidak ada di zip: level " + (if (level != null) "diambil dari ID ($level)" else "diisi 20") + ", peran 4. Ubah di editor bila perlu.")

        val theme = config?.optString("theme", "") ?: ""
        var type = TYPE_BY_THEME[theme] ?: TYPE_BY_THEME[info.optString("tile_set", "")] ?: ""
        if (type.isEmpty() || !cat.types.containsKey(type)) {
            type = guessType(biomes)
            warnings.add("Tipe pulau tidak tercantum di zip; ditebak dari biome dominan: $type.")
        }

        // ---- tabel prefab landmark (index = id) ----
        val prefabs = ArrayList<String>()
        info.optJSONArray("landmarks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val idx = o.optInt("id", i)
                if (idx !in 0..4095) continue
                while (prefabs.size <= idx) prefabs.add("")
                prefabs[idx] = o.optString("prefab", "")
            }
        }

        // ---- whole.garden: x, y, entityType (uint16 LE) ----
        val naturals = JSONArray()
        files["whole.garden"]?.let { g ->
            if (g.size % 6 != 0) warnings.add("whole.garden bukan kelipatan 6 byte; sisanya dibuang.")
            val bb = ByteBuffer.wrap(g).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until g.size / 6) {
                val x = bb.getShort(i * 6).toInt() and 0xFFFF
                val y = bb.getShort(i * 6 + 2).toInt() and 0xFFFF
                val et = bb.getShort(i * 6 + 4).toInt() and 0xFFFF
                if (x >= W || y >= H) continue
                naturals.put(JSONObject().put("x", x).put("y", y).put("entityType", et))
            }
        }

        // ---- whole.landmarks: <HHHBhhhBBB (16 byte) ----
        val landmarks = JSONArray()
        var unknownPrefab = 0
        files["whole.landmarks"]?.let { l ->
            val bb = ByteBuffer.wrap(l).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until l.size / 16) {
                val o = i * 16
                val x = bb.getShort(o).toInt() and 0xFFFF
                val y = bb.getShort(o + 2).toInt() and 0xFFFF
                val pid = bb.getShort(o + 4).toInt() and 0xFFFF
                val prefab = prefabs.getOrNull(pid) ?: ""
                if (x >= W || y >= H) continue
                if (prefab.isEmpty()) { unknownPrefab++; continue }
                landmarks.put(
                    JSONObject().put("x", x).put("y", y).put("id", pid).put("prefab", prefab)
                        .put("rotate", bb.get(o + 6).toInt() and 0xFF)
                        .put("offsetX", bb.getShort(o + 7).toInt()).put("offsetY", bb.getShort(o + 9).toInt())
                        .put("offsetZ", bb.getShort(o + 11).toInt())
                        .put("scaleX", bb.get(o + 13).toInt() and 0xFF).put("scaleY", bb.get(o + 14).toInt() and 0xFF)
                        .put("scaleZ", bb.get(o + 15).toInt() and 0xFF),
                )
            }
        }
        if (unknownPrefab > 0) warnings.add("$unknownPrefab landmark dilewati (id prefab tidak ada di info.yml).")

        // ---- pois.yml: dermaga + bangunan ----
        val ports = JSONArray()
        val buildings = JSONArray()
        files["pois.yml"]?.let { b ->
            val t = String(b, Charsets.UTF_8)
            for (m in PAIR.findAll(yamlSection(t, "port_points"))) {
                ports.put(JSONObject().put("x", m.groupValues[1].toInt()).put("y", m.groupValues[2].toInt()))
            }
            for (m in CAMP.findAll(yamlSection(t, "camp_artifacts"))) {
                buildings.put(
                    JSONObject().put("entityType", m.groupValues[1].toInt())
                        .put("x", m.groupValues[2].toInt()).put("y", m.groupValues[3].toInt()),
                )
            }
        }

        // ---- herds.yml: hanya posisi + grup ----
        val herds = JSONArray()
        var droppedHerds = 0
        files["herds.yml"]?.let { b ->
            var grp: String? = null
            for (ln in String(b, Charsets.UTF_8).lines()) {
                val gm = GROUP.matchEntire(ln.trimEnd())
                if (gm != null) { grp = gm.groupValues[1]; continue }
                val tm = PAIR.find(ln)
                val g = grp
                if (tm == null || g == null || !ln.contains("tile")) continue
                val row = cat.speciesFor(type, g, false).firstOrNull()
                val animal = row?.let { cat.animals[it.id] }
                if (row == null || animal == null) { droppedHerds++; continue }
                herds.put(
                    JSONObject().put("group", g).put("x", tm.groupValues[1].toInt()).put("y", tm.groupValues[2].toInt())
                        .put("entityType", row.id).put("level", if (row.level != 0) row.level else animal.lvMin),
                )
            }
        }
        if (herds.length() > 0) {
            warnings.add("${herds.length()} titik herd diimpor, tetapi jenis hewannya tidak tersimpan di zip: diisi hewan pertama template '$type' per grup. Ganti lewat mode Herd.")
        }
        if (droppedHerds > 0) warnings.add("$droppedHerds titik herd dilewati (grup tidak ada di template tipe '$type').")

        // ---- rakit spec ----
        fun arr(key: String) = info.optJSONArray(key) ?: JSONArray()
        val globals = arr("global_landmarks")
        val spec = JSONObject()
            .put("template_id", id).put("level", level ?: 20).put("role", 4).put("version", 2)
            .put("island_type", type).put("width", W).put("height", H)
            .put("biomes_b64", Base64.getEncoder().encodeToString(biomes))
            .put("naturals", naturals).put("landmarks", landmarks).put("global_landmarks", globals)
            .put("landmark_prefabs", JSONArray(prefabs))
            .put("port_points", ports).put("herds", herds).put("buildings", buildings)
            .put("npcs", arr("npcs")).put("static_animals", arr("static_animals"))
            .put("tutorial_triggers", arr("tutorial_triggers")).put("thorn_bushes", arr("thorn_bushes"))
        info.optJSONObject("spawn_point")?.let { spec.put("spawn_point", it) }
        info.optJSONObject("raft_point")?.let { spec.put("raft_point", it) }

        val details = listOf(
            "tipe $type · ${naturals.length()} natural · ${landmarks.length() + globals.length()} landmark · " +
                "${arr("npcs").length()} npc · ${arr("static_animals").length()} hewan-objek · ${herds.length()} herd · " +
                "${buildings.length()} bangunan",
            "Lapisan air (ocean/rivers) dihitung ulang dari biome.",
        )
        return Result(spec, id, details, warnings)
    }

    private fun parseJsonLike(bytes: ByteArray, name: String): JSONObject {
        val t = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF").trim().removePrefix("---").trim()
        return try {
            JSONObject(t)
        } catch (e: Exception) {
            throw IllegalArgumentException("$name bukan format JSON/flow-style. Hanya zip buatan editor ini atau build_from_spec.py yang didukung.")
        }
    }

    /** Potong satu bagian level-atas YAML sederhana: dari "key:" sampai sebelum kunci level-atas berikutnya. */
    private fun yamlSection(text: String, key: String): String {
        val sb = StringBuilder()
        var inside = false
        for (ln in text.lines()) {
            val top = ln.isNotEmpty() && !ln[0].isWhitespace() && ln[0] != '-' && ln.contains(':')
            if (top) {
                if (inside) break
                if (ln.startsWith("$key:")) { inside = true; sb.append(ln).append('\n') }
                continue
            }
            if (inside) sb.append(ln).append('\n')
        }
        return sb.toString()
    }

    private fun guessType(biomes: ByteArray): String {
        val counts = IntArray(8)
        for (b in biomes) {
            val c = b.toInt() and 0x3F
            if (c in 0..7) counts[c]++
        }
        var best = -1
        for (c in 0..7) if (counts[c] > 0 && (best < 0 || counts[c] > counts[best])) best = c
        return if (best < 0) "grassland" else TYPE_BY_CODE[best]
    }
}
