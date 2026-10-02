package com.megernolep.islandeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.megernolep.islandeditor.data.AppSettings
import java.io.File

private fun firstExisting(f: File, root: File): File {
    var c: File? = f
    while (c != null && !c.isDirectory) c = c.parentFile
    return c ?: root
}

/** Pemilih folder bawaan (butuh izin akses file): telusuri, buat folder baru, pilih. */
@Composable
fun FolderPicker(start: File, onPick: (File) -> Unit, onDismiss: () -> Unit) {
    val root = remember { AppSettings.storageRoot() }
    var cur by remember { mutableStateOf(firstExisting(start, root)) }
    var tick by remember { mutableIntStateOf(0) }
    var newName by remember { mutableStateOf<String?>(null) }
    val dirs = remember(cur, tick) {
        cur.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Column(Modifier.fillMaxWidth().background(Surf).padding(16.dp)) {
                    Text("Pilih folder", color = Accent, fontSize = 16.sp)
                    Text(cur.absolutePath, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    if (cur.absolutePath != root.absolutePath && cur.parentFile != null) {
                        item { FolderRow("⬆   ..  (naik satu tingkat)") { cur = cur.parentFile ?: cur } }
                    }
                    items(dirs, key = { it.absolutePath }) { d -> FolderRow("📁   ${d.name}") { cur = d } }
                    if (dirs.isEmpty()) item { Note("Tidak ada subfolder di sini.", modifier = Modifier.padding(16.dp)) }
                }
                Row(
                    Modifier.fillMaxWidth().background(Surf).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = { newName = "" }, modifier = Modifier.weight(1f)) {
                        Text("＋ Folder baru", color = TextHi, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { onPick(cur) }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                    ) { Text("Pilih folder ini", fontSize = 13.sp) }
                }
            }
        }
    }

    newName?.let { n ->
        AlertDialog(
            onDismissRequest = { newName = null }, containerColor = SurfHi,
            title = { Text("Folder baru") },
            text = { Field(n, { newName = it }, "Nama folder") },
            confirmButton = {
                TextButton(onClick = {
                    val name = AppSettings.sanitizeFileName(n.trim())
                    if (name.isNotEmpty()) {
                        val f = File(cur, name)
                        f.mkdirs()
                        if (f.isDirectory) cur = f
                        tick++
                    }
                    newName = null
                }) { Text("Buat", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { newName = null }) { Text("Batal", color = TextHi) } },
        )
    }
}

@Composable
private fun FolderRow(label: String, onClick: () -> Unit) {
    Text(
        label, color = TextHi, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
