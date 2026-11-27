package id.perusahaan.asetga.data.repository

import androidx.room.withTransaction
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.dao.AssetDao
import id.perusahaan.asetga.data.local.dao.InsuranceDao
import id.perusahaan.asetga.data.local.dao.InsuranceTypeDao
import id.perusahaan.asetga.data.local.dao.InsurerDao
import id.perusahaan.asetga.data.local.dao.PolicyRow
import id.perusahaan.asetga.data.local.entity.InsuranceItemEntity
import id.perusahaan.asetga.data.local.entity.InsurancePolicyEntity
import id.perusahaan.asetga.domain.calc.PolicyCalc
import id.perusahaan.asetga.domain.excel.InsuranceImportTemplate
import id.perusahaan.asetga.domain.model.AddAssetsResult
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.InsuranceImportPreview
import id.perusahaan.asetga.domain.model.InsuranceImportRow
import id.perusahaan.asetga.domain.model.InsurancePolicy
import id.perusahaan.asetga.domain.model.InsuredAsset
import id.perusahaan.asetga.domain.model.PolicyInput
import id.perusahaan.asetga.domain.model.SyncState
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.repository.InsuranceRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.domain.validation.OperationValidator
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsuranceRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: InsuranceDao,
    private val typeDao: InsuranceTypeDao,
    private val insurerDao: InsurerDao,
    private val assetDao: AssetDao,
    private val session: SessionManager,
    private val audit: AuditLogRepository,
    private val clock: Clock,
) : InsuranceRepository {

    private fun today() = LocalDate.now(clock)

    private fun PolicyRow.toDomain(today: LocalDate = today()) = InsurancePolicy(
        id, policyNumber, typeId, typeName, insurerId, insurerName, startDate, endDate, premium, sumInsured, notes,
        itemCount, itemsSumInsured, itemsAcquisitionCost,
        PolicyCalc.status(startDate, endDate, today), PolicyCalc.daysLeft(endDate, today),
    )

    override fun observePolicies(): Flow<List<InsurancePolicy>> = dao.observePolicies().map { l -> l.map { it.toDomain() } }
    override fun observePolicy(id: String): Flow<InsurancePolicy?> = dao.observePolicy(id).map { it?.toDomain() }
    override fun observePoliciesForAsset(assetId: String): Flow<List<InsurancePolicy>> =
        dao.observePoliciesForAsset(assetId).map { l -> l.map { it.toDomain() } }

    override fun observeInsuredAssets(policyId: String): Flow<List<InsuredAsset>> = dao.observeItems(policyId).map { rows ->
        rows.map { InsuredAsset(it.assetId, it.code, it.qrCode, it.name, it.categoryName, it.locationName, it.status, it.acquisitionCost, it.sumInsured) }
    }

    override fun observeAssetIdsInPolicy(policyId: String): Flow<Set<String>> = dao.observeAssetIds(policyId).map { it.toSet() }

    override suspend fun getPolicyInput(id: String): PolicyInput? = dao.getPolicy(id)?.takeIf { it.deletedAt == null }?.let {
        PolicyInput(it.id, it.policyNumber, it.typeId, it.insurerId, it.startDate, it.endDate, it.premium, it.sumInsured, it.notes.orEmpty())
    }

    override suspend fun savePolicy(input: PolicyInput): AppResult<String> {
        session.denyUnless { it.canEditData }?.let { return it }
        val errors = OperationValidator.validatePolicy(input).toMutableMap()
        val number = input.policyNumber.trim()
        if (OperationValidator.FIELD_POLICY_NUMBER !in errors && dao.countByNumber(number, input.id ?: "") > 0) {
            errors[OperationValidator.FIELD_POLICY_NUMBER] = "Nomor polis sudah dipakai"
        }
        if (errors.isNotEmpty()) return AppResult.Failure("Periksa kembali isian", errors)
        val type = typeDao.getById(input.typeId!!) ?: return AppResult.Failure("Tipe asuransi tidak ditemukan")
        val insurer = insurerDao.getById(input.insurerId!!) ?: return AppResult.Failure("Perusahaan asuransi tidak ditemukan")

        val now = clock.millis()
        val old = input.id?.let { dao.getPolicy(it) }
        val entity = InsurancePolicyEntity(
            id = old?.id ?: UUID.randomUUID().toString(),
            policyNumber = number,
            typeId = type.id,
            insurerId = insurer.id,
            startDate = input.startDate!!,
            endDate = input.endDate!!,
            premium = input.premium,
            sumInsured = input.sumInsured,
            notes = input.notes.trimToNull(),
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
            syncState = SyncState.PENDING,
        )
        db.withTransaction {
            if (old == null) {
                dao.insertPolicy(entity)
                audit.log(AuditEntityType.INSURANCE, entity.id, AuditAction.CREATE, "Polis $number (${type.name}, ${insurer.name}) dibuat")
            } else {
                dao.updatePolicy(entity)
                val changes = ChangeSet().apply {
                    field("Nomor polis", old.policyNumber, entity.policyNumber)
                    field("Tipe", typeDao.getById(old.typeId)?.name, type.name)
                    field("Perusahaan", insurerDao.getById(old.insurerId)?.name, insurer.name)
                    field("Mulai", Fmt.date(old.startDate), Fmt.date(entity.startDate))
                    field("Berakhir", Fmt.date(old.endDate), Fmt.date(entity.endDate))
                    field("Premi", old.premium?.let(Fmt::rupiah), entity.premium?.let(Fmt::rupiah))
                    field("Nilai pertanggungan", old.sumInsured?.let(Fmt::rupiah), entity.sumInsured?.let(Fmt::rupiah))
                    field("Catatan", old.notes, entity.notes)
                }
                if (!changes.isEmpty()) audit.log(AuditEntityType.INSURANCE, entity.id, AuditAction.UPDATE, "Polis $number diubah", changes.build())
            }
        }
        return AppResult.Success(entity.id)
    }

    override suspend fun deletePolicy(id: String): AppResult<Unit> {
        session.denyUnless { it.canManageMaster }?.let { return AppResult.Failure("Hanya Admin GA yang dapat menghapus polis") }
        val p = dao.getPolicy(id) ?: return AppResult.Failure("Polis tidak ditemukan")
        // Hapus permanen: polis & keterkaitan asetnya. Aset sendiri tidak terhapus.
        db.withTransaction {
            dao.deleteAllItems(id)
            dao.deletePolicyRow(id)
            audit.log(AuditEntityType.INSURANCE, id, AuditAction.DELETE, "Polis ${p.policyNumber} dihapus permanen")
        }
        return AppResult.Success(Unit)
    }

    override suspend fun addAssets(policyId: String, assetIds: List<String>): AppResult<AddAssetsResult> {
        val eligible = assetIds.distinct().filter { id -> assetDao.getById(id)?.status?.let { it != AssetStatus.WRITE_OFF } == true }
        return addInternal(policyId, eligible.associateWith { null }, source = "manual")
    }

    /** Tambah aset (dengan nilai pertanggungan opsional). Aset yang sudah ada di polis tidak digandakan. */
    private suspend fun addInternal(policyId: String, items: Map<String, Long?>, source: String): AppResult<AddAssetsResult> {
        session.denyUnless { it.canEditData }?.let { return it }
        val policy = dao.getPolicy(policyId)?.takeIf { it.deletedAt == null } ?: return AppResult.Failure("Polis tidak ditemukan")
        if (items.isEmpty()) return AppResult.Success(AddAssetsResult(0, 0))
        val now = clock.millis()
        var added = 0
        db.withTransaction {
            items.entries.chunked(500).forEach { chunk ->
                val result = dao.insertItems(
                    chunk.map { (assetId, sum) -> InsuranceItemEntity(UUID.randomUUID().toString(), policyId, assetId, sum, now, now) },
                )
                added += result.count { it != -1L }
                // Untuk aset yang sudah ada, perbarui nilai pertanggungan bila diisi di file.
                chunk.zip(result).filter { (_, rowId) -> rowId == -1L }.forEach { (entry, _) ->
                    entry.value?.let { dao.updateItemSum(policyId, entry.key, it, now) }
                }
            }
            if (added > 0) {
                audit.log(AuditEntityType.INSURANCE, policyId, AuditAction.UPDATE, "$added aset ditambahkan ke polis ${policy.policyNumber} ($source)")
            }
        }
        return AppResult.Success(AddAssetsResult(added, items.size - added))
    }

    override suspend fun addAssetByCode(policyId: String, code: String): AppResult<String> {
        val asset = assetDao.getByCode(code.trim()) ?: return AppResult.Failure("Kode \"$code\" tidak terdaftar sebagai aset")
        if (asset.status == AssetStatus.WRITE_OFF) return AppResult.Failure("${asset.code} berstatus Write Off")
        return when (val r = addInternal(policyId, mapOf(asset.id to null), source = "scan")) {
            is AppResult.Success -> AppResult.Success(
                if (r.value.added > 0) "${asset.code} ${asset.name} ditambahkan" else "${asset.code} sudah ada di polis ini",
            )
            is AppResult.Failure -> r
        }
    }

    override suspend fun removeAsset(policyId: String, assetId: String): AppResult<Unit> {
        session.denyUnless { it.canEditData }?.let { return it }
        val policy = dao.getPolicy(policyId) ?: return AppResult.Failure("Polis tidak ditemukan")
        val asset = assetDao.getById(assetId)
        val removed = db.withTransaction {
            val n = dao.deleteItem(policyId, assetId)
            if (n > 0) audit.log(AuditEntityType.INSURANCE, policyId, AuditAction.UPDATE, "${asset?.code ?: assetId} dikeluarkan dari polis ${policy.policyNumber}")
            n
        }
        if (removed == 0) return AppResult.Failure("Aset tidak ada di polis")
        return AppResult.Success(Unit)
    }

    override suspend fun setSumInsured(policyId: String, assetId: String, value: Long?): AppResult<Unit> {
        session.denyUnless { it.canEditData }?.let { return it }
        if (value != null && value < 0) return AppResult.Failure("Nilai tidak valid")
        val old = dao.items(policyId).firstOrNull { it.assetId == assetId } ?: return AppResult.Failure("Aset tidak ada di polis")
        db.withTransaction {
            dao.updateItemSum(policyId, assetId, value, clock.millis())
            val policy = dao.getPolicy(policyId)
            audit.log(
                AuditEntityType.INSURANCE, policyId, AuditAction.UPDATE, "Nilai pertanggungan ${old.code} di polis ${policy?.policyNumber}",
                ChangeSet().apply { field("Nilai pertanggungan ${old.code}", old.sumInsured?.let(Fmt::rupiah), value?.let(Fmt::rupiah)) }.build(),
            )
        }
        return AppResult.Success(Unit)
    }

    override suspend fun previewImport(policyId: String, sheet: List<List<String>>): InsuranceImportPreview {
        val (rows, error) = InsuranceImportTemplate.parse(sheet)
        if (error != null) return InsuranceImportPreview(emptyList(), error)
        if (rows.size > MAX_IMPORT_ROWS) return InsuranceImportPreview(emptyList(), "Maksimal $MAX_IMPORT_ROWS baris per upload")
        val existing = dao.assetIds(policyId).toSet()
        val seenAssets = mutableSetOf<String>()
        return InsuranceImportPreview(
            rows.map { r ->
                if (r.errors.isNotEmpty()) return@map InsuranceImportRow(r.rowNumber, r.code, r.sumInsured, null, null, r.errors)
                // Cocokkan dengan kode aset ATAU kode QR.
                val asset = assetDao.getByCode(r.code)
                val errors = buildList {
                    when {
                        asset == null -> add("Kode \"${r.code}\" tidak terdaftar sebagai aset atau kode QR")
                        asset.status == AssetStatus.WRITE_OFF -> add("${asset.code} berstatus Write Off")
                        !seenAssets.add(asset.id) -> add("${asset.code} muncul lebih dari sekali (kode aset & kode QR)")
                    }
                }
                InsuranceImportRow(
                    r.rowNumber, r.code, r.sumInsured, asset?.id, asset?.let { "${it.code} · ${it.name}" }, errors,
                    alreadyInPolicy = asset != null && asset.id in existing,
                )
            },
        )
    }

    override suspend fun commitImport(policyId: String, preview: InsuranceImportPreview): AppResult<AddAssetsResult> =
        addInternal(policyId, preview.valid.associate { it.assetId!! to it.sumInsured }, source = "upload Excel")

    companion object {
        const val MAX_IMPORT_ROWS = 10_000
    }
}
