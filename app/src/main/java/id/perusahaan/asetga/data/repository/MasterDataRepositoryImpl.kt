package id.perusahaan.asetga.data.repository

import androidx.room.withTransaction
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.dao.AssetDao
import id.perusahaan.asetga.data.local.dao.CategoryDao
import id.perusahaan.asetga.data.local.dao.DepartmentDao
import id.perusahaan.asetga.data.local.dao.DivisionDao
import id.perusahaan.asetga.data.local.dao.InsuranceTypeDao
import id.perusahaan.asetga.data.local.dao.InsurerDao
import id.perusahaan.asetga.data.local.dao.LocationDao
import id.perusahaan.asetga.data.local.dao.MasterUsageDao
import id.perusahaan.asetga.data.local.dao.UnitDao
import id.perusahaan.asetga.data.local.dao.VendorDao
import id.perusahaan.asetga.data.local.entity.CategoryEntity
import id.perusahaan.asetga.data.local.entity.DepartmentEntity
import id.perusahaan.asetga.data.local.entity.DivisionEntity
import id.perusahaan.asetga.data.local.entity.InsuranceTypeEntity
import id.perusahaan.asetga.data.local.entity.InsurerEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import id.perusahaan.asetga.data.local.entity.UnitEntity
import id.perusahaan.asetga.data.local.entity.VendorEntity
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.Category
import id.perusahaan.asetga.domain.model.Department
import id.perusahaan.asetga.domain.model.Division
import id.perusahaan.asetga.domain.model.InsuranceType
import id.perusahaan.asetga.domain.model.Insurer
import id.perusahaan.asetga.domain.model.Location
import id.perusahaan.asetga.domain.model.MasterType
import id.perusahaan.asetga.domain.model.MeasureUnit
import id.perusahaan.asetga.domain.model.SyncState
import id.perusahaan.asetga.domain.model.Vendor
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.repository.MasterDataRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.domain.validation.MasterValidator
import id.perusahaan.asetga.domain.validation.MasterValidator.FIELD_NAME
import id.perusahaan.asetga.domain.validation.MasterValidator.FIELD_PREFIX
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MasterDataRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val assetDao: AssetDao,
    private val categoryDao: CategoryDao,
    private val locationDao: LocationDao,
    private val divisionDao: DivisionDao,
    private val departmentDao: DepartmentDao,
    private val vendorDao: VendorDao,
    private val unitDao: UnitDao,
    private val usageDao: MasterUsageDao,
    private val insuranceTypeDao: InsuranceTypeDao,
    private val insurerDao: InsurerDao,
    private val session: SessionManager,
    private val audit: AuditLogRepository,
    private val clock: Clock,
) : MasterDataRepository {

    override fun observeCategories(includeInactive: Boolean): Flow<List<Category>> =
        categoryDao.observeAll(includeInactive).map { l -> l.map { it.toDomain() } }

    override fun observeLocations(includeInactive: Boolean): Flow<List<Location>> =
        locationDao.observeAll(includeInactive).map { l -> l.map { it.toDomain() } }

    override fun observeDivisions(includeInactive: Boolean): Flow<List<Division>> =
        divisionDao.observeAll(includeInactive).map { l -> l.map { it.toDomain() } }

    /** Departemen beserta nama divisinya. */
    override fun observeDepartments(includeInactive: Boolean): Flow<List<Department>> =
        combine(departmentDao.observeAll(includeInactive), divisionDao.observeAll(includeInactive = true)) { deps, divs ->
            val names = divs.associate { it.id to it.name }
            deps.map { d -> d.toDomain(d.divisionId?.let(names::get)) }
        }

    override fun observeInsuranceTypes(includeInactive: Boolean): Flow<List<InsuranceType>> =
        insuranceTypeDao.observeAll(includeInactive).map { l -> l.map { InsuranceType(it.id, it.name, it.isActive) } }

    override fun observeInsurers(includeInactive: Boolean): Flow<List<Insurer>> =
        insurerDao.observeAll(includeInactive).map { l -> l.map { Insurer(it.id, it.name, it.phone, it.email, it.address, it.isActive) } }

    override suspend fun saveInsuranceType(type: InsuranceType): AppResult<InsuranceType> {
        session.denyUnless { it.canManageMaster }?.let { return it }
        val name = type.name.trim()
        nameErrors(name, insuranceTypeDao.countByName(name, type.id), "tipe asuransi")?.let { return it }
        val now = clock.millis()
        val old = type.id.takeIf { it.isNotEmpty() }?.let { insuranceTypeDao.getById(it) }
        return if (old == null) {
            val e = InsuranceTypeEntity(newId(), name, true, now, now)
            insuranceTypeDao.insert(e)
            audit.log(AuditEntityType.MASTER, e.id, AuditAction.CREATE, "Tipe asuransi ${e.name} ditambahkan")
            AppResult.Success(InsuranceType(e.id, e.name))
        } else {
            val e = old.copy(name = name, updatedAt = now, syncState = SyncState.PENDING)
            insuranceTypeDao.update(e)
            logUpdate("Tipe asuransi", e.id, e.name) { field("Nama", old.name, e.name) }
            AppResult.Success(InsuranceType(e.id, e.name, e.isActive))
        }
    }

    override suspend fun saveInsurer(insurer: Insurer): AppResult<Insurer> {
        session.denyUnless { it.canManageMaster }?.let { return it }
        val v = insurer.copy(name = insurer.name.trim(), phone = insurer.phone.trimToNull(), email = insurer.email.trimToNull(), address = insurer.address.trimToNull())
        // Validasi kontak sama dengan vendor.
        val errors = MasterValidator.validateVendor(Vendor("", v.name, v.phone, v.email, v.address)).toMutableMap()
        if (FIELD_NAME !in errors && insurerDao.countByName(v.name, v.id) > 0) errors[FIELD_NAME] = "Nama perusahaan asuransi sudah ada"
        if (errors.isNotEmpty()) return invalid(errors)
        val now = clock.millis()
        val old = v.id.takeIf { it.isNotEmpty() }?.let { insurerDao.getById(it) }
        return if (old == null) {
            val e = InsurerEntity(newId(), v.name, v.phone, v.email, v.address, true, now, now)
            insurerDao.insert(e)
            audit.log(AuditEntityType.MASTER, e.id, AuditAction.CREATE, "Perusahaan asuransi ${e.name} ditambahkan")
            AppResult.Success(v.copy(id = e.id))
        } else {
            val e = old.copy(name = v.name, phone = v.phone, email = v.email, address = v.address, updatedAt = now, syncState = SyncState.PENDING)
            insurerDao.update(e)
            logUpdate("Perusahaan asuransi", e.id, e.name) {
                field("Nama", old.name, e.name)
                field("Telepon", old.phone, e.phone)
                field("Email", old.email, e.email)
                field("Alamat", old.address, e.address)
            }
            AppResult.Success(v)
        }
    }

    override suspend fun saveDivision(division: Division): AppResult<Division> {
        session.denyUnless { it.canManageMaster }?.let { return it }
        val name = division.name.trim()
        nameErrors(name, divisionDao.countByName(name, division.id), "divisi")?.let { return it }
        val now = clock.millis()
        val old = division.id.takeIf { it.isNotEmpty() }?.let { divisionDao.getById(it) }
        return if (old == null) {
            val e = DivisionEntity(newId(), name, true, now, now)
            divisionDao.insert(e)
            audit.log(AuditEntityType.MASTER, e.id, AuditAction.CREATE, "Divisi ${e.name} ditambahkan")
            AppResult.Success(e.toDomain())
        } else {
            val e = old.copy(name = name, updatedAt = now, syncState = SyncState.PENDING)
            divisionDao.update(e)
            logUpdate("Divisi", e.id, e.name) { field("Nama", old.name, e.name) }
            AppResult.Success(e.toDomain())
        }
    }

    override fun observeVendors(includeInactive: Boolean): Flow<List<Vendor>> =
        vendorDao.observeAll(includeInactive).map { l -> l.map { it.toDomain() } }

    override fun observeUnits(includeInactive: Boolean): Flow<List<MeasureUnit>> =
        unitDao.observeAll(includeInactive).map { l -> l.map { it.toDomain() } }

    override suspend fun saveCategory(category: Category): AppResult<Category> {
        session.denyUnless { it.canManageMaster }?.let { return it }
        val c = category.copy(name = category.name.trim(), codePrefix = category.codePrefix.trim().uppercase())
        val errors = MasterValidator.validateCategory(c).toMutableMap()
        if (FIELD_NAME !in errors && categoryDao.countByName(c.name, c.id) > 0) errors[FIELD_NAME] = "Nama kategori sudah ada"
        if (FIELD_PREFIX !in errors && categoryDao.countByPrefix(c.codePrefix, c.id) > 0) errors[FIELD_PREFIX] = "Kode sudah dipakai kategori lain"
        if (errors.isNotEmpty()) return invalid(errors)

        val now = clock.millis()
        val old = c.id.takeIf { it.isNotEmpty() }?.let { categoryDao.getById(it) }
        return if (old == null) {
            val entity = CategoryEntity(newId(), c.name, c.codePrefix, c.usefulLifeYears, c.residualPercent, true, now, now)
            categoryDao.insert(entity)
            audit.log(AuditEntityType.MASTER, entity.id, AuditAction.CREATE, "Kategori ${entity.name} ditambahkan")
            AppResult.Success(entity.toDomain())
        } else {
            val entity = old.copy(
                name = c.name, codePrefix = c.codePrefix, usefulLifeYears = c.usefulLifeYears,
                residualPercent = c.residualPercent, updatedAt = now, syncState = SyncState.PENDING,
            )
            categoryDao.update(entity)
            logUpdate("Kategori", entity.id, entity.name) {
                field("Nama", old.name, entity.name)
                field("Kode", old.codePrefix, entity.codePrefix)
                field("Umur ekonomis (th)", old.usefulLifeYears, entity.usefulLifeYears)
                field("Residu (%)", old.residualPercent, entity.residualPercent)
            }
            AppResult.Success(entity.toDomain())
        }
    }

    override suspend fun saveLocation(location: Location): AppResult<Location> {
        session.denyUnless { it.canManageMaster }?.let { return it }
        val name = location.name.trim()
        nameErrors(name, locationDao.countByName(name, location.id), "lokasi")?.let { return it }
        val now = clock.millis()
        val old = location.id.takeIf { it.isNotEmpty() }?.let { locationDao.getById(it) }
        return if (old == null) {
            val e = LocationEntity(newId(), name, location.address.trimToNull(), true, now, now)
            locationDao.insert(e)
            audit.log(AuditEntityType.MASTER, e.id, AuditAction.CREATE, "Lokasi ${e.name} ditambahkan")
            AppResult.Success(e.toDomain())
        } else {
            val e = old.copy(name = name, address = location.address.trimToNull(), updatedAt = now, syncState = SyncState.PENDING)
            locationDao.update(e)
            logUpdate("Lokasi", e.id, e.name) {
                field("Nama", old.name, e.name)
                field("Alamat", old.address, e.address)
            }
            AppResult.Success(e.toDomain())
        }
    }

    override suspend fun saveDepartment(department: Department): AppResult<Department> {
        session.denyUnless { it.canManageMaster }?.let { return it }
        val name = department.name.trim()
        val errors = mutableMapOf<String, String>()
        (MasterValidator.validateName(name) ?: if (departmentDao.countByName(name, department.id) > 0) "Nama departemen sudah ada" else null)
            ?.let { errors[FIELD_NAME] = it }
        val division = department.divisionId?.let { divisionDao.getById(it) }
        if (division == null) errors[MasterValidator.FIELD_DIVISION] = "Pilih divisi induk"
        if (errors.isNotEmpty()) return invalid(errors)

        val now = clock.millis()
        val old = department.id.takeIf { it.isNotEmpty() }?.let { departmentDao.getById(it) }
        return if (old == null) {
            val e = DepartmentEntity(newId(), name, true, now, now, divisionId = division!!.id)
            departmentDao.insert(e)
            audit.log(AuditEntityType.MASTER, e.id, AuditAction.CREATE, "Departemen ${e.name} (Divisi ${division.name}) ditambahkan")
            AppResult.Success(e.toDomain(division.name))
        } else {
            val e = old.copy(name = name, divisionId = division!!.id, updatedAt = now, syncState = SyncState.PENDING)
            val oldDivisionName = old.divisionId?.let { divisionDao.getById(it)?.name }
            db.withTransaction {
                departmentDao.update(e)
                // Aset di departemen ini ikut pindah divisi agar data tetap konsisten.
                if (old.divisionId != e.divisionId) assetDao.syncDivisionForDepartment(e.id, e.divisionId, now)
            }
            logUpdate("Departemen", e.id, e.name) {
                field("Nama", old.name, e.name)
                field("Divisi", oldDivisionName, division.name)
            }
            AppResult.Success(e.toDomain(division.name))
        }
    }

    override suspend fun saveVendor(vendor: Vendor): AppResult<Vendor> {
        session.denyUnless { it.canManageMaster }?.let { return it }
        val v = vendor.copy(name = vendor.name.trim(), phone = vendor.phone.trimToNull(), email = vendor.email.trimToNull(), address = vendor.address.trimToNull())
        val errors = MasterValidator.validateVendor(v).toMutableMap()
        if (FIELD_NAME !in errors && vendorDao.countByName(v.name, v.id) > 0) errors[FIELD_NAME] = "Nama vendor sudah ada"
        if (errors.isNotEmpty()) return invalid(errors)
        val now = clock.millis()
        val old = v.id.takeIf { it.isNotEmpty() }?.let { vendorDao.getById(it) }
        return if (old == null) {
            val e = VendorEntity(newId(), v.name, v.phone, v.email, v.address, true, now, now)
            vendorDao.insert(e)
            audit.log(AuditEntityType.MASTER, e.id, AuditAction.CREATE, "Vendor ${e.name} ditambahkan")
            AppResult.Success(e.toDomain())
        } else {
            val e = old.copy(name = v.name, phone = v.phone, email = v.email, address = v.address, updatedAt = now, syncState = SyncState.PENDING)
            vendorDao.update(e)
            logUpdate("Vendor", e.id, e.name) {
                field("Nama", old.name, e.name)
                field("Telepon", old.phone, e.phone)
                field("Email", old.email, e.email)
                field("Alamat", old.address, e.address)
            }
            AppResult.Success(e.toDomain())
        }
    }

    override suspend fun saveUnit(unit: MeasureUnit): AppResult<MeasureUnit> {
        session.denyUnless { it.canManageMaster }?.let { return it }
        val name = unit.name.trim()
        nameErrors(name, unitDao.countByName(name, unit.id), "satuan")?.let { return it }
        val now = clock.millis()
        val old = unit.id.takeIf { it.isNotEmpty() }?.let { unitDao.getById(it) }
        return if (old == null) {
            val e = UnitEntity(newId(), name, true, now, now)
            unitDao.insert(e)
            audit.log(AuditEntityType.MASTER, e.id, AuditAction.CREATE, "Satuan ${e.name} ditambahkan")
            AppResult.Success(e.toDomain())
        } else {
            val e = old.copy(name = name, updatedAt = now, syncState = SyncState.PENDING)
            unitDao.update(e)
            logUpdate("Satuan", e.id, e.name) { field("Nama", old.name, e.name) }
            AppResult.Success(e.toDomain())
        }
    }

    override suspend fun setActive(type: MasterType, id: String, active: Boolean): AppResult<Unit> {
        session.denyUnless { it.canManageMaster }?.let { return it }
        val now = clock.millis()
        val name: String? = when (type) {
            MasterType.CATEGORY -> categoryDao.getById(id)?.name?.also { categoryDao.setActive(id, active, now) }
            MasterType.LOCATION -> locationDao.getById(id)?.name?.also { locationDao.setActive(id, active, now) }
            MasterType.DIVISION -> {
                if (!active && divisionDao.countActiveDepartments(id) > 0) {
                    return AppResult.Failure("Nonaktifkan atau pindahkan dulu departemen di bawah divisi ini")
                }
                divisionDao.getById(id)?.name?.also { divisionDao.setActive(id, active, now) }
            }
            MasterType.DEPARTMENT -> departmentDao.getById(id)?.name?.also { departmentDao.setActive(id, active, now) }
            MasterType.VENDOR -> vendorDao.getById(id)?.name?.also { vendorDao.setActive(id, active, now) }
            MasterType.UNIT -> unitDao.getById(id)?.name?.also { unitDao.setActive(id, active, now) }
            MasterType.INSURANCE_TYPE -> insuranceTypeDao.getById(id)?.name?.also { insuranceTypeDao.setActive(id, active, now) }
            MasterType.INSURER -> insurerDao.getById(id)?.name?.also { insurerDao.setActive(id, active, now) }
        }
        if (name == null) return AppResult.Failure("Data tidak ditemukan")
        audit.log(
            AuditEntityType.MASTER, id,
            if (active) AuditAction.RESTORE else AuditAction.DEACTIVATE,
            "${type.label} $name ${if (active) "diaktifkan kembali" else "dinonaktifkan"}",
        )
        return AppResult.Success(Unit)
    }

    override suspend fun delete(type: MasterType, id: String): AppResult<Unit> {
        session.denyUnless { it.canManageMaster }?.let { return it }
        val name: String = when (type) {
            MasterType.CATEGORY -> categoryDao.getById(id)?.name
            MasterType.LOCATION -> locationDao.getById(id)?.name
            MasterType.DIVISION -> divisionDao.getById(id)?.name
            MasterType.DEPARTMENT -> departmentDao.getById(id)?.name
            MasterType.VENDOR -> vendorDao.getById(id)?.name
            MasterType.UNIT -> unitDao.getById(id)?.name
            MasterType.INSURANCE_TYPE -> insuranceTypeDao.getById(id)?.name
            MasterType.INSURER -> insurerDao.getById(id)?.name
        } ?: return AppResult.Failure("Data tidak ditemukan")

        val usage = usageOf(type, id)
        if (usage.isNotEmpty()) {
            val hint = when (type) {
                MasterType.DIVISION -> "Pindahkan departemen & aset ke divisi lain dulu."
                MasterType.CATEGORY, MasterType.LOCATION, MasterType.DEPARTMENT -> "Pindahkan dulu ke ${type.singular} lain (ubah aset / mutasi)."
                MasterType.VENDOR -> "Ubah vendor pada catatan servis tersebut dulu."
                MasterType.UNIT -> "Ubah satuan barang tersebut dulu."
                MasterType.INSURANCE_TYPE, MasterType.INSURER -> "Ubah atau hapus polis tersebut dulu."
            }
            return AppResult.Failure("$name tidak bisa dihapus karena dipakai ${usage.joinToString(", ")}. $hint")
        }

        db.withTransaction {
            when (type) {
                MasterType.CATEGORY -> usageDao.deleteCategory(id)
                MasterType.LOCATION -> usageDao.deleteLocation(id)
                MasterType.DIVISION -> usageDao.deleteDivision(id)
                MasterType.DEPARTMENT -> usageDao.deleteDepartment(id)
                MasterType.VENDOR -> usageDao.deleteVendor(id)
                MasterType.UNIT -> usageDao.deleteUnit(id)
                MasterType.INSURANCE_TYPE -> insuranceTypeDao.delete(id)
                MasterType.INSURER -> insurerDao.delete(id)
            }
            audit.log(AuditEntityType.MASTER, id, AuditAction.DELETE, "${type.label} $name dihapus")
        }
        return AppResult.Success(Unit)
    }

    /** Daftar pemakaian, mis. ["12 aset", "2 riwayat mutasi"]. Kosong = aman dihapus. */
    private suspend fun usageOf(type: MasterType, id: String): List<String> {
        fun MutableList<String>.addIfUsed(count: Int, label: String) { if (count > 0) add("$count $label") }
        return buildList {
            when (type) {
                MasterType.CATEGORY -> addIfUsed(usageDao.assetsInCategory(id), "aset")
                MasterType.LOCATION -> {
                    addIfUsed(usageDao.assetsInLocation(id), "aset")
                    addIfUsed(usageDao.itemsInLocation(id), "barang stok")
                    addIfUsed(usageDao.mutationsWithLocation(id), "riwayat mutasi")
                    addIfUsed(usageDao.opnameInLocation(id), "sesi stock opname")
                }
                MasterType.DIVISION -> {
                    addIfUsed(usageDao.departmentsInDivision(id), "departemen")
                    addIfUsed(usageDao.assetsInDivision(id), "aset")
                }
                MasterType.DEPARTMENT -> {
                    addIfUsed(usageDao.assetsInDepartment(id), "aset")
                    addIfUsed(usageDao.mutationsWithDepartment(id), "riwayat mutasi")
                }
                MasterType.VENDOR -> addIfUsed(usageDao.servicesWithVendor(id), "catatan servis")
                MasterType.UNIT -> addIfUsed(usageDao.itemsWithUnit(id), "barang stok")
                MasterType.INSURANCE_TYPE -> addIfUsed(insuranceTypeDao.usage(id), "polis")
                MasterType.INSURER -> addIfUsed(insurerDao.usage(id), "polis")
            }
        }
    }

    private fun nameErrors(name: String, duplicates: Int, label: String): AppResult.Failure? {
        val err = MasterValidator.validateName(name) ?: if (duplicates > 0) "Nama $label sudah ada" else null
        return err?.let { invalid(mapOf(FIELD_NAME to it)) }
    }

    private fun invalid(errors: Map<String, String>) = AppResult.Failure("Periksa kembali isian", errors)

    private suspend fun logUpdate(label: String, id: String, name: String, block: ChangeSet.() -> Unit) {
        val changes = ChangeSet().apply(block)
        if (!changes.isEmpty()) audit.log(AuditEntityType.MASTER, id, AuditAction.UPDATE, "$label $name diubah", changes.build())
    }

    private fun newId() = UUID.randomUUID().toString()
}
