package com.lburne.bounded.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.ui.theme.Rift
import com.lburne.bounded.ui.theme.RiftNumber
import com.lburne.bounded.ui.theme.TabShape

val CardShape = RoundedCornerShape(6.dp)

private val GreyscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Battlefields are printed landscape. */
val Card.isLandscape: Boolean get() = type == CardType.BATTLEFIELD || orientation == "landscape"

/**
 * A card's art at card proportions. Landscape cards are turned upright when [upright] is set,
 * so they line up with the rest of a grid. Cards you don't own can be shown in grey.
 */
@Composable
fun CardImage(
    card: Card,
    modifier: Modifier = Modifier,
    upright: Boolean = true,
    greyedOut: Boolean = false
) {
    val context = LocalContext.current
    val rotate = upright && card.isLandscape
    val request = remember(card.imageUrl, rotate) {
        ImageRequest.Builder(context)
            .data(card.imageUrl)
            .crossfade(true)
            .apply { if (rotate) transformations(RotateTransformation(90f)) }
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        loading = { LoadingCardPlaceholder() },
        error = { ErrorCardPlaceholder() },
        contentScale = ContentScale.Crop,
        colorFilter = if (greyedOut) GreyscaleFilter else null,
        alpha = if (greyedOut) 0.55f else 1f,
        modifier = modifier
            .aspectRatio(if (card.isLandscape && !upright) 1f / CARD_ASPECT_RATIO else CARD_ASPECT_RATIO)
            .clip(CardShape)
    )
}

/**
 * A card in a collection grid: the art, greyed out if you have no copies, with the number
 * owned. When the cell is wide enough, minus and plus buttons sit under the art; on the
 * smallest card size, open the card to change the count.
 */
@Composable
fun CardTile(
    card: Card,
    quantity: Int,
    onClick: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val showStepper = maxWidth >= 104.dp
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .clickable(onClickLabel = "Open card") { onClick() }
                    .semantics { contentDescription = "${card.name}, ${quantityLabel(quantity)}" }
            ) {
                CardImage(card = card, greyedOut = quantity == 0, modifier = Modifier.fillMaxWidth())
                if (quantity > 0 && !showStepper) {
                    QuantityBadge(quantity, Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp))
                }
            }
            DomainStripe(card.domain, faded = quantity == 0, modifier = Modifier.padding(top = 5.dp))
            if (showStepper) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDecrement, enabled = quantity > 0) {
                        Icon(Icons.Default.Remove, contentDescription = "Remove a copy of ${card.name}", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = quantity.toString(),
                        style = RiftNumber.copy(fontSize = 18.sp),
                        textAlign = TextAlign.Center,
                        color = if (quantity > 0) Rift.Gold else Rift.Muted,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onIncrement) {
                        Icon(Icons.Default.Add, contentDescription = "Add a copy of ${card.name}", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** The number owned, on a gold-edged tab over the corner of the art. */
@Composable
fun QuantityBadge(quantity: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Rift.Ink, TabShape)
            .border(1.dp, Rift.Gold, TabShape)
            .padding(horizontal = 7.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "×$quantity", style = RiftNumber.copy(fontSize = 15.sp), color = Rift.Ivory)
    }
}

private fun quantityLabel(quantity: Int) = when (quantity) {
    0 -> "not owned"
    1 -> "1 copy owned"
    else -> "$quantity copies owned"
}

/** A placeholder the size of the grid, for empty results. */
@Composable
fun EmptyState(title: String, body: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        action?.invoke()
    }
}

/** Fills a cell while a grid is loading. */
@Composable
fun CardSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) { Box(Modifier.fillMaxSize()) }
}
