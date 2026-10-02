package com.megernolep.islandeditor.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/** Pengaturan penyimpanan (folder proyek & export) + pemeriksaan izin akses file. */
object AppSettings {
    private const val PREFS = "island_editor"
    private const val K_SETUP = "setup_done"
    private const val K_PROJECT = "project_dir"
    private const val K_EXPORT = "export_dir"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    fun storageRoot(): File = Environment.getExternalStorageDirectory()

    fun defaultProjectDir() = File(storageRoot(), "IslandEditor/Projects")
    fun defaultExportDir() = File(storageRoot(), "IslandEditor/Export")

    fun projectDir(ctx: Context): File = prefs(ctx).getString(K_PROJECT, null)?.let { File(it) } ?: defaultProjectDir()
    fun exportDir(ctx: Context): File = prefs(ctx).getString(K_EXPORT, null)?.let { File(it) } ?: defaultExportDir()

    fun isSetupDone(ctx: Context): Boolean = prefs(ctx).getBoolean(K_SETUP, false)

    fun saveSetup(ctx: Context, project: File, export: File) {
        prefs(ctx).edit()
            .putString(K_PROJECT, project.absolutePath)
            .putString(K_EXPORT, export.absolutePath)
            .putBoolean(K_SETUP, true)
            .apply()
    }

    /** Android 11+: "Akses semua file" (MANAGE_EXTERNAL_STORAGE). Android 10: izin tulis storage. */
    fun hasStorageAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun sanitizeId(s: String): String = s.trim().replace(Regex("[^A-Za-z0-9_.-]"), "_").trim('.')
    fun sanitizeFileName(s: String): String = s.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    /** Tulis lewat file sementara supaya file lama tidak rusak kalau proses mati di tengah jalan. */
    fun writeAtomic(f: File, bytes: ByteArray) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) {
            f.writeBytes(bytes)
            tmp.delete()
        }
    }
}
