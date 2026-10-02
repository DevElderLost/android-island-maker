package com.megernolep.islandeditor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.megernolep.islandeditor.data.AppSettings
import com.megernolep.islandeditor.ui.IslandTheme
import com.megernolep.islandeditor.ui.ProjectListScreen
import com.megernolep.islandeditor.ui.ProjectListViewModel
import java.io.File

/** Layar pertama: daftar proyek. Belum setup / izin dicabut -> diarahkan ke SetupActivity. */
class ProjectListActivity : ComponentActivity() {
    private val vm: ProjectListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AppSettings.isSetupDone(this) || !AppSettings.hasStorageAccess(this)) {
            goSetup(false)
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            IslandTheme {
                ProjectListScreen(vm, onOpen = { openEditor(it) }, onSettings = { goSetup(true) })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        if (!AppSettings.hasStorageAccess(this)) { goSetup(false); return }
        vm.refresh()    // thumbnail & urutan terbaru setelah kembali dari editor
    }

    private fun goSetup(fromSettings: Boolean) {
        startActivity(Intent(this, SetupActivity::class.java).putExtra("from_settings", fromSettings))
        if (!fromSettings) finish()
    }

    private fun openEditor(file: File) {
        startActivity(Intent(this, MainActivity::class.java).putExtra("project_path", file.absolutePath))
    }
}
