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
import id.perusahaan.asetga.domain.model.MasterType
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.ui.feature.home.PlaceholderScreen
import id.perusahaan.asetga.ui.feature.master.MasterListScreen
import id.perusahaan.asetga.ui.feature.master.MasterMenuScreen
import id.perusahaan.asetga.ui.feature.more.MoreScreen
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
                PlaceholderScreen("Beranda", "Dashboard ringkasan aset, nilai buku, dan daftar perlu tindakan hadir pada tahap (f).")
            }
            composable<AssetsRoute> {
                PlaceholderScreen("Aset", "Register aset, pencarian, filter, dan foto hadir pada tahap (b).")
            }
            composable<ScanRoute> {
                PlaceholderScreen("Scan", "Scan QR/barcode dan stock opname hadir pada tahap (c) dan (g).")
            }
            composable<StockRoute> {
                PlaceholderScreen("Stok", "Stok barang habis pakai hadir pada tahap (e).")
            }
            composable<MoreRoute> {
                MoreScreen(
                    user = user,
                    onOpenMaster = { nav.navigate(MasterMenuRoute) },
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
