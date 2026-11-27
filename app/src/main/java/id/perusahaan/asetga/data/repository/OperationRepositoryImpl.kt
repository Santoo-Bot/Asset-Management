package id.perusahaan.asetga.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.dao.AssetDao
import id.perusahaan.asetga.data.local.dao.DepartmentDao
import id.perusahaan.asetga.data.local.dao.LocationDao
import id.perusahaan.asetga.data.local.dao.MutationDao
import id.perusahaan.asetga.data.local.dao.MutationRow
import id.perusahaan.asetga.data.local.dao.ServiceDao
import id.perusahaan.asetga.data.local.dao.ServiceRow
import id.perusahaan.asetga.data.local.dao.VendorDao
import id.perusahaan.asetga.data.local.entity.AssetMutationEntity
import id.perusahaan.asetga.data.local.entity.ServiceRecordEntity
import id.perusahaan.asetga.domain.calc.ServiceDueCalc
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetMutation
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.MutationInput
import id.perusahaan.asetga.domain.model.ServiceInput
import id.perusahaan.asetga.domain.model.ServiceRecord
import id.perusahaan.asetga.domain.model.ServiceSchedule
import id.perusahaan.asetga.domain.model.SyncState
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.repository.OperationRepository
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

internal fun MutationRow.toDomain() = AssetMutation(
    id, assetId, mutationDate, fromLocation, toLocation, fromDepartment, toDepartment, fromPic, toPic, reason, approvedBy, createdBy,
    fromUserLocation, toUserLocation,
)

internal fun ServiceRow.toDomain() = ServiceRecord(
    id, assetId, assetCode, assetName, serviceDate, type, vendorId, vendorName, cost, description, photoPath, nextServiceDate,
)

@Singleton
class OperationRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val assetDao: AssetDao,
    private val mutationDao: MutationDao,
    private val serviceDao: ServiceDao,
    private val locationDao: LocationDao,
    private val departmentDao: DepartmentDao,
    private val vendorDao: VendorDao,
    private val session: SessionManager,
    private val audit: AuditLogRepository,
    private val clock: Clock,
) : OperationRepository {

    private fun today() = LocalDate.now(clock)

    override fun observeMutations(assetId: String): Flow<List<AssetMutation>> =
        mutationDao.observeForAsset(assetId).map { list -> list.map { it.toDomain() } }

    override suspend fun recordMutation(input: MutationInput): AppResult<Unit> {
        session.denyUnless { it.canEditData }?.let { return it }
        return db.withTransaction {
            val asset = assetDao.getById(input.assetId) ?: return@withTransaction AppResult.Failure("Aset tidak ditemukan")
            val errors = OperationValidator.validateMutation(input, asset.locationId, asset.userLocationDetail, asset.acquisitionDate, today())
            if (errors.isNotEmpty()) return@withTransaction AppResult.Failure("Periksa kembali isian", errors)
            val toLocation = input.toLocationId!!
            val toLocationEntity = locationDao.getById(toLocation) ?: return@withTransaction AppResult.Failure("Lokasi tujuan tidak ditemukan")

            val now = clock.millis()
            val toUser = input.toUserLocation.trimToNull()
            mutationDao.insert(
                AssetMutationEntity(
                    id = UUID.randomUUID().toString(),
                    assetId = asset.id,
                    mutationDate = input.date!!,
                    fromLocationId = asset.locationId,
                    toLocationId = toLocation,
                    // Mutasi hanya mencatat lokasi & pengguna; departemen/PIC tidak lagi disimpan.
                    fromDepartmentId = null,
                    toDepartmentId = null,
                    fromPic = null,
                    toPic = null,
                    reason = "",
                    approvedBy = null,
                    createdByUserId = session.currentUser?.id,
                    createdAt = now,
                    updatedAt = now,
                    fromUserLocation = asset.userLocationDetail,
                    toUserLocation = toUser,
                ),
            )
            // Data aset ikut diperbarui saat mutasi disimpan.
            assetDao.update(asset.copy(locationId = toLocation, userLocationDetail = toUser, updatedAt = now, syncState = SyncState.PENDING))
            val changes = ChangeSet().apply {
                field("Lokasi", locationDao.getById(asset.locationId)?.name, toLocationEntity.name)
                field("Pengguna / detail lokasi", asset.userLocationDetail, toUser)
            }
            audit.log(AuditEntityType.ASSET, asset.id, AuditAction.UPDATE, "Mutasi aset ${asset.code}", changes.build())
            AppResult.Success(Unit)
        }
    }

    override fun observeServices(assetId: String): Flow<List<ServiceRecord>> =
        serviceDao.observeForAsset(assetId).map { list -> list.map { it.toDomain() } }

    override fun pagingServices(query: String): Flow<PagingData<ServiceRecord>> =
        Pager(PagingConfig(pageSize = 40, enablePlaceholders = false)) { serviceDao.paging(query.trim()) }
            .flow.map { page -> page.map { it.toDomain() } }

    override fun observeSchedules(): Flow<List<ServiceSchedule>> = serviceDao.observeSchedules().map { rows ->
        val today = today()
        rows.map { ServiceSchedule(it.assetId, it.assetCode, it.assetName, it.lastServiceDate, it.nextServiceDate, ServiceDueCalc.status(it.nextServiceDate, today)) }
    }

    override suspend fun getService(id: String): ServiceRecord? = serviceDao.getRow(id)?.toDomain()

    override suspend fun saveService(input: ServiceInput): AppResult<Unit> {
        session.denyUnless { it.canEditData }?.let { return it }
        val errors = OperationValidator.validateService(input, today())
        if (errors.isNotEmpty()) return AppResult.Failure("Periksa kembali isian", errors)
        val asset = assetDao.getById(input.assetId) ?: return AppResult.Failure("Aset tidak ditemukan")
        if (input.vendorId != null && vendorDao.getById(input.vendorId) == null) return AppResult.Failure("Vendor tidak ditemukan")

        val now = clock.millis()
        val old = input.id?.let { serviceDao.getById(it) }
        val entity = ServiceRecordEntity(
            id = old?.id ?: UUID.randomUUID().toString(),
            assetId = asset.id,
            serviceDate = input.serviceDate!!,
            type = input.type,
            vendorId = input.vendorId,
            cost = input.cost ?: 0,
            description = input.description.trim(),
            photoPath = input.photoPath,
            nextServiceDate = input.nextServiceDate,
            createdByUserId = old?.createdByUserId ?: session.currentUser?.id,
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
        )
        db.withTransaction {
            if (old == null) {
                serviceDao.insert(entity)
                audit.log(
                    AuditEntityType.SERVICE, entity.id, AuditAction.CREATE,
                    "Servis ${input.type.label} ${asset.code} ${Fmt.date(entity.serviceDate)} · ${Fmt.rupiah(entity.cost)}",
                )
            } else {
                serviceDao.update(entity)
                val changes = ChangeSet().apply {
                    field("Tanggal", Fmt.date(old.serviceDate), Fmt.date(entity.serviceDate))
                    field("Jenis", old.type.label, entity.type.label)
                    field("Biaya", Fmt.rupiah(old.cost), Fmt.rupiah(entity.cost))
                    field("Deskripsi", old.description, entity.description)
                    field("Jadwal berikutnya", old.nextServiceDate?.let(Fmt::date), entity.nextServiceDate?.let(Fmt::date))
                }
                audit.log(AuditEntityType.SERVICE, entity.id, AuditAction.UPDATE, "Catatan servis ${asset.code} diubah", changes.build())
            }
            // Tampilkan juga di riwayat aset.
            audit.log(AuditEntityType.ASSET, asset.id, if (old == null) AuditAction.CREATE else AuditAction.UPDATE, "Servis ${input.type.label}: ${entity.description.take(60)}")
        }
        return AppResult.Success(Unit)
    }
}
