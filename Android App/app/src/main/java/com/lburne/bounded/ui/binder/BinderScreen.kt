package com.lburne.bounded.ui.binder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lburne.bounded.data.filter.CardSort
import com.lburne.bounded.data.filter.Ownership
import com.lburne.bounded.data.repository.GridDensity
import com.lburne.bounded.ui.collection.formatMoney
import com.lburne.bounded.ui.common.ActiveFilterRow
import com.lburne.bounded.ui.common.CardFilterSheet
import com.lburne.bounded.ui.common.CardTile
import com.lburne.bounded.ui.common.EmptyState
import com.lburne.bounded.ui.common.GridSpacing
import com.lburne.bounded.ui.common.LocalGridDensity
import com.lburne.bounded.ui.common.SearchField
import com.lburne.bounded.ui.common.rememberCardGridCells

/**
 * A binder's cards. The header (search, quick filters and the result count) scrolls with the
 * grid, so the cards get the whole screen on short landscape phones; the filter button stays
 * in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinderScreen(
    viewModel: BinderViewModel,
    currencySymbol: String,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    onScan: () -> Unit,
    onGridDensityChange: (GridDensity) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }

    // The binder was deleted, here or on another device.
    LaunchedEffect(state.isMissing) { if (state.isMissing) onBack() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to binders") }
                },
                title = {
                    Column {
                        Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${state.progress.owned} of ${state.progress.total} collected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(badge = {
                            if (state.filter.activeCount > 0) Badge { Text(state.filter.activeCount.toString()) }
                        }) { Icon(Icons.Default.FilterList, contentDescription = "Filters") }
                    }
                    OverflowMenu(
                        isSubBinder = state.binder != null,
                        onGridDensityChange = onGridDensityChange,
                        onEdit = { showEditor = true },
                        onDelete = { confirmDelete = true }
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                expanded = fabExpanded,
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                text = { Text("Scan cards") }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyVerticalGrid(
            state = gridState,
            columns = rememberCardGridCells(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 88.dp // clear the FAB
            ),
            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
            verticalArrangement = Arrangement.spacedBy(GridSpacing),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header", contentType = "header") {
                BinderHeader(
                    state = state,
                    currencySymbol = currencySymbol,
                    onQueryChange = viewModel::setQuery,
                    onFilterChange = viewModel::updateFilter,
                    onSortChange = viewModel::setSort
                )
            }

            if (state.cards.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    EmptyState(
                        title = "No cards match",
                        body = "Try a different search, or clear some filters.",
                        action = if (!state.filter.isEmpty) {
                            { TextButton(onClick = { viewModel.updateFilter(com.lburne.bounded.data.filter.CardFilter()) }) { Text("Clear search and filters") } }
                        } else null
                    )
                }
            }

            items(state.cards, key = { it.card.cardId }, contentType = { "card" }) { item ->
                CardTile(
                    card = item.card,
                    quantity = item.quantity,
                    onClick = { onCardClick(item.card.cardId) },
                    onIncrement = { viewModel.setQuantity(item, item.quantity + 1) },
                    onDecrement = { viewModel.setQuantity(item, item.quantity - 1) }
                )
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            CardFilterSheet(
                filter = state.filter,
                onFilterChange = viewModel::updateFilter,
                resultCount = state.cards.size,
                onDone = { showFilters = false }
            )
        }
    }

    if (showEditor && state.binder != null) {
        BinderEditorSheet(
            initial = state.binder,
            countFor = viewModel::countFor,
            onSave = {
                viewModel.saveBinder(it)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }

    if (confirmDelete && state.binder != null) {
        DeleteBinderDialog(
            name = state.title,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteBinder()
                onBack()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun BinderHeader(
    state: BinderUiState,
    currencySymbol: String,
    onQueryChange: (String) -> Unit,
    onFilterChange: (com.lburne.bounded.data.filter.CardFilter) -> Unit,
    onSortChange: (CardSort) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(progress = { state.progress.fraction }, modifier = Modifier.fillMaxWidth())
        SearchField(query = state.filter.query, onQueryChange = onQueryChange, placeholder = "Search name, champion, region, artist")

        // Quick toggles for the filters people use most, then a chip for each active filter.
        ActiveFilterRow(
            filter = state.filter.copy(ownership = Ownership.ALL),
            onFilterChange = { onFilterChange(it.copy(ownership = state.filter.ownership)) },
            modifier = Modifier.fillMaxWidth(),
            leading = {
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortChip(state.sort, onSortChange)
                    listOf(Ownership.OWNED, Ownership.MISSING).forEach { option ->
                        FilterChip(
                            selected = state.filter.ownership == option,
                            onClick = {
                                val next = if (state.filter.ownership == option) Ownership.ALL else option
                                onFilterChange(state.filter.copy(ownership = next))
                            },
                            label = { Text(option.label) },
                            leadingIcon = if (state.filter.ownership == option) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null
                        )
                    }
                }
            }
        )

        val count = state.cards.size
        Text(
            buildString {
                append(if (count == 1) "1 card" else "$count cards")
                if (state.progress.value > 0) append(" · collection value ${formatMoney(currencySymbol, state.progress.value)}")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun SortChip(sort: CardSort, onSortChange: (CardSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = sort != CardSort.SET,
            onClick = { open = true },
            label = { Text(if (sort == CardSort.SET) "Sort" else sort.label) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CardSort.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSortChange(option)
                        open = false
                    },
                    trailingIcon = if (option == sort) {
                        { Icon(Icons.Default.Check, contentDescription = "Selected") }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun OverflowMenu(
    isSubBinder: Boolean,
    onGridDensityChange: (GridDensity) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val density = LocalGridDensity.current
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Card size",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            GridDensity.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onGridDensityChange(option)
                        open = false
                    },
                    trailingIcon = if (option == density) {
                        { Icon(Icons.Default.Check, contentDescription = "Selected") }
                    } else null
                )
            }
            if (isSubBinder) {
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Edit binder") }, onClick = { open = false; onEdit() })
                DropdownMenuItem(text = { Text("Delete binder") }, onClick = { open = false; onDelete() })
            }
        }
    }
}
