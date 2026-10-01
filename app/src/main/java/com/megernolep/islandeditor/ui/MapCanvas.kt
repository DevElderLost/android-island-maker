package com.megernolep.islandeditor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.megernolep.islandeditor.domain.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin

private val CvBg = Color(0xFF0A0D12)

// Koordinat data (x, y naik) -> koordinat peta layar (y turun). 1 satuan = 1 tile.
private fun mx(x: Int) = x + 0.5f
private fun my(y: Int) = H - y - 0.5f
private fun tileTop(y: Int) = (H - 1 - y).toFloat()

@Composable
fun MapCanvas(vm: EditorViewModel, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(CvBg)
            .onSizeChanged { vm.onCanvasSize(IntSize(it.width, it.height)) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val t0 = System.currentTimeMillis()
                    var multi = false
                    if (!vm.panMode) vm.strokeBegin(down.position)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            if (!multi) {
                                multi = true
                                vm.strokeCancel(System.currentTimeMillis() - t0 < 500)
                            }
                            vm.transform(
                                event.calculateCentroid(), event.calculatePan(),
                                event.calculateZoom(), event.calculateRotation(),
                            )
                            event.changes.forEach { it.consume() }
                        } else if (!multi && pressed.size == 1) {
                            val ch = pressed[0]
                            if (vm.panMode) vm.panBy(ch.positionChange()) else vm.strokeMove(ch.position)
                            ch.consume()
                        }
                    } while (event.changes.any { it.pressed && !it.changedToUp() })
                    vm.strokeEnd()
                }
            },
    ) {
        vm.rev          // baca state -> redraw saat biome / objek berubah
        val v = vm.view
        val doc = vm.doc
        val k = v.k
        fun px(n: Float) = n / k   // ukuran konstan dalam piksel layar

        withTransform({
            translate(v.tx, v.ty)
            rotate(degrees = v.rot * 180f / PI.toFloat(), pivot = Offset.Zero)
            scale(k, k, pivot = Offset.Zero)
        }) {
            drawImage(vm.image, dstSize = IntSize(W, H), filterQuality = FilterQuality.None)
            drawOverlays(vm, doc, ::px)
        }
    }
}

private fun DrawScope.dot(x: Int, y: Int, color: Color, rTiles: Float, outline: Boolean, px: (Float) -> Float) {
    val r = max(rTiles, px(2.2f))
    drawCircle(color, r, Offset(mx(x), my(y)))
    if (outline) drawCircle(Color.Black, r, Offset(mx(x), my(y)), style = Stroke(px(1f)))
}

private fun DrawScope.box(x0: Int, y0: Int, x1: Int, y1: Int, fill: Color?, line: Color?, dashed: Boolean, px: (Float) -> Float, sw: Float = 1.5f) {
    val tl = Offset(x0.toFloat(), tileTop(y1))
    val sz = Size((x1 - x0 + 1).toFloat(), (y1 - y0 + 1).toFloat())
    if (fill != null) drawRect(fill, tl, sz)
    if (line != null) {
        val eff = if (dashed) PathEffect.dashPathEffect(floatArrayOf(px(4f), px(3f))) else null
        drawRect(line, tl, sz, style = Stroke(px(sw), pathEffect = eff))
    }
}

private fun DrawScope.arrow(x: Int, y: Int, deg: Float, len: Float, color: Color, px: (Float) -> Float) {
    val th = deg * PI.toFloat() / 180f
    val a = Offset(mx(x), my(y))
    val b = Offset(a.x + sin(th) * len, a.y - cos(th) * len)   // arah data (sin, cos); sumbu y peta dibalik
    drawLine(color, a, b, strokeWidth = px(2f))
}

private fun DrawScope.drawOverlays(vm: EditorViewModel, doc: Doc, px: (Float) -> Float) {
    val o = vm.opts
    val mode = vm.mode

    // zona pemicu misi (hanya editor)
    if (o.trigShow) {
        val a = if (mode == Mode.TRIGGER) .55f else .3f
        for (z in doc.trig) {
            val c = Color(z.color).copy(alpha = a)
            for (i in z.cells) drawRect(c, Offset((i % W).toFloat(), tileTop(i / W)), Size(1f, 1f))
        }
        val p = vm.trigPending
        if (mode == Mode.TRIGGER && p != null) {
            val h = vm.hover
            val a0 = Offset(mx(p.x), my(p.y))
            if (h != null && o.trigTool == "line") {
                drawLine(Color.White.copy(alpha = .4f), a0, Offset(mx(h.x), my(h.y)), strokeWidth = max(px(1.5f), o.trigSize.toFloat()))
            } else if (h != null && o.trigTool == "rect") {
                box(min(p.x, h.x), min(p.y, h.y), max(p.x, h.x), max(p.y, h.y), null, Color.White, true, px)
            }
            drawCircle(Color.White, px(4f), a0)
        }
    }

    // thornbush: kotak 2 warna agar terbaca sebagai semak
    for (k in doc.thorns) {
        val x = k % W; val y = k / W
        drawRect(
            if (((x + y) and 1) != 0) Color(0xEB961C1C) else Color(0xEBCD3C32),
            Offset(x.toFloat(), tileTop(y)), Size(1f, 1f),
        )
    }

    for (n in doc.naturals) {
        val (c, r) = vm.natVis(n.entityType)
        dot(n.x, n.y, Color(c), r, false, px)
    }
    for (l in doc.landmarks) {
        if (!EditorData.isGlobalPrefab(l.prefab)) { dot(l.x, l.y, Color(0xFFC86BD8), 2f, true, px); continue }
        arrow(l.x, l.y, l.rotate * 2f, 10f, Color(0xFFFF6A3D), px)
        box(l.x - 1, l.y - 1, l.x + 1, l.y + 1, Color(0xFFFF6A3D), Color.Black, false, px, 1f)
    }
    for (d in doc.decor) {
        arrow(d.x, d.y, d.yaw.toFloat(), 10f, Color(0xFFFFD23F), px)
        dot(d.x, d.y, Color(0xFFFFD23F), 3f, true, px)
    }
    for (n in doc.npcs) {
        val story = n.kind == "story"
        if (!story && n.radius > 0) {
            drawCircle(
                Color(0x8CFF5AD1), n.radius.toFloat(), Offset(mx(n.x), my(n.y)),
                style = Stroke(px(1f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(4f), px(3f)))),
            )
        }
        dot(n.x, n.y, if (story) Color(0xFFFFD23F) else Color(0xFFFF5AD1), if (story) 4f else 2.5f, true, px)
    }
    for (p in doc.ports) dot(p.x, p.y, Color(0xFF3AA0FF), 2.5f, true, px)
    for (h in doc.herds) dot(h.x, h.y, Color(0xFFFF4040), 1.5f, true, px)
    for (b in doc.buildings) {
        val r = Ops.footRect(doc, vm.catalog, b)
        box(r.x0, r.y0, r.x1, r.y1, Color(0x59FFA030), Color(0xFFFFA030), false, px)
        dot(b.x, b.y, Color(0xFFFFA030), 1.5f, true, px)
    }
    if (mode == Mode.BUILDING) {
        val h = vm.hover
        if (h != null) {
            val f = Ops.footCheck(doc, vm.catalog, Building(h.x, h.y, o.selBuilding))
            val bad = f.oob || f.overlap || f.water > 0
            box(f.rect.x0, f.rect.y0, f.rect.x1, f.rect.y1,
                if (bad) Color(0x59FF3C3C) else Color(0x4DFFFFFF), if (bad) Color(0xFFFF3C3C) else Color.White, true, px)
        }
    }
    doc.raft?.let { r ->
        val st = Ops.raftCheck(doc, r.x, r.y)
        val col = if (st.ok) Color(0xFF37D7FF) else Color(0xFFFFB020)
        box(r.x - 2, r.y - 2, r.x + 5, r.y + 5, null, col, true, px, 1f)
        box(r.x, r.y, r.x + 3, r.y + 3, col.copy(alpha = .45f), col, false, px)
    }
    doc.spawn?.let { s ->
        val ok = Ops.spawnAreaOk(doc, s.x, s.y)
        val col = if (ok) Color(0xFF5CFF7A) else Color(0xFFFFB020)
        box(s.x - 4, s.y - 4, s.x + 4, s.y + 4, null, col, true, px)
        dot(s.x, s.y, col, 3f, true, px)
        drawCircle(Color.Black, px(2f), Offset(mx(s.x), my(s.y)))
    }
}
