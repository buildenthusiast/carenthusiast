package bg.car.enthusiast.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import bg.car.enthusiast.R
import bg.car.enthusiast.core.Level
import bg.car.enthusiast.core.Rules
import bg.car.enthusiast.core.Settings
import bg.car.enthusiast.data.Store
import bg.car.enthusiast.ui.MainActivity
import bg.car.enthusiast.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val NAME = "daily-check"

    /** Всеки ден в избрания час се проверяват сроковете и има ли нова версия. */
    fun schedule(ctx: Context, s: Settings, replace: Boolean) {
        val now = ZonedDateTime.now()
        var next = now.withHour(s.notifyHour).withMinute(0).withSecond(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val work = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        val policy = if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, policy, work)
    }

    /** Еднократна проверка веднага (за бутона „Провери сега“). */
    fun now(ctx: Context) {
        WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<DailyWorker>().build())
    }
}

class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Notifier.checkDue(applicationContext)
        runCatching { Notifier.checkUpdate(applicationContext) }
        Result.success()
    }
}

object Notifier {
    private const val CH_DUE = "due"
    private const val CH_UPDATE = "update"

    /** Известие за всичко, което изтича до N дни, вече е изтекло или наближава по километри. */
    fun checkDue(ctx: Context): Int {
        val d = Store(ctx).load()
        val list = Rules.all(d).filter { it.level == Level.SOON || it.level == Level.EXPIRED }
        list.forEach { due ->
            val title = "${due.item.name} · ${due.car.title}"
            show(ctx, CH_DUE, (due.item.id % Int.MAX_VALUE).toInt(), title, due.text)
        }
        return list.size
    }

    fun checkUpdate(ctx: Context) {
        val store = Store(ctx)
        val d = store.load()
        if (!d.settings.autoUpdate) return
        val r = Updater.latest() ?: return
        if (!Updater.isNewer(r) || d.settings.notifiedVersion >= r.versionCode) return
        show(ctx, CH_UPDATE, 1, "Нова версия ${r.versionName}", "Отвори приложението, за да обновиш.")
        store.save(d.copy(settings = d.settings.copy(notifiedVersion = r.versionCode)))
    }

    fun test(ctx: Context) = show(ctx, CH_DUE, 2, "Car Enthusiast", "Известията работят.")

    private fun show(ctx: Context, channel: String, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DUE, "Изтичащи срокове", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_UPDATE, "Нови версии", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }
}
