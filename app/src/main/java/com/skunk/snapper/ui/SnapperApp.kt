package com.skunk.snapper.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Catches("catches", "Catches", Icons.AutoMirrored.Filled.List),
    Map("map", "Go Fish", Icons.Filled.Map),
    Identify("identify", "Identify", Icons.Filled.PhotoCamera),
    Around("around", "Regs", Icons.Filled.Gavel),
    Favorites("favorites", "Favorites", Icons.Filled.Star)
}

@Composable
fun SnapperApp() {
    val nav = rememberNavController()
    val vm: CatchViewModel = viewModel()

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = Tab.entries.any { it.route == currentRoute }

    // App-wide: when the keyboard is dismissed, drop text-field focus so the cursor
    // stops blinking (Compose otherwise keeps the field focused after the IME hides).
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(imeVisible) { if (!imeVisible) focusManager.clearFocus() }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any {
                            it.route == tab.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Identify.route,
            // Only reserve space for the bottom nav bar; inner screens' TopAppBars
            // handle the status-bar inset themselves (avoids a doubled top gap).
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        ) {
            composable(Tab.Catches.route) {
                CatchListScreen(
                    vm = vm,
                    onAddCatch = {
                        vm.startNewCatch()
                        nav.navigate("add")
                    },
                    onOpenCatch = { id -> nav.navigate("detail/$id") },
                    onOpenStats = { nav.navigate("stats") }
                )
            }
            composable(Tab.Map.route) {
                MapScreen(
                    vm = vm,
                    onOpenCatch = { id -> nav.navigate("detail/$id") },
                    onOpenFish = { fish -> vm.openFish(fish); nav.navigate("fishDetail") }
                )
            }
            composable(Tab.Identify.route) {
                IdentifyScreen(
                    vm = vm,
                    onLogCatch = { nav.navigate("add") },
                    onViewRegs = { species -> vm.openFishByName(species); nav.navigate("fishDetail") }
                )
            }
            composable(Tab.Around.route) {
                WhatsAroundScreen(
                    vm = vm,
                    onOpenFish = { fish -> vm.openFish(fish); nav.navigate("fishDetail") },
                    onOpenCredits = { nav.navigate("credits") }
                )
            }
            composable("fishDetail") {
                FishDetailScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable("credits") {
                CreditsScreen(onBack = { nav.popBackStack() })
            }
            composable(Tab.Favorites.route) {
                FavoritesScreen(
                    vm = vm,
                    onOpenSpot = { spot ->
                        vm.focusOnSpot(spot)
                        nav.navigate(Tab.Map.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable("stats") {
                StatsScreen(
                    vm = vm,
                    onOpenCatch = { id -> nav.navigate("detail/$id") },
                    onBack = { nav.popBackStack() }
                )
            }
            composable("add") {
                AddCatchScreen(
                    vm = vm,
                    onDone = { nav.popBackStack() },
                    onPickLocation = { nav.navigate("pickLocation") }
                )
            }
            composable("pickLocation") {
                LocationPickerScreen(vm = vm, onDone = { nav.popBackStack() })
            }
            composable(
                route = "detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                CatchDetailScreen(vm = vm, catchId = id, onBack = { nav.popBackStack() })
            }
        }
    }
}
