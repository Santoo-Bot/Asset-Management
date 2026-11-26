package id.perusahaan.asetga.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.perusahaan.asetga.domain.model.MasterType
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.ui.feature.asset.AssetDetailScreen
import id.perusahaan.asetga.ui.feature.asset.AssetFormScreen
import id.perusahaan.asetga.ui.feature.asset.AssetListScreen
import id.perusahaan.asetga.ui.feature.audit.AuditLogScreen
import id.perusahaan.asetga.ui.feature.dashboard.DashboardScreen
import id.perusahaan.asetga.ui.feature.data.BackupScreen
import id.perusahaan.asetga.ui.feature.data.ImportExportScreen
import id.perusahaan.asetga.ui.feature.opname.OpnameListScreen
import id.perusahaan.asetga.ui.feature.opname.OpnameSessionScreen
import id.perusahaan.asetga.ui.feature.insurance.AssetInsuranceSection
import id.perusahaan.asetga.ui.feature.insurance.InsuranceAssetPickerScreen
import id.perusahaan.asetga.ui.feature.insurance.InsuranceListScreen
import id.perusahaan.asetga.ui.feature.insurance.PolicyDetailScreen
import id.perusahaan.asetga.ui.feature.insurance.PolicyFormScreen
import id.perusahaan.asetga.ui.feature.label.LabelScreen
import id.perusahaan.asetga.ui.feature.scan.ScanScreen
import id.perusahaan.asetga.ui.feature.master.MasterListScreen
import id.perusahaan.asetga.ui.feature.master.MasterMenuScreen
import id.perusahaan.asetga.ui.feature.more.MoreScreen
import id.perusahaan.asetga.ui.feature.operation.AssetOperationActions
import id.perusahaan.asetga.ui.feature.operation.AssetOperationsSection
import id.perusahaan.asetga.ui.feature.operation.MutationFormScreen
import id.perusahaan.asetga.ui.feature.operation.ServiceFormScreen
import id.perusahaan.asetga.ui.feature.operation.ServiceListScreen
import id.perusahaan.asetga.ui.feature.scan.ScanPickScreen
import id.perusahaan.asetga.ui.feature.stock.ItemDetailScreen
import id.perusahaan.asetga.ui.feature.stock.ItemFormScreen
import id.perusahaan.asetga.ui.feature.stock.StockListScreen
import id.perusahaan.asetga.ui.feature.settings.SettingsScreen
import id.perusahaan.asetga.ui.feature.users.UsersScreen
import kotlin.reflect.KClass

private data class TopLevel(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val topLevels = listOf(
    TopLevel(DashboardRoute, DashboardRoute::class, "Beranda", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    TopLevel(AssetsRoute, AssetsRoute::class, "Aset", Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
    TopLevel(ScanRoute, ScanRoute::class, "Scan", Icons.Filled.QrCodeScanner, Icons.Filled.QrCodeScanner),
    TopLevel(StockRoute, StockRoute::class, "Stok", Icons.Outlined.Inventory, Icons.Filled.Inventory),
    TopLevel(MoreRoute, MoreRoute::class, "Lainnya", Icons.Filled.Menu, Icons.Filled.Menu),
)

private fun NavDestination?.isTopLevel(): Boolean = this != null && topLevels.any { hasRoute(it.routeClass) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScaffold(user: User) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination

    Scaffold(
        bottomBar = {
            if (destination.isTopLevel()) {
                BottomBar(destination, onSelect = { nav.navigateTopLevel(it) })
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = DashboardRoute, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable<DashboardRoute> {
                DashboardScreen(
                    userName = user.fullName,
                    onOpenPolicy = { nav.navigate(PolicyDetailRoute(it)) },
                    onOpenInsurance = { nav.navigate(InsuranceListRoute) },
                    onOpenAsset = { nav.navigate(AssetDetailRoute(it)) },
                    onOpenItem = { nav.navigate(ItemDetailRoute(it)) },
                    onOpenAssets = { nav.navigateTopLevel(AssetsRoute) },
                    onOpenStock = { nav.navigateTopLevel(StockRoute) },
                    onOpenServices = { nav.navigate(ServiceListRoute) },
                )
            }
            composable<AssetsRoute> {
                AssetListScreen(
                    onOpenAsset = { nav.navigate(AssetDetailRoute(it)) },
                    onAddAsset = { nav.navigate(AssetFormRoute()) },
                    onPrintLabels = { nav.navigate(LabelRoute) },
                )
            }
            composable<ScanRoute> {
                ScanScreen(
                    onOpenAsset = { nav.navigate(AssetDetailRoute(it)) },
                    onOpenOpname = { nav.navigate(OpnameListRoute) },
                )
            }
            composable<AssetDetailRoute> {
                AssetDetailScreen(
                    onBack = nav::navigateUp,
                    onEdit = { nav.navigate(AssetFormRoute(it)) },
                    onPrintLabel = { nav.navigate(LabelRoute) },
                    extraActions = { assetId, canEdit ->
                        AssetOperationActions(
                            canEdit = canEdit,
                            onMutation = { nav.navigate(MutationFormRoute(assetId)) },
                            onService = { nav.navigate(ServiceFormRoute(assetId)) },
                        )
                    },
                    extraSections = { assetId ->
                        AssetInsuranceSection(onOpenPolicy = { nav.navigate(PolicyDetailRoute(it)) })
                        AssetOperationsSection(
                            canEdit = user.role.canEditData,
                            onEditService = { serviceId -> nav.navigate(ServiceFormRoute(assetId, serviceId)) },
                        )
                    },
                )
            }
            composable<AssetFormRoute> { entry ->
                val isEdit = entry.toRoute<AssetFormRoute>().id != null
                AssetFormScreen(
                    onBack = nav::navigateUp,
                    onSaved = { id ->
                        if (isEdit) {
                            nav.popBackStack()
                        } else {
                            nav.navigate(AssetDetailRoute(id)) { popUpTo<AssetFormRoute> { inclusive = true } }
                        }
                    },
                )
            }
            composable<LabelRoute> { LabelScreen(onBack = nav::navigateUp) }
            composable<ImportExportRoute> { ImportExportScreen(onBack = nav::navigateUp) }
            composable<BackupRoute> { BackupScreen(onBack = nav::navigateUp) }
            composable<AuditLogRoute> { AuditLogScreen(onBack = nav::navigateUp) }
            composable<InsuranceListRoute> {
                InsuranceListScreen(
                    onBack = nav::navigateUp,
                    onOpenPolicy = { nav.navigate(PolicyDetailRoute(it)) },
                    onAddPolicy = { nav.navigate(PolicyFormRoute()) },
                    onOpenMaster = { nav.navigate(MasterMenuRoute) },
                )
            }
            composable<PolicyFormRoute> { entry ->
                PolicyFormScreen(
                    onBack = nav::navigateUp,
                    onSaved = { id, isNew ->
                        if (isNew) nav.navigate(PolicyDetailRoute(id)) { popUpTo<PolicyFormRoute> { inclusive = true } } else nav.popBackStack()
                    },
                )
            }
            composable<PolicyDetailRoute> { entry ->
                val scanned by entry.savedStateHandle.getStateFlow<String?>(SCAN_RESULT_KEY, null).collectAsStateWithLifecycle()
                val pickerMessage by entry.savedStateHandle.getStateFlow<String?>(PICKER_MESSAGE_KEY, null).collectAsStateWithLifecycle()
                PolicyDetailScreen(
                    message = pickerMessage,
                    onMessageConsumed = { entry.savedStateHandle[PICKER_MESSAGE_KEY] = null },
                    onBack = nav::navigateUp,
                    onEdit = { nav.navigate(PolicyFormRoute(it)) },
                    onPickAssets = { nav.navigate(InsuranceAssetPickerRoute(it)) },
                    onScan = { nav.navigate(ScanPickRoute("Scan aset untuk polis")) },
                    onOpenAsset = { nav.navigate(AssetDetailRoute(it)) },
                    scannedCode = scanned,
                    onScanConsumed = { entry.savedStateHandle[SCAN_RESULT_KEY] = null },
                )
            }
            composable<InsuranceAssetPickerRoute> {
                InsuranceAssetPickerScreen(
                    onBack = nav::navigateUp,
                    onDone = { message ->
                        nav.previousBackStackEntry?.savedStateHandle?.set(PICKER_MESSAGE_KEY, message)
                        nav.popBackStack()
                    },
                )
            }
            composable<OpnameListRoute> {
                OpnameListScreen(onBack = nav::navigateUp, onOpenSession = { nav.navigate(OpnameSessionRoute(it)) })
            }
            composable<OpnameSessionRoute> {
                OpnameSessionScreen(onBack = nav::navigateUp, onOpenAsset = { nav.navigate(AssetDetailRoute(it)) })
            }
            composable<StockRoute> { entry ->
                val scanned by entry.savedStateHandle.getStateFlow<String?>(SCAN_RESULT_KEY, null).collectAsStateWithLifecycle()
                StockListScreen(
                    scannedCode = scanned,
                    onScanConsumed = { entry.savedStateHandle[SCAN_RESULT_KEY] = null },
                    onScan = { nav.navigate(ScanPickRoute("Scan barcode barang")) },
                    onOpenItem = { nav.navigate(ItemDetailRoute(it)) },
                    onAddItem = { barcode -> nav.navigate(ItemFormRoute(barcode = barcode)) },
                )
            }
            composable<ScanPickRoute> { entry ->
                ScanPickScreen(
                    title = entry.toRoute<ScanPickRoute>().title,
                    onBack = nav::navigateUp,
                    onCode = { code ->
                        nav.previousBackStackEntry?.savedStateHandle?.set(SCAN_RESULT_KEY, code)
                        nav.popBackStack()
                    },
                )
            }
            composable<ItemDetailRoute> {
                ItemDetailScreen(onBack = nav::navigateUp, onEdit = { nav.navigate(ItemFormRoute(id = it)) })
            }
            composable<ItemFormRoute> {
                ItemFormScreen(
                    onBack = nav::navigateUp,
                    onSaved = { id, isNew ->
                        if (isNew) {
                            nav.navigate(ItemDetailRoute(id)) { popUpTo<ItemFormRoute> { inclusive = true } }
                        } else {
                            nav.popBackStack()
                        }
                    },
                )
            }
            composable<MutationFormRoute> { MutationFormScreen(onBack = nav::navigateUp, onSaved = { nav.popBackStack() }) }
            composable<ServiceFormRoute> { ServiceFormScreen(onBack = nav::navigateUp, onSaved = { nav.popBackStack() }) }
            composable<ServiceListRoute> {
                ServiceListScreen(onBack = nav::navigateUp, onOpenAsset = { nav.navigate(AssetDetailRoute(it)) })
            }
            composable<MoreRoute> {
                MoreScreen(
                    user = user,
                    onOpenMaster = { nav.navigate(MasterMenuRoute) },
                    onOpenServices = { nav.navigate(ServiceListRoute) },
                    onOpenOpname = { nav.navigate(OpnameListRoute) },
                    onOpenImportExport = { nav.navigate(ImportExportRoute) },
                    onOpenBackup = { nav.navigate(BackupRoute) },
                    onOpenAuditLog = { nav.navigate(AuditLogRoute) },
                    onOpenInsurance = { nav.navigate(InsuranceListRoute) },
                    onOpenUsers = { nav.navigate(UsersRoute) },
                    onOpenSettings = { nav.navigate(SettingsRoute) },
                )
            }
            composable<MasterMenuRoute> {
                MasterMenuScreen(onBack = nav::navigateUp, onOpen = { type -> nav.navigate(MasterListRoute(type.name)) })
            }
            composable<MasterListRoute> { entry ->
                val type = MasterType.valueOf(entry.toRoute<MasterListRoute>().type)
                MasterListScreen(type = type, onBack = nav::navigateUp)
            }
            composable<UsersRoute> { UsersScreen(onBack = nav::navigateUp) }
            composable<SettingsRoute> { SettingsScreen(onBack = nav::navigateUp) }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(destination: NavDestination?, onSelect: (Any) -> Unit) {
    NavigationBar {
        topLevels.forEach { item ->
            val selected = destination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
            val isScan = item.routeClass == ScanRoute::class
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item.route) },
                icon = {
                    if (isScan) {
                        // Tombol scan menonjol di tengah agar mudah dijangkau ibu jari.
                        Box(
                            Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(item.icon, contentDescription = item.label, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    } else {
                        Icon(if (selected) item.selectedIcon else item.icon, contentDescription = item.label)
                    }
                },
                label = { if (!isScan) Text(item.label) },
            )
        }
    }
}
