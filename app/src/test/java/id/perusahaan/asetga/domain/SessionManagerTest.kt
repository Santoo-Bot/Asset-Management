package id.perusahaan.asetga.domain

import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.domain.session.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class SessionManagerTest {
    private class MutableClock(var now: Long) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("Asia/Jakarta")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }

    private val user = User("u1", "budi", "Budi", Role.STAFF, hasPin = false, isActive = true)
    private val clock = MutableClock(0)
    private val session = SessionManager(clock)

    @Test fun `restore tanpa pengguna terakhir menjadi LoggedOut`() {
        session.restore(null, lockEnabled = true)
        assertEquals(SessionState.LoggedOut, session.state.value)
        assertNull(session.currentUser)
    }

    @Test fun `restore dengan kunci aktif menjadi Locked`() {
        session.restore(user, lockEnabled = true)
        assertEquals(SessionState.Locked(user), session.state.value)
        assertEquals(user, session.currentUser)
    }

    @Test fun `restore tanpa kunci langsung Active`() {
        session.restore(user, lockEnabled = false)
        assertEquals(SessionState.Active(user), session.state.value)
    }

    @Test fun `pengguna nonaktif tidak dipulihkan`() {
        session.restore(user.copy(isActive = false), lockEnabled = true)
        assertEquals(SessionState.LoggedOut, session.state.value)
    }

    @Test fun `terkunci otomatis setelah timeout di latar belakang`() {
        session.login(user)
        session.onAppBackgrounded()
        clock.now += SessionManager.DEFAULT_LOCK_TIMEOUT_MS - 1
        session.onAppForegrounded()
        assertEquals(SessionState.Active(user), session.state.value)

        session.onAppBackgrounded()
        clock.now += SessionManager.DEFAULT_LOCK_TIMEOUT_MS
        session.onAppForegrounded()
        assertEquals(SessionState.Locked(user), session.state.value)

        session.unlock()
        assertEquals(SessionState.Active(user), session.state.value)
    }

    @Test fun `tidak terkunci bila fitur kunci dimatikan`() {
        session.login(user)
        session.lockEnabled = false
        session.onAppBackgrounded()
        clock.now += 10 * SessionManager.DEFAULT_LOCK_TIMEOUT_MS
        session.onAppForegrounded()
        assertEquals(SessionState.Active(user), session.state.value)
    }

    @Test fun `refresh pengguna yang dinonaktifkan mengeluarkan sesi`() {
        session.login(user)
        session.refreshUser(user.copy(role = Role.ADMIN))
        assertEquals(Role.ADMIN, session.currentUser?.role)
        session.refreshUser(user.copy(isActive = false))
        assertEquals(SessionState.LoggedOut, session.state.value)
    }
}
