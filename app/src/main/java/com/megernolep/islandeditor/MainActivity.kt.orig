package com.megernolep.islandeditor

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.megernolep.islandeditor.ui.EditorScreen
import com.megernolep.islandeditor.ui.EditorViewModel
import com.megernolep.islandeditor.ui.IslandTheme

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { IslandTheme { EditorScreen(vm) } }
    }

    /** Draft otomatis: aman walau proses dimatikan sistem setelah app ke latar belakang. */
    override fun onStop() {
        vm.saveDraft()
        super.onStop()
    }
}
