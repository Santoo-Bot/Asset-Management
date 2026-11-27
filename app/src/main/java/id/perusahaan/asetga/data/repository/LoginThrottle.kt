package id.perusahaan.asetga.data.repository

import id.perusahaan.asetga.data.local.dao.SettingsDao
import id.perusahaan.asetga.data.local.entity.AppSettingEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Membatasi percobaan masuk/buka kunci yang salah: setelah [FREE_ATTEMPTS] kali salah berturut-turut,
 * pengguna harus menunggu dan jedanya naik dua kali lipat di setiap kegagalan berikutnya (maks. [MAX_LOCK_MS]).
 *
 * Status disimpan di tabel `app_settings` (kunci `throttle.<kunci>`) sehingga tetap berlaku walau aplikasi
 * ditutup paksa lalu dibuka lagi; tidak butuh perubahan skema. Nilai: `<jumlah gagal>;<terkunci sampai (ms)>`.
 */
@Singleton
class LoginThrottle @Inject constructor(
    private val settings: SettingsDao,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Sisa detik yang harus ditunggu; 0 bila boleh mencoba. */
    suspend fun remainingSeconds(key: String): Long = mutex.withLock {
        val until = read(key).second
        val left = until - clock.millis()
        if (left <= 0) 0 else (left + 999) / 1000
    }

    suspend fun recordFailure(key: String) = mutex.withLock {
        val failures = read(key).first + 1
        val lock = lockMillis(failures)
        write(key, failures, if (lock > 0) clock.millis() + lock else 0L)
    }

    suspend fun reset(key: String) = mutex.withLock {
        if (read(key).first != 0) write(key, 0, 0L)
    }

    private suspend fun read(key: String): Pair<Int, Long> {
        val parts = settings.get(PREFIX + key)?.split(';') ?: return 0 to 0L
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toLongOrNull() ?: 0L)
    }

    private suspend fun write(key: String, failures: Int, lockedUntil: Long) =
        settings.put(AppSettingEntity(PREFIX + key, "$failures;$lockedUntil"))

    companion object {
        const val PREFIX = "throttle."
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCK_MS = 30_000L
        const val MAX_LOCK_MS = 15 * 60_000L

        /** Kunci untuk username yang tidak terdaftar, supaya tidak membuat baris baru per tebakan. */
        const val UNKNOWN_KEY = "unknown"

        /** Lama penguncian setelah [failures] kali gagal berturut-turut. */
        fun lockMillis(failures: Int): Long {
            if (failures < FREE_ATTEMPTS) return 0
            val shift = (failures - FREE_ATTEMPTS).coerceAtMost(10)
            return (BASE_LOCK_MS shl shift).coerceAtMost(MAX_LOCK_MS)
        }

        fun message(seconds: Long): String {
            val wait = if (seconds >= 120) "${(seconds + 59) / 60} menit" else "$seconds detik"
            return "Terlalu banyak percobaan. Coba lagi dalam $wait."
        }
    }
}
