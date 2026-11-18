package bg.car.enthusiast.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import bg.car.enthusiast.BuildConfig
import bg.car.enthusiast.R
import bg.car.enthusiast.cloud.Cloud
import bg.car.enthusiast.core.Data
import bg.car.enthusiast.core.Due
import bg.car.enthusiast.core.Level
import bg.car.enthusiast.core.Rules
import bg.car.enthusiast.data.Json
import bg.car.enthusiast.data.Store
import bg.car.enthusiast.update.Updater
import bg.car.enthusiast.work.Notifier
import bg.car.enthusiast.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Намерената нова версия, обща за целия екран. */
object UpdateState {
    var release by mutableStateOf<Updater.Release?>(null)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        Scheduler.schedule(this, store.load().settings, replace = false)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) { App(store) }
            }
        }
    }
}

@Composable
fun App(store: Store) {
    var data by remember { mutableStateOf(store.load()) }
    var tab by remember { mutableIntStateOf(0) }
    var carId by remember { mutableStateOf<Long?>(null) }
    val ctx = LocalContext.current
    var garage by remember { mutableStateOf(Cloud.garageId(ctx)) }
    val change: (((Data) -> Data)) -> Unit = { block ->
        val old = data
        data = store.update(block)
        Cloud.push(ctx, old, data)
    }
    // При всяко отваряне проверяваме дали някой не е споделил гаража си с нас.
    LaunchedEffect(Unit) {
        if (Cloud.email != null) garage = runCatching { Cloud.connect(ctx) }.getOrDefault(garage)
    }
    DisposableEffect(garage) {
        val stop = Cloud.listen(ctx) { data = it }
        onDispose { stop() }
    }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val car = data.cars.firstOrNull { it.id == carId }
    if (car != null) {
        BackHandler { carId = null }
        CarScreen(car, data, change, onBack = { carId = null })
        return
    }

    Column {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_car), null, Modifier.size(28.dp), tint = Amber)
            Spacer(Modifier.width(8.dp))
            Text("Car Enthusiast", style = MaterialTheme.typography.titleLarge)
        }
        TabRow(selectedTabIndex = tab) {
            listOf("Предстоящи", "Автомобили", "Настройки").forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UpdateBanner()
            when (tab) {
                0 -> Upcoming(data, onOpen = { carId = it }, onCars = { tab = 1 })
                1 -> Cars(data, change, onOpen = { carId = it })
                else -> SettingsScreen(data, change, garage) { garage = it }
            }
        }
    }
}

/** Всички срокове от всички коли, най-спешните най-отгоре. */
@Composable
private fun Upcoming(data: Data, onOpen: (Long) -> Unit, onCars: () -> Unit) {
    if (data.cars.isEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Още няма автомобили.", style = MaterialTheme.typography.titleMedium)
                Text("Добави кола и въведи винетка, гражданска, преглед, масло и други срокове. Ще те подсещам ${data.settings.notifyDays} дни преди всеки от тях.")
                Button(onClick = onCars) { Text("Добави автомобил") }
            }
        }
        return
    }
    val all = Rules.all(data)
    if (all.isEmpty()) {
        Text("Няма въведени срокове. Отвори колата в „Автомобили“ и добави винетка, гражданска и т.н.")
        return
    }
    val urgent = all.filter { it.level == Level.EXPIRED || it.level == Level.SOON }
    if (urgent.isNotEmpty()) {
        Text("Внимание", style = MaterialTheme.typography.titleMedium, color = Red)
        urgent.forEach { DueCard(it, showCar = true) { onOpen(it.car.id) } }
    }
    val rest = all - urgent.toSet()
    if (rest.isNotEmpty()) {
        Text("Предстоящи по дата", style = MaterialTheme.typography.titleMedium)
        rest.forEach { DueCard(it, showCar = true) { onOpen(it.car.id) } }
    }
}

@Composable
fun DueCard(d: Due, showCar: Boolean, onClick: () -> Unit) {
    val urgent = d.level == Level.EXPIRED || d.level == Level.SOON
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (urgent) CardDefaults.cardColors(containerColor = levelColor(d.level).copy(alpha = 0.12f)) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(d.item.name, style = MaterialTheme.typography.titleMedium)
            if (showCar) Text(d.car.title, style = MaterialTheme.typography.bodySmall)
            Text(d.text, color = levelColor(d.level), fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun Cars(data: Data, change: ((Data) -> Data) -> Unit, onOpen: (Long) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val all = Rules.all(data)
    data.cars.forEach { car ->
        val dues = all.filter { it.car.id == car.id }
        val worst = dues.firstOrNull()
        Card(Modifier.fillMaxWidth().clickable { onOpen(car.id) }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(car.name, style = MaterialTheme.typography.titleLarge)
                val info = listOfNotNull(
                    car.plate.ifBlank { null },
                    car.year?.toString(),
                    car.odometer?.let { bg.car.enthusiast.core.Texts.km(it) },
                ).joinToString(" · ")
                if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall)
                val bad = dues.count { it.level == Level.EXPIRED }
                val soon = dues.count { it.level == Level.SOON }
                when {
                    bad > 0 -> Text("Изтекли: $bad", color = Red, fontWeight = FontWeight.Bold)
                    soon > 0 -> Text("Изтичат скоро: $soon", color = levelColor(Level.SOON), fontWeight = FontWeight.Bold)
                    worst != null -> Text("Най-близко: ${worst.item.name} – ${worst.text}", color = Green)
                    else -> Text("Няма въведени срокове", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Button(onClick = { adding = true }, Modifier.fillMaxWidth()) { Text("Добави автомобил") }
    if (adding) {
        CarDialog(null, onDismiss = { adding = false }) { car ->
            change { it.copy(cars = it.cars + car) }
            adding = false
            onOpen(car.id)
        }
    }
}

@Composable
private fun UpdateBanner() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (UpdateState.release == null) UpdateState.release = withContext(Dispatchers.IO) { runCatching { Updater.latest() }.getOrNull() }?.takeIf { Updater.isNewer(it) }
    }
    val r = UpdateState.release ?: return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Amber.copy(alpha = 0.2f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Има нова версия ${r.versionName}", style = MaterialTheme.typography.titleMedium)
            if (r.notes.isNotBlank()) Text(r.notes, style = MaterialTheme.typography.bodySmall)
            progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            if (error.isNotEmpty()) Text(error, color = Red)
            Button(onClick = {
                val f = file
                if (!Updater.canInstall(ctx)) {
                    Updater.askInstallPermission(ctx)
                } else if (f != null) {
                    Updater.install(ctx, f)
                } else {
                    error = ""
                    progress = 0f
                    scope.launch {
                        try {
                            val downloaded = withContext(Dispatchers.IO) { Updater.download(ctx, r) { p -> progress = p } }
                            file = downloaded
                            Updater.install(ctx, downloaded)
                        } catch (e: Exception) {
                            error = "Грешка: ${e.message ?: e}"
                        } finally {
                            progress = null
                        }
                    }
                }
            }, enabled = progress == null) {
                Text(
                    when {
                        !Updater.canInstall(ctx) -> "Разреши инсталиране"
                        file != null -> "Инсталирай"
                        else -> "Изтегли и инсталирай"
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(data: Data, change: ((Data) -> Data) -> Unit, garage: String?, onGarage: (String?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Account(garage, onGarage)
    HorizontalDivider()
    var s by remember { mutableStateOf(data.settings) }
    var message by remember { mutableStateOf("") }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            message = runCatching {
                ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Json.write(data).toByteArray()) }
                "Данните са записани във файла."
            }.getOrElse { "Грешка: ${it.message}" }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            message = runCatching {
                val text = ctx.contentResolver.openInputStream(uri)!!.use { it.bufferedReader().readText() }
                val d = Json.parse(text)
                change { d.copy(settings = it.settings) }
                "Заредени: ${d.cars.size} коли, ${d.items.size} срока, ${d.repairs.size} ремонта."
            }.getOrElse { "Грешка: файлът не е разпознат." }
        }
    }

    Text("Известия", style = MaterialTheme.typography.titleMedium)
    LabeledSlider("Подсещай ${s.notifyDays} дни преди изтичане", s.notifyDays.toFloat(), 1f..30f, 1f) {
        s = s.copy(notifyDays = it.roundToInt())
    }
    LabeledSlider("Час на известието: ${s.notifyHour}:00", s.notifyHour.toFloat(), 6f..22f, 1f) {
        s = s.copy(notifyHour = it.roundToInt())
    }
    LabeledSlider("Подсещай ${bg.car.enthusiast.core.Texts.km(s.kmWarn)} преди обслужване", s.kmWarn.toFloat(), 100f..3000f, 100f) {
        s = s.copy(kmWarn = (it / 100).roundToInt() * 100)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = s.autoUpdate, onCheckedChange = { s = s.copy(autoUpdate = it) })
        Text("Известие при нова версия")
    }
    Button(onClick = {
        change { it.copy(settings = s.copy(notifiedVersion = it.settings.notifiedVersion)) }
        Scheduler.schedule(ctx, s, replace = true)
        message = "Настройките са запазени."
    }, Modifier.fillMaxWidth()) { Text("Запази") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            scope.launch {
                val n = withContext(Dispatchers.IO) { Notifier.checkDue(ctx) }
                if (n == 0) Notifier.test(ctx)
                message = if (n == 0) "Няма изтичащи срокове. Изпратих пробно известие." else "Изпратени известия: $n"
            }
        }) { Text("Провери сега") }
    }

    HorizontalDivider()
    Text("Архив", style = MaterialTheme.typography.titleMedium)
    Text(
        "Данните се пазят на телефона. Запиши ги във файл (например в Google Drive), за да ги пренесеш на друг телефон.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { export.launch("car-enthusiast.json") }) { Text("Запиши във файл") }
        OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "*/*")) }) { Text("Зареди от файл") }
    }

    HorizontalDivider()
    Text("Версия", style = MaterialTheme.typography.titleMedium)
    Text("Car Enthusiast ${BuildConfig.VERSION_NAME}")
    OutlinedButton(onClick = {
        message = "Проверка…"
        scope.launch {
            message = try {
                val r = withContext(Dispatchers.IO) { Updater.latest() }
                when {
                    r == null -> "Още няма издадена версия в GitHub."
                    Updater.isNewer(r) -> {
                        UpdateState.release = r
                        "Има нова версия ${r.versionName}. Натисни „Изтегли и инсталирай“ най-горе."
                    }
                    else -> "Имаш последната версия."
                }
            } catch (e: Exception) {
                "Грешка: ${e.message ?: e}"
            }
        }
    }) { Text("Провери за нова версия") }
    if (message.isNotEmpty()) Text(message)
}

/** Вход с Google и споделяне на колите с други хора. */
@Composable
private fun Account(garage: String?, onGarage: (String?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var members by remember { mutableStateOf<List<String>>(emptyList()) }
    var newMail by remember { mutableStateOf("") }
    val me = Cloud.email

    fun act(block: suspend () -> String) {
        busy = true
        message = ""
        scope.launch {
            message = try {
                block()
            } catch (e: Exception) {
                "Грешка: ${e.message ?: e}"
            }
            members = runCatching { Cloud.members(ctx) }.getOrDefault(members)
            busy = false
        }
    }

    LaunchedEffect(garage) {
        members = runCatching { Cloud.members(ctx) }.getOrDefault(emptyList())
    }

    Text("Профил и споделяне", style = MaterialTheme.typography.titleMedium)
    if (me == null || garage == null) {
        Text(
            "Влез с Google, за да се пазят колите в облака и да ги споделиш със семейството. " +
                "Всички, които добавиш, виждат и променят едни и същи коли и получават известията.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = {
            act {
                val mail = if (Cloud.email == null) Cloud.signIn(ctx) else Cloud.email!!
                onGarage(Cloud.connect(ctx))
                "Влезе като $mail. Данните са качени в облака."
            }
        }, enabled = !busy) { Text("Вход с Google") }
    } else {
        Text("Влязъл като $me")
        Text("Достъп до колите имат:", style = MaterialTheme.typography.bodySmall)
        members.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m, Modifier.weight(1f))
                if (m != me) {
                    TextButton(onClick = { act { Cloud.removeMember(ctx, m); "$m е премахнат." } }, enabled = !busy) {
                        Text("Махни", color = Red)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Gmail на човек за споделяне", newMail, Modifier.weight(1f)) { newMail = it }
            OutlinedButton(onClick = {
                val mail = newMail.trim()
                act {
                    Cloud.addMember(ctx, mail)
                    newMail = ""
                    "Добавен е $mail. Нека инсталира приложението и влезе с този Google акаунт."
                }
            }, enabled = !busy && newMail.contains('@')) { Text("Добави") }
        }
        TextButton(onClick = {
            Cloud.signOut(ctx)
            onGarage(null)
            message = "Излезе от профила. Данните остават на телефона."
        }) { Text("Изход") }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotEmpty()) Text(message)
}
