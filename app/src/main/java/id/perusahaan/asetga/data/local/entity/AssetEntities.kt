package id.perusahaan.asetga.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.model.ServiceType
import id.perusahaan.asetga.domain.model.SyncState
import java.time.LocalDate

@Entity(
    tableName = "assets",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = LocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = DepartmentEntity::class, parentColumns = ["id"], childColumns = ["departmentId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["name"]),
        Index(value = ["categoryId"]),
        Index(value = ["locationId"]),
        Index(value = ["departmentId"]),
        Index(value = ["condition"]),
        Index(value = ["status"]),
        Index(value = ["serialNumber"]),
        Index(value = ["qrCode"], unique = true),
    ],
)
data class AssetEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val categoryId: String,
    val brandModel: String? = null,
    val serialNumber: String? = null,
    val acquisitionDate: LocalDate,
    /** Rupiah utuh. */
    val acquisitionCost: Long,
    val locationId: String,
    val departmentId: String? = null,
    val picName: String? = null,
    val condition: AssetCondition,
    val status: AssetStatus,
    val warrantyUntil: LocalDate? = null,
    val notes: String? = null,
    /** Path relatif terhadap filesDir. */
    val photoPath: String? = null,
    val disposalDate: LocalDate? = null,
    val disposalValue: Long? = null,
    // ---- Ditambahkan di skema versi 2 ----
    /** Isi label QR bila berbeda dari kode aset; null = QR memakai kode aset. */
    val qrCode: String? = null,
    /** Pengguna / detail lokasi, mis. "Budi · Ruang 301". */
    val userLocationDetail: String? = null,
    val division: String? = null,
    val prNumber: String? = null,
    val poNumber: String? = null,
    val grNumber: String? = null,
    val requester: String? = null,
    val memoNumber: String? = null,
    val memoDate: LocalDate? = null,
    val followUp: String? = null,
    /** Nomor / keterangan Berita Acara Serah Terima. */
    val handoverDoc: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

@Entity(
    tableName = "asset_mutations",
    foreignKeys = [
        ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["assetId"]), Index(value = ["mutationDate"])],
)
data class AssetMutationEntity(
    @PrimaryKey val id: String,
    val assetId: String,
    val mutationDate: LocalDate,
    val fromLocationId: String?,
    val toLocationId: String?,
    val fromDepartmentId: String?,
    val toDepartmentId: String?,
    val fromPic: String?,
    val toPic: String?,
    val reason: String,
    val approvedBy: String?,
    val createdByUserId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

@Entity(
    tableName = "service_records",
    foreignKeys = [
        ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = VendorEntity::class, parentColumns = ["id"], childColumns = ["vendorId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["assetId"]), Index(value = ["vendorId"]), Index(value = ["serviceDate"]), Index(value = ["nextServiceDate"])],
)
data class ServiceRecordEntity(
    @PrimaryKey val id: String,
    val assetId: String,
    val serviceDate: LocalDate,
    val type: ServiceType,
    val vendorId: String?,
    val cost: Long,
    val description: String,
    val photoPath: String?,
    val nextServiceDate: LocalDate?,
    val createdByUserId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)
