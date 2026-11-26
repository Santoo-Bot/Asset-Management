package id.perusahaan.asetga.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.dao.AssetDao
import id.perusahaan.asetga.data.local.dao.AssetDetailRow
import id.perusahaan.asetga.data.local.dao.AssetListRow
import id.perusahaan.asetga.data.local.dao.CategoryDao
import id.perusahaan.asetga.data.local.dao.DepartmentDao
import id.perusahaan.asetga.data.local.dao.LocationDao
import id.perusahaan.asetga.data.local.entity.AssetEntity
import id.perusahaan.asetga.domain.calc.AssetAgeCalc
import id.perusahaan.asetga.domain.calc.AssetCodeGenerator
import id.perusahaan.asetga.domain.calc.Depreciation
import id.perusahaan.asetga.domain.calc.WarrantyCalc
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.Asset
import id.perusahaan.asetga.domain.model.AssetDetail
import id.perusahaan.asetga.domain.model.AssetFilter
import id.perusahaan.asetga.domain.model.AssetInput
import id.perusahaan.asetga.domain.model.AssetListItem
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.SyncState
import id.perusahaan.asetga.domain.repository.AssetRepository
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.repository.SettingsRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.domain.validation.AssetValidator
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal fun AssetEntity.toDomain() = Asset(
    id, code, name, categoryId, brandModel, serialNumber, acquisitionDate, acquisitionCost, locationId, departmentId,
    picName, condition, status, warrantyUntil, notes, photoPath, disposalDate, disposalValue,
)

internal fun AssetListRow.toDomain() = AssetListItem(
    id, code, name, categoryName, locationName, picName, condition, status, photoPath, warrantyUntil, acquisitionCost,
)

@Singleton
class AssetRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: AssetDao,
    private val categoryDao: CategoryDao,
    private val locationDao: LocationDao,
    private val departmentDao: DepartmentDao,
    private val settings: SettingsRepository,
    private val session: SessionManager,
    private val audit: AuditLogRepository,
    private val clock: Clock,
) : AssetRepository {

    private fun today(): LocalDate = LocalDate.now(clock)

    override fun paging(filter: AssetFilter): Flow<PagingData<AssetListItem>> =
        Pager(PagingConfig(pageSize = 40, prefetchDistance = 20, enablePlaceholders = false)) {
            dao.paging(filter.query.trim(), filter.categoryId, filter.locationId, filter.condition?.name, filter.status?.name, filter.sort.name)
        }.flow.map { page -> page.map { it.toDomain() } }

    override fun count(filter: AssetFilter): Flow<Int> =
        dao.count(filter.query.trim(), filter.categoryId, filter.locationId, filter.condition?.name, filter.status?.name)

    override fun observeDetail(id: String): Flow<AssetDetail?> =
        combine(dao.observeDetail(id), settings.settings) { row, s -> row?.toDetail(s.defaultResidualPercent) }

    private fun AssetDetailRow.toDetail(defaultResidual: Double): AssetDetail {
        val a = asset.toDomain()
        // Aset yang sudah dihapus/dijual berhenti disusutkan pada tanggal penghapusan.
        val asOf = if (a.status == AssetStatus.DIHAPUS_DIJUAL) a.disposalDate ?: today() else today()
        val residual = categoryResidualPercent ?: defaultResidual
        return AssetDetail(
            asset = a,
            categoryName = categoryName,
            usefulLifeYears = usefulLifeYears,
            residualPercent = residual,
            locationName = locationName,
            departmentName = departmentName,
            age = AssetAgeCalc.of(a.acquisitionDate, asOf),
            depreciation = Depreciation.calculate(a.acquisitionCost, a.acquisitionDate, usefulLifeYears, residual, asOf),
            warrantyStatus = WarrantyCalc.status(a.warrantyUntil, today()),
        )
    }

    override suspend fun get(id: String): Asset? = dao.getById(id)?.toDomain()

    override suspend fun findByCode(code: String): Asset? = dao.getByCode(code.trim())?.toDomain()

    override suspend fun getListItems(ids: List<String>): List<AssetListItem> =
        ids.chunked(500).flatMap { dao.listItemsByIds(it) }.map { it.toDomain() }.sortedBy { it.code }

    override suspend fun suggestCode(categoryId: String, year: Int): String {
        val prefix = categoryDao.getById(categoryId)?.codePrefix ?: "AST"
        val seq = AssetCodeGenerator.nextSequence(prefix, year, dao.codesStartingWith("$prefix-$year-"))
        return AssetCodeGenerator.format(prefix, year, seq)
    }

    override suspend fun save(input: AssetInput): AppResult<Asset> {
        session.denyUnless { it.canEditData }?.let { return it }
        val errors = AssetValidator.validate(input, today()).toMutableMap()
        if (errors.isNotEmpty()) return AppResult.Failure("Periksa kembali isian", errors)

        val categoryId = input.categoryId!!
        val acqDate = input.acquisitionDate!!
        if (categoryDao.getById(categoryId) == null) return AppResult.Failure("Kategori tidak ditemukan")
        if (locationDao.getById(input.locationId!!) == null) return AppResult.Failure("Lokasi tidak ditemukan")
        if (input.departmentId != null && departmentDao.getById(input.departmentId) == null) return AppResult.Failure("Departemen tidak ditemukan")

        return db.withTransaction {
            val old = input.id?.let { dao.getById(it) }
            // Kode otomatis dibuat di dalam transaksi agar tidak bentrok; unique index tetap jadi pengaman terakhir.
            val code = AssetValidator.normalizeCode(input.code).ifEmpty {
                if (old != null) old.code else suggestCode(categoryId, acqDate.year)
            }
            if (dao.countByCode(code, old?.id ?: "") > 0) {
                return@withTransaction AppResult.Failure(
                    "Kode aset sudah dipakai",
                    mapOf(AssetValidator.FIELD_CODE to "Kode $code sudah dipakai aset lain"),
                )
            }
            val now = clock.millis()
            val disposed = input.status == AssetStatus.DIHAPUS_DIJUAL
            val entity = AssetEntity(
                id = old?.id ?: UUID.randomUUID().toString(),
                code = code,
                name = input.name.trim(),
                categoryId = categoryId,
                brandModel = input.brandModel.trimToNull(),
                serialNumber = input.serialNumber.trimToNull(),
                acquisitionDate = acqDate,
                acquisitionCost = input.acquisitionCost!!,
                locationId = input.locationId,
                departmentId = input.departmentId,
                picName = input.picName.trimToNull(),
                condition = input.condition,
                status = input.status,
                warrantyUntil = input.warrantyUntil,
                notes = input.notes.trimToNull(),
                photoPath = input.photoPath,
                disposalDate = if (disposed) input.disposalDate ?: today() else null,
                disposalValue = if (disposed) input.disposalValue else null,
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
                syncState = SyncState.PENDING,
            )
            if (old == null) {
                dao.insert(entity)
                audit.log(AuditEntityType.ASSET, entity.id, AuditAction.CREATE, "Aset ${entity.code} ${entity.name} ditambahkan")
            } else {
                dao.update(entity)
                val changes = ChangeSet().apply {
                    field("Kode", old.code, entity.code)
                    field("Nama", old.name, entity.name)
                    field("Kategori", categoryDao.getById(old.categoryId)?.name, categoryDao.getById(entity.categoryId)?.name)
                    field("Merk/Tipe", old.brandModel, entity.brandModel)
                    field("No. seri", old.serialNumber, entity.serialNumber)
                    field("Tgl perolehan", Fmt.date(old.acquisitionDate), Fmt.date(entity.acquisitionDate))
                    field("Harga perolehan", Fmt.rupiah(old.acquisitionCost), Fmt.rupiah(entity.acquisitionCost))
                    field("Lokasi", locationDao.getById(old.locationId)?.name, locationDao.getById(entity.locationId)?.name)
                    field("Departemen", old.departmentId?.let { departmentDao.getById(it)?.name }, entity.departmentId?.let { departmentDao.getById(it)?.name })
                    field("PIC", old.picName, entity.picName)
                    field("Kondisi", old.condition.label, entity.condition.label)
                    field("Status", old.status.label, entity.status.label)
                    field("Garansi s/d", old.warrantyUntil?.let(Fmt::date), entity.warrantyUntil?.let(Fmt::date))
                    field("Catatan", old.notes, entity.notes)
                    field("Foto", old.photoPath?.let { "ada" }, entity.photoPath?.let { if (it == old.photoPath) "ada" else "diganti" })
                }
                if (!changes.isEmpty()) {
                    audit.log(AuditEntityType.ASSET, entity.id, AuditAction.UPDATE, "Aset ${entity.code} diubah", changes.build())
                }
            }
            AppResult.Success(entity.toDomain())
        }
    }
}
