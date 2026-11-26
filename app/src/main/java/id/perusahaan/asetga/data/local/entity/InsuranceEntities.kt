package id.perusahaan.asetga.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import id.perusahaan.asetga.domain.model.SyncState
import java.time.LocalDate

/** Master tipe asuransi, mis. All Risk, Kendaraan Bermotor, Kebakaran. (skema v6) */
@Entity(tableName = "insurance_types", indices = [Index(value = ["name"])])
data class InsuranceTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

/** Master perusahaan asuransi. (skema v6) */
@Entity(tableName = "insurers", indices = [Index(value = ["name"])])
data class InsurerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

/** Polis asuransi. Dihapus = soft delete (deletedAt). (skema v6) */
@Entity(
    tableName = "insurance_policies",
    foreignKeys = [
        ForeignKey(entity = InsuranceTypeEntity::class, parentColumns = ["id"], childColumns = ["typeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = InsurerEntity::class, parentColumns = ["id"], childColumns = ["insurerId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["policyNumber"]), Index(value = ["typeId"]), Index(value = ["insurerId"]), Index(value = ["endDate"])],
)
data class InsurancePolicyEntity(
    @PrimaryKey val id: String,
    val policyNumber: String,
    val typeId: String,
    val insurerId: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** Premi (Rp). */
    val premium: Long? = null,
    /** Nilai pertanggungan total polis (Rp), opsional. */
    val sumInsured: Long? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

/** Aset yang ditanggung sebuah polis. Satu aset hanya sekali per polis. (skema v6) */
@Entity(
    tableName = "insurance_items",
    foreignKeys = [
        ForeignKey(entity = InsurancePolicyEntity::class, parentColumns = ["id"], childColumns = ["policyId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["policyId", "assetId"], unique = true), Index(value = ["assetId"])],
)
data class InsuranceItemEntity(
    @PrimaryKey val id: String,
    val policyId: String,
    val assetId: String,
    /** Nilai pertanggungan untuk aset ini (Rp), opsional. */
    val sumInsured: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState = SyncState.PENDING,
)
