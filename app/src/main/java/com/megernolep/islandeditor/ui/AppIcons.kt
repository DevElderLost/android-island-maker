package com.megernolep.islandeditor.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ikon native Material (Jetpack Compose) untuk tombol bulat IconBtn / MiniIcon. */
fun IconKind.vector(): ImageVector = when (this) {
    IconKind.ZOOM_IN -> Icons.Filled.ZoomIn
    IconKind.ZOOM_OUT -> Icons.Filled.ZoomOut
    IconKind.FIT -> Icons.Filled.FitScreen
    IconKind.ROT_LEFT -> Icons.Filled.RotateLeft
    IconKind.ROT_RIGHT -> Icons.Filled.RotateRight
    IconKind.UNDO -> Icons.Filled.Undo
    IconKind.REDO -> Icons.Filled.Redo
    IconKind.FLIP_H -> Icons.Filled.SwapHoriz
    IconKind.FLIP_V -> Icons.Filled.SwapVert
    IconKind.IMPORT -> Icons.Filled.FolderOpen
    IconKind.TRASH -> Icons.Filled.Delete
    IconKind.BACK -> Icons.AutoMirrored.Filled.ArrowBack
    IconKind.BRUSH -> Icons.Filled.Brush
    IconKind.ERASER -> Icons.Filled.CleaningServices
    IconKind.LAYERS -> Icons.Filled.Layers
    IconKind.IMAGE -> Icons.Filled.Image
    IconKind.EYE -> Icons.Filled.Visibility
    IconKind.EYE_OFF -> Icons.Filled.VisibilityOff
    IconKind.UP -> Icons.Filled.KeyboardArrowUp
    IconKind.DOWN -> Icons.Filled.KeyboardArrowDown
    IconKind.MOVE -> Icons.Filled.OpenWith
    IconKind.CLOSE -> Icons.Filled.Close
    IconKind.FOLDER -> Icons.Filled.Folder
    IconKind.COMPASS -> Icons.Filled.Explore
    IconKind.PAN -> Icons.Filled.PanTool
    IconKind.SETTINGS -> Icons.Filled.Settings
    else -> Icons.Filled.Info
}

/** Ikon chip mode (menggantikan emoji di Mode). */
fun modeIcon(m: Mode): ImageVector = when (m) {
    Mode.BIOME -> Icons.Filled.Palette
    Mode.ROCK -> Icons.Filled.Terrain
    Mode.NATURAL -> Icons.Filled.Park
    Mode.LANDMARK -> Icons.Filled.AccountBalance
    Mode.RAIL -> Icons.Filled.Train
    Mode.PORT -> Icons.Filled.Anchor
    Mode.SPAWN -> Icons.Filled.PersonPin
    Mode.RAFT -> Icons.Filled.DirectionsBoat
    Mode.HERD -> Icons.Filled.Pets
    Mode.DECOR -> Icons.Filled.EmojiNature
    Mode.NPC -> Icons.Filled.Face
    Mode.BUILDING -> Icons.Filled.Home
    Mode.TRIGGER -> Icons.Filled.Flag
    Mode.THORN -> Icons.Filled.Eco
    Mode.ERASE -> Icons.Filled.CleaningServices
    else -> Icons.Filled.Info
}

/** Ikon tiap kode biome (menggantikan emoji di Biomes.info). */
fun biomeIcon(code: Int): ImageVector = when (code) {
    0 -> Icons.Filled.Park              // hutan sedang
    1 -> Icons.Filled.Nature            // hutan tropis
    2 -> Icons.Filled.WbSunny           // gurun
    3 -> Icons.Filled.Landscape         // tundra
    4 -> Icons.Filled.AcUnit            // salju
    5 -> Icons.Filled.Agriculture       // padang rumput
    6 -> Icons.Filled.Eco               // rawa
    7 -> Icons.Filled.Whatshot          // vulkanik
    9 -> Icons.Filled.Grain             // pantai kerikil
    10 -> Icons.Filled.BeachAccess      // pantai pasir
    11, 12 -> Icons.Filled.Waves        // laut
    13 -> Icons.Filled.Waves            // sungai
    14 -> Icons.Filled.WaterDrop        // danau
    15 -> Icons.Filled.LocalFireDepartment  // lava
    else -> Icons.Filled.Info
}

/** Ikon + teks sebaris (menggantikan "emoji teks"). */
@Composable
fun IconLabel(
    icon: ImageVector, text: String, modifier: Modifier = Modifier,
    tint: Color = TextHi, textColor: Color = TextHi,
    fontSize: TextUnit = 12.sp, iconSize: Dp = 16.dp,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(6.dp))
        Text(text, color = textColor, fontSize = fontSize)
    }
}
