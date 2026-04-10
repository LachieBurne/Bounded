package com.lburne.bounded

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lburne.bounded.ui.binder.BinderScreen
import com.lburne.bounded.ui.binder.BinderViewModel
import com.lburne.bounded.ui.binder.BinderViewModelFactory
import com.lburne.bounded.ui.deck.DeckEditorScreen
import com.lburne.bounded.ui.deck.DeckEditorViewModel
import com.lburne.bounded.ui.deck.DeckEditorViewModelFactory
import com.lburne.bounded.ui.deck.DeckListScreen
import com.lburne.bounded.ui.deck.DeckListViewModel
import com.lburne.bounded.ui.deck.DeckListViewModelFactory
import com.lburne.bounded.ui.detail.CardDetailScreen
import com.lburne.bounded.ui.detail.CardDetailViewModel
import com.lburne.bounded.ui.detail.CardDetailViewModelFactory
import com.lburne.bounded.ui.login.LoginScreen
import com.lburne.bounded.ui.scanner.ScannerScreen
import com.lburne.bounded.ui.scanner.ScanResultScreen
import com.lburne.bounded.ui.scanner.ScanResultViewModel
import com.lburne.bounded.ui.theme.RiftboundTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as BoundedApplication

        setContent {
            RiftboundTheme(darkTheme = true) {
                val navController = rememberNavController()

                // 1. OBSERVE CURRENT SCREEN
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // 2. DEFINE WHERE BAR SHOULD SHOW
                val showBottomBar = currentRoute in listOf("binder", "deck_list")

                // 3. CHECK AUTH STATE
                val startDestination = if (FirebaseAuth.getInstance().currentUser != null) "binder" else "login"

                // 4. LOAD DATA
                LaunchedEffect(Unit) {
                    app.repository.initializeMasterList()
                }

                Scaffold(
                    bottomBar = {
                        // ANIMATION BLOCK
                        AnimatedVisibility(
                            visible = showBottomBar,
                            // Slide up from the bottom (initialOffsetY = height of the bar)
                            enter = slideInVertically(initialOffsetY = { it }),
                            // Slide down to the bottom (targetOffsetY = height of the bar)
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            NavigationBar {
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = "Binder"
                                        )
                                    },
                                    label = { Text("Binder") },
                                    selected = currentRoute == "binder",
                                    onClick = {
                                        navController.navigate("binder") {
                                            popUpTo("binder") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Style,
                                            contentDescription = "Decks"
                                        )
                                    },
                                    label = { Text("Decks") },
                                    selected = currentRoute == "deck_list",
                                    onClick = {
                                        navController.navigate("deck_list") {
                                            popUpTo("binder") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { _ ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        // When the bar is hidden, calculateBottomPadding() returns 0.dp,
                        // giving the Detail screen full height automatically.
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate("binder") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("binder") {
                            val viewModel: BinderViewModel by viewModels { BinderViewModelFactory(app.repository, app.priceRepository) }
                            BinderScreen(
                                viewModel = viewModel,
                                onCardClick = { cardId -> navController.navigate("detail/$cardId") },
                                onScannerClick = { navController.navigate("scanner") },
                                onAccountClick = { 
                                    // Completely sign out from Firebase to clear the active session
                                    FirebaseAuth.getInstance().signOut()
                                    
                                    // Navigate to login and nuke the entire backstack behind it
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = "detail/{cardId}",
                            arguments = listOf(navArgument("cardId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val cardId = backStackEntry.arguments?.getString("cardId") ?: return@composable

                            // Get the application instance (app is already in scope from onCreate)

                            val viewModel: CardDetailViewModel = viewModel(
                                factory = CardDetailViewModelFactory(
                                    app.repository,
                                    app.priceRepository,
                                    cardId
                                )
                            )

                            CardDetailScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("deck_list") {
                            val viewModel: DeckListViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                factory = DeckListViewModelFactory(app.repository)
                            )
                            DeckListScreen(
                                viewModel = viewModel,
                                onDeckClick = { deckId -> navController.navigate("deck_editor/$deckId") }
                            )
                        }

                        composable(
                            route = "deck_editor/{deckId}",
                            arguments = listOf(navArgument("deckId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val deckId = backStackEntry.arguments?.getString("deckId") ?: return@composable

                            val viewModel: DeckEditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                factory = DeckEditorViewModelFactory(app.repository, app.priceRepository, deckId)
                            )

                            DeckEditorScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onCardClick = { cardId -> navController.navigate("detail/$cardId") }
                            )
                        }

                        composable("scanner") {
                            ScannerScreen(
                                onFinishScanning = { scannedIds ->
                                    val idString = scannedIds.joinToString(",")
                                    if (idString.isNotEmpty()) {
                                        navController.navigate("scan_result/$idString") {
                                            popUpTo("scanner") { inclusive = true }
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "scan_result/{cardIds}",
                            arguments = listOf(navArgument("cardIds") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val idsParam = backStackEntry.arguments?.getString("cardIds") ?: ""
                            val scannedIds = idsParam.split(",").filter { it.isNotEmpty() }
                            
                            val viewModel: ScanResultViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                factory = ScanResultViewModel.Factory(app, app.repository, app.priceRepository)
                            )
                            
                            ScanResultScreen(
                                scannedCardIds = scannedIds,
                                onBack = { navController.popBackStack() },
                                onNavigateToBinder = {
                                    navController.navigate("binder") {
                                        popUpTo("binder") { inclusive = true }
                                    }
                                },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}