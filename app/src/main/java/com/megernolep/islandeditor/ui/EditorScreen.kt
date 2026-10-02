package com.megernolep.islandeditor.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.height

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.megernolep.islandeditor.domain.EditorData

@Composable
fun EditorScreen(vm: EditorViewModel) {
    val ctx = LocalContext.current
    var menuOpen by rememberSaveable { mutableStateOf(true) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addReferenceImage(uri)
    }

    LaunchedEffect(vm.toast) {
        vm.toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.toast = null }
    }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding()) {
        Header(vm)
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = SurfHi)

        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            MapCanvas(vm)
            Column(
                Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp, end = 64.dp),   // toolbar+panel kolom
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CanvasToolbar(vm, onImport = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier)
                if (vm.layersOpen) {
                    LayersPanel(vm, onAddImage = { imagePicker.launch(arrayOf("image/*")) })
                }
            }
            ViewControls(vm, Modifier.align(Alignment.TopEnd).padding(8.dp))
            Text(
                "${vm.doc.naturals.size} natural · ${vm.doc.herds.size} herd · ${vm.doc.buildings.size} bangunan",
                color = Muted, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                    .background(Bg.copy(alpha = .6f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        if (vm.hint.isNotEmpty() && vm.mode != Mode.BUILDING) {
            Note(vm.hint, Warn, Modifier.fillMaxWidth().background(SurfHi).padding(horizontal = 12.dp, vertical = 6.dp))
        }

        // Handle di tengah-atas menu: geser ke atas = muncul, geser ke bawah = sembunyi (atau ketuk).
        MenuHandle(open = menuOpen, onChange = { menuOpen = it })
        AnimatedVisibility(menuOpen, enter = expandVertically(), exit = shrinkVertically()) {
        Column {
        AnimatedVisibility(vm.panelOpen) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 300.dp).background(Surf)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
            ) { ToolPanel(vm); Box(Modifier.size(8.dp)) }
        }

        ModeBar(vm)
        ActionBar(vm) { importLauncher.launch(arrayOf("*/*")) }
        }
        }
    }

    Dialogs(vm)
}

// ------------------------------------------------------------ header
@Composable
private fun Header(vm: EditorViewModel) {
    var open by remember { mutableStateOf(true) }
    val d = vm.doc
    Column(Modifier.fillMaxWidth().background(Surf).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val act = LocalContext.current as? android.app.Activity
            IconBtn(IconKind.BACK) { act?.finish() }
            Text("  " + vm.projectTitle(), fontSize = 16.sp, color = Accent, maxLines = 1, modifier = Modifier.weight(1f))
            Row(Modifier.clickable { open = !open }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (open) "Sembunyikan" else "Info pulau", color = Muted, fontSize = 12.sp)
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp),
                )
            }
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(d.templateId, { vm.setTemplateId(it.filter { c -> !c.isWhitespace() }) }, "template_id", Modifier.weight(1f))
                    Field(d.level.toString(), { it.toIntOrNull()?.let { v -> vm.setLevel(v.coerceIn(1, 99)) } }, "lv", Modifier.weight(.35f), number = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dropdown(
                        "Peran ${d.role}", (1..6).map { it to "Peran $it" }, { vm.setRole(it) }, Modifier.weight(.45f),
                    )
                    Dropdown(
                        vm.catalog.types[d.islandType]?.label ?: d.islandType,
                        EditorData.typeOrder.filter { vm.catalog.types.containsKey(it) }.map { it to (vm.catalog.types.getValue(it).label) },
                        { vm.requestTypeChange(it) }, Modifier.weight(.55f),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------ kontrol tampilan
@Composable
private fun CircleBtn(label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background((if (active) Accent else Surf).copy(alpha = .92f))
            .border(1.dp, Outline, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (active) Bg else TextHi, fontSize = 16.sp) }
}

@Composable
private fun ViewControls(vm: EditorViewModel, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconBtn(IconKind.ZOOM_IN) { vm.zoomBy(1.4f) }
        IconBtn(IconKind.ZOOM_OUT) { vm.zoomBy(1f / 1.4f) }
        IconBtn(IconKind.FIT) { vm.fitView() }
        IconBtn(IconKind.ROT_LEFT) { vm.rotateBy(-0.2618f) }    // 15° berlawanan arah jarum jam
        IconBtn(IconKind.ROT_RIGHT) { vm.rotateBy(0.2618f) }    // 15° searah jarum jam
        IconBtn(IconKind.COMPASS, active = vm.gameView) { vm.toggleGameView() }
        IconBtn(IconKind.PAN, active = vm.panMode) { vm.panMode = !vm.panMode }
    }
}

// ------------------------------------------------------------ bar mode
@Composable
private fun ModeBar(vm: EditorViewModel) {
    LazyRow(
        Modifier.fillMaxWidth().background(Surf), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(Mode.values().toList()) { m ->
            val on = vm.mode == m
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(if (on) Accent else SurfHi)
                    .border(1.dp, if (on) Accent else Outline, RoundedCornerShape(50))
                    .clickable { vm.onModeTap(m) }.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(modeIcon(m), contentDescription = null, tint = if (on) Bg else TextHi, modifier = Modifier.size(16.dp))
                Text("  ${m.label}", color = if (on) Bg else TextHi, fontSize = 12.sp)
            }
        }
    }
}

// ------------------------------------------------------------ bar aksi
@Composable
private fun ActionBar(vm: EditorViewModel, onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Surf).padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(IconKind.TRASH, tint = Danger) { vm.clearPrompt = true }
            OutlinedButton(
                onClick = { vm.requestExport(ExportKind.SPEC) }, enabled = !vm.busy,
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Outline),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) { IconLabel(Icons.Filled.Save, "Simpan .spec.json") }
            Button(
                onClick = { vm.requestExport(ExportKind.ISLAND) }, enabled = !vm.busy,
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) { IconLabel(Icons.Filled.Archive, "Export as Island", tint = Bg, textColor = Bg) }
        }
    }
}

@Composable
private fun SmallBtn(label: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, if (danger) Danger.copy(alpha = .5f) else Outline),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) { Text(label, color = if (!enabled) Muted else if (danger) Danger else TextHi, fontSize = 12.sp) }
}

// ------------------------------------------------------------ dialog
@Composable
private fun Dialogs(vm: EditorViewModel) {
    val ctx = LocalContext.current

    vm.pendingExport?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.dismissExport() }, containerColor = SurfHi,
            title = { Text("Periksa dulu") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    p.warnings.forEach { Note("• $it", Warn) }
                }
            },
            confirmButton = { TextButton(onClick = { vm.confirmExport() }) { Text("Tetap export", color = Accent) } },
            dismissButton = { TextButton(onClick = { vm.dismissExport() }) { Text("Perbaiki dulu", color = TextHi) } },
        )
    }

    vm.typeChangePrompt?.let { t ->
        val label = vm.catalog.types[t]?.label ?: t
        AlertDialog(
            onDismissRequest = { vm.dismissTypeChange() }, containerColor = SurfHi,
            title = { Text("Ubah tipe ke $label?") },
            text = { Text("Semua tile darat/pantai/laut diubah ke biome tipe baru. Herd yang tidak cocok dibuang. Objek lain tetap. Bisa di-undo.", fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { vm.applyTypeChange(t) }) { Text("Ubah", color = Accent) } },
            dismissButton = { TextButton(onClick = { vm.dismissTypeChange() }) { Text("Batal", color = TextHi) } },
        )
    }

    if (vm.clearPrompt) {
        AlertDialog(
            onDismissRequest = { vm.clearPrompt = false }, containerColor = SurfHi,
            title = { Text("Kosongkan semua?") },
            text = { Text("Peta dan semua objek dihapus (template_id, level, tipe tetap). Bisa di-undo.", fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { vm.clearAll() }) { Text("Kosongkan", color = Danger) } },
            dismissButton = { TextButton(onClick = { vm.clearPrompt = false }) { Text("Batal", color = TextHi) } },
        )
    }

    vm.exportResult?.let { r ->
        AlertDialog(
            onDismissRequest = { vm.exportResult = null }, containerColor = SurfHi,
            title = { IconLabel(Icons.Filled.CheckCircle, r.title, tint = Accent, fontSize = 18.sp, iconSize = 22.dp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(r.path, color = Accent, fontSize = 13.sp)
                    r.details.forEach { Note(it) }
                    r.warnings.forEach { Note("[!] $it", Warn) }
                }
            },
            confirmButton = { TextButton(onClick = { vm.exportResult = null }) { Text("OK", color = Accent) } },
            dismissButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = r.mime
                        putExtra(Intent.EXTRA_STREAM, r.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(send, "Bagikan file"))
                }) { Text("Bagikan", color = TextHi) }
            },
        )
    }
}

@Composable
private fun MenuHandle(open: Boolean, onChange: (Boolean) -> Unit) {
    var acc by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxWidth().height(30.dp)
            .background(Surf, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .clickable { onChange(!open) }
            .pointerInput(open) {
                detectVerticalDragGestures(
                    onDragStart = { acc = 0f },
                    onDragEnd = {
                        if (acc < -30f) onChange(true) else if (acc > 30f) onChange(false)
                        acc = 0f
                    },
                    onDragCancel = { acc = 0f },
                    onVerticalDrag = { change, dy -> change.consume(); acc += dy },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 44.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(Outline))
    }
}

/** Undo · Redo · Flip H · Flip V · Impor — ikon bulat berbaris horizontal di kiri atas kanvas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CanvasToolbar(vm: EditorViewModel, onImport: () -> Unit, modifier: Modifier) {
    // Dua kelompok = dua anak FlowRow: kalau tidak muat satu baris, kelompok kedua turun UTUH ke baris bawah.
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBtn(IconKind.UNDO, enabled = vm.canUndo) { vm.undo() }
            IconBtn(IconKind.REDO, enabled = vm.canRedo) { vm.redo() }
            IconBtn(IconKind.FLIP_H) { vm.flip(true) }
            IconBtn(IconKind.FLIP_V) { vm.flip(false) }
            IconBtn(IconKind.IMPORT) { onImport() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBtn(IconKind.BRUSH, active = vm.mode != Mode.ERASE) { vm.selectBrush() }
            IconBtn(IconKind.ERASER, active = vm.mode == Mode.ERASE) { vm.selectEraser() }
            IconBtn(IconKind.LAYERS, active = vm.layersOpen) { vm.layersOpen = !vm.layersOpen }
        }
    }
}
