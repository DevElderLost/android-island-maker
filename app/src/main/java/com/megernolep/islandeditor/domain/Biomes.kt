package com.megernolep.islandeditor.domain

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Kode biome, warna peta, dan nama (dicocokkan dari data terrain asli). */
object Biomes {
    class Info(val name: String, val icon: String, val cat: String)

    val baseColors: Map<Int, IntArray> = linkedMapOf(
        0 to intArrayOf(34, 120, 60), 1 to intArrayOf(20, 140, 40), 2 to intArrayOf(210, 180, 100),
        3 to intArrayOf(170, 190, 200), 4 to intArrayOf(235, 235, 245), 5 to intArrayOf(190, 170, 90),
        6 to intArrayOf(90, 100, 60), 7 to intArrayOf(120, 40, 30), 9 to intArrayOf(180, 170, 150),
        10 to intArrayOf(225, 210, 160), 11 to intArrayOf(90, 140, 190), 12 to intArrayOf(40, 100, 170),
        13 to intArrayOf(60, 130, 200), 14 to intArrayOf(70, 150, 210), 15 to intArrayOf(200, 60, 20),
    )

    val info: Map<Int, Info> = mapOf(
        0 to Info("Hutan sedang", "🌳", "Tanah"), 1 to Info("Hutan tropis", "🌴", "Tanah"),
        2 to Info("Gurun", "🏜️", "Tanah"), 3 to Info("Tundra", "🪨", "Tanah"),
        4 to Info("Padang salju", "❄️", "Salju"), 5 to Info("Padang rumput", "🌾", "Tanah"),
        6 to Info("Rawa lumpur", "🐊", "Tanah"), 7 to Info("Vulkanik", "🌋", "Tanah"),
        9 to Info("Pantai kerikil", "🪨", "Pantai"), 10 to Info("Pantai pasir", "🏖️", "Pantai"),
        11 to Info("Laut dingin", "🌊", "Air laut"), 12 to Info("Laut hangat", "🌊", "Air laut"),
        13 to Info("Sungai", "🏞️", "Air sungai/danau"), 14 to Info("Danau", "💦", "Air sungai/danau"),
        15 to Info("Lava", "🔥", "Lava"),
    )

    val short: Map<Int, String> = linkedMapOf(
        0 to "hutan", 1 to "tropis", 2 to "gurun", 3 to "tundra", 4 to "salju", 5 to "padang",
        6 to "rawa", 7 to "vulkanik", 9 to "p.kerikil", 10 to "p.pasir", 11 to "laut dingin",
        12 to "laut", 13 to "sungai", 14 to "danau", 15 to "lava",
    )

    /** Kode >= 11 = air / lava (tidak bisa dipijak). */
    fun isWaterOrLava(code: Int) = code >= 11

    private fun jsRound(v: Double) = floor(v + 0.5).toInt()

    /** Warna ARGB untuk satu kombinasi biome + flag. */
    fun variantColor(base: IntArray, collidable: Boolean, notPlant: Boolean): Int {
        var r = base[0]; var g = base[1]; var b = base[2]
        if (collidable && notPlant) {
            // batu/gunung (flag 0xC0): lebih gelap & sedikit keabuan dari biome dasarnya
            val k = intArrayOf(28, 32, 28)
            val m = { c: Int, i: Int -> jsRound((c * .45 + k[i] * .55) * .8) }
            return argb(m(r, 0), m(g, 1), m(b, 2))
        }
        if (collidable) { r = min(255, r + 40); g = max(0, g - 20); b = max(0, b - 20) }
        if (notPlant) { r = max(0, r - 30); g = max(0, g - 30); b = max(0, b - 30) }
        return argb(r, g, b)
    }

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** Lookup 256 entri: nilai byte tile -> warna ARGB. */
    val lut: IntArray = IntArray(256) { v ->
        variantColor(baseColors[v and 0x3F] ?: intArrayOf(0, 0, 0), (v and FLAG_COLLIDABLE) != 0, (v and FLAG_NOPLANT) != 0)
    }
}
