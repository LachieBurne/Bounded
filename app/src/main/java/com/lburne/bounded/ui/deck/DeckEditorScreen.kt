package com.lburne.bounded.ui.deck

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
import androidx.compose.material.icons.filled.Download
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
import com.lburne.bounded.ui.binder.BinderUiState
import com.lburne.bounded.ui.binder.FilterSheet
import com.lburne.bounded.ui.binder.SleekSearchBar
import com.lburne.bounded.ui.binder.getCardBorderBrush
import com.lburne.bounded.ui.binder.getDomainColor

private const val GITHUB_ASSETS_URL = "https://lachieburne.github.io/Riftbounded"

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

    val context = LocalContext.current

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

    val activeZoneCounts = remember(currentStep, mainSlots, sideSlots) {
        val targetSlots = if (currentStep == DeckBuildStep.SELECT_SIDEBOARD) sideSlots else mainSlots
        val map = mutableMapOf<String, Int>()
        targetSlots.forEach { map[it.card.cardId] = it.deckQuantity }
        map
    }

    val hasMissingCards = remember(mainSlots, sideSlots) {
        (mainSlots + sideSlots).any { it.isMissingCards }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentStep) {
        if (currentStep == DeckBuildStep.COMPLETE) {
            selectedTabIndex = 0
        } else if (currentStep == DeckBuildStep.SELECT_LEGEND) {
            selectedTabIndex = 1
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(deck?.name ?: "Edit Deck") },
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
                            Icon(Icons.Default.Share, contentDescription = "Export Deck", tint = Color.White)
                        }

                        if (hasMissingCards) {
                            IconButton(onClick = { viewModel.onAddAllMissingToBinder() }) {
                                Icon(
                                    imageVector = Icons.Default.LibraryAdd,
                                    contentDescription = "Add all missing cards to binder",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text(
                            text="My Deck",
                            color=Color.White
                        ) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text(
                            text="Add Cards",
                            color=Color.White
                        ) }
                    )
                    // NEW TAB 3: STATS
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = { Text(
                            text="Stats",
                            color=Color.White
                        ) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (selectedTabIndex) {
                0 -> {
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
                        onCardClick = onCardClick
                    )
                }
                1 -> {
                    val runeCount = mainSlots.sumOf { if (it.card.type == CardType.RUNE) it.deckQuantity else 0 }
                    val battlefieldCount = mainSlots.sumOf { if (it.card.type == CardType.BATTLEFIELD) it.deckQuantity else 0 }
                    val totalMain = mainSlots.sumOf { it.deckQuantity }
                    val variableCount = (totalMain - 17).coerceAtLeast(0)
                    val sideCount = sideSlots.sumOf { it.deckQuantity }

                    val dynamicTitle = when (currentStep) {
                        DeckBuildStep.SELECT_RUNES -> "Select Runes ($runeCount/12)"
                        DeckBuildStep.SELECT_BATTLEFIELDS -> "Select Battlefields ($battlefieldCount/3)"
                        DeckBuildStep.SELECT_MAIN -> "Main Deck ($variableCount/39)"
                        DeckBuildStep.SELECT_SIDEBOARD -> "Sideboard ($sideCount/8)"
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

                    DeckBrowseTab(
                        step = currentStep,
                        title = dynamicTitle,
                        instruction = currentStep.instruction,
                        cards = availableCards,
                        totalCountsById = totalCountsById,
                        totalCountsByName = totalCountsByName,
                        activeZoneCounts = activeZoneCounts,
                        viewModel = viewModel,
                        allowedDomains = allowedFilterDomains,
                        onAdd = { cardObj -> viewModel.onCardSelect(cardObj) },
                        onRemove = { cardId -> viewModel.onRemoveCard(cardId) },
                        onCardClick = onCardClick
                    )
                }
                // NEW: STATS TAB RENDER
                2 -> {
                    DeckStatsTab(stats = deckStats, currencySymbol = currencySymbol)
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
    onCardClick: (String) -> Unit
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

        // Section rendering remains same...
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
                    val canAdd = totalCopiesByName < 3 && currentMainCount < 39
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
                val canAdd = totalCopiesByName < 3 && sbTotal < 8
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
        tonalElevation = 2.dp
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
                        Text("Need ${slot.missingCount}", color = Color(0xFFFF9800), style = MaterialTheme.typography.labelSmall)

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
                // Add to Binder (Download)
                if (isGhost) {
                    IconButton(
                        onClick = { onAddMissing(card.cardId) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            "Add to Binder",
                            tint = Color.White, // <--- CHANGED to White
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

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
    onCardClick: (String) -> Unit
) {
    var showFilterSheet by remember { mutableStateOf(false) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterDomain by viewModel.filterDomain.collectAsState()
    val filterRarity by viewModel.filterRarity.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val filterCost by viewModel.filterCost.collectAsState()
    val filterMight by viewModel.filterMight.collectAsState()
    val showOwnedOnly by viewModel.showOwnedOnly.collectAsState()

    val isFiltering = filterDomain != null || filterRarity != null || filterType != null || filterCost != null || filterMight != null || showOwnedOnly

    val dummyUiState = BinderUiState(
        searchQuery = searchQuery,
        filterDomain = filterDomain,
        filterRarity = filterRarity,
        filterType = filterType,
        filterCost = filterCost,
        filterMight = filterMight,
        showOwnedOnly = showOwnedOnly
    )

    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            FilterSheet(
                uiState = dummyUiState,
                allowedDomains = allowedDomains,
                onDomainChanged = { viewModel.onDomainSelected(it) },
                onRarityChanged = { viewModel.onRaritySelected(it) },
                onTypeChanged = { viewModel.onTypeSelected(it) },
                onSetChanged = { viewModel.onSetSelected(it) },
                onCostChanged = { viewModel.onCostSelected(it) },
                onMightChanged = { viewModel.onMightSelected(it) },
                onOwnedOnlyChanged = { viewModel.onToggleOwnedOnly(it) },
                onClearAll = { viewModel.clearFilters() }
            )
        }
    }

    val gridState = rememberLazyGridState()
    LaunchedEffect(instruction) { gridState.scrollToItem(0) }

    val ownedCards = remember(cards) { cards.filter { it.quantity > 0 } }
    val unownedCards = remember(cards) { cards.filter { it.quantity == 0 } }
    val isLandscapeMode = cards.firstOrNull()?.card?.type == CardType.BATTLEFIELD
    val minColumnSize = if (isLandscapeMode) 160.dp else 120.dp

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = minColumnSize),
            contentPadding = PaddingValues(top = 140.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            fun androidx.compose.foundation.lazy.grid.LazyGridScope.cardItems(cardList: List<CardWithQuantity>) {
                items(cardList, key = { it.card.cardId }) { item ->
                    val visualCount = totalCountsById[item.card.cardId] ?: 0
                    val logicalCount = totalCountsByName[item.card.name] ?: 0
                    val currentZoneCount = activeZoneCounts[item.card.cardId] ?: 0
                    val isRune = item.card.type == CardType.RUNE
                    val shouldEnforceLimit = (step == DeckBuildStep.SELECT_MAIN || step == DeckBuildStep.SELECT_SIDEBOARD) && !isRune
                    val isMaxed = shouldEnforceLimit && logicalCount >= 3
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

            if (ownedCards.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Owned Cards", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                }
                cardItems(ownedCards)
            }
            if (unownedCards.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Unowned Cards", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                }
                cardItems(unownedCards)
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 4.dp)
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = instruction, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SleekSearchBar(
                    query = searchQuery,
                    onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onFilterClicked = { showFilterSheet = true },
                    isFilterActive = isFiltering,
                    onSettingsClicked = null
                )
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