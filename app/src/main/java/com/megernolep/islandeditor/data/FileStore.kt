package com.megernolep.islandeditor.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** Semua I/O file: simpan ke Downloads (MediaStore, tanpa izin storage), baca dari Storage Access Framework, draft autosave. */
object FileStore {
    const val MIME_JSON = "application/json"
    const val MIME_ZIP = "application/zip"

    /** Menulis [bytes] ke folder Downloads publik. Mengembalikan Uri hasilnya (bisa dibagikan). */
    fun saveToDownloads(ctx: Context, name: String, mime: String, bytes: ByteArray): Uri {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Gagal membuat file di Downloads (insert mengembalikan null)")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Tidak bisa membuka file tujuan")
            out.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)   // jangan tinggalkan file setengah jadi
            throw e
        }
        return uri
    }

    fun readText(ctx: Context, uri: Uri): String {
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("Tidak bisa membuka file")
        return input.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun draftFile(ctx: Context) = File(ctx.filesDir, "draft.spec.json")

    fun writeDraft(ctx: Context, json: String) {
        val tmp = File(ctx.filesDir, "draft.spec.json.tmp")
        tmp.writeText(json, Charsets.UTF_8)
        if (!tmp.renameTo(draftFile(ctx))) { draftFile(ctx).writeText(json, Charsets.UTF_8); tmp.delete() }
    }

    fun readDraft(ctx: Context): String? = draftFile(ctx).takeIf { it.exists() }?.readText(Charsets.UTF_8)
}
