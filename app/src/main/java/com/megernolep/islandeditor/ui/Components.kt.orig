package com.megernolep.islandeditor.ui

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

@Composable
fun Note(text: String, color: Color = Muted, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    Text(text, color = color, fontSize = 12.sp, lineHeight = 17.sp, modifier = modifier.padding(vertical = 3.dp))
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked, onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = Muted, checkmarkColor = Bg),
            modifier = Modifier.padding(end = 8.dp).size(20.dp),
        )
        Text(label, fontSize = 13.sp)
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
            Text("  ▾", color = Muted)
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
