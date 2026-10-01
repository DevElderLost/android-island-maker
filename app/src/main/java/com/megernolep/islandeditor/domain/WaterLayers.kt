package com.megernolep.islandeditor.domain

import org.json.JSONObject
import java.util.Base64
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min

/**
 * Turunan whole.ocean + whole.rivers dari grid biome (port 1:1 dari editor HTML / water_layers.py).
 *  ocean : (W+1)*(H+1) byte.  <128 = kedalaman laut, >=128 = kedalaman danau, 0 = tidak ada air.
 *  rivers: (W+1)*(H+1)*3 byte [arus x, arus y, kedalaman]; 127,127 = tanpa arus; kedalaman < 5 = bukan sungai.
 */
object WaterLayers {
    class Result(val ocean: ByteArray, val rivers: ByteArray)

    private fun jsRound(v: Double): Int = floor(v + 0.5).toInt()
    private fun clamp(v: Int, a: Int, b: Int) = if (v < a) a else if (v > b) b else v

    /** Ambil lapisan air yang sudah dihitung editor (ocean_b64/rivers_b64); null kalau tidak ada / ukuran salah. */
    fun decodeFromSpec(spec: JSONObject, w: Int = W, h: Int = H): Result? {
        val o = spec.optString("ocean_b64", "")
        val r = spec.optString("rivers_b64", "")
        if (o.isEmpty() || r.isEmpty()) return null
        return try {
            val ocean = Base64.getDecoder().decode(o)
            val rivers = Base64.getDecoder().decode(r)
            val nv = (w + 1) * (h + 1)
            if (ocean.size != nv || rivers.size != nv * 3) null else Result(ocean, rivers)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun build(biomes: ByteArray, w: Int = W, h: Int = H): Result {
        val e1 = w + 1; val e2 = h + 1; val nv = e1 * e2

        // kelas tile: 0 darat, 1 laut, 2 sungai, 3 danau (di luar peta = laut)
        fun cls(x: Int, y: Int): Int {
            if (x < 0 || y < 0 || x >= w || y >= h) return 1
            return when (biomes[y * w + x].toInt() and 0x3F) { 11, 12 -> 1; 13 -> 2; 14 -> 3; else -> 0 }
        }

        // bit0 laut, bit1 sungai, bit2 danau, bit3 darat (4 tile yang menyentuh vertex)
        val has = IntArray(nv)
        for (y in 0 until e2) for (x in 0 until e1) {
            var m = 0
            for (dy in -1..0) for (dx in -1..0) {
                val c = cls(x + dx, y + dy)
                m = m or (if (c == 0) 8 else (1 shl (c - 1)))
            }
            has[y * e1 + x] = m
        }

        // jarak BFS 4-arah (dalam vertex) dari vertex yang punya bit tertentu
        fun bfs(bit: Int): IntArray {
            val d = IntArray(nv) { 999 }
            val q = IntArray(nv)
            var hd = 0; var tl = 0
            for (i in 0 until nv) if ((has[i] and bit) != 0) { d[i] = 0; q[tl++] = i }
            while (hd < tl) {
                val i = q[hd++]
                val x = i % e1; val y = i / e1; val nd = d[i] + 1
                if (x > 0 && d[i - 1] > nd) { d[i - 1] = nd; q[tl++] = i - 1 }
                if (x < e1 - 1 && d[i + 1] > nd) { d[i + 1] = nd; q[tl++] = i + 1 }
                if (y > 0 && d[i - e1] > nd) { d[i - e1] = nd; q[tl++] = i - e1 }
                if (y < e2 - 1 && d[i + e1] > nd) { d[i + e1] = nd; q[tl++] = i + e1 }
            }
            return d
        }

        val dX = bfs(8); val dO = bfs(1); val dL = bfs(4); val dR = bfs(2)
        val ocean = ByteArray(nv)
        val rivers = ByteArray(nv * 3)
        for (i in 0 until nv) { rivers[i * 3] = 127; rivers[i * 3 + 1] = 127 }

        for (i in 0 until nv) {
            val m = has[i]
            val xx = m and 8; val oo = m and 1; val rr = m and 2; val ll = m and 4
            var v = 0
            if (xx == 0) {
                if (ll != 0) {
                    v = if (rr != 0 || oo != 0) 191 else clamp(jsRound(196.0 + 6 * dX[i]), 128, 255)
                } else if (oo != 0) {
                    v = if (rr != 0) 64 else min(127, jsRound(66.0 + 5.5 * dX[i]))
                }
            } else if (ll != 0) {
                v = 190
            } else if (oo != 0) {
                v = 63
            } else { // darat murni / tepi sungai: landaian ke laut atau danau terdekat
                val co = jsRound(61.0 - 5.6 * dO[i])
                val cl = 189 - 7 * dL[i]
                v = if (dL[i] < dO[i]) (if (cl > 128) cl else 0) else (if (co > 0) co else 0)
            }
            ocean[i] = (v and 0xFF).toByte()
            var dep = 0
            if (rr != 0) {
                dep = if (xx != 0) 140 else if (oo != 0 || ll != 0) 200 else min(255, 165 + 35 * dX[i])
            } else if (xx != 0 && dR[i] in 1..3) {
                dep = intArrayOf(80, 42, 10)[dR[i] - 1]
            }
            rivers[i * 3 + 2] = (dep and 0xFF).toByte()
        }

        // potensial arus: BFS tile 4-arah lewat sungai+danau, mulai dari laut
        val p = IntArray(w * h) { -1 }
        val q = IntArray(w * h)
        var hd = 0; var tl = 0
        fun isW(x: Int, y: Int): Boolean { val c = cls(x, y); return c == 2 || c == 3 }
        val dxs = intArrayOf(1, -1, 0, 0); val dys = intArrayOf(0, 0, 1, -1)
        for (y in 0 until h) for (x in 0 until w) {
            if (cls(x, y) != 1) continue
            for (k in 0 until 4) {
                val nx = x + dxs[k]; val ny = y + dys[k]
                if (nx in 0 until w && ny in 0 until h && isW(nx, ny) && p[ny * w + nx] < 0) {
                    p[ny * w + nx] = 1; q[tl++] = ny * w + nx
                }
            }
        }
        while (hd < tl) {
            val i = q[hd++]
            val x = i % w; val y = i / w
            for (k in 0 until 4) {
                val nx = x + dxs[k]; val ny = y + dys[k]
                if (nx in 0 until w && ny in 0 until h && isW(nx, ny) && p[ny * w + nx] < 0) {
                    p[ny * w + nx] = p[i] + 1; q[tl++] = ny * w + nx
                }
            }
        }

        val pvArr = FloatArray(nv) { Float.NaN }
        for (y in 0 until e2) for (x in 0 until e1) {
            var s = 0.0; var n = 0
            for (dy in -1..0) for (dx in -1..0) {
                val tx = x + dx; val ty = y + dy
                if (tx < 0 || ty < 0 || tx >= w || ty >= h) { n++; continue }
                val c = cls(tx, ty)
                if (c == 1) n++
                else if (p[ty * w + tx] >= 0) { s += p[ty * w + tx]; n++ }
            }
            if (n > 0) pvArr[y * e1 + x] = (s / n).toFloat()   // editor memakai Float32Array
        }
        fun pv(x: Int, y: Int): Double = if (x < 0 || y < 0 || x >= e1 || y >= e2) Double.NaN else pvArr[y * e1 + x].toDouble()

        for (y in 0 until e2) for (x in 0 until e1) {
            val i = y * e1 + x
            if ((rivers[i * 3 + 2].toInt() and 0xFF) < 5) continue
            var gx = 0.0; var gy = 0.0
            val p0 = pvArr[i].toDouble()
            for (k in 1..2) {
                val a = pv(x + k, y); val b = pv(x - k, y); val c = pv(x, y + k); val d = pv(x, y - k)
                gx += (if (a.isNaN()) p0 else a) - (if (b.isNaN()) p0 else b)
                gy += (if (c.isNaN()) p0 else c) - (if (d.isNaN()) p0 else d)
            }
            if (gx.isNaN() || gy.isNaN()) continue
            val ln = hypot(gx, gy)
            if (ln < 1e-6) continue
            rivers[i * 3] = clamp(jsRound((-gx / ln + 1) * 127.5), 0, 255).toByte()
            rivers[i * 3 + 1] = clamp(jsRound((-gy / ln + 1) * 127.5), 0, 255).toByte()
        }
        return Result(ocean, rivers)
    }
}
