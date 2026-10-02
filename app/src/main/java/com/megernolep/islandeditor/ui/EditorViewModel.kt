package com.megernolep.islandeditor.ui

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.FileProvider
import com.megernolep.islandeditor.data.AppSettings
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.megernolep.islandeditor.data.FileStore
import com.megernolep.islandeditor.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class TileHit(val x: Int, val y: Int, val inside: Boolean)

/**
 * Satu-satunya pemegang state editor. Semua logika "apa yang terjadi saat tile disentuh" ada di sini,
 * sedangkan perhitungan murni ada di paket domain (Ops, WaterLayers, SpecCodec, IslandBuilder).
 */
class EditorViewModel(app: Application) : AndroidViewModel(app) {
    val catalog: Catalog = Catalog.load(app)
    private val prefs = app.getSharedPreferences("island_editor", Context.MODE_PRIVATE)

    // ---------------- state dokumen ----------------
    var doc by mutableStateOf(Doc())
        private set
    /** Naik tiap biome berubah di tempat — dibaca oleh canvas supaya redraw. */
    var rev by mutableIntStateOf(0)
        private set
    var canUndo by mutableStateOf(false)
        private set

    // ---------------- state UI ----------------
    var mode by mutableStateOf(Mode.BIOME)
        private set
    var panelOpen by mutableStateOf(true)
    var opts by mutableStateOf(
        ToolOpts(
            selNatural = catalog.naturals.firstOrNull { it.cat == "tree" }?.id ?: 0,
            selLandmark = catalog.landmarks.firstOrNull()?.prefab ?: "",
            selRail = (catalog.landmarks.firstOrNull { it.prefab.contains("train_wreckage_01_a", true) }
                ?: catalog.landmarks.firstOrNull { EditorData.isGlobalPrefab(it.prefab) })?.prefab ?: "",
        ),
    )
        private set
    var hint by mutableStateOf("")
        private set
    var hover by mutableStateOf<Pt?>(null)
        private set
    var trigPending by mutableStateOf<Pt?>(null)
        private set
    var panMode by mutableStateOf(false)
    var gameView by mutableStateOf(true)
        private set
    var view by mutableStateOf(ViewState())
        private set
    var busy by mutableStateOf(false)
        private set
    var toast by mutableStateOf<String?>(null)
    var pendingExport by mutableStateOf<ExportPrompt?>(null)
        private set
    var exportResult by mutableStateOf<ExportResult?>(null)
    var typeChangePrompt by mutableStateOf<String?>(null)
        private set
    var clearPrompt by mutableStateOf(false)

    val rockMarks = mutableStateMapOf<Int, String>()
    private val natVisCache = HashMap<Int, Pair<Int, Float>>()

    // ---------------- bitmap peta ----------------
    private val bitmap: Bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val image: ImageBitmap = bitmap.asImageBitmap()
    private val pixels = IntArray(W * H)

    private fun refreshBitmap() {
        val b = doc.biomes
        val lut = Biomes.lut
        for (y in 0 until H) {
            val row = (H - 1 - y) * W      // Unity menggambar baris data 0 di BAWAH: flip vertikal
            val src = y * W
            for (x in 0 until W) pixels[row + x] = lut[b[src + x].toInt() and 0xFF]
        }
        bitmap.setPixels(pixels, 0, W, 0, 0, W, H)
    }

    private fun bump() { refreshBitmap(); rev++ }

    // ---------------- undo ----------------
    // ---------------- seleksi & rotasi objek (landmark / kereta / hewan objek) ----------------
    var selection by mutableStateOf<Sel?>(null)
        private set
    /** Derajat saat sedang memutar (label di kanvas); null kalau tidak sedang memutar. */
    var rotLabel by mutableStateOf<Float?>(null)
        private set
    private var rotating = false
    private var rotStartAngle = 0f
    private var rotBase = 0f
    private var rotSnap: Doc? = null
    private var rotPushed = false

    fun selectedPose(): Pose? {
        val s = selection ?: return null
        return when (s.kind) {
            SelKind.LANDMARK -> doc.landmarks.getOrNull(s.index)?.let { l ->
                val g = EditorData.isGlobalPrefab(l.prefab)
                Pose(l.x, l.y, (l.rotate * 2).toFloat(), if (g) 3f else 4f, if (g) 7f else 4f)
            }
            SelKind.DECOR -> doc.decor.getOrNull(s.index)?.let { d -> Pose(d.x, d.y, d.yaw.toFloat(), 3f, 3f) }
        }
    }

    fun mapToScreen(m: Offset): Offset {
        val vw = view
        val r = rotV(m.x * vw.k, m.y * vw.k, vw.rot)
        return Offset(vw.tx + r.x, vw.ty + r.y)
    }

    fun hitRotateHandle(p: Offset, radiusPx: Float): Boolean {
        val pose = selectedPose() ?: return false
        return (p - mapToScreen(frameOf(pose, view.k).handle)).getDistance() <= radiusPx
    }

    private fun angleAt(screen: Offset, pose: Pose): Float {
        val m = screenToMap(screen)
        val dx = m.x - (pose.x + 0.5f)
        val dy = m.y - (H - pose.y - 0.5f)
        return Math.toDegrees(atan2(dx.toDouble(), (-dy).toDouble())).toFloat()
    }

    private fun snapDeg(d: Float): Float {
        val n = (d / 45f).roundToInt() * 45f
        return if (abs(d - n) < 3f) n else d
    }

    fun rotateBegin(p: Offset) {
        val pose = selectedPose() ?: return
        rotating = true
        rotSnap = doc.deepCopy()
        rotPushed = false
        rotBase = pose.deg
        rotStartAngle = angleAt(p, pose)
        rotLabel = pose.deg
    }

    fun rotateMove(p: Offset) {
        if (!rotating) return
        val s = selection ?: return
        val pose = selectedPose() ?: return
        val raw = rotBase + (angleAt(p, pose) - rotStartAngle)
        applyRotation(s, snapDeg(((raw % 360f) + 360f) % 360f))
    }

    fun rotateEnd() { rotating = false; rotLabel = null; rotSnap = null }

    private fun prepareRotUndo() {
        if (!rotPushed) { rotSnap?.let { pushUndo(it) }; rotPushed = true }
    }

    private fun applyRotation(s: Sel, deg: Float) {
        when (s.kind) {
            SelKind.LANDMARK -> {
                val l = doc.landmarks.getOrNull(s.index) ?: return
                val r = ((Math.round(deg / 2f) % 180) + 180) % 180      // yaw = rotate * 2°
                if (r == l.rotate) return
                prepareRotUndo()
                doc = doc.copy(landmarks = doc.landmarks.toMutableList().also { it[s.index] = l.copy(rotate = r) })
                if (EditorData.isGlobalPrefab(l.prefab)) opts = opts.copy(railRot = r)   // ingat untuk objek berikutnya
                rotLabel = r * 2f
            }
            SelKind.DECOR -> {
                val d = doc.decor.getOrNull(s.index) ?: return
                val r = ((Math.round(deg) % 360) + 360) % 360
                if (r == d.yaw) return
                prepareRotUndo()
                doc = doc.copy(decor = doc.decor.toMutableList().also { it[s.index] = d.copy(yaw = r) })
                opts = opts.copy(decRot = r)
                rotLabel = r.toFloat()
            }
        }
        rev++
    }

    private fun nearest(cands: List<Triple<Int, Int, Int>>, x: Int, y: Int): Int {
        var best = -1
        var bd = 7                       // jarak kuadrat < 7  (~2,5 tile)
        for ((i, px, py) in cands) {
            val d = (px - x) * (px - x) + (py - y) * (py - y)
            if (d < bd) { bd = d; best = i }
        }
        return best
    }

    /** Ketuk dekat objek yang ada -> pilih (false = dokumen tidak berubah). Kalau tidak -> taruh baru & pilih (true). */
    private fun placeOrSelectLandmark(x: Int, y: Int, prefab: String, rot: Int, global: Boolean): Boolean {
        val cands = doc.landmarks.mapIndexedNotNull { i, l ->
            if (EditorData.isGlobalPrefab(l.prefab) == global) Triple(i, l.x, l.y) else null
        }
        val hit = nearest(cands, x, y)
        if (hit >= 0) { selection = Sel(SelKind.LANDMARK, hit); return false }
        val rotate = if (global) ((rot % 180) + 180) % 180 else 0
        doc = doc.copy(landmarks = doc.landmarks + Landmark(x, y, prefab, rotate = rotate))
        selection = Sel(SelKind.LANDMARK, doc.landmarks.lastIndex)
        return true
    }

    private fun placeOrSelectDecor(x: Int, y: Int, type: Int, yaw: Int): Boolean {
        val hit = nearest(doc.decor.mapIndexed { i, d -> Triple(i, d.x, d.y) }, x, y)
        if (hit >= 0) { selection = Sel(SelKind.DECOR, hit); return false }
        doc = doc.copy(decor = doc.decor + Decor(x, y, type, yaw))
        selection = Sel(SelKind.DECOR, doc.decor.lastIndex)
        return true
    }

    // ---------------- proyek (file .spec.json di folder proyek) ----------------
    var projectFile: File? = null
        private set
    private var savedDoc: Doc? = null
    private var savedRev = 0

    private fun keyOf(f: File) = f.name.removeSuffix(".spec.json")
    private fun projectKey(): String = projectFile?.let { keyOf(it) } ?: "scratch"
    fun projectTitle(): String = projectFile?.let { keyOf(it) } ?: "Island Editor"

    /** Muat proyek dari file .spec.json (dipanggil MainActivity dengan path dari daftar proyek). */
    fun openProject(file: File?) {
        if (file == null || !file.exists()) return
        try {
            val d = SpecCodec.decode(JSONObject(file.readText(Charsets.UTF_8)), catalog)
            projectFile = file
            doc = d
            undoStack.clear(); redoStack.clear(); canUndo = false; canRedo = false
            selection = null; trigPending = null
            applyTypeUi()
            bump()
            savedDoc = doc; savedRev = rev
            loadLayers()
        } catch (e: Exception) {
            toast = "Gagal membuka proyek: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Hasil export: spec -> folder proyek (file ikut diganti nama bila template_id berubah), zip -> folder export. */
    private fun writeOutput(kind: ExportKind, name: String, bytes: ByteArray): File {
        val ctx = getApplication<Application>()
        if (kind == ExportKind.ISLAND) {
            val f = File(AppSettings.exportDir(ctx).also { it.mkdirs() }, AppSettings.sanitizeFileName(name))
            AppSettings.writeAtomic(f, bytes)
            return f
        }
        val dir = AppSettings.projectDir(ctx).also { it.mkdirs() }
        var target = File(dir, AppSettings.sanitizeFileName(name))
        val cur = projectFile
        if (cur != null && cur != target && target.exists()) target = cur   // ID baru bentrok dengan proyek lain: tetap di file lama
        AppSettings.writeAtomic(target, bytes)
        if (cur != null && cur != target) {
            File(ctx.filesDir, "refs/${keyOf(cur)}").renameTo(File(ctx.filesDir, "refs/${keyOf(target)}"))
            cur.delete()
        }
        projectFile = target
        savedDoc = doc; savedRev = rev
        return target
    }

    // ---------------- kuas <-> penghapus (ikon toolbar) ----------------
    private var lastDrawMode = Mode.BIOME
    private fun lastDrawModeSync(m: Mode) { if (m == Mode.ERASE && mode != Mode.ERASE) lastDrawMode = mode }
    private fun switchMode(m: Mode) {
        mode = m; trigPending = null; hover = null; hint = ""; selection = null; layerEditing = false
    }
    /** Ikon Kuas: dari penghapus kembali ke mode menggambar sebelumnya; selain itu ke kuas biome. */
    fun selectBrush() { switchMode(if (mode == Mode.ERASE) lastDrawMode else Mode.BIOME) }
    fun selectEraser() { if (mode != Mode.ERASE) { lastDrawMode = mode; switchMode(Mode.ERASE) } }

    // ---------------- layer: peta + gambar referensi untuk menjiplak ----------------
    val layers = mutableStateListOf(Layer(id = 0, kind = LayerKind.MAP, name = "Peta pulau"))
    var selectedLayerId by mutableIntStateOf(0)
    var layersOpen by mutableStateOf(false)
    /** true = gerakan di kanvas menggeser/menskala/memutar gambar referensi terpilih (bukan menggambar). */
    var layerEditing by mutableStateOf(false)
    private val layerBitmaps = ConcurrentHashMap<Int, ImageBitmap>()
    private var nextLayerId = 1

    fun layerBitmap(id: Int): ImageBitmap? = if (id == 0) image else layerBitmaps[id]
    fun selectedLayer(): Layer? = layers.firstOrNull { it.id == selectedLayerId }

    private fun updateLayer(id: Int, f: (Layer) -> Layer) {
        val i = layers.indexOfFirst { it.id == id }
        if (i >= 0) layers[i] = f(layers[i])
    }

    fun setLayerVisible(id: Int, v: Boolean) { updateLayer(id) { it.copy(visible = v) }; persistLayers() }
    fun setLayerOpacity(id: Int, v: Float) { updateLayer(id) { it.copy(opacity = v.coerceIn(0f, 1f)) } }
    fun layersChanged() = persistLayers()

    fun moveLayer(id: Int, up: Boolean) {
        val i = layers.indexOfFirst { it.id == id }
        val j = if (up) i + 1 else i - 1
        if (i < 0 || j < 0 || j >= layers.size) return
        val a = layers[i]; layers[i] = layers[j]; layers[j] = a
        persistLayers()
    }

    fun removeLayer(id: Int) {
        val l = layers.firstOrNull { it.id == id } ?: return
        if (l.kind != LayerKind.IMAGE) return
        layers.removeAll { it.id == id }
        layerBitmaps.remove(id)
        layerDir(false)?.let { File(it, l.file).delete() }
        if (selectedLayerId == id) selectedLayerId = 0
        layerEditing = false
        persistLayers()
        rev++
    }

    fun fitLayerToMap(id: Int) {
        val b = layerBitmaps[id] ?: return
        updateLayer(id) { it.copy(cx = W / 2f, cy = H / 2f, rotDeg = 0f, widthTiles = min(W.toFloat(), W.toFloat() * b.width / b.height)) }
        persistLayers()
    }

    /** Geser gambar referensi (delta layar -> delta peta). */
    fun layerPan(dScreen: Offset) {
        val l = selectedLayer() ?: return
        if (l.kind != LayerKind.IMAGE) return
        val m = rotV(dScreen.x, dScreen.y, -view.rot)
        updateLayer(l.id) { it.copy(cx = it.cx + m.x / view.k, cy = it.cy + m.y / view.k) }
    }

    /** Skala + putar gambar referensi terhadap titik tengah jari. */
    fun layerTransform(centroid: Offset, pan: Offset, zoom: Float, rotDeg: Float) {
        val l = selectedLayer() ?: return
        if (l.kind != LayerKind.IMAGE) return
        val pivot = screenToMap(centroid)
        val d = pivot - screenToMap(centroid - pan)
        val th = Math.toRadians(rotDeg.toDouble())
        val c = cos(th).toFloat(); val s = sin(th).toFloat()
        val vx = l.cx + d.x - pivot.x; val vy = l.cy + d.y - pivot.y
        updateLayer(l.id) {
            it.copy(
                cx = pivot.x + (vx * c - vy * s) * zoom,
                cy = pivot.y + (vx * s + vy * c) * zoom,
                widthTiles = (it.widthTiles * zoom).coerceIn(4f, 4096f),
                rotDeg = it.rotDeg + rotDeg,
            )
        }
    }

    private fun layerDir(create: Boolean): File? {
        val d = File(getApplication<Application>().filesDir, "refs/${projectKey()}")
        if (create) d.mkdirs()
        return d
    }

    private fun decodeBytes(bytes: ByteArray, maxSide: Int = 2048): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (max(o.outWidth, o.outHeight) / s > maxSide) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun exifDegrees(bytes: ByteArray): Int = try {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) { 0 }

    /** Impor gambar referensi: disalin ke penyimpanan app (maks 2048 px) supaya tetap ada walau file asli dihapus. */
    fun addReferenceImage(uri: Uri) {
        val ctx = getApplication<Application>()
        busy = true
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("Tidak bisa membuka gambar")
                    var bmp = decodeBytes(bytes) ?: throw IOException("Format gambar tidak didukung")
                    val deg = exifDegrees(bytes)
                    if (deg != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
                    val id = nextLayerId++
                    val fileName = "img_$id.png"
                    File(layerDir(true), fileName).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    val label = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Gambar $id"
                    RefLoaded(id, fileName, bmp, label)
                }
                layerBitmaps[r.id] = r.bmp.asImageBitmap()
                val w = min(W.toFloat(), W.toFloat() * r.bmp.width / r.bmp.height)
                layers.add(Layer(r.id, LayerKind.IMAGE, r.name, true, 0.5f, W / 2f, H / 2f, w, 0f, r.file))
                selectedLayerId = r.id
                layersOpen = true
                layerEditing = false
                persistLayers()
                rev++
            } catch (e: Exception) {
                toast = "Gagal impor gambar: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    private fun persistLayers() {
        try {
            val dir = layerDir(true) ?: return
            val arr = JSONArray()
            for (l in layers) {
                arr.put(
                    JSONObject().put("id", l.id).put("kind", l.kind.name).put("name", l.name)
                        .put("visible", l.visible).put("opacity", l.opacity.toDouble())
                        .put("cx", l.cx.toDouble()).put("cy", l.cy.toDouble())
                        .put("w", l.widthTiles.toDouble()).put("rot", l.rotDeg.toDouble()).put("file", l.file),
                )
            }
            File(dir, "layers.json").writeText(arr.toString())
        } catch (_: Exception) { /* best-effort */ }
    }

    private fun loadLayers() {
        layers.clear(); layerBitmaps.clear()
        selectedLayerId = 0; layerEditing = false
        val dir = layerDir(false)
        val loaded = ArrayList<Layer>()
        try {
            val f = dir?.let { File(it, "layers.json") }
            if (f != null && f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    loaded.add(
                        Layer(
                            o.optInt("id"), if (o.optString("kind") == "MAP") LayerKind.MAP else LayerKind.IMAGE,
                            o.optString("name", "Layer"), o.optBoolean("visible", true),
                            o.optDouble("opacity", 1.0).toFloat(), o.optDouble("cx", 128.0).toFloat(),
                            o.optDouble("cy", 128.0).toFloat(), o.optDouble("w", 256.0).toFloat(),
                            o.optDouble("rot", 0.0).toFloat(), o.optString("file", ""),
                        ),
                    )
                }
            }
        } catch (_: Exception) { loaded.clear() }
        if (loaded.none { it.kind == LayerKind.MAP }) loaded.add(0, Layer(id = 0, kind = LayerKind.MAP, name = "Peta pulau"))
        layers.addAll(loaded)
        nextLayerId = (loaded.maxOfOrNull { it.id } ?: 0) + 1
        val imgs = loaded.filter { it.kind == LayerKind.IMAGE && it.file.isNotEmpty() }
        if (imgs.isNotEmpty() && dir != null) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    for (l in imgs) {
                        val f = File(dir, l.file)
                        if (f.exists()) decodeBytes(f.readBytes())?.let { layerBitmaps[l.id] = it.asImageBitmap() }
                    }
                }
                rev++
            }
        }
    }

    private val undoStack = ArrayList<Doc>()
    private val redoStack = ArrayList<Doc>()
    var canRedo by mutableStateOf(false)
        private set

    private fun pushUndo(snapshot: Doc) {
        undoStack.add(snapshot)
        if (undoStack.size > 40) undoStack.removeAt(0)
        canUndo = true
        redoStack.clear(); canRedo = false   // REDO_CLEAR
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(doc.deepCopy()); canRedo = true
        doc = undoStack.removeAt(undoStack.lastIndex)
        selection = null   // SEL_UNDO
        trigPending = null
        canUndo = undoStack.isNotEmpty()
        bump()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(doc.deepCopy())
        doc = redoStack.removeAt(redoStack.lastIndex)
        selection = null
        trigPending = null
        canUndo = true
        canRedo = redoStack.isNotEmpty()
        bump()
    }

    /** Edit diskret (satu langkah undo). */
    private fun edit(block: (Doc) -> Doc) {
        pushUndo(doc.deepCopy())
        selection = null   // SEL_EDIT
        doc = block(doc)
        bump()
    }

    // ---------------- metadata pulau ----------------
    fun setTemplateId(v: String) { doc = doc.copy(templateId = v) }
    fun setLevel(v: Int) { doc = doc.copy(level = v) }
    fun setRole(v: Int) { doc = doc.copy(role = v) }
    fun setBuildingAnchor(v: String) { doc = doc.copy(buildingAnchor = v) }

    fun typeCodes(type: String): Set<Int> {
        val p = catalog.types[type] ?: return emptySet()
        return buildSet { add(p.land); add(p.beach); add(p.ocean); add(13); add(14); if (p.lava) add(15) }
    }

    /** Ganti tipe pulau: kalau peta sudah dilukis, minta konfirmasi dulu. */
    fun requestTypeChange(newType: String) {
        if (newType == doc.islandType || !catalog.types.containsKey(newType)) return
        if (doc.isBlank) applyTypeChange(newType) else typeChangePrompt = newType
    }

    fun dismissTypeChange() { typeChangePrompt = null }

    fun applyTypeChange(newType: String) {
        typeChangePrompt = null
        pushUndo(doc.deepCopy())
        val (d, removed) = Ops.convertToType(doc, catalog, newType)
        doc = d
        bump()
        applyTypeUi()
        if (removed > 0) hint = "$removed titik herd dibuang (hewan tidak cocok dengan tipe baru)."
    }

    /** Selaraskan palet biome & kontrol herd dengan tipe pulau aktif. */
    fun applyTypeUi() {
        val p = catalog.types[doc.islandType] ?: return
        if (opts.lockPalette && opts.selBiome !in typeCodes(doc.islandType)) opts = opts.copy(selBiome = p.land)
        refreshHerdControls(true)
    }

    // ---------------- mode & opts ----------------
    fun onModeTap(m: Mode) {
        if (mode == m) { panelOpen = !panelOpen; return }
        lastDrawModeSync(m); mode = m; panelOpen = true; selection = null; layerEditing = false   // SEL_MODE
        trigPending = null; hover = null; hint = ""
    }

    fun setOpts(f: (ToolOpts) -> ToolOpts) { opts = f(opts) }

    // ---------------- pencarian toleran ejaan ----------------
    private fun skel(t: String): String = t.lowercase()
        .replace("ph", "f").replace("ch", "k").replace("th", "t")
        .replace(Regex("c(?=[eiy])"), "s").replace(Regex("[ck]+"), "k")
        .replace("q", "k").replace("x", "ks").replace(Regex("[^a-z]"), "")
        .replace(Regex("[aeiouyh]"), "").replace(Regex("(.)\\1+"), "\$1")

    fun matchQ(text: String, q: String): Boolean {
        val t = text.lowercase()
        if (t.contains(q)) return true
        val sq = skel(q)
        return q.length >= 4 && sq.length >= 3 && skel(t).contains(sq)
    }

    // ---------------- natural ----------------
    fun natLabel(n: NatItem) = EditorData.natLabel(n, rockMarks)
    fun natColor(n: NatItem) = EditorData.natColor(n, rockMarks)

    private fun natAllowed(n: NatItem): Boolean =
        !opts.natTypeFilter || n.biomes.any { it in typeCodes(doc.islandType) }

    private fun natBiomeOk(n: NatItem) = opts.natBiome < 0 || n.biomes.contains(opts.natBiome)

    private fun natFiltered(): List<NatItem> {
        val q = opts.natSearch.trim().lowercase()
        val c = opts.natCat
        return catalog.naturals.filter { n ->
            natAllowed(n) && natBiomeOk(n) &&
                (c == "*" || c.isEmpty() || c in EditorData.natCatsOf(n, rockMarks)) &&
                (q.isEmpty() || matchQ(natLabel(n) + " " + n.id, q))
        }
    }

    fun natItems(): List<NatItem> = natFiltered().take(150)

    fun setRockMark(id: Int, v: String?) {
        if (v.isNullOrEmpty()) rockMarks.remove(id) else rockMarks[id] = v
        onMarksChanged()
    }

    /** Tandai massal semua batu yang tampil di daftar. Mengembalikan jumlahnya. */
    fun bulkMarkRocks(v: String): Int {
        val rows = natFiltered().filter { it.cat == "rock" }
        rows.forEach { if (v.isEmpty()) rockMarks.remove(it.id) else rockMarks[it.id] = v }
        onMarksChanged()
        return rows.size
    }

    private fun onMarksChanged() {
        natVisCache.clear()
        val o = JSONObject()
        rockMarks.forEach { (k, v) -> o.put(k.toString(), v) }
        prefs.edit().putString("rockMarks", o.toString()).apply()
        rev++
    }

    private fun loadRockMarks() {
        try {
            val o = JSONObject(prefs.getString("rockMarks", "{}") ?: "{}")
            for (k in o.keys()) rockMarks[k.toInt()] = o.getString(k)
        } catch (_: Exception) { /* abaikan penanda rusak */ }
    }

    /** (warna, radius dalam tile) untuk titik natural — di-cache karena dipanggil tiap frame. */
    fun natVis(entityType: Int): Pair<Int, Float> = natVisCache.getOrPut(entityType) {
        val n = catalog.natById[entityType] ?: return@getOrPut 0xFFFFFFFF.toInt() to 1f
        val z = if (n.cat == "rock") EditorData.rockSize(n, rockMarks) else null
        EditorData.natColor(n, rockMarks) to (if (z == "besar") 1.5f else if (z == "kecil") 0.5f else 1f)
    }

    // ---------------- landmark & kereta ----------------
    fun lmLabel(l: LmItem): String = l.name + EditorData.lmAlias(l.name).let { if (it.isEmpty()) "" else " · $it" }

    fun lmItems(): List<LmItem> {
        val q = opts.lmSearch.trim().lowercase()
        val c = opts.lmCat
        return catalog.landmarks.filter { l ->
            (c == "*" || c.isEmpty() || l.folder == c) && (q.isEmpty() || matchQ(lmLabel(l) + " " + l.prefab, q))
        }.take(150)
    }

    val lmFolders: List<String> by lazy { catalog.landmarks.map { it.folder }.distinct().sorted() }
    val railItems: List<LmItem> by lazy { catalog.landmarks.filter { EditorData.isGlobalPrefab(it.prefab) } }
    fun railPrefab(): String = opts.railCustom.trim().ifEmpty { opts.selRail }

    // ---------------- herd ----------------
    fun herdItems(): List<HerdItem> {
        val q = opts.herdSearch.trim().lowercase()
        return catalog.speciesFor(doc.islandType, opts.herdGroup, opts.herdAll)
            .mapNotNull { r -> catalog.animals[r.id]?.let { HerdItem(it, r.level, r.count) } }
            .filter { (opts.herdDiet == "*" || it.animal.diet == opts.herdDiet) && (q.isEmpty() || matchQ(it.animal.name + " " + it.animal.id, q)) }
    }

    fun herdDiets(): List<Pair<String, Int>> {
        val rows = catalog.speciesFor(doc.islandType, opts.herdGroup, opts.herdAll).mapNotNull { catalog.animals[it.id] }
        return rows.map { it.diet }.distinct().map { d -> d to rows.count { it.diet == d } }
    }

    fun refreshHerdControls(keepGroup: Boolean) {
        val type = doc.islandType
        val gs = catalog.groupsFor(type, opts.herdAll)
        val prev = opts.herdGroup
        val group = if (keepGroup && prev in gs) prev else if ("land" in gs) "land" else gs.firstOrNull() ?: "land"
        var o = opts.copy(herdGroup = group)
        val diets = catalog.speciesFor(type, group, o.herdAll).mapNotNull { catalog.animals[it.id]?.diet }.distinct()
        o = o.copy(herdDiet = if (o.herdDiet in diets) o.herdDiet else "*")
        opts = o
        val items = herdItems()
        if (items.isNotEmpty() && items.none { it.animal.id == opts.selAnimal }) {
            opts = opts.copy(selAnimal = items[0].animal.id, herdLevel = items[0].level)
        }
        syncHerdLevel()
    }

    fun syncHerdLevel() {
        val a = catalog.animals[opts.selAnimal] ?: return
        val lv = (if (opts.herdLevel == 0) a.lvMin else opts.herdLevel).coerceIn(a.lvMin, a.lvMax)
        if (lv != opts.herdLevel) opts = opts.copy(herdLevel = lv)
    }

    fun pickAnimal(item: HerdItem) {
        opts = opts.copy(selAnimal = item.animal.id, herdLevel = if (item.level != 0) item.level else item.animal.lvMin)
        syncHerdLevel()
    }

    // ---------------- hewan objek ----------------
    fun decorName(a: AnimalItem): String = EditorData.decorAlias[a.id]?.let { "$it (${a.name})" } ?: a.name

    fun decorItems(): List<AnimalItem> {
        val q = opts.decSearch.trim().lowercase()
        val pin = EditorData.decorPin
        return catalog.animals.values
            .filter { q.isEmpty() || matchQ(decorName(it) + " " + it.id, q) }
            .sortedWith { a, b ->
                val pa = pin.indexOf(a.id); val pb = pin.indexOf(b.id)
                if (pa >= 0 || pb >= 0) (if (pa < 0) 99 else pa) - (if (pb < 0) 99 else pb)
                else a.name.compareTo(b.name).let { if (it != 0) it else a.id - b.id }
            }
            .take(150)
    }

    // ---------------- NPC ----------------
    fun npcItems(): List<NpcDef> {
        val q = opts.npcSearch.trim().lowercase()
        return EditorData.npcAll.filter { q.isEmpty() || matchQ(it.name + " " + it.id, q) }
    }

    fun pickNpc(n: NpcDef) { opts = opts.copy(selNpc = n.id, npcRadius = if (n.kind == "story") opts.npcRadius else n.radius) }

    fun placeStoryPreset() {
        pushUndo(doc.deepCopy())
        val (d, bad) = Ops.storyPreset(doc, opts.npcPresetLv - 1)
        doc = d
        hint = if (bad.isNotEmpty()) "[!] ${bad.joinToString(", ")} jatuh di air pada peta ini — cek biome di titik itu." else ""
        bump()
    }

    // ---------------- bangunan ----------------
    val bldCats: List<String> by lazy { catalog.buildings.map { it.cat }.distinct() }
    fun bldItems(): List<BldItem> = catalog.buildings.filter { opts.bldCat == "*" || it.cat == opts.bldCat }

    // ---------------- zona pemicu ----------------
    fun trigCurFlow(): String {
        val t = EditorData.trigTypes.firstOrNull { it.id == opts.trigType } ?: EditorData.trigTypes[0]
        return if (t.id == "custom") opts.trigFlow.trim() else t.flow
    }

    fun clearTrigZone() {
        val flow = trigCurFlow()
        if (flow.isEmpty() || doc.trig.none { it.flow == flow }) return
        edit { it.copy(trig = it.trig.filter { z -> z.flow != flow }) }
    }

    fun cancelTrigPending() { trigPending = null; hover = null }

    // ---------------- thornbush, spawn, rakit, cek ----------------
    fun clearThorns() { if (doc.thorns.isNotEmpty()) edit { it.copy(thorns = emptySet()) } }

    fun autoSpawn() {
        val (p, err) = Ops.autoSpawn(doc)
        if (p == null) { hint = err ?: ""; return }
        edit { it.copy(spawn = p) }
        hint = ""
    }

    var waterReport by mutableStateOf("")
        private set
    var rockReportLines by mutableStateOf<List<String>>(emptyList())
        private set

    fun checkWater() { waterReport = Ops.waterReportText(Ops.diagnoseRivers(doc.biomes)) }
    fun checkRocks() { rockReportLines = Ops.rockReport(doc, catalog) }

    // ---------------- flip / clear ----------------
    fun flip(horizontal: Boolean) { edit { Ops.flip(it, catalog, horizontal) }; toast = if (horizontal) "Flip horizontal selesai" else "Flip vertikal selesai" }

    fun clearAll() {
        clearPrompt = false
        edit { Doc(templateId = it.templateId, level = it.level, role = it.role, islandType = it.islandType, buildingAnchor = it.buildingAnchor) }
        trigPending = null
    }

    // =====================================================================
    //  TAMPILAN: zoom / geser / putar. Satuan peta = 1 tile; sumbu y dibalik (y data naik).
    // =====================================================================
    private var canvasSize = IntSize.Zero

    fun onCanvasSize(s: IntSize) {
        val first = canvasSize == IntSize.Zero
        canvasSize = s
        if (first && s.width > 0 && s.height > 0) fitView()
    }

    private fun rotV(x: Float, y: Float, a: Float): Offset {
        val c = cos(a); val s = sin(a)
        return Offset(x * c - y * s, x * s + y * c)
    }

    fun screenToMap(p: Offset): Offset {
        val v = view
        val r = rotV(p.x - v.tx, p.y - v.ty, -v.rot)
        return Offset(r.x / v.k, r.y / v.k)
    }

    private fun setViewAround(screen: Offset, map: Offset, k: Float, rot: Float) {
        val nk = k.coerceIn(K_MIN, K_MAX)
        val r = rotV(map.x * nk, map.y * nk, rot)
        view = ViewState(screen.x - r.x, screen.y - r.y, nk, rot)
    }

    private fun center() = Offset(canvasSize.width / 2f, canvasSize.height / 2f)

    fun fitView() {
        if (canvasSize.width == 0 || canvasSize.height == 0) return
        gameView = true
        val k = min(canvasSize.width, canvasSize.height) / (W * sqrt(2f)) * 0.95f
        setViewAround(center(), Offset(W / 2f, H / 2f), k, GAME_ROT)
    }

    fun toggleGameView() {
        gameView = !gameView
        val c = center()
        setViewAround(c, screenToMap(c), view.k, if (gameView) GAME_ROT else 0f)
    }

    fun zoomBy(f: Float) { val c = center(); setViewAround(c, screenToMap(c), view.k * f, view.rot) }
    fun rotateBy(rad: Float) { val c = center(); setViewAround(c, screenToMap(c), view.k, view.rot + rad) }
    fun panBy(d: Offset) { view = view.copy(tx = view.tx + d.x, ty = view.ty + d.y) }

    /** Gestur 2 jari: titik peta di bawah pusat jari tetap di bawah jari. */
    fun transform(centroid: Offset, pan: Offset, zoom: Float, rotDeg: Float) {
        val prev = centroid - pan
        val m = screenToMap(prev)
        setViewAround(centroid, m, view.k * zoom, view.rot + rotDeg * (PI.toFloat() / 180f))
    }

    fun tileAt(p: Offset): TileHit {
        val m = screenToMap(p)
        val inside = m.x >= 0f && m.y >= 0f && m.x < W && m.y < H
        val x = floor(m.x).toInt().coerceIn(0, W - 1)
        val y = (H - 1 - floor(m.y).toInt()).coerceIn(0, H - 1)
        return TileHit(x, y, inside)
    }

    // =====================================================================
    //  MENGGAMBAR: satu goresan = satu langkah undo
    // =====================================================================
    private var snap: Doc? = null
    private var painting = false
    private var pushed = false

    private fun mark() {
        if (!pushed) { snap?.let { pushUndo(it) }; pushed = true }
    }

    fun strokeBegin(p: Offset) {
        val t = tileAt(p)
        if (!t.inside) return
        painting = true; pushed = false
        snap = doc.deepCopy()
        if (mode == Mode.BUILDING || (mode == Mode.TRIGGER && trigPending != null)) hover = Pt(t.x, t.y)
        applyPoint(t.x, t.y)
    }

    fun strokeMove(p: Offset) {
        val t = tileAt(p)
        if (mode == Mode.BUILDING || (mode == Mode.TRIGGER && trigPending != null)) hover = if (t.inside) Pt(t.x, t.y) else null
        if (!painting || !t.inside) return
        val o = opts
        when (mode) {
            Mode.BIOME -> { Ops.paintBiome(doc, t.x, t.y, o.brush, o.selBiome, o.collidable, o.notPlant); mark(); bump() }
            Mode.ROCK -> { Ops.paintRock(doc, t.x, t.y, o.rockSize, o.rockRough, o.rockLandOnly, o.rockErase); mark(); bump() }
            Mode.ERASE -> { selection = null; doc = Ops.eraseAt(doc, catalog, t.x, t.y, o.eraseSize, eraseOpts()); mark(); bump() }
            Mode.THORN -> thorn(t.x, t.y)
            Mode.TRIGGER -> if (o.trigTool == "brush") trigStroke(t.x, t.y)
            Mode.NATURAL -> if (Math.random() * o.sprayDensity < 1) {
                doc = doc.copy(naturals = doc.naturals + Natural(t.x, t.y, o.selNatural)); mark(); rev++
            }
            else -> Unit
        }
    }

    fun strokeEnd() { painting = false; snap = null }

    /** Sentuhan kedua muncul: batalkan goresan (undo kalau baru saja mulai). */
    fun strokeCancel(early: Boolean) {
        if (painting) {
            if (early && pushed) { undo(); redoStack.clear(); canRedo = false }
            painting = false; snap = null
        }
    }

    private fun eraseOpts() = EraseOpts(
        opts.eBiome, opts.eNatural, opts.eLandmark, opts.ePort, opts.eSpawn, opts.eRaft,
        opts.eDecor, opts.eNpc, opts.eThorn, opts.eHerd, opts.eBuilding, opts.eTrigger,
    )

    private fun thorn(x: Int, y: Int) {
        val (set, skipped) = Ops.paintThorn(doc, x, y, opts.thornBrush, opts.thornErase, opts.thornLand)
        doc = doc.copy(thorns = set)
        hint = if (skipped > 0) "[x] $skipped tile air dilewati" else ""
        mark(); rev++
    }

    private fun trigStroke(x: Int, y: Int) {
        val flow = trigCurFlow()
        if (flow.isEmpty()) return
        val r = Ops.trigApply(doc, flow, opts.trigExit.trim(), "brush", opts.trigSize, x, y, opts.trigErase, null)
        doc = r.doc; mark(); rev++
    }

    private fun reject(msg: String): Boolean { hint = msg; return false }

    /** Tap pertama pada tile. Mengembalikan true kalau dokumen berubah. */
    private fun applyPoint(x: Int, y: Int) {
        val o = opts
        var changed = true
        when (mode) {
            Mode.BIOME -> { Ops.paintBiome(doc, x, y, o.brush, o.selBiome, o.collidable, o.notPlant); mark(); bump(); return }
            Mode.ROCK -> { Ops.paintRock(doc, x, y, o.rockSize, o.rockRough, o.rockLandOnly, o.rockErase); mark(); bump(); return }
            Mode.ERASE -> { selection = null; doc = Ops.eraseAt(doc, catalog, x, y, o.eraseSize, eraseOpts()); mark(); bump(); return }
            Mode.THORN -> { thorn(x, y); return }
            Mode.TRIGGER -> {
                val flow = trigCurFlow()
                if (flow.isEmpty()) { hint = "[x] Isi nama alur dulu (jenis \"Khusus\")."; return }
                hint = ""
                val r = Ops.trigApply(doc, flow, o.trigExit.trim(), o.trigTool, o.trigSize, x, y, o.trigErase, trigPending)
                doc = r.doc; trigPending = r.pending
                if (r.changed) { mark(); rev++ }
                return
            }
            Mode.NATURAL -> doc = doc.copy(naturals = doc.naturals + Natural(x, y, o.selNatural))
            Mode.LANDMARK -> changed = placeOrSelectLandmark(x, y, o.selLandmark, 0, false)
            Mode.PORT -> doc = doc.copy(ports = listOf(Pt(x, y)))   // MVP: cuma 1 dermaga
            Mode.DECOR ->
                if (doc.code(x, y) >= 11) changed = reject("[x] Ditolak: hewan objek harus di darat/pantai, bukan air.")
                else { hint = ""; changed = placeOrSelectDecor(x, y, o.selDecor, o.decRot) }
            Mode.NPC ->
                if (doc.code(x, y) >= 11) changed = reject("[x] Ditolak: NPC harus di darat/pantai, bukan air.")
                else { hint = ""; doc = Ops.putNpc(doc, o.selNpc, x, y, o.npcRadius.coerceIn(0, 20)) }
            Mode.RAIL -> {
                val pf = railPrefab()
                if (pf.isEmpty()) changed = reject("[x] Pilih prefab dulu.")
                else changed = placeOrSelectLandmark(x, y, pf, o.railRot, true)
            }
            Mode.RAFT ->
                if (x + 3 >= W || y + 3 >= H || Ops.raftCheck(doc, x, y).core > 0) changed = reject("[x] Ditolak: area 4x4 rakit harus di darat dan di dalam peta.")
                else { hint = ""; doc = doc.copy(raft = Pt(x, y)) }
            Mode.SPAWN ->
                if (!Ops.spawnAreaOk(doc, x, y)) changed = reject("[x] Ditolak: area 9x9 di sekitar titik ini kena air/tepi peta. Pilih tempat yang lebih ke dalam daratan.")
                else { hint = ""; doc = doc.copy(spawn = Pt(x, y)) }
            Mode.HERD -> {
                val c = doc.code(x, y)
                val g = o.herdGroup
                if (!EditorData.groupTileOk(g, c)) {
                    changed = reject("Titik ditolak: grup \"$g\" harus di tile ${EditorData.groupTileName[g]}, tile ini \"${Biomes.short[c] ?: c}\".")
                } else {
                    hint = ""; syncHerdLevel()
                    doc = doc.copy(herds = doc.herds + Herd(g, x, y, opts.selAnimal, opts.herdLevel))
                }
            }
            Mode.BUILDING -> {
                val nb = Building(x, y, o.selBuilding)
                val f = Ops.footCheck(doc, catalog, nb)
                hint = footWarning(f)
                if (f.oob) changed = false else doc = doc.copy(buildings = doc.buildings + nb)
            }
        }
        if (changed) { mark(); rev++ }
    }

    fun footWarning(f: FootCheck): String = buildList {
        if (f.oob) add("[x] keluar dari peta")
        if (f.overlap) add("[!] tumpang tindih bangunan lain")
        if (f.water > 0) add("[!] ${f.water} tile di air/lava")
    }.joinToString("\n")

    // =====================================================================
    //  EXPORT / IMPORT
    // =====================================================================
    fun requestExport(kind: ExportKind) {
        if (busy) return
        val warns = ArrayList<String>()
        val d = Ops.diagnoseRivers(doc.biomes)
        if (d.isolated > 0 || d.fat > 0) warns.add("Sungai bermasalah:\n" + Ops.waterReportText(d))
        if (doc.spawn == null) warns.add("Belum ada titik spawn pemain (server akan mencari sendiri dari dermaga).")
        if (doc.raft == null) warns.add("Belum ada titik rakit tutorial (server akan menghitung sendiri).")
        if (warns.isEmpty()) performExport(kind) else pendingExport = ExportPrompt(kind, warns)
    }

    fun confirmExport() { val p = pendingExport ?: return; pendingExport = null; performExport(p.kind) }
    fun dismissExport() { pendingExport = null }

    private fun performExport(kind: ExportKind) {
        if (busy) return
        busy = true
        val copy = doc.deepCopy()      // thread latar tidak boleh membaca biomes yang sedang dilukis
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val spec = SpecCodec.encode(copy, catalog)
                    val tid = spec.getString("template_id")
                    if (kind == ExportKind.SPEC) {
                        ExportResult(
                            "Spec tersimpan", "", listOf("Format .spec.json — bisa diimpor lagi ke editor atau dibuild di repo gen-island."),
                            emptyList(), Uri.EMPTY, FileStore.MIME_JSON,
                        ) to Pair("$tid.spec.json", spec.toString().toByteArray(Charsets.UTF_8))
                    } else {
                        val out = IslandBuilder.build(spec, catalog)
                        ExportResult(
                            "Island zip tersimpan", "", listOf(out.summary),
                            out.warnings, Uri.EMPTY, FileStore.MIME_ZIP,
                        ) to Pair(out.fileName, out.zip)
                    }
                }
                val (meta, file) = result
                val saved = withContext(Dispatchers.IO) { writeOutput(kind, file.first, file.second) }
                exportResult = meta.copy(
                    path = saved.absolutePath,
                    uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", saved),
                )
            } catch (e: Exception) {
                toast = "Gagal export: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    fun importFrom(uri: Uri) {
        if (busy) return
        busy = true
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                val imported = withContext(Dispatchers.Default) {
                    val text = FileStore.readText(ctx, uri)
                    SpecCodec.decode(JSONObject(text), catalog)
                }
                pushUndo(doc.deepCopy())
                selection = null   // SEL_IMPORT
                doc = imported
                trigPending = null
                applyTypeUi()
                bump()
                toast = "Spec diimpor: ${imported.templateId}"
            } catch (e: Exception) {
                toast = "Gagal baca spec.json: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    // ---------------- draft otomatis ----------------
    /** AUTOSAVE_PROJECT — dipanggil MainActivity.onPause/onStop; hanya menulis kalau ada perubahan. */
    fun saveDraft() {
        val cur = projectFile ?: return
        if (rev == savedRev && doc === savedDoc) { persistLayers(); return }
        try {
            AppSettings.writeAtomic(cur, SpecCodec.encode(doc, catalog).toString().toByteArray(Charsets.UTF_8))
            savedRev = rev; savedDoc = doc
        } catch (_: Exception) { /* best-effort */ }
        persistLayers()
    }

    private fun restoreDraft() {
        try {
            val text = FileStore.readDraft(getApplication()) ?: return
            doc = SpecCodec.decode(JSONObject(text), catalog)
        } catch (_: Exception) { /* draft rusak: mulai kosong */ }
    }

    init {
        loadRockMarks()
        // restoreDraft() dinonaktifkan: proyek dimuat lewat openProject()
        applyTypeUi()
        refreshBitmap()
    }

    companion object {
        private const val K_MIN = 0.5f
        private const val K_MAX = 48f
        private val GAME_ROT = (-PI / 4).toFloat()
    }
}
