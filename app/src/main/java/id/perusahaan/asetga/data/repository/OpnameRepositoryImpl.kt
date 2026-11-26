package id.perusahaan.asetga.data.repository

import androidx.room.withTransaction
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.dao.AssetDao
import id.perusahaan.asetga.data.local.dao.OpnameDao
import id.perusahaan.asetga.data.local.dao.OpnameItemRow
import id.perusahaan.asetga.data.local.dao.OpnameSessionRow
import id.perusahaan.asetga.data.local.entity.AuditItemEntity
import id.perusahaan.asetga.data.local.entity.AuditSessionEntity
import id.perusahaan.asetga.domain.excel.OpnameImportTemplate
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.AuditResult
import id.perusahaan.asetga.domain.model.OpnameAddResult
import id.perusahaan.asetga.domain.model.OpnameImportPreview
import id.perusahaan.asetga.domain.model.OpnameImportRow
import id.perusahaan.asetga.domain.model.OpnameItem
import id.perusahaan.asetga.domain.model.OpnameScanOutcome
import id.perusahaan.asetga.domain.model.OpnameSession
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.repository.OpnameRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal fun OpnameSessionRow.toDomain() = OpnameSession(id, name, opnameDate, startedAt, finishedAt, startedBy, note, total, found, damaged, outside)
internal fun OpnameItemRow.toDomain() = OpnameItem(
    id = id, assetId = assetId, code = assetCode ?: scannedCode, name = assetName, qrCode = qrCode, categoryName = categoryName,
    locationName = locationName, userLocation = userLocation, result = result, condition = condition, systemCondition = systemCondition,
    note = note, checkedAt = scannedAt,
)

@Singleton
class OpnameRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: OpnameDao,
    private val assetDao: AssetDao,
    private val session: SessionManager,
    private val audit: AuditLogRepository,
    private val clock: Clock,
) : OpnameRepository {

    override fun observeSessions(): Flow<List<OpnameSession>> = dao.observeSessions().map { l -> l.map { it.toDomain() } }
    override fun observeSession(id: String): Flow<OpnameSession?> = dao.observeSession(id).map { it?.toDomain() }
    override fun observeItems(sessionId: String): Flow<List<OpnameItem>> = dao.observeItems(sessionId).map { l -> l.map { it.toDomain() } }
    override fun observeAssetIds(sessionId: String): Flow<Set<String>> = dao.observeAssetIds(sessionId).map { it.toSet() }

    override suspend fun create(name: String, date: LocalDate, note: String?): AppResult<String> {
        session.denyUnless { it.canEditData }?.let { return it }
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return AppResult.Failure("Nama sesi wajib diisi", mapOf(FIELD_NAME to "Wajib diisi"))
        val now = clock.millis()
        val id = UUID.randomUUID().toString()
        dao.insertSession(AuditSessionEntity(id, cleanName, date, now, null, session.currentUser?.id, note.trimToNull(), now, now))
        audit.log(AuditEntityType.ASSET, id, AuditAction.CREATE, "Sesi stock opname \"$cleanName\" (${Fmt.date(date)}) dibuat")
        return AppResult.Success(id)
    }

    /** Sesi yang masih berjalan, atau pesan penolakan. */
    private suspend fun openSession(sessionId: String): Pair<OpnameSessionRow?, AppResult.Failure?> {
        val s = dao.getSession(sessionId) ?: return null to AppResult.Failure("Sesi tidak ditemukan")
        if (s.finishedAt != null) return null to AppResult.Failure("Sesi sudah selesai dan tidak bisa diubah")
        return s to null
    }

    override suspend fun addAssets(sessionId: String, assetIds: List<String>): AppResult<OpnameAddResult> =
        addInternal(sessionId, assetIds, "dipilih manual")

    private suspend fun addInternal(sessionId: String, assetIds: List<String>, source: String): AppResult<OpnameAddResult> {
        session.denyUnless { it.canEditData }?.let { return it }
        val (s, error) = openSession(sessionId)
        if (error != null) return error
        val requested = assetIds.distinct()
        if (requested.isEmpty()) return AppResult.Success(OpnameAddResult(0, 0))
        val now = clock.millis()
        var added = 0
        var already = 0
        var eligibleCount = 0
        db.withTransaction {
            val existing = dao.assetIds(sessionId).toHashSet()
            requested.chunked(500).forEach { chunk ->
                val eligible = dao.eligibleAssets(chunk)
                eligibleCount += eligible.size
                val fresh = eligible.filter { it.id !in existing }
                already += eligible.size - fresh.size
                dao.insertItems(
                    fresh.map { a ->
                        AuditItemEntity(UUID.randomUUID().toString(), sessionId, a.id, a.code, AuditResult.TIDAK_DITEMUKAN, null, now, now)
                    },
                )
                fresh.forEach { existing += it.id }
                added += fresh.size
            }
            if (added > 0) audit.log(AuditEntityType.ASSET, sessionId, AuditAction.UPDATE, "$added aset ditambahkan ke sesi opname \"${s!!.name}\" ($source)")
        }
        return AppResult.Success(OpnameAddResult(added, already, skippedWriteOff = requested.size - eligibleCount))
    }

    override suspend fun previewImport(sessionId: String, sheet: List<List<String>>): OpnameImportPreview {
        val (rows, error) = OpnameImportTemplate.parse(sheet)
        if (error != null) return OpnameImportPreview(emptyList(), error)
        if (rows.size > MAX_IMPORT_ROWS) return OpnameImportPreview(emptyList(), "Maksimal $MAX_IMPORT_ROWS baris per upload")
        val existing = dao.assetIds(sessionId).toSet()
        val seen = mutableSetOf<String>()
        return OpnameImportPreview(
            rows.map { r ->
                if (r.errors.isNotEmpty()) return@map OpnameImportRow(r.rowNumber, r.code, null, null, r.errors)
                val asset = assetDao.getByCode(r.code)
                val errors = buildList {
                    when {
                        asset == null -> add("Kode \"${r.code}\" tidak terdaftar sebagai aset atau kode QR")
                        asset.status == AssetStatus.WRITE_OFF -> add("${asset.code} berstatus Write Off")
                        !seen.add(asset.id) -> add("${asset.code} muncul lebih dari sekali (kode aset & kode QR)")
                    }
                }
                OpnameImportRow(
                    r.rowNumber, r.code, asset?.id, asset?.let { "${it.code} · ${it.name}" }, errors,
                    alreadyInSession = asset != null && asset.id in existing,
                )
            },
        )
    }

    override suspend fun commitImport(sessionId: String, preview: OpnameImportPreview): AppResult<OpnameAddResult> =
        addInternal(sessionId, preview.valid.mapNotNull { it.assetId }, "upload Excel")

    override suspend fun removeItem(sessionId: String, itemId: String): AppResult<Unit> {
        session.denyUnless { it.canEditData }?.let { return it }
        val (_, error) = openSession(sessionId)
        if (error != null) return error
        if (dao.deleteItem(sessionId, itemId) == 0) return AppResult.Failure("Aset tidak ada di sesi ini")
        return AppResult.Success(Unit)
    }

    override suspend fun scan(sessionId: String, code: String): OpnameScanOutcome {
        if (session.currentUser?.role?.canEditData != true) return OpnameScanOutcome.Error("Anda tidak memiliki akses")
        val (_, error) = openSession(sessionId)
        if (error != null) return OpnameScanOutcome.Error(error.message)
        val clean = code.trim()
        if (clean.isEmpty()) return OpnameScanOutcome.Error("Kode kosong")
        val asset = assetDao.getByCode(clean)
        val now = clock.millis()
        return db.withTransaction {
            if (asset != null) {
                val item = dao.findByAsset(sessionId, asset.id)
                when {
                    item == null -> {
                        // Aset terdaftar tetapi tidak ada di daftar sesi ini → dicatat sebagai di luar daftar.
                        dao.insertItem(
                            AuditItemEntity(
                                UUID.randomUUID().toString(), sessionId, asset.id, asset.code, AuditResult.TAK_TERDAFTAR, now, now, now,
                                condition = asset.condition,
                            ),
                        )
                        OpnameScanOutcome.NotInList(asset.code, asset.name)
                    }
                    item.result == AuditResult.TIDAK_DITEMUKAN -> {
                        dao.updateItem(item.id, AuditResult.DITEMUKAN, asset.condition, item.note, now, now)
                        OpnameScanOutcome.Found(asset.code, asset.name)
                    }
                    else -> OpnameScanOutcome.AlreadyScanned(asset.code, asset.name)
                }
            } else if (dao.findUnknownCode(sessionId, clean) != null) {
                OpnameScanOutcome.AlreadyScanned(clean, "Kode tidak dikenal")
            } else {
                dao.insertItem(AuditItemEntity(UUID.randomUUID().toString(), sessionId, null, clean, AuditResult.TAK_TERDAFTAR, now, now, now))
                OpnameScanOutcome.Unknown(clean)
            }
        }
    }

    override suspend fun updateItem(sessionId: String, itemId: String, found: Boolean, condition: AssetCondition?, note: String?): AppResult<Unit> {
        session.denyUnless { it.canEditData }?.let { return it }
        val (_, error) = openSession(sessionId)
        if (error != null) return error
        val item = dao.getItem(itemId)?.takeIf { it.sessionId == sessionId } ?: return AppResult.Failure("Aset tidak ada di sesi ini")
        val now = clock.millis()
        val cleanNote = note.trimToNull()
        when {
            // Hasil scan di luar daftar: hasil tetap, hanya kondisi & catatan.
            item.result == AuditResult.TAK_TERDAFTAR -> dao.updateItem(item.id, item.result, condition ?: item.condition, cleanNote, item.scannedAt, now)
            found -> {
                val cond = condition ?: item.assetId?.let { assetDao.getById(it)?.condition }
                dao.updateItem(item.id, AuditResult.DITEMUKAN, cond, cleanNote, item.scannedAt ?: now, now)
            }
            else -> dao.updateItem(item.id, AuditResult.TIDAK_DITEMUKAN, null, cleanNote, null, now)
        }
        return AppResult.Success(Unit)
    }

    override suspend fun finish(sessionId: String, note: String): AppResult<Unit> {
        session.denyUnless { it.canEditData }?.let { return it }
        val (s, error) = openSession(sessionId)
        if (error != null) return error
        s!!
        if (s.total == 0) return AppResult.Failure("Tambahkan aset ke sesi terlebih dahulu")
        dao.finish(sessionId, clock.millis(), note.trimToNull() ?: s.note)
        audit.log(
            AuditEntityType.ASSET, sessionId, AuditAction.UPDATE,
            "Stock opname \"${s.name}\" selesai: ${s.found}/${s.total} ditemukan, ${s.total - s.found} tidak ditemukan, ${s.outside} di luar daftar",
        )
        return AppResult.Success(Unit)
    }

    override suspend fun delete(sessionId: String): AppResult<Unit> {
        session.denyUnless { it.canManageMaster }?.let { return it }
        val s = dao.getSession(sessionId) ?: return AppResult.Failure("Sesi tidak ditemukan")
        db.withTransaction {
            dao.deleteItems(sessionId)
            dao.deleteSession(sessionId)
        }
        audit.log(AuditEntityType.ASSET, sessionId, AuditAction.DELETE, "Sesi stock opname \"${s.name}\" dihapus (${s.total} aset)")
        return AppResult.Success(Unit)
    }

    companion object {
        const val FIELD_NAME = "name"
        const val MAX_IMPORT_ROWS = 10_000
    }
}
