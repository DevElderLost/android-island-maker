package com.megernolep.islandeditor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class LayerKind { MAP, IMAGE }

/**
 * Satu layer. Urutan list = bawah -> atas. MAP (id 0) = peta pulau yang diedit;
 * IMAGE = gambar referensi (posisi/skala/rotasi dalam koordinat PETA: cx,cy = pusat, widthTiles = lebar dalam tile).
 */
data class Layer(
    val id: Int,
    val kind: LayerKind,
    val name: String,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val cx: Float = 128f,
    val cy: Float = 128f,
    val widthTiles: Float = 256f,
    val rotDeg: Float = 0f,
    val file: String = "",
)

internal class RefLoaded(val id: Int, val file: String, val bmp: android.graphics.Bitmap, val name: String)

/** Tombol ikon kecil (32dp) untuk panel layer. */
@Composable
fun MiniIcon(
    kind: IconKind, active: Boolean = false, enabled: Boolean = true,
    tint: Color = TextHi, onClick: () -> Unit,
) {
    val fg = when {
        !enabled -> Muted.copy(alpha = .45f)
        active -> Bg
        else -> tint
    }
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
            .background(if (active) Accent else SurfHi)
            .border(1.dp, Outline, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Canvas(Modifier.size(18.dp)) { drawIconGlyph(kind, fg) } }
}

/** Panel layer di sisi kiri kanvas (gaya aplikasi gambar). */
@Composable
fun LayersPanel(vm: EditorViewModel, onAddImage: () -> Unit, modifier: Modifier = Modifier) {
    vm.rev   // segarkan thumbnail peta saat berubah
    val sel = vm.selectedLayer()
    Column(
        modifier.width(232.dp).clip(RoundedCornerShape(16.dp)).background(Surf.copy(alpha = .96f))
            .border(1.dp, Outline, RoundedCornerShape(16.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Layer", color = TextHi, fontSize = 14.sp, modifier = Modifier.weight(1f))
            MiniIcon(IconKind.IMAGE) { onAddImage() }
            Spacer(Modifier.width(6.dp))
            MiniIcon(IconKind.CLOSE) { vm.layersOpen = false; vm.layerEditing = false }
        }

        LazyColumn(Modifier.heightIn(max = 190.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(vm.layers.asReversed(), key = { it.id }) { l -> LayerRow(vm, l, l.id == vm.selectedLayerId) }
        }
        if (vm.layers.size == 1) Note("Ketuk ikon gambar (kanan atas) untuk mengimpor gambar referensi jiplakan.")

        if (sel != null) {
            Text("Opasitas ${(sel.opacity * 100).roundToInt()}%", color = TextHi, fontSize = 12.sp)
            Slider(
                value = sel.opacity, onValueChange = { vm.setLayerOpacity(sel.id, it) },
                onValueChangeFinished = { vm.layersChanged() }, valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Outline),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniIcon(IconKind.UP) { vm.moveLayer(sel.id, true) }
                MiniIcon(IconKind.DOWN) { vm.moveLayer(sel.id, false) }
                if (sel.kind == LayerKind.IMAGE) {
                    MiniIcon(IconKind.MOVE, active = vm.layerEditing) { vm.layerEditing = !vm.layerEditing }
                    MiniIcon(IconKind.FIT) { vm.fitLayerToMap(sel.id) }
                    MiniIcon(IconKind.TRASH, tint = Danger) { vm.removeLayer(sel.id) }
                }
            }
            if (vm.layerEditing && sel.kind == LayerKind.IMAGE) {
                Note("Mode atur gambar: 1 jari geser · 2 jari skala & putar gambar.", Accent2)
            }
        }
    }
}

@Composable
private fun LayerRow(vm: EditorViewModel, l: Layer, selected: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) Accent.copy(alpha = .20f) else Color.Transparent)
            .clickable {
                vm.selectedLayerId = l.id
                if (l.kind != LayerKind.IMAGE) vm.layerEditing = false
            }
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black)
                .border(1.dp, Outline, RoundedCornerShape(6.dp)),
        ) {
            val bmp = vm.layerBitmap(l.id)
            if (bmp != null) {
                Image(
                    bmp, contentDescription = null, contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            l.name, color = if (l.visible) TextHi else Muted, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp).weight(1f),
        )
        MiniIcon(if (l.visible) IconKind.EYE else IconKind.EYE_OFF) { vm.setLayerVisible(l.id, !l.visible) }
    }
}
