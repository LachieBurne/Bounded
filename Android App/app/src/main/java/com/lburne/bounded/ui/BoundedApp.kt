package com.lburne.bounded.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.lburne.bounded.ui.theme.Rift
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.lburne.bounded.BoundedApplication
import com.lburne.bounded.ui.collection.CollectionScreen
import com.lburne.bounded.ui.collection.CollectionViewModel
import com.lburne.bounded.ui.common.LocalGridDensity
import com.lburne.bounded.ui.deck.DeckEditorScreen
import com.lburne.bounded.ui.deck.DeckEditorViewModel
import com.lburne.bounded.ui.deck.DeckEditorViewModelFactory
import com.lburne.bounded.ui.deck.DeckImportScreen
import com.lburne.bounded.ui.deck.DeckImportViewModel
import com.lburne.bounded.ui.deck.DeckListScreen
import com.lburne.bounded.ui.deck.DeckListViewModel
import com.lburne.bounded.ui.deck.DeckListViewModelFactory
import com.lburne.bounded.ui.detail.CardDetailScreen
import com.lburne.bounded.ui.detail.CardDetailViewModel
import com.lburne.bounded.ui.detail.CardDetailViewModelFactory
import com.lburne.bounded.ui.login.LoginScreen
import com.lburne.bounded.ui.scanner.ScanResultScreen
import com.lburne.bounded.ui.scanner.ScanResultViewModel
import com.lburne.bounded.ui.scanner.ScannerScreen
import com.lburne.bounded.ui.settings.SettingsScreen

/** Navigation routes. Top-level tabs are [COLLECTION], [DECKS] and [SETTINGS]. */
object Routes {
    const val LOGIN = "login"
    const val COLLECTION = "collection"
    const val CARD = "card/{cardId}"
    const val DECKS = "decks"
    const val DECK = "deck/{deckId}"
    const val DECK_IMPORT = "deck_import"
    const val SCANNER = "scanner"
    const val SCAN_RESULT = "scan_result/{cardIds}"
    const val SETTINGS = "settings"

    fun card(id: String) = "card/$id"
    fun deck(id: String) = "deck/$id"
}

private enum class Tab(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector) {
    COLLECTION(Routes.COLLECTION, "Collection", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    DECKS(Routes.DECKS, "Decks", Icons.Filled.Style, Icons.Outlined.Style),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings);

    companion object {
        /** The tab a screen belongs to, or null for full-screen tasks (card, deck editor, scanner). */
        fun of(route: String?): Tab? = when (route) {
            Routes.COLLECTION -> COLLECTION
            Routes.DECKS -> DECKS
            Routes.SETTINGS -> SETTINGS
            else -> null
        }
    }
}

/** Below this width the tabs are a bottom bar; at or above it, a navigation rail. */
private val RailBreakpoint = 600.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoundedApp(app: BoundedApplication) {
    val navController = rememberNavController()
    val density by app.settingsRepository.gridDensity.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    val currentTab = Tab.of(backStack?.destination?.route)

    LaunchedEffect(Unit) { app.repository.initializeMasterList() }

    CompositionLocalProvider(LocalGridDensity provides density) {
        Surface(color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val useRail = maxWidth >= RailBreakpoint
                val showRail = useRail && currentTab != null
                val showBar = !useRail && currentTab != null
                // The bar or rail already pads for the system bars on its side.
                val consumed = when {
                    showBar -> WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                    showRail -> WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
                    else -> WindowInsets(0)
                }
                Row(Modifier.fillMaxSize()) {
                    if (showRail) {
                        NavigationRail(containerColor = Rift.BarGround) {
                            Tab.values().forEach { tab ->
                                NavigationRailItem(
                                    selected = tab == currentTab,
                                    onClick = { navController.openTab(tab, currentTab) },
                                    icon = { TabIcon(tab, tab == currentTab) },
                                    label = { Text(tab.label.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp)) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = Rift.Gold,
                                        selectedTextColor = Rift.Gold,
                                        indicatorColor = Color.Transparent,
                                        unselectedIconColor = Rift.Muted,
                                        unselectedTextColor = Rift.Muted
                                    )
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        AppNavHost(app, navController, Modifier.weight(1f).consumeWindowInsets(consumed))
                        if (showBar) {
                            HorizontalDivider(color = Rift.Line)
                            NavigationBar(containerColor = Rift.BarGround, tonalElevation = 0.dp) {
                                Tab.values().forEach { tab ->
                                    NavigationBarItem(
                                        selected = tab == currentTab,
                                        onClick = { navController.openTab(tab, currentTab) },
                                        icon = { TabIcon(tab, tab == currentTab) },
                                        label = { Text(tab.label.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp)) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Rift.Gold,
                                            selectedTextColor = Rift.Gold,
                                            indicatorColor = Color.Transparent,
                                            unselectedIconColor = Rift.Muted,
                                            unselectedTextColor = Rift.Muted
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A tab's icon, with a gold diamond above it when it's the open tab. */
@Composable
private fun TabIcon(tab: Tab, selected: Boolean) {
    Box(contentAlignment = Alignment.TopCenter) {
        Icon(if (selected) tab.selected else tab.unselected, contentDescription = null)
        if (selected) {
            Box(
                Modifier
                    .offset(y = (-10).dp)
                    .size(7.dp)
                    .rotate(45f)
                    .background(Rift.Gold)
            )
        }
    }
}

/** Opens a tab where you left it, or returns to its first screen if it's already open. */
private fun NavHostController.openTab(tab: Tab, current: Tab?) {
    if (tab == current) {
        popBackStack(tab.route, inclusive = false)
        return
    }
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(app: BoundedApplication, navController: NavHostController, modifier: Modifier) {
    val startDestination = if (FirebaseAuth.getInstance().currentUser != null) Routes.COLLECTION else Routes.LOGIN
    val currency by app.priceRepository.currency.collectAsState()

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Routes.COLLECTION) { popUpTo(Routes.LOGIN) { inclusive = true } }
            })
        }

        composable(Routes.COLLECTION) {
            val vm: CollectionViewModel = viewModel(factory = CollectionViewModel.Factory(app.repository, app.priceRepository))
            CollectionScreen(
                viewModel = vm,
                onCardClick = { navController.navigate(Routes.card(it)) },
                onScan = { navController.navigate(Routes.SCANNER) },
                onGridDensityChange = app.settingsRepository::setGridDensity
            )
        }

        composable(Routes.CARD, arguments = listOf(navArgument("cardId") { type = NavType.StringType })) { entry ->
            val cardId = entry.arguments?.getString("cardId") ?: return@composable
            val vm: CardDetailViewModel = viewModel(factory = CardDetailViewModelFactory(app.repository, app.priceRepository, cardId))
            CardDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.DECKS) {
            val vm: DeckListViewModel = viewModel(factory = DeckListViewModelFactory(app.repository))
            DeckListScreen(
                viewModel = vm,
                onDeckClick = { navController.navigate(Routes.deck(it)) },
                onImport = { navController.navigate(Routes.DECK_IMPORT) }
            )
        }

        composable(Routes.DECK, arguments = listOf(navArgument("deckId") { type = NavType.StringType })) { entry ->
            val deckId = entry.arguments?.getString("deckId") ?: return@composable
            val vm: DeckEditorViewModel = viewModel(factory = DeckEditorViewModelFactory(app.repository, app.priceRepository, deckId))
            DeckEditorScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onCardClick = { navController.navigate(Routes.card(it)) }
            )
        }

        composable(Routes.DECK_IMPORT) {
            val vm: DeckImportViewModel = viewModel(factory = DeckImportViewModel.Factory(app.repository))
            DeckImportScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onImported = { deckId ->
                    navController.navigate(Routes.deck(deckId)) { popUpTo(Routes.DECKS) }
                }
            )
        }

        composable(Routes.SCANNER) {
            ScannerScreen(
                onFinishScanning = { scannedIds ->
                    val ids = scannedIds.joinToString(",")
                    if (ids.isNotEmpty()) {
                        navController.navigate("scan_result/$ids") { popUpTo(Routes.SCANNER) { inclusive = true } }
                    } else {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SCAN_RESULT, arguments = listOf(navArgument("cardIds") { type = NavType.StringType })) { entry ->
            val scannedIds = (entry.arguments?.getString("cardIds") ?: "").split(",").filter { it.isNotEmpty() }
            val vm: ScanResultViewModel = viewModel(factory = ScanResultViewModel.Factory(app, app.repository, app.priceRepository))
            ScanResultScreen(
                scannedCardIds = scannedIds,
                onBack = { navController.popBackStack() },
                onNavigateToBinder = {
                    // Back to wherever scanning started: the collection or a binder.
                    if (!navController.popBackStack()) navController.navigate(Routes.COLLECTION)
                },
                viewModel = vm
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                currency = currency,
                onCurrencyChange = app.priceRepository::setCurrency,
                gridDensity = LocalGridDensity.current,
                onGridDensityChange = app.settingsRepository::setGridDensity,
                onSignOut = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }
    }
}
