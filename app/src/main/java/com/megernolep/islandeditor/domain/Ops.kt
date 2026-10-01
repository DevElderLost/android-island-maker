package com.megernolep.islandeditor.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class Rect(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    val w: Int get() = x1 - x0 + 1
    val h: Int get() = y1 - y0 + 1
}
data class FootCheck(val rect: Rect, val oob: Boolean, val water: Int, val overlap: Boolean)
data class RaftCheck(val ok: Boolean, val oob: Boolean, val core: Int, val margin: Int)
data class RiverDiag(val comps: Int, val tiles: Int, val isolated: Int, val fat: Int)
data class EraseOpts(
    val biome: Boolean = true, val natural: Boolean = true, val landmark: Boolean = true, val port: Boolean = true,
    val spawn: Boolean = true, val raft: Boolean = true, val decor: Boolean = true, val npc: Boolean = true,
    val thorn: Boolean = true, val herd: Boolean = true, val building: Boolean = true, val trigger: Boolean = false,
)
class TrigResult(val doc: Doc, val pending: Pt?, val changed: Boolean)

/** Operasi murni pada [Doc]: kuas, penghapus, flip, validasi, zona pemicu. Tidak bergantung pada UI. */
object Ops {
    private val dirs4 = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))

    // ---------------- kuas ----------------
    /** Mengubah doc.biomes di tempat. */
    fun paintBiome(doc: Doc, x: Int, y: Int, size: Int, biome: Int, collidable: Boolean, notPlant: Boolean) {
        val half = (size - 1) / 2
        val v = biome or (if (collidable) FLAG_COLLIDABLE else 0) or (if (notPlant) FLAG_NOPLANT else 0)
        for (dy in -half..half) for (dx in -half..half) {
            val tx = x + dx; val ty = y + dy
            if (!doc.inside(tx, ty)) continue
            doc.biomes[ty * W + tx] = v.toByte()
        }
    }

    private fun rockHash(ix: Int, iy: Int): Double {
        var h = ix * 374761393 + iy * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967295.0
    }

    private fun rockNoise(x: Double, y: Double, sc: Double): Double {
        val fx = x / sc; val fy = y / sc
        val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt()
        val tx = fx - x0; val ty = fy - y0
        val sx = tx * tx * (3 - 2 * tx); val sy = ty * ty * (3 - 2 * ty)
        val a = rockHash(x0, y0); val b = rockHash(x0 + 1, y0)
        val c = rockHash(x0, y0 + 1); val d = rockHash(x0 + 1, y0 + 1)
        return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy
    }

    /** Kuas gunung/batu besar: flag 0xC0 di atas biome yang ada, tepi organik. Mengubah biomes di tempat. */
    fun paintRock(doc: Doc, x: Int, y: Int, sizeTiles: Int, roughPct: Int, landOnly: Boolean, erase: Boolean) {
        val r = sizeTiles / 2.0
        val rough = roughPct / 100.0
        val rad = ceil(r * (1 + rough * .6)).toInt()
        for (dy in -rad..rad) for (dx in -rad..rad) {
            val tx = x + dx; val ty = y + dy
            if (!doc.inside(tx, ty)) continue
            val n = rockNoise(tx.toDouble(), ty.toDouble(), 6.0) * .65 + rockNoise(tx + 31.0, ty + 57.0, 2.5) * .35 - .5
            if (hypot(dx.toDouble(), dy.toDouble()) / r + rough * n * 1.4 > 1) continue
            val i = ty * W + tx
            val raw = doc.biomes[i].toInt() and 0xFF
            val code = raw and 0x3F
            if (erase) { doc.biomes[i] = code.toByte(); continue }
            if (landOnly && (code > 10 || code == 8)) continue   // 11-14 air, 15 lava
            doc.biomes[i] = (raw or FLAGS_ROCK).toByte()
        }
    }

    /** Thornbush = objek tile (1 tile = 1 thornbush). Hasil: (set baru, jumlah tile air yang dilewati). */
    fun paintThorn(doc: Doc, x: Int, y: Int, size: Int, erase: Boolean, landOnly: Boolean): Pair<Set<Int>, Int> {
        val half = (size - 1) / 2
        if (erase) {
            val kept = doc.thorns.filterTo(LinkedHashSet()) { k -> !(abs(k % W - x) <= half && abs(k / W - y) <= half) }
            return kept to 0
        }
        val out = LinkedHashSet(doc.thorns)
        var skipped = 0
        for (dy in -half..half) for (dx in -half..half) {
            val tx = x + dx; val ty = y + dy
            if (!doc.inside(tx, ty)) continue
            val k = ty * W + tx
            if (k in out) continue
            if (landOnly && doc.code(tx, ty) >= 11) { skipped++; continue }
            out.add(k)
        }
        return out to skipped
    }

    // ---------------- bangunan ----------------
    fun footRect(doc: Doc, cat: Catalog, b: Building): Rect {
        val info = cat.bldById[b.entityType]
        val w = info?.w ?: 1; val h = info?.h ?: 1
        val c = doc.buildingAnchor == "center"
        val x0 = if (c) b.x - w / 2 else b.x
        val y0 = if (c) b.y - h / 2 else b.y
        return Rect(x0, y0, x0 + w - 1, y0 + h - 1)
    }

    fun footCheck(doc: Doc, cat: Catalog, b: Building): FootCheck {
        val r = footRect(doc, cat, b)
        var water = 0
        val oob = r.x0 < 0 || r.y0 < 0 || r.x1 >= W || r.y1 >= H
        if (!oob) for (yy in r.y0..r.y1) for (xx in r.x0..r.x1) if (doc.code(xx, yy) >= 11) water++
        val overlap = doc.buildings.any { o ->
            val q = footRect(doc, cat, o)
            !(q.x1 < r.x0 || q.x0 > r.x1 || q.y1 < r.y0 || q.y0 > r.y1)
        }
        return FootCheck(r, oob, water, overlap)
    }

    // ---------------- spawn / rakit ----------------
    /** Sama seperti server (StarterIslands.AreaClear): area 9x9 harus darat penuh dan di dalam peta. */
    fun spawnAreaOk(doc: Doc, x: Int, y: Int): Boolean {
        for (dy in -4..4) for (dx in -4..4) {
            val tx = x + dx; val ty = y + dy
            if (!doc.inside(tx, ty)) return false
            if (doc.code(tx, ty) >= 11) return false
        }
        return true
    }

    /** Titik darat 9x9 terdekat ke pusat daratan. Pair(titik, pesan error). */
    fun autoSpawn(doc: Doc): Pair<Pt?, String?> {
        var sx = 0.0; var sy = 0.0; var n = 0
        for (y in 0 until H) for (x in 0 until W) if (doc.code(x, y) < 9) { sx += x; sy += y; n++ }
        if (n == 0) return null to "Belum ada daratan. Lukis pulau dulu."
        val cx = sx / n; val cy = sy / n
        var best: Pt? = null; var bd = 1e18
        for (y in 4 until H - 4) for (x in 4 until W - 4) {
            val d = (x - cx) * (x - cx) + (y - cy) * (y - cy)
            if (d >= bd || !spawnAreaOk(doc, x, y)) continue
            bd = d; best = Pt(x, y)
        }
        return if (best == null) null to "Tidak ada area darat 9x9 yang bebas air. Perlebar daratan." else best to null
    }

    /** Server (FindLayout.Prop): inti 4x4 harus darat; margin 2 tile di sekelilingnya juga bebas air. */
    fun raftCheck(doc: Doc, x: Int, y: Int): RaftCheck {
        var core = 0; var margin = 0; var oob = false
        for (dy in -2..5) for (dx in -2..5) {
            val tx = x + dx; val ty = y + dy
            val inCore = dx in 0..3 && dy in 0..3
            if (!doc.inside(tx, ty)) { oob = true; continue }
            if (doc.code(tx, ty) >= 11) { if (inCore) core++ else margin++ }
        }
        return RaftCheck(!oob && core == 0 && margin == 0, oob, core, margin)
    }

    // ---------------- NPC ----------------
    fun putNpc(doc: Doc, id: String, x: Int, y: Int, radius: Int): Doc {
        val info = EditorData.npcById[id] ?: return doc
        val n = if (info.kind == "story") Npc(id, "story", x, y, epic = info.epic) else Npc(id, "bot", x, y, radius = radius)
        return doc.copy(npcs = doc.npcs.filter { it.id != id } + n)
    }

    /** Taruh K & T di posisi tetap level [lv] (0-based). Hasil: (doc, nama NPC yang jatuh di air). */
    fun storyPreset(doc: Doc, lv: Int): Pair<Doc, List<String>> {
        var d = doc
        val bad = ArrayList<String>()
        for (id in listOf("story_k", "story_t")) {
            val p = EditorData.storyPos.getValue(id)[lv]
            if (d.code(p.x, p.y) >= 11) bad.add(EditorData.npcById.getValue(id).name)
            d = putNpc(d, id, p.x, p.y, 0)
        }
        return d to bad
    }

    // ---------------- penghapus ----------------
    fun eraseAt(doc: Doc, cat: Catalog, x: Int, y: Int, size: Int, o: EraseOpts): Doc {
        val half = (size - 1) / 2
        if (o.biome) {
            for (dy in -half..half) for (dx in -half..half) {
                val tx = x + dx; val ty = y + dy
                if (doc.inside(tx, ty)) doc.biomes[ty * W + tx] = 0
            }
        }
        val tol = max(half, 2)
        fun keep(px: Int, py: Int) = !(abs(px - x) <= tol && abs(py - y) <= tol)
        var d = doc
        if (o.natural) d = d.copy(naturals = d.naturals.filter { keep(it.x, it.y) })
        if (o.landmark) d = d.copy(landmarks = d.landmarks.filter { keep(it.x, it.y) })
        if (o.port) d = d.copy(ports = d.ports.filter { keep(it.x, it.y) })
        val sp = d.spawn
        if (o.spawn && sp != null && !keep(sp.x, sp.y)) d = d.copy(spawn = null)
        val rf = d.raft
        if (o.raft && rf != null && !keep(rf.x + 1, rf.y + 1)) d = d.copy(raft = null)
        if (o.decor) d = d.copy(decor = d.decor.filter { keep(it.x, it.y) })
        if (o.npc) d = d.copy(npcs = d.npcs.filter { keep(it.x, it.y) })
        if (o.thorn) d = d.copy(thorns = d.thorns.filterTo(LinkedHashSet()) { k -> !(abs(k % W - x) <= half && abs(k / W - y) <= half) })
        if (o.trigger) d = d.copy(trig = trigEraseArea(d.trig, x, y, half))
        if (o.herd) d = d.copy(herds = d.herds.filter { keep(it.x, it.y) })
        if (o.building) {
            val kept = d.buildings.filter { b ->
                val r = footRect(d, cat, b)
                r.x1 < x - half || r.x0 > x + half || r.y1 < y - half || r.y0 > y + half
            }
            d = d.copy(buildings = kept)
        }
        return d
    }

    // ---------------- flip ----------------
    /** Cermin seluruh peta (biome + semua objek). [horizontal]: x -> W-1-x, selain itu y -> H-1-y. */
    fun flip(doc: Doc, cat: Catalog, horizontal: Boolean): Doc {
        val h = horizontal
        val nb = ByteArray(W * H)
        for (y in 0 until H) for (x in 0 until W) {
            val nx = if (h) W - 1 - x else x
            val ny = if (h) y else H - 1 - y
            nb[ny * W + nx] = doc.biomes[y * W + x]
        }
        fun fx(x: Int) = if (h) W - 1 - x else x
        fun fy(y: Int) = if (h) y else H - 1 - y
        val centered = doc.buildingAnchor == "center"
        val flippedBuildings = doc.buildings.map { b ->
            val r = footRect(doc, cat, b)
            if (h) {
                val x0 = W - r.x0 - r.w
                b.copy(x = if (centered) x0 + r.w / 2 else x0)
            } else {
                val y0 = H - r.y0 - r.h
                b.copy(y = if (centered) y0 + r.h / 2 else y0)
            }
        }
        return doc.copy(
            biomes = nb,
            naturals = doc.naturals.map { it.copy(x = fx(it.x), y = fy(it.y)) },
            ports = doc.ports.map { Pt(fx(it.x), fy(it.y)) },
            spawn = doc.spawn?.let { Pt(fx(it.x), fy(it.y)) },
            raft = doc.raft?.let { if (h) Pt(W - it.x - 4, it.y) else Pt(it.x, H - it.y - 4) },
            herds = doc.herds.map { it.copy(x = fx(it.x), y = fy(it.y)) },
            thorns = doc.thorns.mapTo(LinkedHashSet()) { k -> fy(k / W) * W + fx(k % W) },
            npcs = doc.npcs.map { it.copy(x = fx(it.x), y = fy(it.y)) },
            // hewan objek: yaw derajat, arah=(sin,cos) -> flip H: 360-yaw, flip V: 180-yaw
            decor = doc.decor.map { d ->
                val yaw = (((if (h) 360 - d.yaw else 180 - d.yaw) % 360) + 360) % 360
                d.copy(x = fx(d.x), y = fy(d.y), yaw = yaw)
            },
            // landmark: rotate = byte, yaw = rotate*2° -> flip H: r' = 180-r, flip V: r' = 90-r (modulo 180)
            landmarks = doc.landmarks.map { l ->
                val r = (((if (h) 180 - l.rotate else 90 - l.rotate) % 180) + 180) % 180
                l.copy(x = fx(l.x), y = fy(l.y), rotate = r)
            },
            trig = doc.trig.map { z ->
                z.copy(cells = z.cells.mapTo(LinkedHashSet()) { i -> fy(i / W) * W + fx(i % W) })
            },
            buildings = flippedBuildings,
        )
    }

    // ---------------- tipe pulau ----------------
    /** Ubah semua tile darat/pantai/laut ke biome tipe baru; buang herd yang tidak cocok. Hasil: (doc, jumlah herd dibuang). */
    fun convertToType(doc: Doc, cat: Catalog, newType: String): Pair<Doc, Int> {
        val p = cat.types[newType] ?: return doc to 0
        val map = HashMap<Int, Int>()
        for (c in 0..7) map[c] = p.land
        map[9] = p.beach; map[10] = p.beach; map[11] = p.ocean; map[12] = p.ocean
        if (!p.lava) map[15] = p.land
        val nb = ByteArray(doc.biomes.size)
        for (i in nb.indices) {
            val v = doc.biomes[i].toInt() and 0xFF
            val m = map[v and 0x3F]
            nb[i] = (if (m != null) (v and 0xC0) or m else v).toByte()
        }
        val spec = cat.species[newType].orEmpty()
        val kept = doc.herds.filter { h ->
            val rows = spec[h.group].orEmpty()
            rows.isEmpty() || rows.any { it.id == h.entityType }
        }
        return doc.copy(biomes = nb, herds = kept, islandType = newType) to (doc.herds.size - kept.size)
    }

    // ---------------- zona pemicu ----------------
    private fun trigStamp(cells: MutableSet<Int>, x: Int, y: Int, size: Int, add: Boolean) {
        val h = (size - 1) shr 1; val h2 = size - 1 - h
        for (dy in -h..h2) for (dx in -h..h2) {
            val tx = x + dx; val ty = y + dy
            if (tx < 0 || ty < 0 || tx >= W || ty >= H) continue
            if (add) cells.add(ty * W + tx) else cells.remove(ty * W + tx)
        }
    }

    private fun trigLine(cells: MutableSet<Int>, x0: Int, y0: Int, x1: Int, y1: Int, size: Int, add: Boolean) {
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy; var x = x0; var y = y0
        for (guard in 0 until 4096) {
            trigStamp(cells, x, y, size, add)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    private fun trigRect(cells: MutableSet<Int>, x0: Int, y0: Int, x1: Int, y1: Int, add: Boolean) {
        for (y in max(0, min(y0, y1))..min(H - 1, max(y0, y1)))
            for (x in max(0, min(x0, x1))..min(W - 1, max(x0, x1))) {
                if (add) cells.add(y * W + x) else cells.remove(y * W + x)
            }
    }

    fun trigColor(flow: String): Int = EditorData.trigColorByFlow[flow] ?: EditorData.TRIG_DEFAULT_COLOR

    /** Satu ketukan/klik pada mode zona pemicu. tool: "brush" | "line" | "rect". */
    fun trigApply(doc: Doc, flow: String, exit: String, tool: String, size: Int, x: Int, y: Int, erase: Boolean, pending: Pt?): TrigResult {
        val zones = doc.trig.toMutableList()
        var idx = zones.indexOfFirst { it.flow == flow }
        if (idx < 0 && !erase) {
            zones.add(TrigZone(flow, "", trigColor(flow), emptySet()))
            idx = zones.lastIndex
        }
        val cells: MutableSet<Int>? = if (idx >= 0) zones[idx].cells.toMutableSet() else null
        var newPending = pending
        var changed = true
        if (idx >= 0 && !erase) zones[idx] = zones[idx].copy(exit = exit)
        if (tool == "brush") {
            if (cells != null) trigStamp(cells, x, y, size, !erase)
        } else if (pending == null) {
            newPending = Pt(x, y); changed = false
        } else {
            newPending = null
            if (cells != null) {
                if (tool == "line") trigLine(cells, pending.x, pending.y, x, y, size, !erase)
                else trigRect(cells, pending.x, pending.y, x, y, !erase)
            }
        }
        if (cells != null && idx >= 0) zones[idx] = zones[idx].copy(cells = cells)
        return TrigResult(doc.copy(trig = zones.filter { it.cells.isNotEmpty() }), newPending, changed)
    }

    fun trigEraseArea(zones: List<TrigZone>, x: Int, y: Int, half: Int): List<TrigZone> =
        zones.map { z ->
            val c = z.cells.toMutableSet()
            for (dy in -half..half) for (dx in -half..half) {
                val tx = x + dx; val ty = y + dy
                if (tx in 0 until W && ty in 0 until H) c.remove(ty * W + tx)
            }
            z.copy(cells = c)
        }.filter { it.cells.isNotEmpty() }

    // ---------------- validasi ----------------
    fun isRock(doc: Doc, x: Int, y: Int): Boolean =
        doc.inside(x, y) && (doc.raw(x, y) and FLAGS_ROCK) == FLAGS_ROCK

    private fun rockInRect(doc: Doc, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        for (y in y0..y1) for (x in x0..x1) if (isRock(doc, x, y)) return true
        return false
    }

    /** Laporan "cek batu vs spawn/rakit/objek". */
    fun rockReport(doc: Doc, cat: Catalog): List<String> {
        var total = 0
        for (b in doc.biomes) if ((b.toInt() and FLAGS_ROCK) == FLAGS_ROCK) total++
        val out = arrayListOf("Tile batu/gunung: $total")
        val bad = ArrayList<String>()
        doc.spawn?.let { if (rockInRect(doc, it.x - 4, it.y - 4, it.x + 4, it.y + 4)) bad.add("area spawn 9x9 kena batu: pemain bisa terjebak") }
        doc.raft?.let { if (rockInRect(doc, it.x, it.y, it.x + 3, it.y + 3)) bad.add("area rakit 4x4 kena batu") }
        if (doc.ports.any { rockInRect(doc, it.x - 1, it.y - 1, it.x + 1, it.y + 1) }) bad.add("dermaga kena batu")
        doc.naturals.count { isRock(doc, it.x, it.y) }.let { if (it > 0) bad.add("$it natural ada di dalam batu (tidak terjangkau)") }
        doc.herds.count { isRock(doc, it.x, it.y) }.let { if (it > 0) bad.add("$it titik herd ada di dalam batu") }
        doc.buildings.count { b -> footRect(doc, cat, b).let { r -> rockInRect(doc, r.x0, r.y0, r.x1, r.y1) } }
            .let { if (it > 0) bad.add("$it bangunan menimpa batu") }
        doc.npcs.count { isRock(doc, it.x, it.y) }.let { if (it > 0) bad.add("$it NPC ada di dalam batu") }
        doc.decor.count { isRock(doc, it.x, it.y) }.let { if (it > 0) bad.add("$it hewan objek ada di dalam batu") }
        return out + (if (bad.isEmpty()) listOf("✅ tidak ada tabrakan") else bad.map { "⚠️ $it" })
    }

    /** Diagnosa sungai: alur terisolasi / terlalu lebar membuat sungai tampak salah di game. */
    fun diagnoseRivers(biomes: ByteArray): RiverDiag {
        val seen = BooleanArray(W * H)
        fun cl(x: Int, y: Int): Int {
            if (x < 0 || y < 0 || x >= W || y >= H) return 1
            return when (biomes[y * W + x].toInt() and 0x3F) { 11, 12 -> 1; 13 -> 2; 14 -> 3; else -> 0 }
        }
        var comps = 0; var tiles = 0; var isolated = 0; var fat = 0
        for (y in 0 until H) for (x in 0 until W) {
            if (cl(x, y) != 2 || seen[y * W + x]) continue
            comps++
            val st = ArrayDeque<Int>()
            st.addLast(y * W + x); seen[y * W + x] = true
            var n = 0; var touch = false; var maxw = 0
            while (st.isNotEmpty()) {
                val i = st.removeLast()
                val cx = i % W; val cy = i / W
                n++
                for (d in dirs4) {
                    val nx = cx + d[0]; val ny = cy + d[1]
                    val c = cl(nx, ny)
                    if (c == 1 || c == 3) touch = true
                    if (c == 2 && !seen[ny * W + nx]) { seen[ny * W + nx] = true; st.addLast(ny * W + nx) }
                }
                var w = 0; while (cl(cx + w, cy) == 2 && w < 40) w++
                var hh = 0; while (cl(cx, cy + hh) == 2 && hh < 40) hh++
                maxw = max(maxw, min(w, hh))
            }
            tiles += n
            if (!touch) isolated++
            if (maxw > 10) fat++
        }
        return RiverDiag(comps, tiles, isolated, fat)
    }

    fun waterReportText(d: RiverDiag): String {
        if (d.comps == 0) return "Tidak ada sungai (kode 13) di peta. Danau/laut tidak perlu dicek."
        val t = arrayListOf("${d.comps} alur sungai, ${d.tiles} tile.")
        if (d.isolated > 0) t.add("⚠ ${d.isolated} alur tidak menyentuh laut/danau → di game jadi genangan tanpa arus. Sambungkan ujungnya ke laut atau danau.")
        if (d.fat > 0) t.add("⚠ ${d.fat} alur lebih lebar dari 10 tile → lebih cocok Danau (kode 14), sungai asli 3–7 tile.")
        if (d.isolated == 0 && d.fat == 0) t.add("✓ Tersambung dan lebarnya wajar.")
        return t.joinToString("\n")
    }
}
