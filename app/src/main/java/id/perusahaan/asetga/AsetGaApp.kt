package id.perusahaan.asetga

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import id.perusahaan.asetga.data.seed.SampleDataSeeder
import id.perusahaan.asetga.data.worker.ReminderNotifier
import id.perusahaan.asetga.data.worker.ReminderScheduler
import id.perusahaan.asetga.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import id.perusahaan.asetga.domain.session.SessionManager
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AsetGaApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var session: SessionManager
    @Inject lateinit var seeder: SampleDataSeeder
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var reminders: ReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // No-op di build release; data contoh hanya untuk debug.
        ProcessLifecycleOwner.get().lifecycleScope.launch { seeder.seedIfNeeded() }

        ReminderNotifier.ensureChannel(this)
        // Jadwalkan ulang pengingat harian setiap kali jam/aktif berubah di Pengaturan.
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            settings.settings
                .map { it.reminderHour to it.notificationsEnabled }
                .distinctUntilChanged()
                .collect { (hour, enabled) -> reminders.schedule(hour, enabled) }
        }

        // Kunci otomatis saat aplikasi lama di latar belakang.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = session.onAppForegrounded()
            override fun onStop(owner: LifecycleOwner) = session.onAppBackgrounded()
        })
    }
}
