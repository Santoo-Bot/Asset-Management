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
fun AuditLogEntity.toDomain() = AuditLogEntry(id, timestamp, userName, entityType, entityId, action, summary)

internal fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
