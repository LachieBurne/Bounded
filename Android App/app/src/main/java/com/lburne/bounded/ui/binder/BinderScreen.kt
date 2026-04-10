package com.lburne.bounded.ui.binder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinderScreen(
    viewModel: BinderViewModel,
    onCardClick: (String) -> Unit,
    onScannerClick: () -> Unit,
    onAccountClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }

    // 1. Scroll State & Coroutine Scope
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // 2. Visibility Logic: Show button only if we've scrolled past the first few items
    val showScrollToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 5 }
    }

    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            FilterSheet(
                uiState = uiState,
                onDomainChanged = { viewModel.onDomainSelected(it) },
                onRarityChanged = { viewModel.onRaritySelected(it) },
                onTypeChanged = { viewModel.onTypeSelected(it) },
                onSetChanged = { viewModel.onSetSelected(it) }, // Pass the new Set filter
                onCostChanged = { viewModel.onCostSelected(it) },
                onMightChanged = { viewModel.onMightSelected(it) },
                onOwnedOnlyChanged = { viewModel.onToggleOwnedOnly(it) },
                onClearAll = { viewModel.clearFilters() }
            )
        }
    }

    var showSettingsSheet by remember { mutableStateOf(false) }
    val currentCurrency by viewModel.currentCurrency.collectAsState()

    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            CurrencySettingsSheet(
                currentCurrency = currentCurrency,
                onCurrencySelected = {
                    viewModel.setCurrency(it)
                    showSettingsSheet = false
                }
            )
        }
    }

    Scaffold(
        topBar = {
            // Header with Search
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                SleekSearchBar(
                    query = uiState.searchQuery,
                    onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onFilterClicked = { showFilterSheet = true },
                    isFilterActive = uiState.isFiltering,
                    onSettingsClicked = { showSettingsSheet = true },
                    onAccountClicked = { onAccountClick() }
                )
            }
        },
        // 3. The New FAB
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AnimatedVisibility(
                    visible = showScrollToTop,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                gridState.animateScrollToItem(0)
                            }
                        },
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        shape = CircleShape,
                        modifier = Modifier.padding(bottom = 0.dp) // Stack above the lower FAB
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Scroll to Top"
                        )
                    }
                }
                
                FloatingActionButton(
                    onClick = onScannerClick,
                    containerColor = Color(0xFF9D4EDD), // Same purple accent
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 96.dp) // Lift it above the Nav Bar
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Scan Cards"
                    )
                }
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                state = gridState, // <--- Attach the state here
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = 160.dp, // Extra padding at bottom so FAB doesn't cover last cards
                    start = 8.dp,
                    end = 8.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // (Existing Grid Content)
                if (uiState.cards.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No cards found.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(
                    items = uiState.cards,
                    key = { it.card.cardId }
                ) { item ->
                    CardGridItem(
                        item = item,
                        onIncrement = { viewModel.onIncrementCard(item) },
                        onDecrement = { viewModel.onDecrementCard(item) },
                        onClick = { onCardClick(item.card.cardId) }
                    )
                }
            }
        }
    }
}