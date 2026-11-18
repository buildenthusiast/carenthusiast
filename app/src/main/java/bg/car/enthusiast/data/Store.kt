package bg.car.enthusiast.data

import android.content.Context
import bg.car.enthusiast.core.Car
import bg.car.enthusiast.core.Data
import bg.car.enthusiast.core.Item
import bg.car.enthusiast.core.Kind
import bg.car.enthusiast.core.Repair
import bg.car.enthusiast.core.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Всичко се пази в SharedPreferences като един JSON. */
class Store(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("store", Context.MODE_PRIVATE)

    fun load(): Data = prefs.getString("data", null)?.let { runCatching { Json.parse(it) }.getOrNull() } ?: Data()

    fun save(d: Data) {
        prefs.edit().putString("data", Json.write(d)).apply()
    }

    fun update(block: (Data) -> Data): Data {
        val d = block(load())
        save(d)
        return d
    }

    companion object {
        fun newId(): Long = System.currentTimeMillis() * 1000 + (0..999).random()
    }
}

object Json {
    fun write(d: Data): String = JSONObject()
        .put("version", 1)
        .put("cars", JSONArray(d.cars.map { car(it) }))
        .put("items", JSONArray(d.items.map { item(it) }))
        .put("repairs", JSONArray(d.repairs.map { repair(it) }))
        .put("settings", settings(d.settings))
        .toString(2)

    fun parse(text: String): Data {
        val j = JSONObject(text)
        return Data(
            cars = j.optJSONArray("cars").objects().map { car(it) },
            items = j.optJSONArray("items").objects().mapNotNull { runCatching { item(it) }.getOrNull() },
            repairs = j.optJSONArray("repairs").objects().map { repair(it) },
            settings = j.optJSONObject("settings")?.let { settings(it) } ?: Settings(),
        )
    }

    private fun car(c: Car) = JSONObject()
        .put("id", c.id).put("name", c.name).put("plate", c.plate).putOpt("year", c.year)
        .putOpt("odometer", c.odometer).putOpt("odometerDate", c.odometerDate?.toString()).put("note", c.note)

    private fun car(j: JSONObject) = Car(
        id = j.getLong("id"),
        name = j.optString("name"),
        plate = j.optString("plate"),
        year = j.intOrNull("year"),
        odometer = j.intOrNull("odometer"),
        odometerDate = j.dateOrNull("odometerDate"),
        note = j.optString("note"),
    )

    private fun item(i: Item) = JSONObject()
        .put("id", i.id).put("carId", i.carId).put("kind", i.kind.name).put("title", i.title)
        .putOpt("from", i.from?.toString()).putOpt("to", i.to?.toString()).putOpt("lastKm", i.lastKm)
        .putOpt("intervalKm", i.intervalKm).putOpt("cost", i.cost).put("note", i.note)

    private fun item(j: JSONObject) = Item(
        id = j.getLong("id"),
        carId = j.getLong("carId"),
        kind = runCatching { Kind.valueOf(j.getString("kind")) }.getOrDefault(Kind.CUSTOM),
        title = j.optString("title"),
        from = j.dateOrNull("from"),
        to = j.dateOrNull("to"),
        lastKm = j.intOrNull("lastKm"),
        intervalKm = j.intOrNull("intervalKm"),
        cost = j.doubleOrNull("cost"),
        note = j.optString("note"),
    )

    private fun repair(r: Repair) = JSONObject()
        .put("id", r.id).put("carId", r.carId).put("date", r.date.toString()).put("title", r.title)
        .putOpt("km", r.km).putOpt("cost", r.cost).put("note", r.note)

    private fun repair(j: JSONObject) = Repair(
        id = j.getLong("id"),
        carId = j.getLong("carId"),
        date = j.dateOrNull("date") ?: LocalDate.now(),
        title = j.optString("title"),
        km = j.intOrNull("km"),
        cost = j.doubleOrNull("cost"),
        note = j.optString("note"),
    )

    private fun settings(s: Settings) = JSONObject()
        .put("notifyDays", s.notifyDays).put("notifyHour", s.notifyHour).put("kmWarn", s.kmWarn)
        .put("autoUpdate", s.autoUpdate).put("notifiedVersion", s.notifiedVersion)

    private fun settings(j: JSONObject): Settings {
        val d = Settings()
        return Settings(
            notifyDays = j.optInt("notifyDays", d.notifyDays),
            notifyHour = j.optInt("notifyHour", d.notifyHour),
            kmWarn = j.optInt("kmWarn", d.kmWarn),
            autoUpdate = j.optBoolean("autoUpdate", d.autoUpdate),
            notifiedVersion = j.optInt("notifiedVersion", d.notifiedVersion),
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.intOrNull(k: String): Int? = if (has(k) && !isNull(k)) optInt(k) else null
    private fun JSONObject.doubleOrNull(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k) else null
    private fun JSONObject.dateOrNull(k: String): LocalDate? =
        if (has(k) && !isNull(k)) runCatching { LocalDate.parse(getString(k)) }.getOrNull() else null
}
