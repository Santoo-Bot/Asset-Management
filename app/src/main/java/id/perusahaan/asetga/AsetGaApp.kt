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
import id.perusahaan.asetga.domain.session.SessionManager
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AsetGaApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var session: SessionManager
    @Inject lateinit var seeder: SampleDataSeeder

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // No-op di build release; data contoh hanya untuk debug.
        ProcessLifecycleOwner.get().lifecycleScope.launch { seeder.seedIfNeeded() }

        // Kunci otomatis saat aplikasi lama di latar belakang.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = session.onAppForegrounded()
            override fun onStop(owner: LifecycleOwner) = session.onAppBackgrounded()
        })
    }
}
