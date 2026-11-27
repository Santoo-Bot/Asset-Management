package id.perusahaan.asetga.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.repository.AuditLogRepositoryImpl
import id.perusahaan.asetga.data.repository.LoginThrottle
import id.perusahaan.asetga.data.repository.UserRepositoryImpl
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.security.PasswordHasher
import id.perusahaan.asetga.domain.session.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MutableClock(var now: Long = 1_000_000L) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)
}

@RunWith(AndroidJUnit4::class)
class LoginThrottleTest {
    private lateinit var db: AppDatabase
    private val clock = MutableClock()
    private val session = SessionManager(clock)
    private lateinit var users: UserRepositoryImpl

    @Before fun setUp() {
        db = inMemoryDb()
        val audit = AuditLogRepositoryImpl(db.auditLogDao(), session, clock)
        users = UserRepositoryImpl(db.userDao(), PasswordHasher(iterations = 1_000), session, audit, clock, LoginThrottle(db.settingsDao(), clock))
    }

    @After fun tearDown() = db.close()

    private suspend fun adminId(): String {
        val admin = (users.createUser("admin", "Admin GA", Role.ADMIN, "admin1234") as AppResult.Success).value
        session.login(admin)
        return admin.id
    }

    @Test fun `lama penguncian naik bertahap dan dibatasi`() {
        assertEquals(0L, LoginThrottle.lockMillis(4))
        assertEquals(30_000L, LoginThrottle.lockMillis(5))
        assertEquals(60_000L, LoginThrottle.lockMillis(6))
        assertEquals(120_000L, LoginThrottle.lockMillis(7))
        assertEquals(LoginThrottle.MAX_LOCK_MS, LoginThrottle.lockMillis(50))
    }

    @Test fun `lima kata sandi salah mengunci walau berikutnya benar, lalu terbuka setelah jeda`() = runTest {
        adminId()
        repeat(5) { assertTrue(users.authenticate("admin", "salah1234") is AppResult.Failure) }
        val locked = users.authenticate("admin", "admin1234")
        assertTrue(locked is AppResult.Failure && locked.message.startsWith("Terlalu banyak percobaan"))

        clock.now += 31_000
        assertTrue(users.authenticate("admin", "admin1234") is AppResult.Success)
        // Berhasil → hitungan diulang dari nol.
        repeat(4) { assertTrue(users.authenticate("admin", "salah1234") is AppResult.Failure) }
        assertTrue(users.authenticate("admin", "admin1234") is AppResult.Success)
    }

    @Test fun `kegagalan berikutnya menggandakan jeda`() = runTest {
        val id = adminId()
        repeat(5) { users.authenticate("admin", "salah1234") }
        clock.now += 31_000
        users.authenticate("admin", "salah1234") // gagal ke-6 → kunci 60 detik
        assertEquals(60L, users.lockedSeconds(id))
    }

    @Test fun `PIN salah berulang mengunci PIN yang benar`() = runTest {
        val id = adminId()
        users.setPin(id, "2580")
        repeat(5) { assertTrue(!users.verifyPin(id, "0000")) }
        assertTrue(users.lockedSeconds(id) > 0)
        assertTrue(!users.verifyPin(id, "2580"))
        clock.now += 31_000
        assertTrue(users.verifyPin(id, "2580"))
        assertEquals(0L, users.lockedSeconds(id))
    }

    @Test fun `username tidak terdaftar memakai satu hitungan bersama`() = runTest {
        adminId()
        repeat(6) { users.authenticate("hantu$it", "apa-saja1") }
        assertEquals(1, db.settingsDao().observeAll().first().count { it.key.startsWith(LoginThrottle.PREFIX) })
    }
}
