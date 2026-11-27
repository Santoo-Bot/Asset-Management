package id.perusahaan.asetga.data.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import id.perusahaan.asetga.MainActivity
import id.perusahaan.asetga.R
import id.perusahaan.asetga.domain.model.ReminderSummary
import id.perusahaan.asetga.domain.repository.DashboardRepository
import id.perusahaan.asetga.domain.repository.SettingsRepository
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

object ReminderNotifier {
    const val CHANNEL_ID = "pengingat_harian"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Pengingat harian", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Garansi segera habis, servis jatuh tempo/terlambat, stok di bawah minimum, dan polis asuransi yang segera berakhir"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Isi baris notifikasi; kosong bila tidak ada yang perlu diingatkan. */
    fun lines(s: ReminderSummary): List<String> = buildList {
        if (s.serviceOverdue > 0) add("${s.serviceOverdue} aset servis terlambat")
        if (s.serviceDueSoon > 0) add("${s.serviceDueSoon} aset servis jatuh tempo ≤30 hari")
        if (s.warrantyExpiring > 0) add("${s.warrantyExpiring} aset garansi segera habis (≤90 hari)")
        if (s.reorderItems > 0) add("${s.reorderItems} barang stok di bawah minimum")
        if (s.policiesExpiring > 0) add("${s.policiesExpiring} polis asuransi berakhir ≤30 hari")
    }

    fun show(context: Context, summary: ReminderSummary) {
        if (summary.isEmpty || !canNotify(context)) return
        ensureChannel(context)
        val lines = lines(summary)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val style = NotificationCompat.InboxStyle().also { st -> lines.forEach(st::addLine) }
        // Di layar kunci hanya tampil judul umum; rincian jumlah aset baru terlihat setelah perangkat dibuka.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Aset GA")
            .setContentText("Ada hal yang perlu tindakan")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentTitle("Aset GA: ${lines.size} hal perlu tindakan")
            .setContentText(lines.joinToString(" · "))
            .setStyle(style)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Izin dicabut di antara pengecekan dan notify.
        }
    }
}

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dashboard: DashboardRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!settings.current().notificationsEnabled) return Result.success()
        return runCatching {
            ReminderNotifier.show(applicationContext, dashboard.reminderSummary())
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

/** Menjadwalkan cek harian pada jam yang diatur (default 08:00). */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule(hour: Int, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntil(hour, LocalDateTime.now()).toMillis(), TimeUnit.MILLISECONDS)
            .addTag("hour-$hour")
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val WORK_NAME = "pengingat-harian"

        /** Jeda sampai jam [hour]:00 berikutnya. */
        fun delayUntil(hour: Int, now: LocalDateTime): Duration {
            var next = now.toLocalDate().atTime(LocalTime.of(hour.coerceIn(0, 23), 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
