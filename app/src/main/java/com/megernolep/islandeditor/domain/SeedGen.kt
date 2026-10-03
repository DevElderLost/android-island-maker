package com.megernolep.islandeditor.domain

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * SEED_GEN — pembuat pulau dari KODE SEED (seperti seed dunia Minecraft).
 *
 * Aturan seed (sama dengan Minecraft):
 *  - teks angka ("12345", "-77")  -> dipakai apa adanya sebagai Long
 *  - teks lain ("pulau kita")     -> String.hashCode()
 *  - kosong                       -> acak (kode yang dipakai ditampilkan & disimpan di spec supaya bisa diulang)
 *
 * Seed + tipe + ukuran yang sama  => bentuk daratan, pantai, danau, sungai, batu, dermaga, spawn & rakit SELALU sama.
 * Isi pulau (natural / landmark / herd) mengikuti data referensi:
 *  1. terrain asli di folder terrains di data server (file .zip) yang lake_biome-nya sama dengan tipe pulau (kalau folder server diset), atau
 *  2. katalog bawaan editor (natural per biome, landmark Cliff, spesies per grup).
 * Jadi isi pulau bisa berbeda antara HP yang punya data server dan yang tidak.
 *
 * Tanpa dependensi Android (org.json + java saja) supaya mudah dites.
 */
object SeedGen {
    class Options(
        val seed: String,
        val type: String,
        val level: Int,
        val templateId: String,
        /** 0 = kecil, 1 = sedang, 2 = besar */
        val size: Int = 1,
        /** isi natural + landmark + herd; false = hanya medan, dermaga, spawn, rakit */
        val populate: Boolean = true,
        /** folder data server (boleh null) */
        val serverDir: File? = null,
    )

    class Result(val doc: Doc, val seedText: String, val seedValue: Long, val notes: List<String>)

    val SIZE_LABELS = listOf("Kecil", "Sedang", "Besar")

    // ------------------------------------------------------------------ seed & RNG
    private const val GOLDEN = -7046029254386353131L
    private const val MIX1 = -4658895280553007687L
    private const val MIX2 = -7723592293110705685L

    private fun mix(z0: Long): Long {
        var z = z0
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    private fun sub(seed: Long, salt: Int): Long = mix(seed + (salt.toLong() + 1L) * GOLDEN)

    fun seedValue(text: String): Long {
        val t = text.trim()
        return t.toLongOrNull() ?: t.hashCode().toLong()
    }

    fun randomSeedText(): String =
        (mix(System.nanoTime() xor (System.currentTimeMillis() shl 17)) ushr 33).toString()

    private class Rng(seed: Long) {
        private var s = seed
        fun nextLong(): Long { s += GOLDEN; return mix(s) }
        fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))
        fun nextInt(n: Int): Int = if (n <= 1) 0 else ((nextLong() ushr 33) % n).toInt()
        fun range(a: Int, b: Int): Int = a + nextInt(b - a + 1)
        fun <T> shuffle(list: MutableList<T>) {
            for (i in list.size - 1 downTo 1) { val j = nextInt(i + 1); val t = list[i]; list[i] = list[j]; list[j] = t }
        }
    }

    private class Noise(private val seed: Long) {
        private fun lat(ix: Int, iy: Int): Double =
            (mix(mix(seed + ix.toLong() * GOLDEN) + iy.toLong()) ushr 11) * (1.0 / (1L shl 53))

        fun value(x: Double, y: Double): Double {
            val x0 = Math.floor(x).toInt(); val y0 = Math.floor(y).toInt()
            val fx = x - x0; val fy = y - y0
            val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)
            val a = lat(x0, y0); val b = lat(x0 + 1, y0)
            val c = lat(x0, y0 + 1); val d = lat(x0 + 1, y0 + 1)
            val top = a + (b - a) * sx; val bot = c + (d - c) * sx
            return top + (bot - top) * sy
        }

        /** 0..1 */
        fun fbm(x: Double, y: Double, oct: Int): Double {
            var f = 1.0; var amp = 1.0; var sum = 0.0; var norm = 0.0
            for (o in 0 until oct) {
                sum += value(x * f + o * 17.31, y * f + o * 9.77) * amp
                norm += amp; f *= 2.0; amp *= 0.5
            }
            return sum / norm
        }
    }

    private val DX4 = intArrayOf(1, -1, 0, 0)
    private val DY4 = intArrayOf(0, 0, 1, -1)
    private val DX8 = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
    private val DY8 = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

    // ------------------------------------------------------------------ profil referensi
    private class LmRec(
        val prefab: String, val rotate: Int, val ox: Int, val oy: Int, val oz: Int,
        val sx: Int, val sy: Int, val sz: Int,
    )

    private class Pick(val ids: IntArray, val cum: DoubleArray) {
        fun pick(r: Double): Int {
            val t = r * cum[cum.size - 1]
            for (i in cum.indices) if (t < cum[i]) return ids[i]
            return ids[ids.size - 1]
        }
    }

    private class Profile(
        val label: String,
        /** kode biome -> natural per tile */
        val density: Map<Int, Double>,
        val picks: Map<Int, Pick>,
        val landmarks: List<LmRec>,
        val landmarkCount: Int,
        val herdCount: Map<String, Int>,
    )

    private val LAKE_RE = Regex("\"lake_biome\"\\s*:\\s*\"([^\"]+)\"")
    private val HERD_GROUP_RE = Regex("^\\s{2}(\\w+):\\s*$")

    private fun readZipEntries(bytes: ByteArray, names: Set<String>): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name in names) out[e.name] = z.readBytes()
                    e = z.nextEntry
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun le16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or (b[o + 1].toInt() shl 8)

    /** Baca terrain asli di folder server yang lake_biome-nya = [lake]. null kalau tidak ada yang cocok. */
    private fun loadProfile(serverDir: File, lake: String, notes: MutableList<String>): Profile? {
        val root = ServerSeed.findRoot(serverDir) ?: run {
            notes.add("Folder data server tidak berisi islands.json — memakai katalog bawaan editor.")
            return null
        }
        val files = File(root, "terrains").listFiles { f -> f.isFile && f.name.endsWith(".zip") }?.sortedBy { it.name }
            ?: return null
        val tileCount = IntArray(64)
        val natCount = HashMap<Int, HashMap<Int, Int>>()
        val lms = ArrayList<LmRec>()
        val herdSum = HashMap<String, Int>()
        val seen = HashSet<Int>()
        var used = 0
        val names = HashSet<String>()
        var lmTotal = 0
        for (f in files) {
            if (used >= 8) break
            val ent = readZipEntries(f.readBytes(), setOf("info.yml", "whole.biomes", "whole.garden", "whole.landmarks", "herds.yml"))
            val info = ent["info.yml"] ?: continue
            val bio = ent["whole.biomes"] ?: continue
            if (bio.size != W * H) continue
            val infoText = info.toString(Charsets.UTF_8)
            if (LAKE_RE.find(infoText)?.groupValues?.get(1) != lake) continue
            val garden = ent["whole.garden"] ?: ByteArray(0)
            if (!seen.add(bio.contentHashCode() * 31 + garden.size)) continue     // terrain kembar (alias) dilewati
            used++; names.add(f.nameWithoutExtension)

            for (v in bio) { val raw = v.toInt() and 0xFF; if ((raw and FLAGS_ROCK) != FLAGS_ROCK) tileCount[raw and 0x3F]++ }
            var i = 0
            while (i + 6 <= garden.size) {
                val x = le16(garden, i); val y = le16(garden, i + 2); val et = le16(garden, i + 4)
                i += 6
                if (x !in 0 until W || y !in 0 until H) continue
                val raw = bio[y * W + x].toInt() and 0xFF
                if ((raw and FLAGS_ROCK) == FLAGS_ROCK) continue
                natCount.getOrPut(raw and 0x3F) { HashMap() }.merge(et, 1, Int::plus)
            }

            val prefabs = HashMap<Int, String>()
            try {
                val arr = JSONObject(infoText).optJSONArray("landmarks")
                if (arr != null) for (k in 0 until arr.length()) {
                    val o = arr.optJSONObject(k) ?: continue
                    prefabs[o.optInt("id", k)] = o.optString("prefab", "")
                }
            } catch (_: Exception) {
            }
            val lm = ent["whole.landmarks"] ?: ByteArray(0)
            var j = 0
            while (j + 16 <= lm.size) {
                val pid = le16(lm, j + 4)
                val prefab = prefabs[pid]
                if (!prefab.isNullOrEmpty()) {
                    lms.add(LmRec(
                        prefab, lm[j + 6].toInt() and 0xFF,
                        le16(lm, j + 7).toShort().toInt(), le16(lm, j + 9).toShort().toInt(), le16(lm, j + 11).toShort().toInt(),
                        lm[j + 13].toInt() and 0xFF, lm[j + 14].toInt() and 0xFF, lm[j + 15].toInt() and 0xFF,
                    ))
                    lmTotal++
                }
                j += 16
            }

            ent["herds.yml"]?.toString(Charsets.UTF_8)?.let { txt ->
                var group: String? = null
                for (line in txt.lines()) {
                    HERD_GROUP_RE.find(line)?.let { group = it.groupValues[1] }
                    if (line.contains("- id:") && group != null) herdSum.merge(group!!, 1, Int::plus)
                }
            }
        }
        if (used == 0) return null

        val density = HashMap<Int, Double>()
        val picks = HashMap<Int, Pick>()
        for ((code, m) in natCount) {
            val tiles = tileCount[code]
            if (tiles < 50) continue
            val total = m.values.sum()
            val d = total.toDouble() / tiles
            if (d < 0.0005) continue
            val top = m.entries.filter { it.key !in EditorData.thornPlantIds }.sortedByDescending { it.value }.take(120)
            if (top.isEmpty()) continue
            var acc = 0.0
            val cum = DoubleArray(top.size) { acc += top[it].value.toDouble(); acc }
            density[code] = d
            picks[code] = Pick(IntArray(top.size) { top[it].key }, cum)
        }
        val herds = herdSum.mapValues { (it.value.toDouble() / used).roundToInt().coerceIn(5, 200) }
        return Profile("terrain asli: ${names.joinToString(", ")}", density, picks, lms, lmTotal / used, herds)
    }

    /** Cadangan tanpa data server: natural per biome dari katalog editor. */
    private fun catalogProfile(cat: Catalog, pre: TypePreset): Profile {
        val density = HashMap<Int, Double>()
        val picks = HashMap<Int, Pick>()
        val catW = mapOf("tree" to 0.35, "bush" to 0.25, "grass" to 0.30, "rock" to 0.10, "cactus" to 0.40)
        for ((code, d) in listOf(pre.land to 0.10, pre.beach to 0.035)) {
            val byCat = HashMap<String, MutableList<NatItem>>()
            for (n in cat.naturals) {
                if (code !in n.biomes || n.id in EditorData.thornPlantIds) continue
                if (n.cat == "rock") {
                    if (code == pre.beach) continue
                    if (EditorData.rockSize(n, emptyMap()) !in setOf("besar", "kecil")) continue
                } else if (n.cat !in catW) continue
                byCat.getOrPut(n.cat) { ArrayList() }.add(n)
            }
            val ids = ArrayList<Int>(); val ws = ArrayList<Double>()
            for ((c, list) in byCat) for (n in list) { ids.add(n.id); ws.add((catW[c] ?: 0.1) / list.size) }
            if (ids.isEmpty()) continue
            var acc = 0.0
            val cum = DoubleArray(ws.size) { acc += ws[it]; acc }
            density[code] = d
            picks[code] = Pick(ids.toIntArray(), cum)
        }
        val lms = cat.landmarks.filter { it.folder.startsWith("Cliff") }
            .map { LmRec(it.prefab, 0, 0, 0, 0, 51, 51, 51) }
        return Profile("katalog bawaan editor", density, picks, lms, 50, emptyMap())
    }

    // ------------------------------------------------------------------ medan
    private class Terrain(
        val raw: ByteArray,        // byte biome final (kode | flag)
        val dist: IntArray,        // jarak (4-arah) ke laut terbuka
    )

    private fun bfs(sources: BooleanArray, passable: (Int) -> Boolean): IntArray {
        val d = IntArray(W * H) { -1 }
        val q = IntArray(W * H)
        var hd = 0; var tl = 0
        for (i in sources.indices) if (sources[i]) { d[i] = 0; q[tl++] = i }
        while (hd < tl) {
            val i = q[hd++]; val x = i % W; val y = i / W
            for (k in 0 until 4) {
                val nx = x + DX4[k]; val ny = y + DY4[k]
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue
                val ni = ny * W + nx
                if (d[ni] >= 0 || !passable(ni)) continue
                d[ni] = d[i] + 1; q[tl++] = ni
            }
        }
        return d
    }

    /** Komponen terhubung (4-arah) dari tile yang `inSet`; mengembalikan label per tile (-1 = bukan anggota) + ukuran. */
    private fun components(inSet: BooleanArray): Pair<IntArray, MutableList<Int>> {
        val label = IntArray(W * H) { -1 }
        val sizes = ArrayList<Int>()
        val q = IntArray(W * H)
        for (s in inSet.indices) {
            if (!inSet[s] || label[s] >= 0) continue
            val id = sizes.size
            var hd = 0; var tl = 0
            q[tl++] = s; label[s] = id
            while (hd < tl) {
                val i = q[hd++]; val x = i % W; val y = i / W
                for (k in 0 until 4) {
                    val nx = x + DX4[k]; val ny = y + DY4[k]
                    if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue
                    val ni = ny * W + nx
                    if (inSet[ni] && label[ni] < 0) { label[ni] = id; q[tl++] = ni }
                }
            }
            sizes.add(tl)
        }
        return label to sizes
    }

    private fun landShape(seed: Long, sizeScale: Double): BooleanArray {
        val rng = Rng(sub(seed, 1))
        val nBase = Noise(sub(seed, 2)); val nWx = Noise(sub(seed, 3)); val nWy = Noise(sub(seed, 4)); val nDet = Noise(sub(seed, 5))
        val cx = W / 2.0 + (rng.nextDouble() - 0.5) * 20
        val cy = H / 2.0 + (rng.nextDouble() - 0.5) * 20
        val aspect = 0.85 + rng.nextDouble() * 0.3
        val v = DoubleArray(W * H)
        for (y in 0 until H) for (x in 0 until W) {
            val wx = (nWx.fbm(x / 64.0, y / 64.0, 3) - 0.5) * 70
            val wy = (nWy.fbm(x / 64.0 + 31.7, y / 64.0 + 17.3, 3) - 0.5) * 70
            val dx = (x + wx - cx) * aspect; val dy = (y + wy - cy) / aspect
            val r = hypot(dx, dy) / 92.0
            var vv = 1.05 - r * 1.15 + (nBase.fbm(x / 38.0, y / 38.0, 5) - 0.5) * 0.55 + (nDet.fbm(x / 9.0, y / 9.0, 2) - 0.5) * 0.18
            val edge = min(min(x, y), min(W - 1 - x, H - 1 - y))
            if (edge < 12) vv -= (12 - edge) * 0.12
            v[y * W + x] = vv
        }
        // ambang dicari supaya luas daratan (+pantai) mendekati target
        val target = ((0.31 * sizeScale * sizeScale).coerceIn(0.18, 0.42) * W * H).toInt()
        var lo = v.min(); var hi = v.max()
        repeat(30) {
            val mid = (lo + hi) / 2
            var c = 0
            for (x in v) if (x > mid) c++
            if (c > target) lo = mid else hi = mid
        }
        var land = BooleanArray(W * H) { v[it] > hi }

        // haluskan duri 1-tile
        val sm = land.copyOf()
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            var n = 0
            for (k in 0 until 8) if (land[(y + DY8[k]) * W + x + DX8[k]]) n++
            val i = y * W + x
            if (land[i] && n <= 2) sm[i] = false else if (!land[i] && n >= 7) sm[i] = true
        }
        land = sm
        // buang pulau kecil
        val (label, sizes) = components(land)
        for (i in land.indices) if (land[i] && sizes[label[i]] < 120) land[i] = false
        return land
    }

    private fun disc(cx: Int, cy: Int, r: Double, f: (Int, Int) -> Unit) {
        val ri = Math.ceil(r).toInt()
        for (dy in -ri..ri) for (dx in -ri..ri) {
            if (hypot(dx.toDouble(), dy.toDouble()) <= r) {
                val x = cx + dx; val y = cy + dy
                if (x in 0 until W && y in 0 until H) f(x, y)
            }
        }
    }

    // ------------------------------------------------------------------ generate
    /** @throws IllegalArgumentException kalau tipe pulau tidak dikenal. */
    fun generate(opt: Options, cat: Catalog): Result {
        val pre = cat.types[opt.type] ?: throw IllegalArgumentException("Tipe pulau '${opt.type}' tidak dikenal.")
        val seedText = opt.seed.trim().ifEmpty { randomSeedText() }
        val seed = seedValue(seedText)
        val notes = ArrayList<String>()
        val sizeScale = when (opt.size) { 0 -> 0.8; 2 -> 1.2; else -> 1.0 }
        val lakeCode = if (pre.lava) 15 else 14
        val oceanCode = pre.ocean

        // ---- 1. daratan, laut terbuka, kolam pedalaman ----
        var land = landShape(seed, sizeScale)
        val notLand = BooleanArray(W * H) { !land[it] }
        val border = BooleanArray(W * H)
        for (i in border.indices) { val x = i % W; val y = i / W; if (!land[i] && (x == 0 || y == 0 || x == W - 1 || y == H - 1)) border[i] = true }
        val seaDist = bfs(border) { notLand[it] }
        val sea = BooleanArray(W * H) { seaDist[it] >= 0 }
        val pocketSet = BooleanArray(W * H) { notLand[it] && !sea[it] }
        val (pLabel, pSizes) = components(pocketSet)
        val lake = BooleanArray(W * H)
        for (i in lake.indices) if (pLabel[i] >= 0) { if (pSizes[pLabel[i]] < 40) land[i] = true else lake[i] = true }

        // jarak ke laut terbuka (untuk pantai, batu, sungai)
        val dist = bfs(sea) { true }

        // ---- 2. kode biome dasar + pantai ----
        val raw = ByteArray(W * H)
        val nBeach = Noise(sub(seed, 6))
        for (i in raw.indices) {
            val x = i % W; val y = i / W
            raw[i] = when {
                sea[i] -> oceanCode
                lake[i] -> lakeCode
                else -> {
                    val bw = 2 + (nBeach.fbm(x / 12.0, y / 12.0, 2) * 3.5).toInt()
                    if (dist[i] <= bw) pre.beach else pre.land
                }
            }.toByte()
        }
        fun code(i: Int) = raw[i].toInt() and 0x3F
        fun isLandTile(i: Int) = code(i) < 11
        fun isRock(i: Int) = (raw[i].toInt() and FLAGS_ROCK) == FLAGS_ROCK

        // ---- 3. danau tambahan ----
        val rngLake = Rng(sub(seed, 7))
        val nLake = Noise(sub(seed, 8))
        val lakeTarget = when (opt.size) { 0 -> rngLake.range(0, 1); 1 -> rngLake.range(1, 2); else -> rngLake.range(1, 3) }
        val centers = ArrayList<IntArray>()
        run {
            val cand = ArrayList<Int>()
            for (i in raw.indices) if (isLandTile(i) && dist[i] >= 16) cand.add(i)
            rngLake.shuffle(cand)
            for (c in cand) {
                if (centers.size >= lakeTarget) break
                val x = c % W; val y = c / W
                if (centers.any { hypot((it[0] - x).toDouble(), (it[1] - y).toDouble()) < 30 }) continue
                centers.add(intArrayOf(x, y))
                val r0 = rngLake.range(4, 8).toDouble()
                disc(x, y, r0 * 1.4) { px, py ->
                    val pi = py * W + px
                    val rr = r0 * (0.75 + 0.5 * nLake.fbm(px / 6.0, py / 6.0, 2))
                    if (isLandTile(pi) && dist[pi] > 8 && hypot((px - x).toDouble(), (py - y).toDouble()) < rr) raw[pi] = lakeCode.toByte()
                }
            }
        }

        // ---- 4. batu / gunung (flag 0xC0) di pedalaman ----
        val rngRock = Rng(sub(seed, 9))
        val nRock = Noise(sub(seed, 10))
        run {
            val cand = ArrayList<Int>(); val score = DoubleArray(W * H)
            for (i in raw.indices) {
                if (!isLandTile(i) || code(i) == pre.beach || dist[i] < 7) continue
                val x = i % W; val y = i / W
                score[i] = nRock.fbm(x / 22.0, y / 22.0, 3) + 0.004 * min(dist[i], 40)
                cand.add(i)
            }
            if (cand.size > 200) {
                val frac = 0.05 + rngRock.nextDouble() * 0.05
                val sorted = cand.map { score[it] }.sortedDescending()
                val thr = sorted[(sorted.size * frac).toInt().coerceIn(0, sorted.size - 1)]
                val mark = BooleanArray(W * H)
                for (i in cand) if (score[i] >= thr) mark[i] = true
                // buang bintik
                for (i in cand) {
                    if (!mark[i]) continue
                    val x = i % W; val y = i / W
                    var n = 0
                    for (k in 0 until 8) { val nx = x + DX8[k]; val ny = y + DY8[k]; if (nx in 0 until W && ny in 0 until H && mark[ny * W + nx]) n++ }
                    if (n >= 3) raw[i] = (raw[i].toInt() or FLAGS_ROCK).toByte()
                }
            }
        }

        // ---- 5. dermaga, spawn, rakit ----
        val rngPoi = Rng(sub(seed, 11))
        fun areaFree(cx: Int, cy: Int, r: Int, needLand: Boolean): Boolean {
            for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
                if (x < 0 || y < 0 || x >= W || y >= H) return false
                val i = y * W + x
                if (isRock(i)) return false
                if (needLand && !isLandTile(i)) return false
            }
            return true
        }
        val ports = ArrayList<Pt>()
        run {
            val cand = ArrayList<Int>()
            for (i in raw.indices) if (code(i) == pre.beach && dist[i] == 1 && !isRock(i)) cand.add(i)
            rngPoi.shuffle(cand)
            val wantPorts = if (opt.size == 2) 2 else 1
            for (strict in listOf(true, false)) {
                for (c in cand) {
                    if (ports.size >= wantPorts) break
                    val x = c % W; val y = c / W
                    if (!areaFree(x, y, 1, strict)) continue
                    if (ports.any { hypot((it.x - x).toDouble(), (it.y - y).toDouble()) < 60 }) continue
                    ports.add(Pt(x, y))
                }
                if (ports.isNotEmpty()) break
            }
        }
        var spawn: Pt? = null
        var raft: Pt? = null
        if (ports.isEmpty()) {
            notes.add("Tidak ada tempat dermaga yang cocok — dermaga/spawn/rakit tidak dibuat (letakkan manual).")
        } else {
            val p0 = ports[0]
            val ring = ArrayList<Pt>()
            for (dy in -30..30) for (dx in -30..30) {
                val d = hypot(dx.toDouble(), dy.toDouble())
                if (d in 6.0..30.0) ring.add(Pt(p0.x + dx, p0.y + dy))
            }
            val ringSorted = ring.map { it to (hypot((it.x - p0.x).toDouble(), (it.y - p0.y).toDouble()).toInt() / 4 * 1000 + rngPoi.nextInt(1000)) }
                .sortedBy { it.second }.map { it.first }
            spawn = ringSorted.firstOrNull { areaFree(it.x, it.y, 4, true) && dist[it.y * W + it.x] >= 3 }
                ?: ringSorted.firstOrNull { areaFree(it.x, it.y, 4, true) }
            if (spawn == null) notes.add("Tidak ada area darat 9x9 bebas batu dekat dermaga — spawn tidak dibuat.")
            val rr = ArrayList<Pt>()
            for (dy in -16..16) for (dx in -16..16) {
                val d = hypot(dx.toDouble(), dy.toDouble())
                if (d in 3.0..16.0) rr.add(Pt(p0.x + dx, p0.y + dy))
            }
            val rrSorted = rr.map { it to (hypot((it.x - p0.x).toDouble(), (it.y - p0.y).toDouble()).toInt() / 3 * 1000 + rngPoi.nextInt(1000)) }
                .sortedBy { it.second }.map { it.first }
            raft = rrSorted.firstOrNull { c ->
                var ok = c.x >= 0 && c.y >= 0 && c.x + 3 < W && c.y + 3 < H
                if (ok) loop@ for (yy in c.y..c.y + 3) for (xx in c.x..c.x + 3) if (!sea[yy * W + xx]) { ok = false; break@loop }
                ok
            }
            if (raft == null) notes.add("Tidak ada laut 4x4 bebas dekat dermaga — rakit tidak dibuat.")
        }
        // jaga zona dermaga/spawn tetap bebas batu
        fun clearRock(cx: Int, cy: Int, r: Int) {
            for (y in cy - r..cy + r) for (x in cx - r..cx + r) if (x in 0 until W && y in 0 until H) {
                val i = y * W + x; raw[i] = (raw[i].toInt() and FLAGS_ROCK.inv()).toByte()
            }
        }
        ports.forEach { clearRock(it.x, it.y, 3) }
        spawn?.let { clearRock(it.x, it.y, 6) }
        val reserved = BooleanArray(W * H)
        fun reserve(cx: Int, cy: Int, r: Int) {
            for (y in cy - r..cy + r) for (x in cx - r..cx + r) if (x in 0 until W && y in 0 until H) reserved[y * W + x] = true
        }
        ports.forEach { reserve(it.x, it.y, 3) }
        spawn?.let { reserve(it.x, it.y, 6) }
        raft?.let { r -> for (y in r.y - 1..r.y + 4) for (x in r.x - 1..r.x + 4) if (x in 0 until W && y in 0 until H) reserved[y * W + x] = true }

        // ---- 6. sungai (tidak untuk pulau lava) ----
        if (!pre.lava) {
            val rngRiv = Rng(sub(seed, 12))
            val nRiv = Noise(sub(seed, 13))
            val wanted = when (opt.size) { 0 -> 1; 1 -> rngRiv.range(1, 2); else -> rngRiv.range(2, 3) }
            val starts = ArrayList<Int>()
            for (i in raw.indices) if (isLandTile(i) && code(i) != 13 && dist[i] >= 12 && !reserved[i]) starts.add(i)
            rngRiv.shuffle(starts)
            val startsUsed = ArrayList<Int>()
            for (s in starts) {
                if (startsUsed.size >= wanted) break
                val sx = s % W; val sy = s / W
                if (startsUsed.any { hypot((it % W - sx).toDouble(), (it / W - sy).toDouble()) < 40 }) continue
                startsUsed.add(s)
                val visited = HashSet<Int>()
                var cx = sx; var cy = sy
                var steps = 0
                while (steps < 900) {
                    disc(cx, cy, 1.5) { px, py ->
                        val pi = py * W + px
                        if (isLandTile(pi) && !reserved[pi]) raw[pi] = 13
                    }
                    val cur = cy * W + cx
                    visited.add(cur)
                    if (dist[cur] <= 1) break
                    val strict = steps % 4 == 3
                    var best = -1; var bestScore = Double.MAX_VALUE
                    for (pass in 0..1) {
                        for (k in 0 until 8) {
                            val nx = cx + DX8[k]; val ny = cy + DY8[k]
                            if (nx < 1 || ny < 1 || nx >= W - 1 || ny >= H - 1) continue
                            val ni = ny * W + nx
                            if (pass == 0 && (visited.contains(ni) || reserved[ni])) continue
                            val dd = dist[ni]
                            if (dd < 0 || (if (strict || pass == 1) dd >= dist[cur] else dd > dist[cur])) continue
                            val sc = dd - (nRiv.fbm(nx / 9.0, ny / 9.0, 2) - 0.5) * 2.2
                            if (sc < bestScore) { bestScore = sc; best = ni }
                        }
                        if (best >= 0) break
                    }
                    if (best < 0) break
                    cx = best % W; cy = best / W
                    steps++
                }
            }
        }

        // ---- 7. isi pulau ----
        val naturals = ArrayList<Natural>()
        val landmarks = ArrayList<Landmark>()
        val herds = ArrayList<Herd>()
        if (opt.populate) {
            var profile: Profile? = null
            if (opt.serverDir != null) {
                profile = try { loadProfile(opt.serverDir, pre.lakeBiome, notes) } catch (e: Exception) { notes.add("Data server gagal dibaca (${e.message}) — memakai katalog bawaan."); null }
                if (profile == null && notes.none { it.startsWith("Folder data server") || it.startsWith("Data server gagal") }) {
                    notes.add("Tidak ada terrain server dengan biome '${pre.lakeBiome}' — memakai katalog bawaan editor.")
                }
            }
            val prof = profile ?: catalogProfile(cat, pre)
            notes.add("Referensi isi pulau: ${prof.label}.")

            // natural
            val rngNat = Rng(sub(seed, 14))
            val nGrove = Noise(sub(seed, 15))
            val taken = BooleanArray(W * H)
            for (y in 0 until H) for (x in 0 until W) {
                val i = y * W + x
                if (reserved[i] || isRock(i)) continue
                val c = code(i)
                val d = prof.density[c] ?: continue
                val f = ((nGrove.fbm(x / 14.0, y / 14.0, 3) - 0.5) * 3.0 + 1.0).coerceIn(0.15, 2.2)
                if (rngNat.nextDouble() < d * f) {
                    val pk = prof.picks[c] ?: continue
                    if (taken[i]) continue
                    taken[i] = true
                    naturals.add(Natural(x, y, pk.pick(rngNat.nextDouble())))
                }
            }

            // landmark di tile batu
            val rngLm = Rng(sub(seed, 16))
            if (prof.landmarks.isNotEmpty()) {
                val rockTiles = ArrayList<Int>()
                for (i in raw.indices) if (isRock(i) && !reserved[i]) rockTiles.add(i)
                if (rockTiles.size >= 20) {
                    rngLm.shuffle(rockTiles)
                    val want = min((prof.landmarkCount * sizeScale * sizeScale).roundToInt(), rockTiles.size / 6)
                    for (t in rockTiles) {
                        if (landmarks.size >= want) break
                        val x = t % W; val y = t / W
                        if (landmarks.any { abs(it.x - x) < 4 && abs(it.y - y) < 4 }) continue
                        val r = prof.landmarks[rngLm.nextInt(prof.landmarks.size)]
                        landmarks.add(Landmark(x, y, r.prefab, r.rotate, r.ox, r.oy, r.oz, r.sx, r.sy, r.sz))
                    }
                } else notes.add("Medan hampir tanpa batu — landmark tidak dibuat.")
            }

            // herd
            val rngHerd = Rng(sub(seed, 17))
            val dLand = bfs(BooleanArray(W * H) { isLandTile(it) || lake[it] }) { sea[it] }
            val lakeEdge = bfs(BooleanArray(W * H) { code(it) != lakeCode }) { code(it) == lakeCode }
            val defaults = mapOf("land" to 120, "beach" to 60, "lake_shallow" to 40, "lake_deep" to 20, "ocean" to 100)
            for (g in cat.groupsFor(opt.type, false)) {
                val rows = cat.speciesFor(opt.type, g, false).filter { it.count > 0 }
                if (rows.isEmpty()) continue
                val tiles = ArrayList<Int>()
                for (i in raw.indices) {
                    if (reserved[i] || isRock(i)) continue
                    val c = code(i)
                    val ok = when (g) {
                        "land" -> c == pre.land || c == 13
                        "beach" -> c == pre.beach
                        "lake_shallow" -> c == lakeCode && lakeEdge[i] in 0..3
                        "lake_deep" -> c == lakeCode && lakeEdge[i] > 3
                        "ocean" -> c == oceanCode && dLand[i] >= 4
                        else -> false
                    }
                    if (ok) tiles.add(i)
                }
                if (tiles.isEmpty()) continue
                rngHerd.shuffle(tiles)
                val sep = 5
                val want = min((prof.herdCount[g] ?: defaults[g] ?: 40) * (if (g == "ocean" || g == "land") sizeScale else 1.0).let { it * 1.0 }, tiles.size / 12.0).toInt().coerceAtLeast(1)
                val cum = DoubleArray(rows.size); var acc = 0.0
                for ((k, r) in rows.withIndex()) { acc += r.count; cum[k] = acc }
                val placed = ArrayList<Int>()
                for (t in tiles) {
                    if (placed.size >= want) break
                    val x = t % W; val y = t / W
                    if (placed.any { abs(it % W - x) < sep && abs(it / W - y) < sep }) continue
                    placed.add(t)
                    val pickV = rngHerd.nextDouble() * acc
                    var k = 0; while (k < cum.size - 1 && pickV >= cum[k]) k++
                    val row = rows[k]
                    val a = cat.animals[row.id]
                    val lv = if (a != null) opt.level.coerceIn(a.lvMin, a.lvMax) else opt.level
                    herds.add(Herd(g, x, y, row.id, lv))
                }
            }
        }

        val doc = Doc(
            templateId = opt.templateId, level = opt.level, role = 4, islandType = opt.type,
            biomes = raw, naturals = naturals, landmarks = landmarks, ports = ports, herds = herds,
            spawn = spawn, raft = raft, seed = seedText,
        )
        notes.add(0, "Seed \"$seedText\" (nilai $seed), ukuran ${SIZE_LABELS[opt.size.coerceIn(0, 2)]}: " +
            "${naturals.size} natural · ${landmarks.size} landmark · ${herds.size} titik herd · ${ports.size} dermaga")
        return Result(doc, seedText, seed, notes)
    }
}
