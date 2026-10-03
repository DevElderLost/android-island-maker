package com.megernolep.islandeditor.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun SetupScreen(
    granted: Boolean,
    projectDir: File,
    exportDir: File,
    serverDir: File?,
    fromSettings: Boolean,
    onRequestPermission: () -> Unit,
    onPickProject: (File) -> Unit,
    onPickExport: (File) -> Unit,
    onPickServer: (File) -> Unit,
    onClearServer: () -> Unit,
    onFinish: () -> Unit,
) {
    var picking by remember { mutableIntStateOf(0) }   // 0 = tidak, 1 = folder proyek, 2 = folder export

    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconLabel(Icons.Filled.BeachAccess, "Island Editor", tint = Accent, textColor = Accent, fontSize = 22.sp, iconSize = 28.dp)
        Text(
            if (fromSettings) "Pengaturan penyimpanan" else "Selamat datang! Atur penyimpanan dulu sebelum mulai.",
            color = TextHi, fontSize = 15.sp,
        )

        StepCard("1", "Izin akses file", ok = granted) {
            Note(
                if (granted) "[ok] Izin sudah diberikan."
                else "Android 11+ butuh izin “Akses semua file” supaya proyek & hasil export bisa disimpan di folder pilihanmu.",
                if (granted) Accent else Muted,
            )
            if (!granted) {
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                ) { Text("Beri izin") }
            }
        }

        StepCard("2", "Folder proyek (.spec.json)", ok = granted) {
            Note(projectDir.absolutePath, TextHi)
            OutlinedButton(onClick = { picking = 1 }, enabled = granted) { Text("Pilih folder", color = TextHi) }
        }

        StepCard("3", "Folder export (zip pulau)", ok = granted) {
            Note(exportDir.absolutePath, TextHi)
            OutlinedButton(onClick = { picking = 2 }, enabled = granted) { Text("Pilih folder", color = TextHi) }
        }

        StepCard("4", "Folder data server (opsional)", ok = granted && serverDir != null) {
            Note(
                serverDir?.absolutePath ?: "Belum diatur. Pilih folder data server (berisi islands.json) untuk fitur Seed ke data server.",
                if (serverDir != null) TextHi else Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = 3 }, enabled = granted) { Text("Pilih folder", color = TextHi) }
                if (serverDir != null) OutlinedButton(onClick = onClearServer, enabled = granted) { Text("Hapus", color = TextHi) }
            }
        }

        Button(
            onClick = onFinish, enabled = granted, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
        ) { Text(if (fromSettings) "Simpan" else "Mulai", fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp)) }
    }

    if (picking != 0) {
        FolderPicker(
            start = when (picking) {
                1 -> projectDir
                2 -> exportDir
                else -> serverDir ?: exportDir
            },
            onPick = { f ->
                when (picking) {
                    1 -> onPickProject(f)
                    2 -> onPickExport(f)
                    else -> onPickServer(f)
                }
                picking = 0
            },
            onDismiss = { picking = 0 },
        )
    }
}

@Composable
private fun StepCard(num: String, title: String, ok: Boolean, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surf)
            .border(1.dp, Outline, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (ok) Accent else SurfHi),
                contentAlignment = Alignment.Center,
            ) { Text(num, color = if (ok) Bg else Muted, fontSize = 13.sp) }
            Text("  $title", color = TextHi, fontSize = 15.sp)
        }
        content()
    }
}
