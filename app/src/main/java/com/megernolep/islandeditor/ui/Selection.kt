package com.megernolep.islandeditor.ui

import androidx.compose.ui.geometry.Offset
import com.megernolep.islandeditor.domain.H
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class SelKind { LANDMARK, DECOR }

/** Objek yang sedang dipilih: jenis + index di list dokumen. */
data class Sel(val kind: SelKind, val index: Int)

/**
 * Pose objek terpilih (koordinat DATA). deg = arah hadap, searah jarum jam dari "atas" peta.
 * halfW/halfL = setengah lebar / panjang kotak seleksi dalam tile.
 */
data class Pose(val x: Int, val y: Int, val deg: Float, val halfW: Float, val halfL: Float)

/** Kerangka kotak seleksi dalam koordinat PETA (1 satuan = 1 tile, y ke bawah). */
class Frame(val c: Offset, val f: Offset, val r: Offset, val hw: Float, val hl: Float) {
    /** Titik di kerangka objek: a = searah depan, b = ke kanan. */
    fun pt(a: Float, b: Float) = Offset(c.x + f.x * a + r.x * b, c.y + f.y * a + r.y * b)

    /** Pojok depan-kanan = letak ikon rotasi. */
    val handle: Offset get() = pt(hl, hw)
}

fun frameOf(p: Pose, k: Float): Frame {
    val th = Math.toRadians(p.deg.toDouble())
    val s = sin(th).toFloat()
    val c = cos(th).toFloat()
    val minHalf = 22f / k          // kotak tidak boleh lebih kecil dari ±22 px di layar
    return Frame(
        Offset(p.x + 0.5f, H - p.y - 0.5f), Offset(s, -c), Offset(c, s),
        max(p.halfW, minHalf), max(p.halfL, minHalf),
    )
}
