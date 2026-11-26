package id.perusahaan.asetga.domain.model

data class Category(
    val id: String,
    val name: String,
    val codePrefix: String,
    val usefulLifeYears: Int,
    /** Null = memakai nilai residu default dari Pengaturan. */
    val residualPercent: Double?,
    val isActive: Boolean = true,
)

data class Location(
    val id: String,
    val name: String,
    val address: String?,
    val isActive: Boolean = true,
)

data class Division(
    val id: String,
    val name: String,
    val isActive: Boolean = true,
)

/** Departemen berada di bawah satu Divisi. */
data class Department(
    val id: String,
    val name: String,
    val divisionId: String? = null,
    val isActive: Boolean = true,
    /** Diisi saat dibaca dari repository (untuk tampilan). */
    val divisionName: String? = null,
)

data class Vendor(
    val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val address: String?,
    val isActive: Boolean = true,
)

data class MeasureUnit(
    val id: String,
    val name: String,
    val isActive: Boolean = true,
)

data class User(
    val id: String,
    val username: String,
    val fullName: String,
    val role: Role,
    val hasPin: Boolean,
    val isActive: Boolean,
)

/** Satu field yang berubah: nilai lama → nilai baru (null = kosong). */
data class FieldChange(val field: String, val oldValue: String?, val newValue: String?)

data class AuditLogEntry(
    val id: String,
    val timestamp: Long,
    val userName: String,
    val entityType: AuditEntityType,
    val entityId: String,
    val action: AuditAction,
    val summary: String,
    val changes: List<FieldChange> = emptyList(),
)
