package id.perusahaan.asetga.data.repository

import id.perusahaan.asetga.data.local.entity.AuditLogEntity
import id.perusahaan.asetga.data.local.entity.CategoryEntity
import id.perusahaan.asetga.data.local.entity.DepartmentEntity
import id.perusahaan.asetga.data.local.entity.DivisionEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import id.perusahaan.asetga.data.local.entity.UnitEntity
import id.perusahaan.asetga.data.local.entity.UserEntity
import id.perusahaan.asetga.data.local.entity.VendorEntity
import id.perusahaan.asetga.domain.model.AuditLogEntry
import id.perusahaan.asetga.domain.model.FieldChange
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import id.perusahaan.asetga.domain.model.Category
import id.perusahaan.asetga.domain.model.Department
import id.perusahaan.asetga.domain.model.Division
import id.perusahaan.asetga.domain.model.Location
import id.perusahaan.asetga.domain.model.MeasureUnit
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.model.Vendor

fun CategoryEntity.toDomain() = Category(id, name, codePrefix, usefulLifeYears, residualPercent, isActive)
fun LocationEntity.toDomain() = Location(id, name, address, isActive)
fun DepartmentEntity.toDomain(divisionName: String? = null) = Department(id, name, divisionId, isActive, divisionName)
fun DivisionEntity.toDomain() = Division(id, name, isActive)
fun VendorEntity.toDomain() = Vendor(id, name, phone, email, address, isActive)
fun UnitEntity.toDomain() = MeasureUnit(id, name, isActive)
fun UserEntity.toDomain() = User(id, username, fullName, role, hasPin = pinHash != null, isActive = isActive)
fun AuditLogEntity.toDomain() = AuditLogEntry(id, timestamp, userName, entityType, entityId, action, summary, parseChanges(changesJson))

/** changesJson berbentuk {"Field": ["lama", "baru"]}; data rusak diabaikan. */
internal fun parseChanges(json: String?): List<FieldChange> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        Json.parseToJsonElement(json).jsonObject.map { (field, value) ->
            val arr = value.jsonArray
            fun at(i: Int) = arr.getOrNull(i)?.let { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.content }
            FieldChange(field, at(0), at(1))
        }
    }.getOrDefault(emptyList())
}

internal fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
