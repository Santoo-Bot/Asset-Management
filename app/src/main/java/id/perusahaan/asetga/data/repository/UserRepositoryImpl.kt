package id.perusahaan.asetga.data.repository

import id.perusahaan.asetga.data.local.dao.UserDao
import id.perusahaan.asetga.data.local.entity.UserEntity
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.SyncState
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.repository.UserRepository
import id.perusahaan.asetga.domain.security.CredentialRules
import id.perusahaan.asetga.domain.security.CredentialRules.FIELD_FULL_NAME
import id.perusahaan.asetga.domain.security.CredentialRules.FIELD_PASSWORD
import id.perusahaan.asetga.domain.security.CredentialRules.FIELD_USERNAME
import id.perusahaan.asetga.domain.security.PasswordHasher
import id.perusahaan.asetga.domain.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val dao: UserDao,
    private val hasher: PasswordHasher,
    private val session: SessionManager,
    private val audit: AuditLogRepository,
    private val clock: Clock,
    private val throttle: LoginThrottle,
) : UserRepository {

    override fun observeUsers(): Flow<List<User>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeUserCount(): Flow<Int> = dao.observeCount()

    override suspend fun getUser(id: String): User? = dao.getById(id)?.toDomain()

    override suspend fun authenticate(username: String, password: String): AppResult<User> {
        val entity = dao.getByUsername(username.trim().lowercase())
        val key = entity?.id ?: LoginThrottle.UNKNOWN_KEY
        throttle.remainingSeconds(key).takeIf { it > 0 }?.let { return AppResult.Failure(LoginThrottle.message(it)) }
        // Tetap hitung hash walau user tidak ada agar waktu respons tidak membocorkan keberadaan username.
        val ok = withContext(Dispatchers.Default) {
            hasher.verify(password, entity?.passwordHash ?: DUMMY_HASH)
        }
        if (entity == null || !ok) {
            throttle.recordFailure(key)
            return AppResult.Failure("Username atau kata sandi salah")
        }
        throttle.reset(key)
        return if (!entity.isActive) AppResult.Failure("Akun ini dinonaktifkan. Hubungi Admin GA.") else AppResult.Success(entity.toDomain())
    }

    override suspend fun lockedSeconds(userId: String): Long = throttle.remainingSeconds(userId)

    override suspend fun verifyPin(userId: String, pin: String): Boolean {
        if (throttle.remainingSeconds(userId) > 0) return false
        val stored = dao.getById(userId)?.takeIf { it.isActive }?.pinHash ?: return false
        val ok = withContext(Dispatchers.Default) { hasher.verify(pin, stored) }
        if (ok) throttle.reset(userId) else throttle.recordFailure(userId)
        return ok
    }

    override suspend fun createUser(username: String, fullName: String, role: Role, password: String): AppResult<User> {
        val isFirstUser = dao.count() == 0
        if (isFirstUser && role != Role.ADMIN) return AppResult.Failure("Pengguna pertama harus Admin GA")
        if (!isFirstUser) session.denyUnless { it.canManageUsers }?.let { return it }

        val uname = username.trim().lowercase()
        val errors = buildMap {
            CredentialRules.validateUsername(uname)?.let { put(FIELD_USERNAME, it) }
            if (fullName.isBlank()) put(FIELD_FULL_NAME, "Nama lengkap wajib diisi")
            CredentialRules.validatePassword(password)?.let { put(FIELD_PASSWORD, it) }
        }.toMutableMap()
        if (FIELD_USERNAME !in errors && dao.getByUsername(uname) != null) errors[FIELD_USERNAME] = "Username sudah dipakai"
        if (errors.isNotEmpty()) return AppResult.Failure("Periksa kembali isian", errors)

        val now = clock.millis()
        val hash = withContext(Dispatchers.Default) { hasher.hash(password) }
        val entity = UserEntity(
            id = UUID.randomUUID().toString(),
            username = uname,
            fullName = fullName.trim(),
            role = role,
            passwordHash = hash,
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(entity)
        audit.log(AuditEntityType.USER, entity.id, AuditAction.CREATE, "Pengguna ${entity.fullName} (${role.label}) ditambahkan")
        return AppResult.Success(entity.toDomain())
    }

    override suspend fun updateUser(id: String, fullName: String, role: Role, isActive: Boolean): AppResult<User> {
        session.denyUnless { it.canManageUsers }?.let { return it }
        val old = dao.getById(id) ?: return AppResult.Failure("Pengguna tidak ditemukan")
        if (fullName.isBlank()) return AppResult.Failure("Periksa kembali isian", mapOf(FIELD_FULL_NAME to "Nama lengkap wajib diisi"))

        val losesAdmin = old.role == Role.ADMIN && old.isActive && (role != Role.ADMIN || !isActive)
        if (losesAdmin && dao.countActiveByRole(Role.ADMIN) <= 1) {
            return AppResult.Failure("Harus ada minimal satu Admin GA yang aktif")
        }
        if (id == session.currentUser?.id && !isActive) return AppResult.Failure("Anda tidak dapat menonaktifkan akun sendiri")

        val updated = old.copy(
            fullName = fullName.trim(),
            role = role,
            isActive = isActive,
            updatedAt = clock.millis(),
            syncState = SyncState.PENDING,
        )
        dao.update(updated)
        val changes = ChangeSet().apply {
            field("Nama", old.fullName, updated.fullName)
            field("Peran", old.role.label, updated.role.label)
            field("Aktif", old.isActive, updated.isActive)
        }
        if (!changes.isEmpty()) {
            audit.log(AuditEntityType.USER, id, AuditAction.UPDATE, "Data pengguna ${updated.fullName} diubah", changes.build())
        }
        val domain = updated.toDomain()
        session.refreshUser(domain)
        return AppResult.Success(domain)
    }

    override suspend fun changePassword(id: String, newPassword: String): AppResult<Unit> {
        val isSelf = id == session.currentUser?.id
        if (!isSelf) session.denyUnless { it.canManageUsers }?.let { return it }
        CredentialRules.validatePassword(newPassword)?.let { return AppResult.Failure(it, mapOf(FIELD_PASSWORD to it)) }
        val old = dao.getById(id) ?: return AppResult.Failure("Pengguna tidak ditemukan")
        val hash = withContext(Dispatchers.Default) { hasher.hash(newPassword) }
        dao.update(old.copy(passwordHash = hash, updatedAt = clock.millis(), syncState = SyncState.PENDING))
        audit.log(AuditEntityType.USER, id, AuditAction.UPDATE, "Kata sandi ${old.fullName} diubah")
        return AppResult.Success(Unit)
    }

    override suspend fun setPin(id: String, pin: String?): AppResult<Unit> {
        if (id != session.currentUser?.id) return AppResult.Failure(MSG_NO_ACCESS)
        if (pin != null) CredentialRules.validatePin(pin)?.let { return AppResult.Failure(it) }
        val old = dao.getById(id) ?: return AppResult.Failure("Pengguna tidak ditemukan")
        val hash = pin?.let { withContext(Dispatchers.Default) { hasher.hash(it) } }
        dao.update(old.copy(pinHash = hash, updatedAt = clock.millis(), syncState = SyncState.PENDING))
        session.refreshUser(old.copy(pinHash = hash).toDomain())
        return AppResult.Success(Unit)
    }

    override suspend fun deleteUser(id: String): AppResult<Unit> {
        session.denyUnless { it.canManageUsers }?.let { return it }
        if (id == session.currentUser?.id) return AppResult.Failure("Anda tidak dapat menghapus akun sendiri")
        val user = dao.getById(id) ?: return AppResult.Failure("Pengguna tidak ditemukan")
        if (user.role == Role.ADMIN && user.isActive && dao.countActiveByRole(Role.ADMIN) <= 1) {
            return AppResult.Failure("Harus ada minimal satu Admin GA")
        }
        dao.delete(id)
        audit.log(AuditEntityType.USER, id, AuditAction.DELETE, "Pengguna ${user.fullName} (@${user.username}) dihapus permanen")
        return AppResult.Success(Unit)
    }

    companion object {
        // Hash acak tetap (bukan kata sandi siapa pun) untuk menyamakan waktu verifikasi.
        private const val DUMMY_HASH =
            "pbkdf2-sha256$120000\$AAAAAAAAAAAAAAAAAAAAAA==\$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    }
}
