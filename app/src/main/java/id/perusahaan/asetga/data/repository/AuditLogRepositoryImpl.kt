package id.perusahaan.asetga.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import id.perusahaan.asetga.data.local.dao.AuditLogDao
import id.perusahaan.asetga.data.local.entity.AuditLogEntity
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.AuditLogEntry
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogRepositoryImpl @Inject constructor(
    private val dao: AuditLogDao,
    private val session: SessionManager,
    private val clock: Clock,
) : AuditLogRepository {

    override suspend fun log(
        entityType: AuditEntityType,
        entityId: String,
        action: AuditAction,
        summary: String,
        changes: Map<String, Pair<String?, String?>>,
    ) {
        val user = session.currentUser
        val json = changes.takeIf { it.isNotEmpty() }?.let { map ->
            JsonObject(
                map.mapValues { (_, v) ->
                    JsonArray(listOf<JsonElement>(v.first?.let { JsonPrimitive(it) } ?: JsonNull, v.second?.let { JsonPrimitive(it) } ?: JsonNull))
                },
            ).let { Json.encodeToString(JsonObject.serializer(), it) }
        }
        dao.insert(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                timestamp = clock.millis(),
                userId = user?.id,
                userName = user?.fullName ?: "Sistem",
                entityType = entityType,
                entityId = entityId,
                action = action,
                summary = summary,
                changesJson = json,
            ),
        )
    }

    override suspend fun logBatch(entityType: AuditEntityType, action: AuditAction, entries: List<Pair<String, String>>) {
        if (entries.isEmpty()) return
        val user = session.currentUser
        val now = clock.millis()
        entries.chunked(500).forEach { chunk ->
            dao.insertAll(
                chunk.map { (id, summary) ->
                    AuditLogEntity(UUID.randomUUID().toString(), now, user?.id, user?.fullName ?: "Sistem", entityType, id, action, summary)
                },
            )
        }
    }

    override fun paging(entityType: AuditEntityType?, query: String): Flow<PagingData<AuditLogEntry>> =
        Pager(PagingConfig(pageSize = 50, enablePlaceholders = false)) { dao.paging(entityType, query.trim()) }
            .flow.map { page -> page.map { it.toDomain() } }

    override fun observeForEntity(entityType: AuditEntityType, entityId: String): Flow<List<AuditLogEntry>> =
        dao.observeForEntity(entityType, entityId).map { list -> list.map { it.toDomain() } }
}

/** Bandingkan nilai lama & baru; hanya field yang berubah yang dicatat. */
internal class ChangeSet {
    private val changes = linkedMapOf<String, Pair<String?, String?>>()
    fun field(label: String, old: Any?, new: Any?) {
        val o = old?.toString()
        val n = new?.toString()
        if (o != n) changes[label] = o to n
    }
    fun build(): Map<String, Pair<String?, String?>> = changes
    fun isEmpty() = changes.isEmpty()
}
