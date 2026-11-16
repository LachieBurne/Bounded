package com.lburne.bounded.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.Finish
import com.lburne.bounded.data.model.finishes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(
    scannedCardIds: List<String>,
    onBack: () -> Unit,
    onNavigateToBinder: () -> Unit,
    viewModel: ScanResultViewModel
) {
    val scannedCards by viewModel.scannedCards.collectAsState()
    val selectedIndices by viewModel.selectedIndices.collectAsState()
    val signatureSiblings by viewModel.signatureSiblings.collectAsState()
    val finishes by viewModel.finishes.collectAsState()

    LaunchedEffect(scannedCardIds) {
        viewModel.loadScannedCards(scannedCardIds)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Results") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { 
                        if (selectedIndices.size == scannedCards.size) viewModel.deselectAll() else viewModel.selectAll()
                    }) {
                        Text(if (selectedIndices.size == scannedCards.size) "Deselect All" else "Select All", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF13131A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF1B1B22),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedIndices.size} Selected",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Button(
                        onClick = { 
                            viewModel.addSelectedToBinder(null) {
                                onNavigateToBinder()
                            }
                        },
                        enabled = selectedIndices.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9D4EDD))
                    ) {
                        Text("Add to Binder")
                    }
                }
            }
        },
        containerColor = Color(0xFF0D0D12)
    ) { padding ->
        if (scannedCards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No cards detected.", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scannedCards.size) { index ->
                    val card = scannedCards[index]
                    val isSelected = selectedIndices.contains(index)
                    val price = viewModel.getPriceForCard(card.cardId)
                    val currency = viewModel.getCurrencySymbol()
                    
                    ScanResultCardItem(
                        card = card,
                        isSelected = isSelected,
                        price = price,
                        currency = currency,
                        onClick = { viewModel.toggleSelection(index) },
                        isSignature = if (card.cardId in signatureSiblings) card.cardId.endsWith("*") else null,
                        onToggleSignature = { viewModel.toggleSignature(index) },
                        finish = finishes[index] ?: Finish.STANDARD,
                        onToggleFinish = { viewModel.toggleFinish(index, it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScanResultCardItem(
    card: Card,
    isSelected: Boolean,
    price: Double?,
    currency: String,
    onClick: () -> Unit,
    /** Whether this is the signature printing, or null when the card has no signature version. */
    isSignature: Boolean? = null,
    onToggleSignature: () -> Unit = {},
    finish: Finish = Finish.STANDARD,
    onToggleFinish: (Finish) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF9D4EDD) else Color.DarkGray,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A))
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )
                
                // Selection indicator overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isSelected) Color(0x339D4EDD) else Color.Transparent)
                )

                // Checkbox icon
                androidx.compose.material3.Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.align(Alignment.TopEnd),
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF9D4EDD),
                        uncheckedColor = Color.White.copy(alpha = 0.5f)
                    )
                )
            }
            
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val priceText = if (price != null) "$currency${String.format("%.2f", price)}" else "---"
                Text(
                    text = priceText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Green.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )

                // A foil or a signature has the same number as the plain card, so the scanner
                // can't tell them apart. These say which one was scanned.
                val otherFinishes = card.finishes - Finish.STANDARD
                if (isSignature != null || otherFinishes.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        otherFinishes.forEach { option ->
                            FilterChip(
                                selected = finish == option,
                                onClick = { onToggleFinish(option) },
                                label = { Text(option.label) }
                            )
                        }
                        if (isSignature != null) {
                            FilterChip(
                                selected = isSignature,
                                onClick = onToggleSignature,
                                label = { Text("Signature") }
                            )
                        }
                    }
                }
            }
        }
    }
}
