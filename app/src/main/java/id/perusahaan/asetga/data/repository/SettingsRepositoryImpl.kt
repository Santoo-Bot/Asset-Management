package id.perusahaan.asetga.data.repository

import id.perusahaan.asetga.data.local.SettingKeys
import id.perusahaan.asetga.data.local.dao.SettingsDao
import id.perusahaan.asetga.data.local.entity.AppSettingEntity
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.ThemeMode
import id.perusahaan.asetga.domain.repository.AppSettings
import id.perusahaan.asetga.domain.repository.SettingsRepository
import id.perusahaan.asetga.domain.validation.MasterValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dao: SettingsDao,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dao.observeAll()
        .map { rows -> parse(rows.associate { it.key to it.value }) }
        .distinctUntilChanged()

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun setThemeMode(mode: ThemeMode) = put(SettingKeys.THEME_MODE, mode.name)
    override suspend fun setAppLockEnabled(enabled: Boolean) = put(SettingKeys.APP_LOCK_ENABLED, enabled.toString())
    override suspend fun setLastUserId(id: String?) = put(SettingKeys.LAST_USER_ID, id ?: "")
    override suspend fun setReminderHour(hour: Int) = put(SettingKeys.REMINDER_HOUR, hour.coerceIn(0, 23).toString())
    override suspend fun setNotificationsEnabled(enabled: Boolean) = put(SettingKeys.NOTIFICATIONS_ENABLED, enabled.toString())

    override suspend fun setDefaultResidualPercent(percent: Double): AppResult<Unit> {
        MasterValidator.validateResidualPercent(percent)?.let { return AppResult.Failure(it) }
        put(SettingKeys.DEFAULT_RESIDUAL_PERCENT, percent.toString())
        return AppResult.Success(Unit)
    }

    private suspend fun put(key: String, value: String) = dao.put(AppSettingEntity(key, value))

    companion object {
        fun parse(map: Map<String, String>): AppSettings {
            val d = AppSettings()
            return AppSettings(
                themeMode = map[SettingKeys.THEME_MODE]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: d.themeMode,
                appLockEnabled = map[SettingKeys.APP_LOCK_ENABLED]?.toBooleanStrictOrNull() ?: d.appLockEnabled,
                lastUserId = map[SettingKeys.LAST_USER_ID]?.takeIf { it.isNotBlank() },
                defaultResidualPercent = map[SettingKeys.DEFAULT_RESIDUAL_PERCENT]?.toDoubleOrNull() ?: d.defaultResidualPercent,
                reminderHour = map[SettingKeys.REMINDER_HOUR]?.toIntOrNull() ?: d.reminderHour,
                notificationsEnabled = map[SettingKeys.NOTIFICATIONS_ENABLED]?.toBooleanStrictOrNull() ?: d.notificationsEnabled,
            )
        }
    }
}
