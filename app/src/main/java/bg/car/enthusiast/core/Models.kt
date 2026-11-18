package bg.car.enthusiast.core

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Вид на срока или обслужването. */
enum class Kind(
    val label: String,
    /** Има крайна дата. */
    val dated: Boolean,
    /** Следи се и по километри. */
    val km: Boolean,
    /** Има начална дата (валидна от / последно направено). */
    val hasFrom: Boolean,
    /** Срок по подразбиране в месеци, 0 = няма. */
    val months: Int,
    /** Интервал по подразбиране в км, 0 = няма. */
    val intervalKm: Int,
    /** Служи за дневника на ремонтите при подновяване. */
    val service: Boolean,
) {
    VIGNETTE("Винетка", true, false, true, 12, 0, false),
    CIVIL("Гражданска отговорност", true, false, true, 12, 0, false),
    INSPECTION("Технически преглед", true, false, true, 12, 0, false),
    CASCO("Каско", true, false, true, 12, 0, false),
    TAX("Данък МПС", true, false, false, 12, 0, false),
    OIL("Смяна на масло и филтри", true, true, true, 12, 10000, true),
    TIMING("Ангренаж", true, true, true, 60, 90000, true),
    BRAKES("Накладки и дискове", false, true, true, 0, 40000, true),
    TIRES("Сезонна смяна на гуми", true, false, false, 0, 0, true),
    EXTINGUISHER("Пожарогасител", true, false, false, 12, 0, false),
    FIRST_AID("Аптечка", true, false, false, 0, 0, false),
    CUSTOM("Друго", true, true, true, 0, 0, false),
    ;

    val fromLabel: String get() = if (service) "Последно направено на" else "Валидна от"
    val toLabel: String get() = if (service) "Следващо до дата" else "Валидна до"
}

data class Car(
    val id: Long,
    val name: String,
    val plate: String = "",
    val year: Int? = null,
    val odometer: Int? = null,
    val odometerDate: LocalDate? = null,
    val note: String = "",
) {
    val title: String get() = if (plate.isBlank()) name else "$name · $plate"
}

data class Item(
    val id: Long,
    val carId: Long,
    val kind: Kind,
    val title: String = "",
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val lastKm: Int? = null,
    val intervalKm: Int? = null,
    val cost: Double? = null,
    val note: String = "",
) {
    val name: String get() = title.ifBlank { kind.label }
    val dueKm: Int? get() = if (lastKm != null && intervalKm != null && intervalKm > 0) lastKm + intervalKm else null
}

data class Repair(
    val id: Long,
    val carId: Long,
    val date: LocalDate,
    val title: String,
    val km: Int? = null,
    val cost: Double? = null,
    val note: String = "",
)

data class Settings(
    val notifyDays: Int = 5,
    val notifyHour: Int = 9,
    val kmWarn: Int = 1000,
    val autoUpdate: Boolean = true,
    val notifiedVersion: Int = 0,
)

data class Data(
    val cars: List<Car> = emptyList(),
    val items: List<Item> = emptyList(),
    val repairs: List<Repair> = emptyList(),
    val settings: Settings = Settings(),
)

enum class Level { OK, SOON, EXPIRED, UNKNOWN }

/** Колко остава до срока на един запис. */
data class Due(val item: Item, val car: Car, val daysLeft: Long?, val kmLeft: Int?, val level: Level) {
    val text: String get() = Texts.due(this)
    /** За сортиране: по-малко = по-спешно. */
    val urgency: Long
        get() = listOfNotNull(daysLeft, kmLeft?.let { it / 50L }).minOrNull() ?: Long.MAX_VALUE
}

object Rules {
    fun due(item: Item, car: Car, s: Settings, today: LocalDate = LocalDate.now()): Due {
        val days = item.to?.let { ChronoUnit.DAYS.between(today, it) }
        val km = item.dueKm?.let { due -> car.odometer?.let { due - it } }
        val level = when {
            days == null && km == null -> Level.UNKNOWN
            (days != null && days < 0) || (km != null && km < 0) -> Level.EXPIRED
            (days != null && days <= s.notifyDays) || (km != null && km <= s.kmWarn) -> Level.SOON
            else -> Level.OK
        }
        return Due(item, car, days, km, level)
    }

    fun all(d: Data, today: LocalDate = LocalDate.now()): List<Due> {
        val cars = d.cars.associateBy { it.id }
        return d.items.mapNotNull { i -> cars[i.carId]?.let { due(i, it, d.settings, today) } }.sortedBy { it.urgency }
    }

    /** Предложение за следващ период при подновяване. */
    fun renew(item: Item, car: Car, today: LocalDate = LocalDate.now()): Item {
        val oldFrom = item.from
        val oldTo = item.to
        val length: Period? = when {
            oldFrom != null && oldTo != null && oldTo.isAfter(oldFrom) -> Period.between(oldFrom, oldTo.plusDays(1))
            item.kind.months > 0 -> Period.ofMonths(item.kind.months)
            else -> null
        }
        return if (item.kind.service) {
            item.copy(
                from = today,
                to = length?.let { today.plus(it).minusDays(1) } ?: oldTo,
                lastKm = car.odometer ?: item.lastKm,
                intervalKm = item.intervalKm ?: item.kind.intervalKm.takeIf { it > 0 },
            )
        } else {
            val start = if (oldTo != null && !oldTo.isBefore(today)) oldTo.plusDays(1) else today
            item.copy(from = start, to = length?.let { start.plus(it).minusDays(1) })
        }
    }
}

object Texts {
    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun date(d: LocalDate?) = d?.format(DATE) ?: "–"

    fun days(n: Long) = if (n == 1L) "1 ден" else "$n дни"

    fun km(n: Int): String = String.format(java.util.Locale.US, "%,d", n).replace(',', ' ') + " км"

    fun money(v: Double) = if (v % 1.0 == 0.0) "${v.toLong()} €" else String.format(java.util.Locale.US, "%.2f €", v)

    /** Кратко: „3 дни“, „850 км“, „4 мес.“ – за голямото число вдясно. */
    fun short(d: Due): String {
        val dl = d.daysLeft
        val kl = d.kmLeft
        val useKm = kl != null && (dl == null || kl / 50L < dl)
        return when {
            useKm && kl!! < 0 -> "−" + km(-kl)
            useKm -> km(kl!!)
            dl == null -> "–"
            dl < 0 -> "изтекло"
            dl == 0L -> "днес"
            dl < 60 -> days(dl)
            else -> "${dl / 30} мес."
        }
    }

    fun due(d: Due): String {
        val parts = mutableListOf<String>()
        d.daysLeft?.let { n ->
            parts += when {
                n < 0 -> "изтекло преди ${days(-n)} (${date(d.item.to)})"
                n == 0L -> "изтича днес"
                n == 1L -> "изтича утре"
                else -> "изтича след ${days(n)} (${date(d.item.to)})"
            }
        }
        d.kmLeft?.let { k ->
            parts += if (k < 0) "просрочено с ${km(-k)}" else "след ${km(k)} (при ${km(d.item.dueKm ?: 0)})"
        }
        if (parts.isEmpty()) {
            parts += if (d.item.dueKm != null) "при ${km(d.item.dueKm!!)}, въведи километража" else "няма зададен срок"
        }
        return parts.joinToString(" · ")
    }
}
