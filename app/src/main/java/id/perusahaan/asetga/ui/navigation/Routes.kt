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

@Serializable data class MutationFormRoute(val assetId: String)
/** [serviceId] null = catatan servis baru. */
@Serializable data class ServiceFormRoute(val assetId: String, val serviceId: String? = null)
@Serializable data object ServiceListRoute

@Serializable data class ItemDetailRoute(val id: String)
/** [barcode] diisi bila berasal dari hasil scan yang belum terdaftar. */
@Serializable data class ItemFormRoute(val id: String? = null, val barcode: String? = null)
/** Scan sekali lalu kembalikan kode ke layar sebelumnya lewat SavedStateHandle [SCAN_RESULT_KEY]. */
@Serializable data class ScanPickRoute(val title: String)

const val SCAN_RESULT_KEY = "scan_result"

@Serializable data object ImportExportRoute
@Serializable data object BackupRoute
@Serializable data object AuditLogRoute
@Serializable data object OpnameListRoute
@Serializable data class OpnameSessionRoute(val id: String)

@Serializable data object InsuranceListRoute
@Serializable data class PolicyDetailRoute(val id: String)
/** [id] null = polis baru. */
@Serializable data class PolicyFormRoute(val id: String? = null)
@Serializable data class InsuranceAssetPickerRoute(val policyId: String)
const val PICKER_MESSAGE_KEY = "picker_message"
