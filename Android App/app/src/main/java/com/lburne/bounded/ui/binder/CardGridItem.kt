package com.lburne.bounded.ui.binder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.Domain

@Composable
fun CardGridItem(
    item: CardWithQuantity,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onClick: () -> Unit
) {
    val card = item.card
    val borderBrush = getCardBorderBrush(card.domain)
    val domainColor = getDomainColor(card.domain.first())

    // Visual State: Is the card owned?
    val isOwned = item.quantity > 0
    val opacity = if (isOwned) 1f else 0.6f // Dim unowned cards slightly

    val isHorizontal = card.type == CardType.BATTLEFIELD

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(if (isOwned) 4.dp else 0.dp),
        colors = CardDefaults.cardColors(
            // Use 'Surface Variant' (A slightly lighter grey than the background)
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (isOwned) BorderStroke(2.dp, borderBrush) else BorderStroke(1.dp, Color.LightGray),
        shape = RoundedCornerShape(8.dp) // TCG cards have rounded corners
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest
            )) {

            // 1. THE CARD ART (Dominates the view)
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(card.imageUrl)
                    .crossfade(true)
                    .transformations(
                        if (isHorizontal) listOf(RotateTransformation(90f)) else emptyList()
                    )
                    .build(),
                contentDescription = card.name,
                loading = {
                    LoadingCardPlaceholder()
                },
                error = {
                    ErrorCardPlaceholder()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f) // Standard Magic/Pokemon/Lor card ratio
                    .background(Color.DarkGray), // Dark background behind image
                contentScale = ContentScale.Crop,
                alpha = opacity
            )

            // 2. THE QUANTITY CONTROLS (Overlay at bottom)
            // We use a gradient background so text is readable without blocking art too much
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(top = 24.dp, bottom = 4.dp), // Gradient spacing
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Minus Button (Small & White)
                    SmallIconButton(onClick = onDecrement, icon = Icons.Default.Remove)

                    // Quantity Counter
                    Text(
                        text = item.quantity.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isOwned) domainColor else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )

                    // Plus Button (Small & White)
                    SmallIconButton(onClick = onIncrement, icon = Icons.Default.Add)
                }
            }
        }
    }
}

// Helper for smaller buttons to fit the minimal look
@Composable
fun SmallIconButton(onClick: () -> Unit, icon: ImageVector) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun getCardBorderBrush(domains: List<Domain>): Brush {
    if (domains.isEmpty()) return SolidColor(Color.Gray)

    // If only 1 domain, return solid color
    if (domains.size == 1) {
        return SolidColor(getDomainColor(domains.first()))
    }

    // If multiple domains, create a Linear Gradient
    val colors = domains.map { getDomainColor(it) }
    return Brush.linearGradient(colors = colors)
}

// Helper to map Riftbound Domains to UI Colors
fun getDomainColor(domain: Domain): Color {
    return when (domain) {
        Domain.FURY -> Color(0xFFE53935)   // Red
        Domain.CALM -> Color(0xFF43A047) // Green
        Domain.MIND -> Color(0xFF1E88E5)  // Blue
        Domain.BODY -> Color(0xFFFB8C00)// Orange
        Domain.CHAOS -> Color(0xFF8E24AA) // Purple
        Domain.ORDER -> Color(0xFFFDD835) // Yellow
        Domain.COLORLESS -> Color.Gray
    }
}