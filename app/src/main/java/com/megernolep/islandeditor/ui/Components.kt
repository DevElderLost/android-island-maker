package com.megernolep.islandeditor.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import kotlin.math.roundToInt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Section(title: String) {
    Text(
        title.uppercase(), color = Muted, fontSize = 11.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/**
 * Catatan kecil. Penanda status di teks diubah menjadi IKON native (bisa di posisi mana pun, juga multi-baris):
 *   [!] = peringatan (Icons.Filled.Warning)   [x] = ditolak (Icons.Filled.Block)   [ok] = aman (Icons.Filled.CheckCircle)
 */
private val StatusMark = Regex("""\[(?:!|x|ok)]""")

@Composable
fun Note(text: String, color: Color = Muted, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    val annotated = buildAnnotatedString {
        var last = 0
        for (m in StatusMark.findAll(text)) {
            append(text.substring(last, m.range.first))
            appendInlineContent(m.value, "*")
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
    val slot = Placeholder(15.sp, 15.sp, PlaceholderVerticalAlign.TextCenter)
    val inline = mapOf(
        "[!]" to InlineTextContent(slot) { Icon(Icons.Filled.Warning, null, tint = Warn, modifier = Modifier.fillMaxSize()) },
        "[x]" to InlineTextContent(slot) { Icon(Icons.Filled.Block, null, tint = Danger, modifier = Modifier.fillMaxSize()) },
        "[ok]" to InlineTextContent(slot) { Icon(Icons.Filled.CheckCircle, null, tint = Accent, modifier = Modifier.fillMaxSize()) },
    )
    Text(
        annotated, color = color, fontSize = 12.sp, lineHeight = 17.sp,
        inlineContent = inline, modifier = modifier.padding(vertical = 3.dp),
    )
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextHi, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Bg, checkedTrackColor = Accent, checkedBorderColor = Accent,
                uncheckedThumbColor = Muted, uncheckedTrackColor = SurfHi, uncheckedBorderColor = Outline,
            ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, label) ->
            val on = v == selected
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (on) Accent else SurfHi)
                    .border(1.dp, if (on) Accent else Outline, RoundedCornerShape(50))
                    .clickable { onSelect(v) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(label, color = if (on) Bg else TextHi, fontSize = 12.sp) }
        }
    }
}

@Composable
fun IntChips(values: List<Int>, selected: Int, suffix: String = "", onSelect: (Int) -> Unit) =
    Chips(values.map { it to "$it$suffix" }, selected, onSelect)

@Composable
fun <T> Dropdown(value: String, options: List<Pair<T, String>>, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Outline),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(value, color = TextHi, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(SurfHi)) {
            options.forEach { (v, label) ->
                DropdownMenuItem(text = { Text(label, fontSize = 13.sp) }, onClick = { onPick(v); open = false })
            }
        }
    }
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, placeholder: String = "",
    modifier: Modifier = Modifier, number: Boolean = false,
) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextHi),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfHi)
                    .border(1.dp, Outline, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = Muted, fontSize = 13.sp)
                inner()
            }
        },
    )
}

@Composable
fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepBtn("−") { onChange((value - 1).coerceIn(min, max)) }
        Text("$value", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
        StepBtn("+") { onChange((value + 1).coerceIn(min, max)) }
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(SurfHi).border(1.dp, Outline, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 17.sp) }
}

@Composable
fun <T> PickList(
    items: List<T>, key: (T) -> Any, selected: Any?, dot: (T) -> Color,
    label: (T) -> String, sub: (T) -> String, onPick: (T) -> Unit, height: Int = 190,
) {
    Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(10.dp)).background(SurfHi).border(1.dp, Outline, RoundedCornerShape(10.dp))) {
        if (items.isEmpty()) {
            Text("tidak ada hasil", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
        } else {
            LazyColumn {
                items(items, key = { key(it).toString() }) { it ->
                    val on = key(it) == selected
                    Row(
                        Modifier.fillMaxWidth().background(if (on) Accent.copy(alpha = .22f) else Color.Transparent)
                            .clickable { onPick(it) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(dot(it)))
                        Text(label(it), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Text(sub(it), color = Muted, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun SelBox(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(SurfHi)
            .border(1.dp, Outline, RoundedCornerShape(10.dp)).padding(10.dp),
    ) { content() }
}

/**
 * Slider ukuran kuas (menggantikan chip 1/3/7/15 px yang di-hardcode).
 * odd = true: hanya ukuran ganjil (1,3,5,…) supaya kuas simetris di sekitar titik sentuh.
 */
@Composable
fun SizeSlider(
    label: String, value: Int, min: Int, max: Int,
    unit: String = "tile", odd: Boolean = false, onChange: (Int) -> Unit,
) {
    val lo = if (odd) 0 else min
    val hi = if (odd) (max - 1) / 2 else max
    val pos = (if (odd) (value - 1) / 2 else value).coerceIn(lo, hi)
    val shown = if (odd) 2 * pos + 1 else pos
    Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextHi, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("$shown $unit", color = Accent, fontSize = 13.sp)
        }
        Slider(
            value = pos.toFloat(),
            onValueChange = { f ->
                val p = f.roundToInt()
                val v = if (odd) 2 * p + 1 else p
                if (v != shown) onChange(v)
            },
            valueRange = lo.toFloat()..hi.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Outline,
            ),
        )
    }
}
