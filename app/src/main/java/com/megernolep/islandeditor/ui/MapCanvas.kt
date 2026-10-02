package com.megernolep.islandeditor.ui

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

import androidx.compose.ui.draw.clipToBounds

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
    val haptic = LocalHapticFeedback.current
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()   // tanpa ini gambar peta menimpa header & menu
            .background(CvBg)
            .onSizeChanged { vm.onCanvasSize(IntSize(it.width, it.height)) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (vm.layerEditing && vm.selectedLayer()?.kind == LayerKind.IMAGE) {
                        // Mode atur gambar referensi: 1 jari = geser, 2 jari = skala + putar (bukan menggambar).
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                vm.layerTransform(
                                    event.calculateCentroid(), event.calculatePan(),
                                    event.calculateZoom(), event.calculateRotation(),
                                )
                                event.changes.forEach { it.consume() }
                            } else if (pressed.size == 1) {
                                vm.layerPan(pressed[0].positionChange())
                                pressed[0].consume()
                            }
                        } while (event.changes.any { it.pressed && !it.changedToUp() })
                        vm.layersChanged()
                        return@awaitEachGesture
                    }
                    if (vm.hitRotateHandle(down.position, 30.dp.toPx())) {
                        // Pegangan rotasi: tekan lama dulu (cegah putar tak sengaja), lalu geser untuk memutar.
                        val tooEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull
                                if (!ch.pressed) return@withTimeoutOrNull
                                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull
                            }
                        }
                        if (tooEarly == null) {      // timeout tercapai = tekan lama berhasil
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.rotateBegin(down.position)
                            do {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id }
                                if (ch != null && ch.pressed) { vm.rotateMove(ch.position); ch.consume() }
                            } while (ch != null && ch.pressed)
                            vm.rotateEnd()
                        }
                        return@awaitEachGesture
                    }
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
            drawLayers(vm, ::px)   // layer: bawah -> atas, lalu overlay objek di atasnya
            drawOverlays(vm, doc, ::px)
        }
        vm.selectedPose()?.let { drawSelection(vm, it) }   // lapisan layar: ukuran tetap
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

/** Kotak seleksi putus-putus + ikon rotasi di pojok depan-kanan. Digambar di ruang layar (ukuran konstan). */
private fun DrawScope.drawSelection(vm: EditorViewModel, pose: Pose) {
    val fr = frameOf(pose, vm.view.k)
    fun s(a: Float, b: Float): Offset = vm.mapToScreen(fr.pt(a, b))

    val c1 = s(fr.hl, -fr.hw); val c2 = s(fr.hl, fr.hw)
    val c3 = s(-fr.hl, fr.hw); val c4 = s(-fr.hl, -fr.hw)
    val box = Path().apply {
        moveTo(c1.x, c1.y); lineTo(c2.x, c2.y); lineTo(c3.x, c3.y); lineTo(c4.x, c4.y); close()
    }
    drawPath(box, Color.White.copy(alpha = .07f))
    drawPath(
        box, Color.White,
        style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
    )
    // arah depan objek
    drawLine(Accent, s(0f, 0f), s(fr.hl, 0f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)

    // pegangan rotasi
    val h = c2
    val ring = if (vm.rotLabel != null) Accent else Color.White
    drawCircle(Bg, 15.dp.toPx(), h)
    drawCircle(ring, 15.dp.toPx(), h, style = Stroke(2.dp.toPx()))
    val r = 7.dp.toPx()
    drawArc(
        color = ring, startAngle = -60f, sweepAngle = 270f, useCenter = false,
        topLeft = Offset(h.x - r, h.y - r), size = Size(2 * r, 2 * r),
        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
    )
    // kepala panah di ujung busur (sudut 210°, arah singgung searah jarum jam)
    val phi = Math.toRadians(210.0).toFloat()
    val e = Offset(h.x + r * cos(phi), h.y + r * sin(phi))
    val tg = Offset(-sin(phi), cos(phi)); val nm = Offset(cos(phi), sin(phi))
    val a = 4.dp.toPx()
    val head = Path().apply {
        moveTo(e.x + tg.x * 5.dp.toPx(), e.y + tg.y * 5.dp.toPx())
        lineTo(e.x - tg.x * 1.dp.toPx() + nm.x * a, e.y - tg.y * 1.dp.toPx() + nm.y * a)
        lineTo(e.x - tg.x * 1.dp.toPx() - nm.x * a, e.y - tg.y * 1.dp.toPx() - nm.y * a)
        close()
    }
    drawPath(head, ring)

    // label derajat saat memutar
    vm.rotLabel?.let { deg ->
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 14.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(4.dp.toPx(), 0f, 0f, android.graphics.Color.BLACK)
        }
        drawContext.canvas.nativeCanvas.drawText("${deg.roundToInt()}°", h.x, h.y - 24.dp.toPx(), paint)
    }
}

/** Layer dari bawah ke atas. Gambar referensi memakai koordinat peta (pusat, lebar dalam tile, rotasi). */
private fun DrawScope.drawLayers(vm: EditorViewModel, px: (Float) -> Float) {
    for (l in vm.layers) {
        if (!l.visible || l.opacity <= 0f) continue
        if (l.kind == LayerKind.MAP) {
            drawImage(vm.image, dstSize = IntSize(W, H), alpha = l.opacity, filterQuality = FilterQuality.None)
        } else {
            val bmp = vm.layerBitmap(l.id) ?: continue
            val s = l.widthTiles / bmp.width
            withTransform({
                translate(l.cx, l.cy)
                rotate(l.rotDeg, Offset.Zero)
                scale(s, s, Offset.Zero)
            }) {
                drawImage(bmp, topLeft = Offset(-bmp.width / 2f, -bmp.height / 2f), alpha = l.opacity, filterQuality = FilterQuality.Medium)
            }
        }
    }
    // batas kanvas pulau 256x256 selalu terlihat (walau peta transparan / disembunyikan)
    drawRect(Color.White.copy(alpha = .28f), Offset.Zero, Size(W.toFloat(), H.toFloat()), style = Stroke(px(1f)))
}
