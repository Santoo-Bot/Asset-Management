package id.perusahaan.asetga.ui.navigation

import kotlinx.serialization.Serializable

// Route type-safe (Navigation Compose 2.8+).
@Serializable data object DashboardRoute
@Serializable data object AssetsRoute
@Serializable data object ScanRoute
@Serializable data object StockRoute
@Serializable data object MoreRoute

@Serializable data object MasterMenuRoute
/** [type] = nama enum MasterType. */
@Serializable data class MasterListRoute(val type: String)
@Serializable data object UsersRoute
@Serializable data object SettingsRoute

@Serializable data class AssetDetailRoute(val id: String)
/** [id] null = tambah aset baru. */
@Serializable data class AssetFormRoute(val id: String? = null)
@Serializable data object LabelRoute
