package com.lburne.bounded.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.ui.binder.RiftSet
import com.lburne.bounded.ui.binder.getDomainColor

@Composable
fun CardDetailScreen(
    viewModel: CardDetailViewModel,
    onBack: () -> Unit
) {
    val currencySymbol = viewModel.getCurrencySymbol()
    val uiState by viewModel.uiState.collectAsState()

    // 1. Handle Loading/Error states specifically
    val state = uiState
    if (state !is CardDetailUiState.Success) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (state is CardDetailUiState.Loading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text("Error loading card", color = Color.White)
            }
        }
        return
    }

    val item = state.card
    val card = item.card
    val price = state.price

    // Safe multi-domain color handling
    val primaryColor = if (card.domain.isNotEmpty()) getDomainColor(card.domain.first()) else Color.DarkGray
    val isHorizontal = card.type == CardType.BATTLEFIELD

    // Resolve Set Name (e.g., "OGN" -> "Origins")
    val setName = RiftSet.values().find { it.code == card.setCode }?.displayName ?: card.setCode

    // Resolve Rarity Icon URL
    val rarityIconUrl = "https://lachieburne.github.io/Riftbounded/rarity_images/${card.rarity.name.lowercase()}.png"

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // 2. BLURRED BACKGROUND ART
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(card.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(15.dp).alpha(0.75f)
        )

        // 3. DARKENING GRADIENT
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha=0.4f), Color.Black.copy(alpha=0.95f))
                    )
                )
        )

        // 4. MAIN CONTENT
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                // Artist Credit
                if (card.artistName != null) {
                    Text(
                        text = "Art by ${card.artistName}",
                        color = Color.White.copy(0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // THE CARD IMAGE
            Card(
                elevation = CardDefaults.cardElevation(16.dp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(if (isHorizontal) 1.5f else 0.714f)
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(card.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = card.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }

            Spacer(Modifier.height(24.dp))

            // --- INFO PANEL ---
            Text(
                text = card.name,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            // PRICE
            if (price != null && price > 0) {
                Text(
                    text = "${currencySymbol}${String.format("%.2f", price)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFFFD700), // Gold color for money
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text(
                    text = "No Price Data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // TAGS ROW (Rarity Icon | Type | Set)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Rarity Icon
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(rarityIconUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = card.rarity.name,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit
                )

                Text(" • ", color = Color.White.copy(0.4f))

                // Type
                Text(
                    text = card.type.name,
                    color = primaryColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(" • ", color = Color.White.copy(0.4f))

                // Set Name
                Text(
                    text = setName,
                    color = Color.White.copy(0.8f),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(32.dp))

            // --- QUANTITY CONTROLS ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                FilledIconButton(
                    onClick = { viewModel.decrementQuantity() },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(0.1f))
                ) { Icon(Icons.Default.Remove, null, tint = Color.White) }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text(
                        text = "OWNED",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.5f),
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = item.quantity.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledIconButton(
                    onClick = { viewModel.incrementQuantity() },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = primaryColor)
                ) { Icon(Icons.Default.Add, null, tint = Color.Black) }
            }
        }
    }
}