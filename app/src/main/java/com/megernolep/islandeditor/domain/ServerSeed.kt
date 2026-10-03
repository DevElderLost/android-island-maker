package com.megernolep.islandeditor.domain

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * SERVER_SEED — seed opsional ke folder `data/` server offline saat "Export as Island".
 *
 * Yang dikerjakan (idempoten, boleh diulang; entri yang sudah ada TIDAK ditimpa kecuali zip terrain):
 *  1. terrains/<template_id>.zip          <- zip pulau hasil export (selalu diganti)
 *  2. islands.json                        <- entri pulau baru (Id isleNN berikutnya, port = port tertinggi + 100)
 *  3. assets/region_templates.json        <- template region baru = salinan template sebiome dengan level terdekat
 *  4. islands/<isleNN>/config.json        <- salinan config pulau sebiome (MinLevel terdekat), RegionTemplateId diganti
 *
 * Biome pulau baru dibaca dari `lake_biome` di info.yml zip. Pulau lama dicocokkan lewat info.yml terrain-nya,
 * atau biome_effects template region-nya bila zip terrain tidak ada.
 * Tanpa dependensi Android (hanya org.json + java) supaya mudah dites. Server harus di-restart agar data dimuat.
 */
object ServerSeed {
    class Result(val lines: List<String>, val warnings: List<String>)

    private val ID_RE = Regex("^[A-Za-z0-9_.-]+$")
    private val ISLE_RE = Regex("^isle(\\d+)$")
    private val REGION_TPL_RE = Regex("\"RegionTemplateId\"\\s*:\\s*\"([^\"]*)\"")
    private val HERD_TPL_RE = Regex("\"Template\"\\s*:\\s*\"([^\"]+)\"")
    private val LAKE_RE = Regex("\"lake_biome\"\\s*:\\s*\"([^\"]+)\"")

    /** Folder yang memuat islands.json: [dir] sendiri atau [dir]/data. */
    fun findRoot(dir: File): File? =
        listOf(dir, File(dir, "data")).firstOrNull { File(it, "islands.json").isFile }

    private fun writeAtomic(f: File, bytes: ByteArray) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) {
            f.writeBytes(bytes)
            tmp.delete()
        }
    }

    /** lake_biome dari info.yml di dalam zip terrain (null kalau tidak ada / zip rusak). */
    private fun lakeBiomeOf(zip: ByteArray): String? = try {
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            var e = z.nextEntry
            while (e != null && e.name != "info.yml") e = z.nextEntry
            if (e == null) null else LAKE_RE.find(z.readBytes().toString(Charsets.UTF_8))?.groupValues?.get(1)
        }
    } catch (e: Exception) {
        null
    }

    private fun templateOf(configText: String): String? =
        REGION_TPL_RE.find(configText)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
            ?: HERD_TPL_RE.find(configText)?.groupValues?.get(1)

    private fun nextIslandId(islands: List<JSONObject>): String {
        val max = islands.mapNotNull { ISLE_RE.matchEntire(it.optString("Id"))?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        return "isle%02d".format(max + 1)
    }

    private fun nextGatewayPort(islands: List<JSONObject>): Int {
        val used = HashSet<Int>()
        for (o in islands) { used.add(o.optInt("GatewayPort")); used.add(o.optInt("GamePort")) }
        var gw = (islands.maxOfOrNull { it.optInt("GatewayPort") } ?: 8090) + 100
        while (gw in used || gw + 1 in used) gw += 100
        return gw
    }

    /** Sisipkan "key": value di akhir objek JSON tanpa menulis ulang isi lainnya. */
    private fun insertKey(text: String, key: String, valueJson: String): String {
        val end = text.lastIndexOf('}')
        require(end >= 0) { "format JSON tidak dikenali" }
        val before = text.substring(0, end).trimEnd()
        val comma = if (before.endsWith("{")) "" else ","
        return before + comma + "\n  " + JSONObject.quote(key) + ": " + valueJson + "\n}\n"
    }

    private fun setRegionTemplateId(text: String, tid: String): String {
        val m = REGION_TPL_RE.find(text)
        val repl = "\"RegionTemplateId\": " + JSONObject.quote(tid)
        if (m != null) return text.substring(0, m.range.first) + repl + text.substring(m.range.last + 1)
        val i = text.indexOf('{')
        require(i >= 0) { "config.json dasar tidak valid" }
        return text.substring(0, i + 1) + "\n  " + repl + "," + text.substring(i + 1)
    }

    /** @throws IllegalArgumentException kalau folder server / template_id tidak valid (pesan siap tampil). */
    fun apply(serverDir: File, templateId: String, zip: ByteArray, level: Int): Result {
        require(ID_RE.matches(templateId)) {
            "template_id '$templateId' memuat karakter yang tidak aman untuk nama file server (pakai huruf/angka/_ . -)."
        }
        val root = findRoot(serverDir)
            ?: throw IllegalArgumentException("islands.json tidak ditemukan di ${serverDir.absolutePath} (pilih folder data server).")
        val lines = ArrayList<String>()
        val warns = ArrayList<String>()
        val newBiome = lakeBiomeOf(zip)

        // 1) terrain zip
        val terrainFile = File(root, "terrains/$templateId.zip")
        val replaced = terrainFile.exists()
        writeAtomic(terrainFile, zip)
        lines.add("terrains/$templateId.zip ${if (replaced) "diganti" else "dibuat"}")

        // 2) islands.json
        val islandsFile = File(root, "islands.json")
        val islandsJson = JSONObject(islandsFile.readText(Charsets.UTF_8))
        val arr = islandsJson.optJSONArray("Islands")
            ?: throw IllegalArgumentException("islands.json tidak punya array \"Islands\".")
        val islands = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        val existing = islands.firstOrNull { it.optString("Terrain") == templateId }
        val islandId: String
        if (existing != null) {
            islandId = existing.optString("Id")
            lines.add("islands.json: $islandId sudah memakai terrain ini (tidak diubah)")
        } else {
            islandId = nextIslandId(islands)
            val gw = nextGatewayPort(islands)
            val host = islands.lastOrNull()?.optString("Host")?.takeIf { it.isNotEmpty() } ?: "127.0.0.1"
            val minLv = level.coerceAtLeast(1)
            val entry = JSONObject()
            entry.put("Id", islandId)
            entry.put("Name", "Pulau $templateId")
            entry.put("Terrain", templateId)
            entry.put("MinLevel", minLv)
            entry.put("MaxLevel", minLv + 10)
            entry.put("RequiredLevel", (minLv - 2).coerceAtLeast(1))
            entry.put("Host", host)
            entry.put("GatewayPort", gw)
            entry.put("GamePort", gw + 1)
            entry.put("Address", "$host:$gw")
            arr.put(entry)
            writeAtomic(islandsFile, (islandsJson.toString(2) + "\n").toByteArray(Charsets.UTF_8))
            lines.add("islands.json: $islandId ditambahkan (level $minLv-${minLv + 10}, port $gw/${gw + 1})")
        }

        // 3) bahan dasar dari data server
        val regionFile = File(root, "assets/region_templates.json")
        val regionText = if (regionFile.isFile) regionFile.readText(Charsets.UTF_8) else null
        val regions = regionText?.let { JSONObject(it) }

        fun biomesOf(tpl: String?): Set<String> {
            val be = if (tpl == null) null else regions?.optJSONObject(tpl)?.optJSONObject("biome_effects")
            return be?.keys()?.asSequence()?.toSet() ?: emptySet()
        }

        fun islandBiome(o: JSONObject): String? {
            val tf = File(root, "terrains/${o.optString("Terrain")}.zip")
            if (tf.isFile) lakeBiomeOf(tf.readBytes())?.let { return it }
            val cfg = File(root, "islands/${o.optString("Id")}/config.json")
            val tpl = if (cfg.isFile) templateOf(cfg.readText(Charsets.UTF_8)) else null
            return biomesOf(tpl).firstOrNull()
        }

        val candidates = islands.filter { it.optString("Id") != islandId && File(root, "islands/${it.optString("Id")}/config.json").isFile }
        val sameBiome = if (newBiome != null) candidates.filter { islandBiome(it) == newBiome } else emptyList()
        val baseId = sameBiome.ifEmpty { candidates }.minByOrNull { Math.abs(it.optInt("MinLevel") - level) }?.optString("Id")
        val baseText = baseId?.let { File(root, "islands/$it/config.json").readText(Charsets.UTF_8) }
        if (baseId != null && sameBiome.isEmpty()) {
            warns.add("Tidak ada pulau sebiome${if (newBiome != null) " ($newBiome)" else ""} di server — config dasar dari $baseId; Spawn/Zones perlu disesuaikan.")
        }

        // 4) region_templates.json
        var regionOk = false
        if (regions == null || regionText == null) {
            warns.add("assets/region_templates.json tidak ada — region template tidak didaftarkan.")
        } else if (regions.has(templateId)) {
            regionOk = true
            lines.add("region_templates.json: $templateId sudah ada (tidak diubah)")
        } else {
            val baseTpl = if (newBiome == null) null else regions.keys().asSequence()
                .filter { newBiome in biomesOf(it) }
                .sortedWith(compareBy<String>({ biomesOf(it).size }, { Math.abs((regions.optJSONObject(it)?.optInt("level") ?: 0) - level) }, { it }))
                .firstOrNull()
            if (baseTpl == null) {
                warns.add("Tidak ada template region sebiome${if (newBiome != null) " ($newBiome)" else ""} — template baru tidak ditambahkan; config pulau memakai template dasar apa adanya.")
            } else {
                val copy = JSONObject(regions.getJSONObject(baseTpl).toString())
                copy.put("level", level)
                val newText = insertKey(regionText, templateId, copy.toString())
                check(JSONObject(newText).has(templateId)) { "region_templates.json hasil sisip tidak valid — dibatalkan." }
                writeAtomic(regionFile, newText.toByteArray(Charsets.UTF_8))
                regionOk = true
                lines.add("region_templates.json: $templateId ditambahkan (salinan $baseTpl, level $level)")
            }
        }

        // 5) islands/<isleNN>/config.json
        val cfgFile = File(root, "islands/$islandId/config.json")
        if (cfgFile.exists()) {
            lines.add("islands/$islandId/config.json sudah ada (tidak ditimpa)")
        } else if (baseText == null) {
            warns.add("Tidak ada config pulau lain untuk dijadikan dasar — islands/$islandId/config.json TIDAK dibuat, salin manual.")
        } else {
            val text = if (regionOk) setRegionTemplateId(baseText, templateId) else baseText
            writeAtomic(cfgFile, text.toByteArray(Charsets.UTF_8))
            lines.add("islands/$islandId/config.json dibuat (salinan $baseId${if (regionOk) ", RegionTemplateId=$templateId" else ""})")
            lines.add("Spawn/Zones disalin dari $baseId — sesuaikan hewan pulau baru bila perlu.")
        }
        lines.add("Restart server agar data baru dimuat.")
        return Result(lines, warns)
    }
}
