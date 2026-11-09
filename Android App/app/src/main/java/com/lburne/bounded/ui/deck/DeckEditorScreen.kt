package com.lburne.bounded.ui.deck

import com.lburne.bounded.data.rules.DeckIssue
import com.lburne.bounded.data.rules.DeckRules
import com.lburne.bounded.data.rules.IssueCode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.local.DeckCardView
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.ui.common.ActiveFilterRow
import com.lburne.bounded.ui.common.CardFilterSheet
import com.lburne.bounded.ui.common.EmptyState
import com.lburne.bounded.ui.common.GridSpacing
import com.lburne.bounded.ui.common.SearchField
import com.lburne.bounded.ui.common.getCardBorderBrush
import com.lburne.bounded.ui.common.getDomainColor
import com.lburne.bounded.ui.common.rememberCardGridCells
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import com.lburne.bounded.ui.common.BannedTag
import com.lburne.bounded.ui.common.GoldButton
import com.lburne.bounded.ui.theme.Rift
import com.lburne.bounded.ui.theme.RiftNumber
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import com.lburne.bounded.data.rules.DeckStep
import com.lburne.bounded.ui.theme.RiftLabel

private const val GITHUB_ASSETS_URL = "https://lachieburne.github.io/Bounded"

private fun getDomainIconUrl(domain: Domain): String {
    return "$GITHUB_ASSETS_URL/domain_images/${domain.name.lowercase()}.png"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckEditorScreen(
    viewModel: DeckEditorViewModel,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit
) {
    val deck by viewModel.deck.collectAsState()
    val mainSlots by viewModel.mainDeckSlots.collectAsState()
    val sideSlots by viewModel.sideboardSlots.collectAsState()
    val availableCards by viewModel.availableCards.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val deckStats by viewModel.deckStats.collectAsState()
    val prices by viewModel.cardPrices.collectAsState()
    val currencySymbol = viewModel.getCurrencySymbol()
    val deckCounts by viewModel.deckCounts.collectAsState()
    val deckIssues by viewModel.deckIssues.collectAsState()
    val pickerStep by viewModel.pickerStep.collectAsState()
    val canChoosePickerTarget by viewModel.canChoosePickerTarget.collectAsState()
    var showOptions by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val context = LocalContext.current

    // Why a card couldn't be added (copy limit, wrong domain, ...).
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Counts & Stats
    val totalCountsById = remember(mainSlots, sideSlots) {
        val map = mutableMapOf<String, Int>()
        fun add(slots: List<DeckCardView>) {
            slots.forEach { slot -> map[slot.card.cardId] = (map[slot.card.cardId] ?: 0) + slot.deckQuantity }
        }
        add(mainSlots)
        add(sideSlots)
        map
    }

    val totalCountsByName = remember(mainSlots, sideSlots) {
        val map = mutableMapOf<String, Int>()
        fun add(slots: List<DeckCardView>) {
            slots.forEach { slot -> map[slot.card.name] = (map[slot.card.name] ?: 0) + slot.deckQuantity }
        }
        add(mainSlots)
        add(sideSlots)
        map
    }

    val activeZoneCounts = remember(pickerStep, mainSlots, sideSlots) {
        val targetSlots = if (pickerStep == DeckBuildStep.SELECT_SIDEBOARD) sideSlots else mainSlots
        val map = mutableMapOf<String, Int>()
        targetSlots.forEach { map[it.card.cardId] = it.deckQuantity }
        map
    }

    // Copies the deck uses that the collection doesn't have, per card across main deck and sideboard.
    val missingById = remember(mainSlots, sideSlots) {
        (mainSlots + sideSlots).groupBy { it.card.cardId }.mapValues { (_, slots) ->
            slots.first().copy(deckQuantity = slots.sumOf { it.deckQuantity }).let { if (it.isMissingCards) it.missingCount else 0 }
        }.filterValues { it > 0 }
    }
    var confirmAddAll by remember { mutableStateOf(false) }
    if (confirmAddAll) {
        val total = missingById.values.sum()
        AlertDialog(
            onDismissRequest = { confirmAddAll = false },
            title = { Text("Add $total ${if (total == 1) "card" else "cards"} to your collection?") },
            text = { Text("This marks every copy this deck needs as owned. Use it once you have the cards.") },
            confirmButton = {
                TextButton(onClick = { viewModel.onAddAllMissingToBinder(); confirmAddAll = false }) { Text("Add $total") }
            },
            dismissButton = { TextButton(onClick = { confirmAddAll = false }) { Text("Cancel") } }
        )
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    if (showOptions) {
        DeckOptionsSheet(
            deckName = deck?.name ?: "Deck",
            onDismiss = { showOptions = false },
            onShare = { shareDeckText(context, viewModel.getExportText(), "${deck?.name ?: "Deck"} Decklist") },
            onCopy = {
                clipboard.setText(AnnotatedString(viewModel.getExportText()))
                android.widget.Toast.makeText(context, "Decklist copied", android.widget.Toast.LENGTH_SHORT).show()
            },
            onRename = { showRename = true },
            onDelete = { showDelete = true }
        )
    }
    if (showRename) {
        RenameDeckDialog(
            currentName = deck?.name ?: "",
            onRename = { viewModel.renameDeck(it); showRename = false },
            onDismiss = { showRename = false }
        )
    }
    if (showDelete) {
        DeleteDeckDialog(
            deckName = deck?.name ?: "this deck",
            onConfirm = {
                showDelete = false
                viewModel.deleteDeck()
                onBack()
            },
            onDismiss = { showDelete = false }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Wide screens (tablets, unfolded foldables, landscape) show the picker and the deck side
    // by side, so you can see what you're adding to.
    val twoPane = maxWidth >= 840.dp
    val tabs = if (twoPane) listOf("Build", "Stats") else listOf("My Deck", "Add Cards", "Stats")
    val statsTab = tabs.lastIndex
    if (selectedTabIndex > statsTab) selectedTabIndex = 0

    LaunchedEffect(currentStep, twoPane) {
        if (!twoPane) {
            if (currentStep == DeckBuildStep.COMPLETE) selectedTabIndex = 0
            else if (currentStep == DeckBuildStep.SELECT_LEGEND) selectedTabIndex = 1
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(deck?.name ?: "Edit Deck", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val exportText = viewModel.getExportText()
                            shareDeckText(context, exportText, "${deck?.name ?: "Deck"} Decklist")
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share decklist")
                        }
                        IconButton(onClick = { showOptions = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Deck options")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val deckView: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier) {
                DeckViewTab(
                    deck = deck,
                    mainSlots = mainSlots,
                    sideSlots = sideSlots,
                    totalCountsByName = totalCountsByName,
                    prices = prices,
                    currencySymbol = currencySymbol,
                    onIncrement = { id, isSide -> viewModel.onIncrementCard(id, isSide) },
                    onDecrement = { id, isSide -> viewModel.onDecrementCard(id, isSide) },
                    onAddMissing = { id -> viewModel.onAddMissingToBinder(id) },
                    onCardClick = onCardClick,
                    missingById = missingById,
                    onAddAllMissing = { confirmAddAll = true },
                    issues = deckIssues
                )
            }
        }
        val browse: @Composable (Modifier) -> Unit = { modifier ->
            val dynamicTitle = when (pickerStep) {
                DeckBuildStep.SELECT_RUNES -> "Select Runes (${deckCounts.runes}/${DeckRules.RUNE_DECK_SIZE})"
                DeckBuildStep.SELECT_BATTLEFIELDS -> "Select Battlefields (${deckCounts.battlefields}/${DeckRules.BATTLEFIELD_COUNT})"
                DeckBuildStep.SELECT_MAIN -> "Main Deck (${deckCounts.mainDeck}/${DeckRules.MAIN_DECK_MIN})"
                DeckBuildStep.SELECT_SIDEBOARD -> "Sideboard (${deckCounts.sideboard}/${DeckRules.SIDEBOARD_MAX})"
                DeckBuildStep.COMPLETE -> "Deck Complete!"
                else -> currentStep.title
            }

            val legendEntry = mainSlots.find { it.card.cardId == deck?.legendCardId }
            val allowedFilterDomains = remember(currentStep, legendEntry) {
                if (currentStep == DeckBuildStep.SELECT_LEGEND) {
                    null
                } else {
                    val baseDomains = legendEntry?.card?.domain ?: emptyList()
                    if (baseDomains.isNotEmpty()) (baseDomains + Domain.COLORLESS).distinct() else null
                }
            }

            Box(modifier) {
                DeckBrowseTab(
                    issues = deckIssues,
                    step = pickerStep,
                    title = dynamicTitle,
                    instruction = pickerStep.instruction,
                    target = if (canChoosePickerTarget) pickerStep.step else null,
                    onChooseTarget = viewModel::choosePickerTarget,
                    cards = availableCards,
                    totalCountsById = totalCountsById,
                    totalCountsByName = totalCountsByName,
                    activeZoneCounts = activeZoneCounts,
                    viewModel = viewModel,
                    allowedDomains = allowedFilterDomains,
                    onAdd = { cardObj -> viewModel.onCardSelect(cardObj) },
                    onRemove = { cardId -> viewModel.onRemoveCard(cardId) }
                )
            }
        }

        Box(Modifier.padding(innerPadding)) {
            when {
                selectedTabIndex == statsTab -> DeckStatsTab(stats = deckStats, currencySymbol = currencySymbol)
                twoPane -> Row(Modifier.fillMaxSize()) {
                    browse(Modifier.weight(0.6f))
                    VerticalDivider()
                    deckView(Modifier.weight(0.4f))
                }
                selectedTabIndex == 0 -> deckView(Modifier.fillMaxSize())
                else -> browse(Modifier.fillMaxSize())
            }
        }
    }
    }
}

// --- NEW COMPONENT: STATS TAB ---
@Composable
fun DeckStatsTab(stats: DeckStats, currencySymbol: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Estimated Value",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))

                // Total Value
                Text(
                    text = "${currencySymbol}${String.format("%.2f", stats.totalValue)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50) // Green
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(Modifier.height(8.dp))

                // Breakdown Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Owned Value",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${currencySymbol}${String.format("%.2f", stats.ownedValue)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Only show "Cost to Finish" if there is actually a cost
                    if (stats.missingValue > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Cost to Finish",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error // Red text for cost
                            )
                            Text(
                                "${currencySymbol}${String.format("%.2f", stats.missingValue)}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        // Deck is complete
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Status",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Complete",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
        }

        // Summary
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Deck Composition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Cards: ${stats.totalMainDeck}")
                    Text("Units: ${stats.unitCount}")
                    Text("Spells: ${stats.spellCount}")
                    Text("Gear: ${stats.gearCount}")
                }
            }
        }

        // Energy Curve
        Text("Energy Curve", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        SimpleBarChart(
            data = stats.energyCurve,
            label = "Energy",
            color = Color(0xFF2196F3) // Blue
        )

        Spacer(Modifier.height(24.dp))

        // Might Curve
        Text("Might Curve", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        SimpleBarChart(
            data = stats.mightCurve,
            label = "Might",
            color = Color(0xFFD32F2F) // Red
        )

        Spacer(Modifier.height(24.dp))

        // NEW: Domain Breakdown
        Text("Domain Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        DomainBarChart(data = stats.domainBreakdown)

        Spacer(Modifier.height(40.dp))
    }
}

// --- CHARTS ---

@Composable
fun SimpleBarChart(
    data: Map<Int, Int>,
    label: String,
    color: Color
) {
    val maxCount = data.values.maxOrNull() ?: 0
    val maxY = if (maxCount < 5) 5 else maxCount

    val chartHeight = 200.dp
    val maxBarHeight = 140.dp

    Row(
        modifier = Modifier.fillMaxWidth().height(chartHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0..7) {
            val count = data[i] ?: 0
            val heightFraction = if (maxY > 0) count.toFloat() / maxY else 0f
            val labelText = if (i == 7) "7+" else "$i"
            val barHeight = (maxBarHeight * heightFraction).coerceAtLeast(4.dp)

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                if (count > 0) {
                    Text(text = "$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(barHeight)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (count > 0) color else Color.LightGray.copy(alpha = 0.3f))
                )
                Spacer(Modifier.height(4.dp))
                Text(text = labelText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DomainBarChart(data: Map<Domain, Int>) {
    val maxCount = data.values.maxOrNull() ?: 0
    val maxY = if (maxCount < 5) 5 else maxCount

    val chartHeight = 200.dp
    val maxBarHeight = 140.dp

    // Filter to show only active domains
    val activeDomains = Domain.values().filter { (data[it] ?: 0) > 0 }

    if (activeDomains.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            Text("No cards with domains selected yet.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(chartHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        activeDomains.forEach { domain ->
            val count = data[domain] ?: 0
            val heightFraction = if (maxY > 0) count.toFloat() / maxY else 0f
            val barHeight = (maxBarHeight * heightFraction).coerceAtLeast(4.dp)

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                // 1. Count Label
                Text(text = "$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))

                // 2. Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(barHeight)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(getDomainColor(domain))
                )

                Spacer(Modifier.height(8.dp))

                // 3. Icon (Replaces Text)
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(getDomainIconUrl(domain))
                        .crossfade(true)
                        .size(coil.size.Size.ORIGINAL)
                        .build(),
                    contentDescription = domain.name,
                    loading = {
                        // Placeholder while loading
                        Box(Modifier.size(28.dp))
                    },
                    modifier = Modifier.size(28.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

// --- EXISTING TABS (Unchanged from previous versions) ---

@Composable
fun DeckViewTab(
    deck: com.lburne.bounded.data.model.Deck?,
    mainSlots: List<DeckCardView>,
    sideSlots: List<DeckCardView>,
    totalCountsByName: Map<String, Int>,
    prices: Map<String, Double>,
    currencySymbol: String,
    onIncrement: (String, Boolean) -> Unit,
    onDecrement: (String, Boolean) -> Unit,
    onAddMissing: (String) -> Unit,
    onCardClick: (String) -> Unit,
    missingById: Map<String, Int> = emptyMap(),
    onAddAllMissing: () -> Unit = {},
    issues: List<DeckIssue> = emptyList()
) {
    if (mainSlots.isEmpty() && sideSlots.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Style,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Deck is empty. Go to 'Add Cards'!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        return
    }

    val collapsedSections = remember { mutableStateListOf<String>() }
    fun isCollapsed(title: String): Boolean = collapsedSections.contains(title)
    fun toggleSection(title: String) {
        if (collapsedSections.contains(title)) collapsedSections.remove(title) else collapsedSections.add(title)
    }

    // Logic variables
    val legendEntry = mainSlots.find { it.card.cardId == deck?.legendCardId }
    val championEntry = mainSlots.find { it.card.cardId == deck?.championCardId }
    val runes = mainSlots.filter { it.card.type == CardType.RUNE }
    val battlefields = mainSlots.filter { it.card.type == CardType.BATTLEFIELD }
    val rawMainDeck = mainSlots.filter {
        it.card.cardId != deck?.legendCardId && it.card.type != CardType.RUNE && it.card.type != CardType.BATTLEFIELD
    }
    val processedMainDeck = rawMainDeck.mapNotNull { entry ->
        if (entry.card.cardId == deck?.championCardId) {
            if (entry.deckQuantity > 1) entry.copy(deckQuantity = entry.deckQuantity - 1) else null
        } else {
            entry
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        fun header(title: String, count: Int? = null) {
            val collapsed = isCollapsed(title)
            val countText = if (count != null) " • $count" else ""
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp)
                        .clickable { toggleSection(title) }
                ) {
                    Text(
                        text = "$title$countText".uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        val ruleBreaks = issues.filter { it.code in RULE_BREAK_CODES }
        if (ruleBreaks.isNotEmpty()) item {
            val names = (mainSlots + sideSlots).associate { it.card.cardId to it.card.name }
            NotLegalBanner(ruleBreaks.map { issue -> issue.code.message + (issue.subject?.let { " (${names[it] ?: it})" } ?: "") })
        }

        if (missingById.isNotEmpty()) item {
            val missingValue = missingById.entries.sumOf { (id, n) -> (prices[id] ?: 0.0) * n }
            MissingCardsPanel(missingById.values.sum(), missingValue, currencySymbol, onAddAllMissing)
        }

        if (legendEntry != null) {
            header("Legend")
            if (!isCollapsed("Legend")) item {
                val price = prices[legendEntry.card.cardId]
                DeckSlotItem(legendEntry, price, currencySymbol, false, false, onIncrement, onDecrement, onAddMissing, onCardClick) }
        }
        if (championEntry != null) {
            header("Chosen Champion")
            if (!isCollapsed("Chosen Champion")) item {
                val price = prices[championEntry.card.cardId]
                val visualEntry = championEntry.copy(deckQuantity = 1)
                DeckSlotItem(visualEntry, price, currencySymbol, false, false, { _, _ -> }, onDecrement, onAddMissing, onCardClick)
            }
        }
        if (runes.isNotEmpty()) {
            header("Runes", runes.sumOf { it.deckQuantity })
            if (!isCollapsed("Runes")) items(runes) { slot ->
                val price = prices[slot.card.cardId]
                val canAdd = runes.sumOf { it.deckQuantity } < 12
                DeckSlotItem(slot, price, currencySymbol, false, canAdd, onIncrement, onDecrement, onAddMissing, onCardClick)
            }
        }
        if (battlefields.isNotEmpty()) {
            val bfCount = battlefields.sumOf { it.deckQuantity }
            header("Battlefields", bfCount)
            if (!isCollapsed("Battlefields")) items(battlefields) { slot ->
                val price = prices[slot.card.cardId]
                val canAdd = bfCount < 3
                DeckSlotItem(slot, price, currencySymbol, false, canAdd, onIncrement, onDecrement, onAddMissing, onCardClick)
            }
        }
        if (processedMainDeck.isNotEmpty()) {
            val currentMainCount = processedMainDeck.sumOf { it.deckQuantity }
            header("Main Deck", currentMainCount)
            if (!isCollapsed("Main Deck")) {
                items(
                    items = processedMainDeck,
                    key = { item ->
                        val baseId = item.card.cardId
                        if (baseId == deck?.championCardId) "${baseId}_extra_main" else "${baseId}_main"
                    }
                ) { slot ->
                    val price = prices[slot.card.cardId]
                    val totalCopiesByName = totalCountsByName[slot.card.name] ?: 0
                    val canAdd = totalCopiesByName < DeckRules.COPY_LIMIT
                    DeckSlotItem(slot, price, currencySymbol, false, canAdd, onIncrement, onDecrement, onAddMissing, onCardClick)
                }
            }
        }
        if (sideSlots.isNotEmpty()) {
            header("Sideboard", sideSlots.sumOf { it.deckQuantity })
            if (!isCollapsed("Sideboard")) items(sideSlots, key = { "${it.card.cardId}_side" }) { slot ->
                val price = prices[slot.card.cardId]
                val totalCopiesByName = totalCountsByName[slot.card.name] ?: 0
                val sbTotal = sideSlots.sumOf { it.deckQuantity }
                val canAdd = totalCopiesByName < DeckRules.COPY_LIMIT && sbTotal < DeckRules.SIDEBOARD_MAX
                DeckSlotItem(slot, price, currencySymbol, true, canAdd, onIncrement, onDecrement, onAddMissing, onCardClick)
            }
        }
    }
}

@Composable
fun DeckSlotItem(
    slot: DeckCardView,
    price: Double?,
    currencySymbol: String,
    isSideboard: Boolean,
    canAdd: Boolean,
    onIncrement: (String, Boolean) -> Unit,
    onDecrement: (String, Boolean) -> Unit,
    onAddMissing: (String) -> Unit,
    onCardClick: (String) -> Unit
) {
    val card = slot.card
    val isGhost = slot.isMissingCards
    val isLandscape = card.type == CardType.BATTLEFIELD
    val imageRatio = if (isLandscape) 1.4f else 0.7f
    val imageHeight = 56.dp
    val imageWidth = imageHeight * imageRatio

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onCardClick(card.cardId) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        border = if (card.isBannedInStandard) BorderStroke(1.5.dp, Rift.Banned) else null
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Card Image Preview
            Card(
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.size(width = imageWidth, height = imageHeight)
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(card.imageUrl).crossfade(true).size(200).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.width(12.dp))

            // 2. Text Info
            Column(modifier = Modifier.weight(1f)) {
                BannedTag(card, Modifier.padding(bottom = 2.dp))
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isGhost) FontWeight.Normal else FontWeight.Bold,
                    color = if (isGhost) Color.White.copy(alpha = 0.6f) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                if (isGhost) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Missing ${slot.missingCount}", color = Color(0xFFFF9800), style = MaterialTheme.typography.labelSmall)

                        if (price != null && price > 0) {
                            val totalCost = price * slot.missingCount
                            Spacer(Modifier.width(8.dp))
                            Text("•", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${currencySymbol}${String.format("%.2f", totalCost)}",
                                color = Color(0xFF81C784), // Light Green
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    AddToCollectionChip(onClick = { onAddMissing(card.cardId) }, modifier = Modifier.padding(top = 6.dp))
                } else {
                    val showCost = card.type != CardType.LEGEND && card.type != CardType.RUNE && card.type != CardType.BATTLEFIELD
                    val typeText = card.type.name
                    val infoText = if (showCost) "${card.cost} Energy • $typeText" else typeText

                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray // <--- CHANGED to LightGray
                    )
                }
            }

            // 3. Actions (Right Side)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Decrement
                IconButton(
                    onClick = { onDecrement(slot.card.cardId, isSideboard) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Remove,
                        "Remove",
                        tint = Color.LightGray, // <--- CHANGED to LightGray
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Quantity
                Text(
                    text = "${slot.deckQuantity}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // Increment
                IconButton(
                    onClick = { if (canAdd) onIncrement(slot.card.cardId, isSideboard) },
                    modifier = Modifier.size(32.dp),
                    enabled = canAdd
                ) {
                    Icon(
                        Icons.Default.Add,
                        "Add",
                        tint = if (canAdd) Color.White else Color.White.copy(alpha = 0.2f), // <--- CHANGED to White
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckBrowseTab(
    issues: List<DeckIssue>,
    step: DeckBuildStep,
    title: String,
    instruction: String,
    cards: List<CardWithQuantity>,
    totalCountsById: Map<String, Int>,
    totalCountsByName: Map<String, Int>,
    activeZoneCounts: Map<String, Int>,
    viewModel: DeckEditorViewModel,
    allowedDomains: List<Domain>?,
    onAdd: (com.lburne.bounded.data.model.Card) -> Unit,
    onRemove: (String) -> Unit,
    /** Where taps go once the main deck is built (MAIN or SIDEBOARD), or null when there's no choice. */
    target: DeckStep? = null,
    onChooseTarget: (DeckStep) -> Unit = {}
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    val filter by viewModel.filter.collectAsState()

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            CardFilterSheet(
                filter = filter,
                onFilterChange = viewModel::updateFilter,
                resultCount = cards.size,
                onDone = { showFilterSheet = false },
                allowedDomains = allowedDomains
            )
        }
    }

    val gridState = rememberLazyGridState()
    LaunchedEffect(instruction) { gridState.scrollToItem(0) }

    val ownedCards = remember(cards) { cards.filter { it.quantity > 0 } }
    val unownedCards = remember(cards) { cards.filter { it.quantity == 0 } }
    val isLandscapeMode = cards.firstOrNull()?.card?.type == CardType.BATTLEFIELD

    Column(Modifier.fillMaxSize()) {
        // The step, instruction and search stay put; only the cards scroll.
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = instruction, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Rules a finished step now breaks, e.g. a card from before the rules were enforced.
                val ruleBreaks = issues.filter { it.code in RULE_BREAK_CODES }
                if (ruleBreaks.isNotEmpty()) {
                    Text(
                        text = ruleBreaks.joinToString(separator = "\n") { issue ->
                            issue.code.message + (issue.subject?.let { " ($it)" } ?: "")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (target != null) {
                Row(Modifier.border(1.dp, Rift.Line)) {
                    listOf(DeckStep.MAIN to "Main deck", DeckStep.SIDEBOARD to "Sideboard").forEachIndexed { i, (step, label) ->
                        val on = step == target
                        if (i > 0) Box(Modifier.width(1.dp).height(40.dp).background(Rift.Line))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(if (on) Rift.GoldWash else Color.Transparent)
                                .selectable(selected = on, role = Role.Tab) { onChooseTarget(step) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (on) Rift.Gold else Rift.Parchment, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(
                    query = filter.query,
                    onQueryChange = viewModel::setQuery,
                    placeholder = "Search cards",
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showFilterSheet = true }) {
                    BadgedBox(badge = { if (filter.activeCount > 0) Badge { Text(filter.activeCount.toString()) } }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters")
                    }
                }
            }
        }
        ActiveFilterRow(filter = filter, onFilterChange = viewModel::updateFilter, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))

        LazyVerticalGrid(
            state = gridState,
            columns = rememberCardGridCells(landscape = isLandscapeMode),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
            verticalArrangement = Arrangement.spacedBy(GridSpacing),
            modifier = Modifier.fillMaxSize()
        ) {
            fun androidx.compose.foundation.lazy.grid.LazyGridScope.cardItems(cardList: List<CardWithQuantity>) {
                items(cardList, key = { it.card.cardId }) { item ->
                    val visualCount = totalCountsById[item.card.cardId] ?: 0
                    val logicalCount = totalCountsByName[item.card.name] ?: 0
                    val currentZoneCount = activeZoneCounts[item.card.cardId] ?: 0
                    val shouldEnforceLimit = (step == DeckBuildStep.SELECT_MAIN || step == DeckBuildStep.SELECT_SIDEBOARD) &&
                        DeckRules.isPlayable(item.card)
                    val isMaxed = shouldEnforceLimit && logicalCount >= DeckRules.COPY_LIMIT
                    val clickAction = if (isMaxed) { {} } else { { onAdd(item.card) } }

                    DeckBuilderGridItem(
                        item = item,
                        deckCount = visualCount,
                        zoneCount = currentZoneCount,
                        isMaxed = isMaxed,
                        onClick = clickAction,
                        onRemove = { onRemove(item.card.cardId) }
                    )
                }
            }

            if (cards.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "No cards match",
                        body = if (filter.isEmpty) null else "Try a different search, or clear some filters."
                    )
                }
            }
            if (ownedCards.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Owned", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                cardItems(ownedCards)
            }
            if (unownedCards.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Not owned",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
                cardItems(unownedCards)
            }
        }
    }
}

@Composable
fun DeckBuilderGridItem(
    item: CardWithQuantity,
    deckCount: Int,
    zoneCount: Int,
    isMaxed: Boolean = false,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val card = item.card
    val ownedCount = item.quantity
    val isLandscape = card.type == CardType.BATTLEFIELD
    val aspectRatio = if (isLandscape) 1.4f else 0.7f

    // 1. Identify Base Runes
    val isPlentiful = card.type == CardType.RUNE && !card.cardId.substringAfter("-").any { it.isLowerCase() }

    val domainColor = if (card.domain.isNotEmpty()) getDomainColor(card.domain.first()) else Color.White

    // 2. Logic Update: Don't show warning color if the card is plentiful
    val showWarning = !isPlentiful && (deckCount > ownedCount)
    val badgeColor = if (showWarning) Color(0xFFFF9800) else domainColor

    val opacity = if (ownedCount > 0 || isPlentiful) 1f else 0.5f
    val finalOpacity = if (isMaxed) 0.3f else opacity

    val ownedText = if (isPlentiful) "∞" else "$ownedCount"

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio).clickable { onClick() },
        elevation = CardDefaults.cardElevation(4.dp),
        border = BorderStroke(2.dp, getCardBorderBrush(card.domain)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(card.imageUrl).crossfade(true).build(),
                contentDescription = card.name,
                modifier = Modifier.fillMaxSize().alpha(finalOpacity),
                contentScale = ContentScale.Crop
            )

            if (card.isBannedInStandard || card.isBannedIn2v2) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background((if (card.isBannedInStandard) Rift.Banned else Rift.Banned2v2).copy(alpha = 0.92f))
                        .padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BannedTag(card, fontSize = 12)
                }
            }

            if (zoneCount > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(28.dp).clickable { onRemove() },
                    shape = CircleShape, color = Color.Red.copy(alpha = 0.8f), contentColor = Color.White
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp)) }
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                color = Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "$deckCount | $ownedText",
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            if (isMaxed) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp)
                ) {
                    Text("MAX", color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}

/** Issues that mean the deck breaks a rule, as opposed to simply being unfinished. */
private val RULE_BREAK_CODES = setOf(
    IssueCode.INVALID_CHAMPION,
    IssueCode.COPY_LIMIT,
    IssueCode.SIGNATURE_LIMIT,
    IssueCode.SIGNATURE_WRONG_CHAMPION,
    IssueCode.OUTSIDE_DOMAIN_IDENTITY,
    IssueCode.DUPLICATE_BATTLEFIELD,
    IssueCode.NOT_PLAYABLE,
    IssueCode.NOT_ALLOWED_IN_SIDEBOARD,
    IssueCode.SIDEBOARD_TOO_LARGE,
    IssueCode.MAIN_DECK_TOO_LARGE,
    IssueCode.UNKNOWN_CARD,
    IssueCode.BANNED
)

/**
 * Heads the deck list when the collection is short of cards for this deck: how many,
 * what they'd cost, and a clearly labelled way to mark them all as owned.
 */
@Composable
private fun MissingCardsPanel(count: Int, value: Double, currencySymbol: String, onAddAll: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Rift.Surface)
            .border(1.dp, Rift.GoldDeep)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800), modifier = Modifier.size(22.dp))
            Column {
                Text(
                    "$count ${if (count == 1) "card" else "cards"} missing from your collection",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Rift.Ivory
                )
                Text(
                    if (value > 0) "About $currencySymbol${String.format(java.util.Locale.US, "%,.2f", value)} to buy." else "Cards this deck uses that you don't own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Rift.Muted
                )
            }
        }
        GoldButton(onClick = onAddAll, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.LibraryAdd, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add all $count to collection".uppercase(), style = RiftNumber.copy(fontSize = 15.sp, letterSpacing = 0.5.sp))
        }
    }
}

/** On a deck row the collection is short of: adds one copy of the card to the collection. */
@Composable
private fun AddToCollectionChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Rift.GoldWash)
            .border(1.dp, Rift.GoldDeep)
            .clickable(onClickLabel = "Add 1 copy to your collection", onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.LibraryAdd, null, tint = Rift.Gold, modifier = Modifier.size(14.dp))
        Text("Add 1 to collection", style = MaterialTheme.typography.labelMedium, color = Rift.Ivory)
    }
}

/** Says the deck isn't tournament legal, and why, at the top of the deck list. */
@Composable
private fun NotLegalBanner(reasons: List<String>) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Rift.Banned.copy(alpha = 0.15f))
            .border(1.dp, Rift.Banned)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "NOT LEGAL",
            style = RiftLabel.copy(fontSize = 12.sp),
            color = Color.White,
            modifier = Modifier.background(Rift.Banned).padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Text(reasons.joinToString("\n"), color = Rift.Parchment, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}
