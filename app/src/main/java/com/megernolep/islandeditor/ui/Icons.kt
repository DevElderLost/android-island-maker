package com.megernolep.islandeditor.ui

import androidx.compose.material3.Icon

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

enum class IconKind { ZOOM_IN, ZOOM_OUT, FIT, ROT_LEFT, ROT_RIGHT, UNDO, REDO, FLIP_H, FLIP_V, IMPORT, TRASH, BACK, BRUSH, ERASER, LAYERS, IMAGE, EYE, EYE_OFF, UP, DOWN, MOVE, CLOSE, FOLDER, COMPASS, PAN, SETTINGS }

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
        Icon(kind.vector(), contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
    }
}

