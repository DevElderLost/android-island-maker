package com.megernolep.islandeditor.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.megernolep.islandeditor.data.AppSettings
import com.megernolep.islandeditor.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

data class ProjectItem(
    val file: File,
    val id: String,
    val typeKey: String,
    val level: Int,
    val modified: Long,
    val thumb: ImageBitmap?,
    val broken: Boolean = false,
)

/** Ikon proyek: gambar polos biome saja (tanpa bangunan / hewan / objek), di-cache supaya daftar cepat. */
object ProjectThumbs {
    class Meta(val templateId: String, val type: String, val level: Int, val bitmap: Bitmap)

    fun load(ctx: Context, file: File): Meta? {
        val dir = File(ctx.cacheDir, "thumbs").also { it.mkdirs() }
        val prefix = "${file.name.hashCode()}_"
        val key = "$prefix${file.lastModified()}"
        val png = File(dir, "$key.png")
        val meta = File(dir, "$key.txt")
        if (png.exists() && meta.exists()) {
            val bm = BitmapFactory.decodeFile(png.path)
            val lines = meta.readLines()
            if (bm != null && lines.size >= 3) return Meta(lines[0], lines[1], lines[2].toIntOrNull() ?: 0, bm)
        }
        return try {
            val spec = JSONObject(file.readText(Charsets.UTF_8))
            val raw = Base64.getDecoder().decode(spec.getString("biomes_b64"))
            if (raw.size != W * H) return null
            val px = IntArray(W * H)
            for (y in 0 until H) {
                val row = (H - 1 - y) * W        // sama dengan editor: baris data 0 di bawah
                for (x in 0 until W) {
                    val v = raw[y * W + x].toInt() and 0xFF
                    // spec lama memakai bit 0x10/0x20 untuk flag; format baru tidak pernah memasang bit itu
                    px[row + x] = Biomes.lut[if ((v and 0x30) != 0) (v and 15) else v]
                }
            }
            val small = Bitmap.createScaledBitmap(Bitmap.createBitmap(px, W, H, Bitmap.Config.ARGB_8888), 128, 128, false)
            dir.listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }
            png.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val tid = spec.optString("template_id", file.name.removeSuffix(".spec.json"))
            val type = spec.optString("island_type", "")
            val level = spec.optInt("level", 0)
            meta.writeText("$tid\n$type\n$level")
            Meta(tid, type, level, small)
        } catch (e: Exception) {
            null
        }
    }
}

class ProjectListViewModel(app: Application) : AndroidViewModel(app) {
    val catalog: Catalog = Catalog.load(app)
    var items by mutableStateOf<List<ProjectItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var toast by mutableStateOf<String?>(null)
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        val ctx = getApplication<Application>()
        val dir = AppSettings.projectDir(ctx)
        job = viewModelScope.launch {
            loading = true
            val files = withContext(Dispatchers.IO) {
                dir.listFiles { f -> f.isFile && f.name.endsWith(".spec.json") }
                    ?.sortedByDescending { it.lastModified() } ?: emptyList()
            }
            items = files.map { ProjectItem(it, it.name.removeSuffix(".spec.json"), "", 0, it.lastModified(), null) }
            for (f in files) {
                val m = withContext(Dispatchers.IO) { ProjectThumbs.load(ctx, f) }
                items = items.map {
                    if (it.file == f) it.copy(
                        id = m?.templateId ?: it.id, typeKey = m?.type ?: "", level = m?.level ?: 0,
                        thumb = m?.bitmap?.asImageBitmap(), broken = m == null,
                    ) else it
                }
            }
            loading = false
        }
    }

    fun suggestId(): String {
        val dir = AppSettings.projectDir(getApplication())
        var n = 1
        while (File(dir, "pulau_%02d.spec.json".format(n)).exists()) n++
        return "pulau_%02d".format(n)
    }

    /** @throws IllegalArgumentException dengan pesan siap tampil. */
    fun createProject(rawId: String, type: String, level: Int): File {
        val ctx = getApplication<Application>()
        val id = AppSettings.sanitizeId(rawId)
        require(id.isNotEmpty()) { "ID tidak boleh kosong (huruf, angka, _ - .)." }
        val dir = AppSettings.projectDir(ctx)
        dir.mkdirs()
        require(dir.isDirectory) { "Folder proyek tidak bisa diakses. Cek pengaturan penyimpanan." }
        val f = File(dir, "$id.spec.json")
        require(!f.exists()) { "ID \"$id\" sudah dipakai proyek lain." }
        val ocean = (catalog.types[type]?.ocean ?: 11).toByte()
        val doc = Doc(templateId = id, level = level, islandType = type, biomes = ByteArray(W * H) { ocean })
        AppSettings.writeAtomic(f, SpecCodec.encode(doc, catalog).toString().toByteArray(Charsets.UTF_8))
        return f
    }

    fun delete(item: ProjectItem) {
        val ctx = getApplication<Application>()
        item.file.delete()
        File(ctx.filesDir, "refs/${item.file.name.removeSuffix(".spec.json")}").deleteRecursively()
        refresh()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectListScreen(vm: ProjectListViewModel, onOpen: (File) -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ProjectItem?>(null) }
    val fmt = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale("id")) }

    LaunchedEffect(vm.toast) {
        vm.toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.toast = null }
    }

    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🏝️ Proyek Pulau", color = Accent, fontSize = 20.sp, modifier = Modifier.weight(1f))
                IconBtn(IconKind.FOLDER) { onSettings() }
            }
            if (vm.items.isEmpty() && !vm.loading) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada proyek.\nKetuk tombol + di kanan bawah untuk membuat pulau pertamamu.", color = Muted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(vm.items, key = { it.file.path }) { p ->
                        val typeLabel = vm.catalog.types[p.typeKey]?.label ?: "—"
                        ProjectCard(
                            item = p,
                            sub = if (p.broken) "File tidak terbaca" else "$typeLabel · Lv ${p.level} · ${fmt.format(Date(p.modified))}",
                            onClick = { onOpen(p.file) },
                            onLong = { toDelete = p },
                        )
                    }
                }
            }
        }

        // tombol float "+" kanan bawah
        Box(
            Modifier.align(Alignment.BottomEnd).padding(20.dp).size(62.dp)
                .shadow(8.dp, CircleShape).clip(CircleShape).background(Accent)
                .clickable { showNew = true },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(26.dp)) {
                val w = 3.dp.toPx()
                drawLine(Bg, Offset(size.width * .1f, size.height / 2), Offset(size.width * .9f, size.height / 2), strokeWidth = w, cap = StrokeCap.Round)
                drawLine(Bg, Offset(size.width / 2, size.height * .1f), Offset(size.width / 2, size.height * .9f), strokeWidth = w, cap = StrokeCap.Round)
            }
        }
    }

    if (showNew) {
        NewProjectDialog(vm, onDismiss = { showNew = false }, onCreated = { showNew = false; onOpen(it) })
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null }, containerColor = SurfHi,
            title = { Text("Hapus proyek?") },
            text = { Text("“${p.id}” dan layer referensinya akan dihapus permanen dari perangkat.", fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { vm.delete(p); toDelete = null }) { Text("Hapus", color = Danger) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Batal", color = TextHi) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(item: ProjectItem, sub: String, onClick: () -> Unit, onLong: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surf)
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A0D12))
                .border(1.dp, Outline, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val t = item.thumb
            if (t != null) {
                Image(t, contentDescription = null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
            } else {
                Text(if (item.broken) "⚠" else "…", color = Muted, fontSize = 20.sp)
            }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(item.id, color = TextHi, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun NewProjectDialog(vm: ProjectListViewModel, onDismiss: () -> Unit, onCreated: (File) -> Unit) {
    var id by remember { mutableStateOf(vm.suggestId()) }
    var type by remember { mutableStateOf("temperate_forest") }
    var level by remember { mutableStateOf("20") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = SurfHi,
        title = { Text("Proyek pulau baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(id, { id = it.filter { c -> !c.isWhitespace() } }, "ID pulau (template_id)")
                Dropdown(
                    vm.catalog.types[type]?.label ?: type,
                    EditorData.typeOrder.filter { vm.catalog.types.containsKey(it) }.map { it to vm.catalog.types.getValue(it).label },
                    { type = it },
                )
                Field(level, { level = it.filter { c -> c.isDigit() }.take(2) }, "Level", number = true)
                Note(error, Danger)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    onCreated(vm.createProject(id, type, (level.toIntOrNull() ?: 20).coerceIn(1, 99)))
                } catch (e: IllegalArgumentException) {
                    error = e.message ?: "Gagal"
                } catch (e: Exception) {
                    error = "Gagal membuat proyek: ${e.message}"
                }
            }) { Text("Buat", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = TextHi) } },
    )
}
