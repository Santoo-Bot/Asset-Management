package id.perusahaan.asetga.data.repository

import androidx.room.withTransaction
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.dao.AssetDao
import id.perusahaan.asetga.data.local.dao.LocationDao
import id.perusahaan.asetga.data.local.dao.OpnameDao
import id.perusahaan.asetga.data.local.dao.OpnameItemRow
import id.perusahaan.asetga.data.local.dao.OpnameSessionRow
import id.perusahaan.asetga.data.local.entity.AuditItemEntity
import id.perusahaan.asetga.data.local.entity.AuditSessionEntity
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.AuditResult
import id.perusahaan.asetga.domain.model.OpnameItem
import id.perusahaan.asetga.domain.model.OpnameScanOutcome
import id.perusahaan.asetga.domain.model.OpnameSession
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.repository.OpnameRepository
import id.perusahaan.asetga.domain.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal fun OpnameSessionRow.toDomain() = OpnameSession(id, locationId, locationName, startedAt, finishedAt, startedBy, note, expected, found, unregistered)
internal fun OpnameItemRow.toDomain() = OpnameItem(id, assetId, scannedCode, result, scannedAt, assetCode, assetName, assetLocation)

@Singleton
class OpnameRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: OpnameDao,
    private val assetDao: AssetDao,
    private val locationDao: LocationDao,
    private val session: SessionManager,
    private val audit: AuditLogRepository,
    private val clock: Clock,
) : OpnameRepository {

    override fun observeSessions(): Flow<List<OpnameSession>> = dao.observeSessions().map { l -> l.map { it.toDomain() } }
    override fun observeSession(id: String): Flow<OpnameSession?> = dao.observeSession(id).map { it?.toDomain() }
    override fun observeItems(sessionId: String): Flow<List<OpnameItem>> = dao.observeItems(sessionId).map { l -> l.map { it.toDomain() } }

    override suspend fun start(locationId: String): AppResult<String> {
        session.denyUnless { it.canEditData }?.let { return it }
        val location = locationDao.getById(locationId) ?: return AppResult.Failure("Lokasi tidak ditemukan")
        val now = clock.millis()
        val id = UUID.randomUUID().toString()
        db.withTransaction {
            dao.insertSession(AuditSessionEntity(id, locationId, now, null, session.currentUser?.id, null, now, now))
            val items = dao.assetsAt(locationId).map { a ->
                AuditItemEntity(UUID.randomUUID().toString(), id, a.id, a.code, AuditResult.TIDAK_DITEMUKAN, null, now, now)
            }
            items.chunked(500).forEach { dao.insertItems(it) }
        }
        audit.log(AuditEntityType.ASSET, id, AuditAction.CREATE, "Stock opname dimulai di ${location.name}")
        return AppResult.Success(id)
    }

    override suspend fun scan(sessionId: String, code: String): OpnameScanOutcome {
        if (session.currentUser?.role?.canEditData != true) return OpnameScanOutcome.Error("Anda tidak memiliki akses")
        val s = dao.getSession(sessionId) ?: return OpnameScanOutcome.Error("Sesi tidak ditemukan")
        if (s.finishedAt != null) return OpnameScanOutcome.Error("Sesi sudah selesai")
        val clean = code.trim()
        val asset = assetDao.getByCode(clean)
        val now = clock.millis()
        return db.withTransaction {
            val existing = dao.findItem(sessionId, asset?.id, clean)
            when {
                existing != null && existing.result == AuditResult.TIDAK_DITEMUKAN -> {
                    dao.markItem(existing.id, AuditResult.DITEMUKAN, now, clean)
                    OpnameScanOutcome.Found(asset?.code ?: clean, asset?.name.orEmpty())
                }
                existing != null -> OpnameScanOutcome.AlreadyScanned(asset?.code ?: clean, asset?.name.orEmpty())
                asset != null -> {
                    // Aset terdaftar tetapi tercatat di lokasi lain → selisih.
                    dao.insertItem(AuditItemEntity(UUID.randomUUID().toString(), sessionId, asset.id, clean, AuditResult.TAK_TERDAFTAR, now, now, now))
                    OpnameScanOutcome.OtherLocation(asset.code, asset.name, locationDao.getById(asset.locationId)?.name.orEmpty())
                }
                else -> {
                    dao.insertItem(AuditItemEntity(UUID.randomUUID().toString(), sessionId, null, clean, AuditResult.TAK_TERDAFTAR, now, now, now))
                    OpnameScanOutcome.Unknown(clean)
                }
            }
        }
    }

    override suspend fun finish(sessionId: String, note: String): AppResult<Unit> {
        session.denyUnless { it.canEditData }?.let { return it }
        val s = dao.getSession(sessionId) ?: return AppResult.Failure("Sesi tidak ditemukan")
        if (s.finishedAt != null) return AppResult.Failure("Sesi sudah selesai")
        dao.finish(sessionId, clock.millis(), note.trimToNull())
        audit.log(
            AuditEntityType.ASSET, sessionId, AuditAction.UPDATE,
            "Stock opname ${s.locationName} selesai: ${s.found}/${s.expected} ditemukan, ${s.expected - s.found} tidak ditemukan, ${s.unregistered} tidak terdaftar",
        )
        return AppResult.Success(Unit)
    }
}
