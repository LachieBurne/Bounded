package com.lburne.bounded.ui.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lburne.bounded.data.filter.CardFilter
import com.lburne.bounded.data.filter.CardSort
import com.lburne.bounded.data.filter.Ownership
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.model.Binder
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.data.repository.GridDensity
import com.lburne.bounded.ui.binder.BinderEditorSheet
import com.lburne.bounded.ui.binder.DeleteBinderDialog
import com.lburne.bounded.ui.common.CardFilterSheet
import com.lburne.bounded.ui.common.CardTile
import com.lburne.bounded.ui.common.EmptyState
import com.lburne.bounded.ui.common.GoldProgressBar
import com.lburne.bounded.ui.common.GridSpacing
import com.lburne.bounded.ui.common.LocalGridDensity
import com.lburne.bounded.ui.common.RiftLabelText
import com.lburne.bounded.ui.common.SearchField
import com.lburne.bounded.ui.common.SetCodeBadge
import com.lburne.bounded.ui.common.domainIconUrl
import com.lburne.bounded.ui.common.getDomainBand
import com.lburne.bounded.ui.common.getDomainColor
import com.lburne.bounded.ui.common.label
import com.lburne.bounded.ui.common.rememberCardGridCells
import com.lburne.bounded.ui.theme.BandShape
import com.lburne.bounded.ui.theme.ChamferShape
import com.lburne.bounded.ui.theme.Rift
import com.lburne.bounded.ui.theme.RiftLabel
import com.lburne.bounded.ui.theme.RiftNumber

/**
 * The Collection tab. It opens on All cards, the whole collection; binders you make (a
 * master set, a domain, anything) sit in the tab strip and swap the grid in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel,
    onCardClick: (String) -> Unit,
    onScan: () -> Unit,
    onGridDensityChange: (GridDensity) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Binder?>(null) }
    var deleting by remember { mutableStateOf<Binder?>(null) }
    val gridState = rememberLazyGridState()
    val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex < 3 } }

    Scaffold(
        containerColor = Rift.Ink,
        topBar = {
            Column(Modifier.background(Rift.Ink).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(11.dp).rotate(45f).background(Rift.Gold))
                    RiftLabelText(
                        "Collection",
                        style = RiftLabel.copy(fontSize = 24.sp),
                        modifier = Modifier.padding(start = 12.dp).weight(1f)
                    )
                    IconButton(onClick = {
                        if (searching) viewModel.setQuery("")
                        searching = !searching
                    }) {
                        Icon(if (searching) Icons.Default.Close else Icons.Default.Search, contentDescription = if (searching) "Close search" else "Search cards")
                    }
                    OverflowMenu(
                        binder = state.binder,
                        onGridDensityChange = onGridDensityChange,
                        onEdit = { editing = state.binder },
                        onDelete = { deleting = state.binder }
                    )
                }
                BinderTabs(
                    tabs = state.tabs,
                    selectedId = state.selectedId,
                    onSelect = viewModel::select,
                    onNew = { creating = true }
                )
                if (searching) {
                    SearchField(
                        query = state.filter.query,
                        onQueryChange = viewModel::setQuery,
                        placeholder = "Name, tag, artist or number",
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
                    )
                }
                HorizontalDivider(Modifier.padding(top = 10.dp), color = Rift.Track)
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                expanded = fabExpanded,
                shape = ChamferShape,
                containerColor = Rift.Gold,
                contentColor = Rift.OnGold,
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                text = { Text("SCAN", style = RiftNumber.copy(fontSize = 17.sp, letterSpacing = 1.2.sp)) }
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
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 14.dp,
                bottom = padding.calculateBottomPadding() + 96.dp // clear the FAB
            ),
            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "progress", contentType = "progress") {
                ProgressPanel(state, viewModel.currencySymbol, onDomain = viewModel::toggleDomain)
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "toolbar", contentType = "toolbar") {
                Toolbar(
                    filter = state.filter,
                    sort = state.sort,
                    onOpenFilters = { showFilters = true },
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
                            { TextButton(onClick = { viewModel.updateFilter(CardFilter()) }) { Text("Clear search and filters") } }
                        } else null
                    )
                }
            }

            if (state.sections.isNotEmpty()) {
                state.sections.forEach { section ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "set-${section.setCode}", contentType = "set-header") {
                        SetHeader(section)
                    }
                    cardItems(section.cards, onCardClick, viewModel)
                }
            } else {
                cardItems(state.cards, onCardClick, viewModel)
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Rift.Surface
        ) {
            CardFilterSheet(
                filter = state.filter,
                onFilterChange = viewModel::updateFilter,
                resultCount = state.cards.size,
                onDone = { showFilters = false }
            )
        }
    }

    if (creating || editing != null) {
        BinderEditorSheet(
            initial = editing,
            countFor = viewModel::countFor,
            suggestedSets = if (editing == null) state.suggestedSets else emptyList(),
            onQuickMasterSet = {
                viewModel.createMasterSet(it)
                creating = false
            },
            onSave = {
                viewModel.saveBinder(it)
                creating = false
                editing = null
            },
            onDismiss = {
                creating = false
                editing = null
            }
        )
    }

    deleting?.let { binder ->
        DeleteBinderDialog(
            name = binder.name,
            onConfirm = {
                viewModel.deleteBinder(binder.binderId)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

private fun LazyGridScope.cardItems(cards: List<CardWithQuantity>, onCardClick: (String) -> Unit, viewModel: CollectionViewModel) {
    items(cards, key = { it.card.cardId }, contentType = { "card" }) { item ->
        CardTile(
            card = item.card,
            quantity = item.quantity,
            onClick = { onCardClick(item.card.cardId) },
            onIncrement = { viewModel.setQuantity(item, item.quantity + 1) },
            onDecrement = { viewModel.setQuantity(item, item.quantity - 1) }
        )
    }
}

/** All cards first, then your binders, then "+ New binder". */
@Composable
private fun BinderTabs(tabs: List<BinderTab>, selectedId: String, onSelect: (String) -> Unit, onNew: () -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        items(tabs, key = { it.binderId }) { tab ->
            val selected = tab.binderId == selectedId
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .background(if (selected) Rift.Gold else Rift.Surface)
                    .border(1.dp, if (selected) Rift.Gold else Rift.Line)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.binderId) })
                    .padding(start = if (tab.setCode != null || tab.domain != null) 6.dp else 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tab.setCode?.let { SetCodeBadge(it, fontSize = 12) }
                tab.domain?.let {
                    AsyncImage(model = domainIconUrl(it), contentDescription = null, modifier = Modifier.size(24.dp))
                }
                Text(
                    tab.name,
                    color = if (selected) Rift.OnGold else Rift.Parchment,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item(key = "new") {
            OutlinedButton(
                onClick = onNew,
                shape = androidx.compose.ui.graphics.RectangleShape,
                border = BorderStroke(1.dp, Rift.GoldDeep),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Rift.Gold, modifier = Modifier.size(18.dp))
                Text("NEW BINDER", style = RiftNumber.copy(fontSize = 14.sp, letterSpacing = 0.8.sp), color = Rift.Gold, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/** Owned out of total, a gold bar, and each domain's share, which doubles as a filter. */
@Composable
private fun ProgressPanel(state: CollectionUiState, currencySymbol: String, onDomain: (Domain) -> Unit) {
    val p = state.progress
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF1C1612), Color(0xFF120E0B))))
            .border(1.dp, Rift.Line)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%,d".format(p.owned), style = RiftNumber.copy(fontSize = 38.sp, lineHeight = 38.sp), color = Rift.Ivory)
            Text(
                "of ${"%,d".format(p.total)} owned",
                fontSize = 15.sp,
                color = Rift.Muted,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).weight(1f)
            )
            Text("${p.percent}%", style = RiftNumber.copy(fontSize = 20.sp), color = Rift.Gold, modifier = Modifier.padding(bottom = 4.dp))
        }
        GoldProgressBar(p.fraction)
        if (p.value > 0) {
            Text(
                "${"%,d".format(p.copies)} copies · worth ${formatMoney(currencySymbol, p.value)}",
                fontSize = 13.sp,
                color = Rift.Muted
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PlayableDomains.forEach { domain ->
                val progress = state.domainProgress[domain]
                val on = state.filter.domains == setOf(domain)
                val dimmed = state.filter.domains.isNotEmpty() && !on
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .background(if (on) getDomainBand(domain) else Color.Transparent)
                        .border(1.dp, if (on) getDomainColor(domain) else Color.Transparent)
                        .selectable(
                            selected = on,
                            enabled = progress != null,
                            role = Role.Checkbox,
                            onClick = { onDomain(domain) }
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AsyncImage(
                        model = domainIconUrl(domain),
                        contentDescription = "${domain.label()}${progress?.let { ", ${it.percent}% owned" } ?: ""}",
                        modifier = Modifier.size(24.dp).alpha(if (dimmed || progress == null) 0.4f else 1f)
                    )
                    Text(
                        progress?.let { "${it.percent}%" } ?: "–",
                        style = RiftNumber.copy(fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = Rift.Parchment
                    )
                }
            }
        }
    }
}

@Composable
private fun Toolbar(
    filter: CardFilter,
    sort: CardSort,
    onOpenFilters: () -> Unit,
    onFilterChange: (CardFilter) -> Unit,
    onSortChange: (CardSort) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onOpenFilters,
            shape = androidx.compose.ui.graphics.RectangleShape,
            border = BorderStroke(1.dp, Rift.GoldDeep),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(40.dp)
        ) {
            BadgedBox(badge = { if (filter.activeCount > 0) Badge(containerColor = Rift.Gold, contentColor = Rift.OnGold) { Text(filter.activeCount.toString()) } }) {
                Icon(Icons.Default.FilterList, contentDescription = null, tint = Rift.Ivory, modifier = Modifier.size(18.dp))
            }
            Text("FILTER", style = RiftNumber.copy(fontSize = 14.sp, letterSpacing = 0.8.sp), color = Rift.Ivory, modifier = Modifier.padding(start = 8.dp))
        }

        Row(Modifier.border(1.dp, Rift.Line)) {
            Ownership.values().forEach { option ->
                val selected = filter.ownership == option
                Box(
                    Modifier
                        .height(38.dp)
                        .background(if (selected) Rift.Gold else Color.Transparent)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onFilterChange(filter.copy(ownership = option)) })
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(option.label.uppercase(), style = RiftNumber.copy(fontSize = 13.sp, letterSpacing = 0.6.sp), color = if (selected) Rift.OnGold else Rift.Parchment)
                }
            }
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { SortMenu(sort, onSortChange) }
    }
}

@Composable
private fun SortMenu(sort: CardSort, onSortChange: (CardSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(sort.shortLabel, color = Rift.Parchment, fontSize = 14.sp)
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Sort by", tint = Rift.Parchment, modifier = Modifier.size(18.dp))
        }
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

private val CardSort.shortLabel: String
    get() = when (this) {
        CardSort.SET -> "Set"
        CardSort.NAME -> "Name"
        CardSort.COST -> "Cost"
        CardSort.PRICE -> "Price"
        CardSort.OWNED -> "Owned"
    }

/** A set's band at the top of its cards, with your progress in that set. */
@Composable
private fun SetHeader(section: CardSection) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(36.dp)
            .background(Rift.Raised, BandShape)
            .padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SetCodeBadge(section.setCode)
        RiftLabelText(section.name, modifier = Modifier.weight(1f), style = RiftLabel.copy(fontSize = 15.sp))
        Text("${section.progress.owned} / ${section.progress.total}", style = RiftNumber.copy(fontSize = 14.sp), color = Rift.Gold)
    }
}

@Composable
private fun OverflowMenu(
    binder: Binder?,
    onGridDensityChange: (GridDensity) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val density = LocalGridDensity.current
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (binder != null) {
                DropdownMenuItem(text = { Text("Edit ${binder.name}") }, onClick = { open = false; onEdit() })
                DropdownMenuItem(text = { Text("Delete ${binder.name}") }, onClick = { open = false; onDelete() })
                HorizontalDivider()
            }
            Text(
                "Card size",
                style = RiftLabel.copy(fontSize = 12.sp),
                color = Rift.Muted,
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
        }
    }
}
