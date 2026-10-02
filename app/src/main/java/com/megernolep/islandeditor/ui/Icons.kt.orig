package com.megernolep.islandeditor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class IconKind { ZOOM_IN, ZOOM_OUT, FIT, ROT_LEFT, ROT_RIGHT, UNDO, REDO, FLIP_H, FLIP_V, IMPORT, TRASH }

/** Tombol bulat 40dp (gaya sama dengan tombol zoom) dengan ikon vektor. */
@Composable
fun IconBtn(
    kind: IconKind, enabled: Boolean = true, active: Boolean = false,
    tint: Color = TextHi, onClick: () -> Unit,
) {
    val fg = when {
        !enabled -> Muted.copy(alpha = .45f)
        active -> Bg
        else -> tint
    }
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background((if (active) Accent else Surf).copy(alpha = .92f))
            .border(1.dp, Outline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) { drawIconGlyph(kind, fg) }
    }
}

/** Semua ikon digambar pada kotak virtual 24x24 dengan garis 2 satuan, ujung membulat. */
private fun DrawScope.drawIconGlyph(kind: IconKind, color: Color) {
    val u = size.minDimension / 24f
    val st = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

    /** Garis patah dari pasangan x,y. */
    fun poly(vararg p: Float) {
        val path = Path()
        for (i in 0 until p.size / 2) {
            if (i == 0) path.moveTo(p[0] * u, p[1] * u) else path.lineTo(p[2 * i] * u, p[2 * i + 1] * u)
        }
        drawPath(path, color, style = st)
    }

    /** Busur lingkaran + kepala panah di ujungnya (sweep > 0 = searah jarum jam). */
    fun arcArrow(startDeg: Float, sweepDeg: Float) {
        val r = 7f
        drawArc(
            color = color, startAngle = startDeg, sweepAngle = sweepDeg, useCenter = false,
            topLeft = Offset((12f - r) * u, (12f - r) * u), size = Size(2f * r * u, 2f * r * u), style = st,
        )
        val end = Math.toRadians((startDeg + sweepDeg).toDouble()).toFloat()
        val sg = if (sweepDeg >= 0f) 1f else -1f
        val ex = 12f + r * cos(end); val ey = 12f + r * sin(end)
        val tx = -sin(end) * sg; val ty = cos(end) * sg          // arah singgung (searah gerak panah)
        val nx = cos(end); val ny = sin(end)                     // arah radial
        poly(
            ex - tx * 3.5f + nx * 3.5f, ey - ty * 3.5f + ny * 3.5f,
            ex + tx * 1.5f, ey + ty * 1.5f,
            ex - tx * 3.5f - nx * 3.5f, ey - ty * 3.5f - ny * 3.5f,
        )
    }

    when (kind) {
        IconKind.ZOOM_IN -> { poly(5f, 12f, 19f, 12f); poly(12f, 5f, 12f, 19f) }
        IconKind.ZOOM_OUT -> poly(5f, 12f, 19f, 12f)
        IconKind.FIT -> {
            poly(4f, 9f, 4f, 4f, 9f, 4f); poly(15f, 4f, 20f, 4f, 20f, 9f)
            poly(20f, 15f, 20f, 20f, 15f, 20f); poly(9f, 20f, 4f, 20f, 4f, 15f)
        }
        IconKind.ROT_RIGHT -> arcArrow(-60f, 270f)
        IconKind.ROT_LEFT -> arcArrow(240f, -270f)
        IconKind.UNDO -> {
            poly(9f, 6f, 5f, 10f, 9f, 14f)
            val p = Path()
            p.moveTo(5f * u, 10f * u); p.lineTo(14f * u, 10f * u)
            p.arcTo(Rect(10f * u, 10f * u, 18f * u, 18f * u), -90f, 180f, false)
            p.lineTo(9f * u, 18f * u)
            drawPath(p, color, style = st)
        }
        IconKind.REDO -> {
            poly(15f, 6f, 19f, 10f, 15f, 14f)
            val p = Path()
            p.moveTo(19f * u, 10f * u); p.lineTo(10f * u, 10f * u)
            p.arcTo(Rect(6f * u, 10f * u, 14f * u, 18f * u), -90f, -180f, false)
            p.lineTo(15f * u, 18f * u)
            drawPath(p, color, style = st)
        }
        IconKind.FLIP_H -> {
            poly(5f, 8f, 19f, 8f); poly(15f, 4f, 19f, 8f, 15f, 12f)
            poly(5f, 16f, 19f, 16f); poly(9f, 12f, 5f, 16f, 9f, 20f)
        }
        IconKind.FLIP_V -> {
            poly(8f, 5f, 8f, 19f); poly(4f, 9f, 8f, 5f, 12f, 9f)
            poly(16f, 5f, 16f, 19f); poly(12f, 15f, 16f, 19f, 20f, 15f)
        }
        IconKind.IMPORT -> {
            poly(12f, 4f, 12f, 14f); poly(8f, 10f, 12f, 14f, 16f, 10f)
            poly(5f, 14f, 5f, 19f, 19f, 19f, 19f, 14f)
        }
        IconKind.TRASH -> {
            poly(5f, 7f, 19f, 7f); poly(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f)
            poly(7f, 7f, 8f, 20f, 16f, 20f, 17f, 7f)
            poly(10f, 11f, 10f, 16f); poly(14f, 11f, 14f, 16f)
        }
    }
}
