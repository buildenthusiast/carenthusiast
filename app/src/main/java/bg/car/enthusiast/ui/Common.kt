package bg.car.enthusiast.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import bg.car.enthusiast.core.Level
import bg.car.enthusiast.core.Texts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

val Amber = Color(0xFFF5B323)
val Red = Color(0xFFFF6B6B)
val Green = Color(0xFF7BD88F)

fun levelColor(l: Level): Color = when (l) {
    Level.EXPIRED -> Red
    Level.SOON -> Amber
    Level.OK -> Color(0xFFA3A6AD)
    Level.UNKNOWN -> Color(0xFF6E727A)
}

@Composable
fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    number: Boolean = false,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    )
}

/** Поле за дата: натискане отваря календар. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate?, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (LocalDate?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.clickable { open = true }) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(if (value == null) "избери дата" else Texts.date(value), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("Готово") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onChange(null); open = false }) { Text("Изчисти") }
                    TextButton(onClick = { open = false }) { Text("Отказ") }
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float, onChange: (Float) -> Unit) {
    Column {
        Text(label)
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            steps = maxOf(0, ((range.endInclusive - range.start) / step).roundToInt() - 1),
        )
    }
}

fun String.toIntClean(): Int? = filter { it.isDigit() }.toIntOrNull()
fun String.toMoney(): Double? = replace(',', '.').filter { it.isDigit() || it == '.' }.toDoubleOrNull()
fun Int?.str() = this?.toString() ?: ""
fun Double?.str() = this?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""

/** Падащо меню с готови стойности. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(label: String, value: T, options: List<T>, text: (T) -> String, onChange: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = text(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(text(o)) }, onClick = { onChange(o); open = false })
            }
        }
    }
}
