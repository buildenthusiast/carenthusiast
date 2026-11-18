package bg.car.enthusiast.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bg.car.enthusiast.core.Car
import bg.car.enthusiast.core.Data
import bg.car.enthusiast.core.Item
import bg.car.enthusiast.core.Kind
import bg.car.enthusiast.core.Level
import bg.car.enthusiast.core.Repair
import bg.car.enthusiast.core.Rules
import bg.car.enthusiast.core.Texts
import bg.car.enthusiast.data.Store
import java.time.LocalDate
import java.time.Period

/** Какво е отворено в момента върху екрана на колата. */
private sealed interface Edit {
    data object CarInfo : Edit
    data object Odometer : Edit
    data object DeleteCar : Edit
    data class ItemEdit(val item: Item, val renewing: Boolean) : Edit
    data class RepairEdit(val repair: Repair) : Edit
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CarScreen(car: Car, data: Data, change: ((Data) -> Data) -> Unit, onBack: () -> Unit) {
    var edit by remember { mutableStateOf<Edit?>(null) }
    var online by remember { mutableStateOf<OnlineCheck?>(null) }
    online?.let {
        OnlineCheckScreen(it, car.plate) { online = null }
        return
    }
    val dues = data.items.filter { it.carId == car.id }.map { Rules.due(it, car, data.settings) }.sortedBy { it.urgency }
    val repairs = data.repairs.filter { it.carId == car.id }.sortedByDescending { it.date }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Column {
                Text(car.name, style = MaterialTheme.typography.titleLarge)
                if (car.plate.isNotBlank()) Text(car.plate, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Километраж: " + (car.odometer?.let { Texts.km(it) } ?: "не е въведен"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    car.odometerDate?.let { Text("Обновен на ${Texts.date(it)}", style = MaterialTheme.typography.bodySmall) }
                    if (car.year != null) Text("Година: ${car.year}")
                    if (car.note.isNotBlank()) Text(car.note, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { edit = Edit.Odometer }) { Text("Обнови км") }
                        OutlinedButton(onClick = { edit = Edit.CarInfo }) { Text("Редактирай") }
                    }
                    if (car.plate.isNotBlank()) {
                        Text("Провери онлайн по номер:", style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { online = OnlineCheck.VIGNETTE }, label = { Text("Винетка") })
                            AssistChip(onClick = { online = OnlineCheck.CIVIL }, label = { Text("Гражданска") })
                            AssistChip(onClick = { online = OnlineCheck.INSPECTION }, label = { Text("Преглед") })
                        }
                    } else {
                        Text("Въведи регистрационен номер, за да проверяваш винетка, гражданска и преглед онлайн.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text("Срокове и обслужване", style = MaterialTheme.typography.titleMedium)
            dues.forEach { d ->
                val urgent = d.level == Level.EXPIRED || d.level == Level.SOON
                Card(
                    Modifier.fillMaxWidth().clickable { edit = Edit.ItemEdit(d.item, renewing = false) },
                    colors = if (urgent) CardDefaults.cardColors(containerColor = levelColor(d.level).copy(alpha = 0.12f)) else CardDefaults.cardColors(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(d.item.name, style = MaterialTheme.typography.titleMedium)
                        Text(d.text, color = levelColor(d.level), fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal)
                        val period = listOfNotNull(
                            d.item.from?.let { "${d.item.kind.fromLabel.lowercase()} ${Texts.date(it)}" },
                            d.item.lastKm?.let { "при ${Texts.km(it)}" },
                            d.item.cost?.let { Texts.money(it) },
                        ).joinToString(" · ")
                        if (period.isNotBlank()) Text(period, style = MaterialTheme.typography.bodySmall)
                        if (d.item.note.isNotBlank()) Text(d.item.note, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { edit = Edit.ItemEdit(Rules.renew(d.item, car), renewing = true) }) {
                                Text(if (d.item.kind.service) "Направено" else "Поднови")
                            }
                            TextButton(onClick = { edit = Edit.ItemEdit(d.item, renewing = false) }) { Text("Редактирай") }
                        }
                    }
                }
            }
            Text("Добави:", style = MaterialTheme.typography.bodySmall)
            val used = dues.map { it.item.kind }.toSet()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kind.entries.filter { it == Kind.CUSTOM || it !in used }.forEach { k ->
                    AssistChip(onClick = { edit = Edit.ItemEdit(newItem(car, k), renewing = false) }, label = { Text(k.label) })
                }
            }

            HorizontalDivider()
            Text("Ремонти и обслужване", style = MaterialTheme.typography.titleMedium)
            val total = repairs.mapNotNull { it.cost }.sum()
            if (total > 0) Text("Общо похарчено: ${Texts.money(total)}", style = MaterialTheme.typography.bodySmall)
            repairs.forEach { r ->
                Card(Modifier.fillMaxWidth().clickable { edit = Edit.RepairEdit(r) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(Texts.date(r.date), r.km?.let { Texts.km(it) }, r.cost?.let { Texts.money(it) }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Button(onClick = {
                edit = Edit.RepairEdit(Repair(Store.newId(), car.id, LocalDate.now(), "", km = car.odometer))
            }, Modifier.fillMaxWidth()) { Text("Добави ремонт") }

            HorizontalDivider()
            TextButton(onClick = { edit = Edit.DeleteCar }) { Text("Изтрий автомобила", color = Red) }
        }
    }

    when (val e = edit) {
        null -> {}
        Edit.CarInfo -> CarDialog(car, onDismiss = { edit = null }) { updated ->
            change { d -> d.copy(cars = d.cars.map { if (it.id == car.id) updated else it }) }
            edit = null
        }
        Edit.Odometer -> OdometerDialog(car, onDismiss = { edit = null }) { km ->
            change { d -> d.copy(cars = d.cars.map { if (it.id == car.id) it.copy(odometer = km, odometerDate = LocalDate.now()) else it }) }
            edit = null
        }
        Edit.DeleteCar -> AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text("Да изтрия ли ${car.name}?") },
            text = { Text("Изтриват се и всички срокове и ремонти на колата.") },
            confirmButton = {
                TextButton(onClick = {
                    change { d ->
                        d.copy(
                            cars = d.cars.filter { it.id != car.id },
                            items = d.items.filter { it.carId != car.id },
                            repairs = d.repairs.filter { it.carId != car.id },
                        )
                    }
                    edit = null
                    onBack()
                }) { Text("Изтрий", color = Red) }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Отказ") } },
        )
        is Edit.ItemEdit -> ItemDialog(
            e.item,
            car,
            renewing = e.renewing,
            exists = data.items.any { it.id == e.item.id },
            onDismiss = { edit = null },
            onDelete = {
                change { d -> d.copy(items = d.items.filter { it.id != e.item.id }) }
                edit = null
            },
        ) { item, log ->
            change { d ->
                val items = if (d.items.any { it.id == item.id }) d.items.map { if (it.id == item.id) item else it } else d.items + item
                val repairs = if (log) {
                    d.repairs + Repair(Store.newId(), car.id, item.from ?: LocalDate.now(), item.name, km = item.lastKm, cost = item.cost)
                } else {
                    d.repairs
                }
                // Ако при обслужването е въведен по-голям километраж, обновяваме и колата.
                val cars = d.cars.map {
                    val km = item.lastKm
                    if (it.id == car.id && km != null && km > (it.odometer ?: 0)) it.copy(odometer = km, odometerDate = LocalDate.now()) else it
                }
                d.copy(items = items, repairs = repairs, cars = cars)
            }
            edit = null
        }
        is Edit.RepairEdit -> RepairDialog(
            e.repair,
            exists = data.repairs.any { it.id == e.repair.id },
            onDismiss = { edit = null },
            onDelete = {
                change { d -> d.copy(repairs = d.repairs.filter { it.id != e.repair.id }) }
                edit = null
            },
        ) { r ->
            change { d ->
                d.copy(repairs = if (d.repairs.any { it.id == r.id }) d.repairs.map { if (it.id == r.id) r else it } else d.repairs + r)
            }
            edit = null
        }
    }
}

private fun newItem(car: Car, k: Kind): Item {
    val today = LocalDate.now()
    return Item(
        id = Store.newId(),
        carId = car.id,
        kind = k,
        from = if (k.hasFrom) today else null,
        to = if (k.months > 0 && !k.service) today.plusMonths(k.months.toLong()).minusDays(1) else null,
        lastKm = if (k.km && k != Kind.CUSTOM) car.odometer else null,
        intervalKm = k.intervalKm.takeIf { it > 0 },
    )
}

@Composable
fun CarDialog(car: Car?, onDismiss: () -> Unit, onSave: (Car) -> Unit) {
    var name by remember { mutableStateOf(car?.name ?: "") }
    var plate by remember { mutableStateOf(car?.plate ?: "") }
    var year by remember { mutableStateOf((car?.year).str()) }
    var km by remember { mutableStateOf((car?.odometer).str()) }
    var note by remember { mutableStateOf(car?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (car == null) "Нов автомобил" else "Автомобил") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Марка и модел", name) { name = it }
                Field("Регистрационен номер", plate) { plate = it.uppercase() }
                Field("Година", year, number = true) { year = it }
                Field("Километраж", km, number = true) { km = it }
                Field("Бележка (VIN, двигател, гуми…)", note, singleLine = false) { note = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val odo = km.toIntClean()
                val base = car ?: Car(Store.newId(), "")
                onSave(
                    base.copy(
                        name = name.trim(),
                        plate = plate.trim(),
                        year = year.toIntClean(),
                        odometer = odo,
                        odometerDate = if (odo != base.odometer) LocalDate.now() else base.odometerDate,
                        note = note.trim(),
                    ),
                )
            }) { Text("Запази") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отказ") } },
    )
}

@Composable
private fun OdometerDialog(car: Car, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var km by remember { mutableStateOf(car.odometer.str()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Текущ километраж") },
        text = { Field("Километри", km, number = true) { km = it } },
        confirmButton = { TextButton(enabled = km.toIntClean() != null, onClick = { onSave(km.toIntClean()!!) }) { Text("Запази") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отказ") } },
    )
}

/** Бързи срокове за винетка и застраховки. */
private val PERIODS = listOf(
    "Уикенд" to Period.ofDays(2),
    "Седмица" to Period.ofDays(7),
    "Месец" to Period.ofMonths(1),
    "3 месеца" to Period.ofMonths(3),
    "6 месеца" to Period.ofMonths(6),
    "Година" to Period.ofYears(1),
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ItemDialog(
    item: Item,
    car: Car,
    renewing: Boolean,
    exists: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Item, Boolean) -> Unit,
) {
    val k = item.kind
    var title by remember { mutableStateOf(item.title) }
    var from by remember { mutableStateOf(item.from) }
    var to by remember { mutableStateOf(item.to) }
    var lastKm by remember { mutableStateOf(item.lastKm.str()) }
    var interval by remember { mutableStateOf(item.intervalKm.str()) }
    var cost by remember { mutableStateOf(if (renewing) "" else item.cost.str()) }
    var note by remember { mutableStateOf(item.note) }
    var log by remember { mutableStateOf(renewing && k.service) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (renewing) "${item.name}: нов период" else item.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (k == Kind.CUSTOM) Field("Име (напр. ГТП на ремарке, шофьорска книжка)", title) { title = it }
                if (k.hasFrom) DateField(k.fromLabel, from) { from = it }
                if (k.dated) {
                    if (k.hasFrom && !k.service) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PERIODS.filter { k == Kind.VIGNETTE || it.second.toTotalMonths() >= 1 }.forEach { (label, p) ->
                                val start = from ?: LocalDate.now()
                                val end = start.plus(p).minusDays(1)
                                FilterChip(selected = to == end, onClick = { from = start; to = end }, label = { Text(label) })
                            }
                        }
                    }
                    DateField(k.toLabel, to) { to = it }
                }
                if (k.km) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("При км", lastKm, Modifier.weight(1f), number = true) { lastKm = it }
                        Field("На всеки км", interval, Modifier.weight(1f), number = true) { interval = it }
                    }
                    val due = lastKm.toIntClean()?.let { a -> interval.toIntClean()?.let { a + it } }
                    if (due != null) Text("Следващо при ${Texts.km(due)}", style = MaterialTheme.typography.bodySmall)
                    if (car.odometer != null) Text("Сега колата е на ${Texts.km(car.odometer)}", style = MaterialTheme.typography.bodySmall)
                }
                Field("Цена €", cost, number = true) { cost = it }
                Field("Бележка (застраховател, сервиз, масло…)", note, singleLine = false) { note = it }
                if (k.service || renewing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = log, onCheckedChange = { log = it })
                        Text("Запиши и в ремонтите")
                    }
                }
                if (exists && !renewing) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Изтрий този срок", color = Red) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    item.copy(
                        title = title.trim(),
                        from = from,
                        to = to,
                        lastKm = lastKm.toIntClean(),
                        intervalKm = interval.toIntClean(),
                        cost = cost.toMoney(),
                        note = note.trim(),
                    ),
                    log,
                )
            }) { Text("Запази") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отказ") } },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Да изтрия ли „${item.name}“?") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Изтрий", color = Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отказ") } },
        )
    }
}

@Composable
private fun RepairDialog(repair: Repair, exists: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Repair) -> Unit) {
    var date by remember { mutableStateOf<LocalDate?>(repair.date) }
    var title by remember { mutableStateOf(repair.title) }
    var km by remember { mutableStateOf(repair.km.str()) }
    var cost by remember { mutableStateOf(repair.cost.str()) }
    var note by remember { mutableStateOf(repair.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Ремонт" else "Нов ремонт") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Какво е направено", title) { title = it }
                DateField("Дата", date) { date = it }
                Field("Километраж", km, number = true) { km = it }
                Field("Цена €", cost, number = true) { cost = it }
                Field("Бележка (сервиз, части…)", note, singleLine = false) { note = it }
                if (exists) TextButton(onClick = onDelete) { Text("Изтрий ремонта", color = Red) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onSave(
                    repair.copy(
                        date = date ?: LocalDate.now(),
                        title = title.trim(),
                        km = km.toIntClean(),
                        cost = cost.toMoney(),
                        note = note.trim(),
                    ),
                )
            }) { Text("Запази") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отказ") } },
    )
}
