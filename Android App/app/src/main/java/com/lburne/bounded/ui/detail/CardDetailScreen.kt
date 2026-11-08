package com.lburne.bounded.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.ui.common.RiftSet
import com.lburne.bounded.ui.common.getDomainColor
import com.lburne.bounded.ui.common.isLandscape
import com.lburne.bounded.ui.common.label
import com.lburne.bounded.ui.common.rarityIconUrl

/**
 * One card, large, with its price, rules text and the number you own. Phones stack the art
 * over the details; wide or landscape windows put them side by side.
 */
@Composable
fun CardDetailScreen(
    viewModel: CardDetailViewModel,
    onBack: () -> Unit
) {
    val currencySymbol = viewModel.getCurrencySymbol()
    val state = viewModel.uiState.collectAsState().value

    if (state !is CardDetailUiState.Success) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (state is CardDetailUiState.Loading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text("This card couldn't be loaded.", color = Color.White)
            }
        }
        return
    }

    val item = state.card
    val card = item.card

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Blurred art behind everything, darkened so text stays readable.
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(card.imageUrl).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(15.dp).alpha(0.75f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.95f))))
        )

        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val sideBySide = maxWidth > maxHeight && maxWidth >= 560.dp
            val viewportHeight = maxHeight
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    if (card.artistName != null) {
                        Text("Art by ${card.artistName}", color = Color.White.copy(0.7f), fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
                    }
                }

                if (sideBySide) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            CardArt(card.imageUrl, card.isLandscape, card.name)
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center
                        ) {
                            CardInfo(item, state.price, currencySymbol, centred = false)
                            Spacer(Modifier.height(24.dp))
                            QuantityStepper(item.quantity, card.domain.firstOrNull()?.let { getDomainColor(it) } ?: Color.DarkGray, viewModel)
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Art at most about 60% of the screen height, so the count is visible without scrolling.
                        val artRatio = if (card.isLandscape) 88f / 63f else 63f / 88f
                        Box(Modifier.widthIn(max = viewportHeight * 0.6f * artRatio).padding(vertical = 8.dp)) {
                            CardArt(card.imageUrl, card.isLandscape, card.name)
                        }
                        Spacer(Modifier.height(16.dp))
                        QuantityStepper(item.quantity, card.domain.firstOrNull()?.let { getDomainColor(it) } ?: Color.DarkGray, viewModel)
                        Spacer(Modifier.height(24.dp))
                        CardInfo(item, state.price, currencySymbol, centred = true)
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CardArt(imageUrl: String, landscape: Boolean, name: String) {
    Card(
        elevation = CardDefaults.cardElevation(16.dp),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.aspectRatio(if (landscape) 88f / 63f else 63f / 88f)
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(),
            contentDescription = name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardInfo(item: CardWithQuantity, price: Double?, currencySymbol: String, centred: Boolean) {
    val card = item.card
    val setName = card.setName.ifBlank { RiftSet.nameOf(card.setCode) }
    val align = if (centred) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centred) TextAlign.Center else TextAlign.Start
    val arrangement = if (centred) Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(8.dp)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(card.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, textAlign = textAlign)
        Text(
            if (price != null && price > 0) "$currencySymbol${String.format(java.util.Locale.US, "%,.2f", price)}" else "No price yet",
            style = MaterialTheme.typography.titleLarge,
            color = if (price != null && price > 0) Color(0xFFFFD700) else Color.White.copy(0.5f)
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubcomposeAsyncImage(
                model = rarityIconUrl(card.rarity),
                contentDescription = card.rarity.label(),
                modifier = Modifier.size(20.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                "${card.rarity.label()} ${card.type.label()} · $setName · ${card.cardId}",
                color = Color.White.copy(0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (card.tags.isNotEmpty() || card.domain.isNotEmpty()) {
            FlowRow(horizontalArrangement = arrangement) {
                card.domain.forEach { domain ->
                    AssistChip(onClick = {}, label = { Text(domain.label(), color = getDomainColor(domain)) })
                }
                card.tags.forEach { tag ->
                    AssistChip(onClick = {}, label = { Text(tag, color = Color.White) })
                }
            }
        }

        val rules = card.text ?: card.effect
        if (!rules.isNullOrBlank()) {
            Text(
                rules,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(0.9f),
                textAlign = textAlign,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun QuantityStepper(quantity: Int, accent: Color, viewModel: CardDetailViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        FilledIconButton(
            onClick = { viewModel.decrementQuantity() },
            enabled = quantity > 0,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(0.1f))
        ) { Icon(Icons.Default.Remove, "Remove a copy", tint = Color.White) }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Text("OWNED", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f), letterSpacing = 2.sp)
            Text(quantity.toString(), style = MaterialTheme.typography.displayMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }

        FilledIconButton(
            onClick = { viewModel.incrementQuantity() },
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent)
        ) { Icon(Icons.Default.Add, "Add a copy", tint = Color.Black) }
    }
}
