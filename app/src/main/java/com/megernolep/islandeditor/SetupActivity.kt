package com.megernolep.islandeditor

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.megernolep.islandeditor.data.AppSettings
import com.megernolep.islandeditor.ui.IslandTheme
import com.megernolep.islandeditor.ui.SetupScreen
import java.io.File

/** Layar pengaturan awal: izin akses file + folder proyek + folder export. */
class SetupActivity : ComponentActivity() {
    private var granted by mutableStateOf(false)
    private var projectDir by mutableStateOf(File("/"))
    private var exportDir by mutableStateOf(File("/"))
    private var fromSettings = false

    // Android 10: izin baca/tulis storage biasa
    private val legacyPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = AppSettings.hasStorageAccess(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        fromSettings = intent.getBooleanExtra("from_settings", false)
        projectDir = AppSettings.projectDir(this)
        exportDir = AppSettings.exportDir(this)
        granted = AppSettings.hasStorageAccess(this)
        setContent {
            IslandTheme {
                SetupScreen(
                    granted = granted, projectDir = projectDir, exportDir = exportDir, fromSettings = fromSettings,
                    onRequestPermission = { requestAccess() },
                    onPickProject = { projectDir = it },
                    onPickExport = { exportDir = it },
                    onFinish = { finishSetup() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        granted = AppSettings.hasStorageAccess(this)   // kembali dari layar pengaturan sistem
    }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private fun finishSetup() {
        try {
            projectDir.mkdirs()
            exportDir.mkdirs()
            check(projectDir.isDirectory && exportDir.isDirectory) { "Folder tidak bisa dibuat/diakses" }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        AppSettings.saveSetup(this, projectDir, exportDir)
        if (!fromSettings) startActivity(Intent(this, ProjectListActivity::class.java))
        finish()
    }
}
