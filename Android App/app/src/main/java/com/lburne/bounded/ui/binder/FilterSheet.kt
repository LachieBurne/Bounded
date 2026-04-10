package com.lburne.bounded.ui.binder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.data.model.RiftRarity

private const val GITHUB_ASSETS_URL = "https://lachieburne.github.io/Riftbounded"

private fun getRarityIconUrl(rarity: RiftRarity): String {
    return "$GITHUB_ASSETS_URL/rarity_images/${rarity.name.lowercase()}.png"
}

private fun getDomainIconUrl(domain: Domain): String {
    return "$GITHUB_ASSETS_URL/domain_images/${domain.name.lowercase()}.png"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    uiState: BinderUiState,
    allowedDomains: List<Domain>? = null,
    onDomainChanged: (Domain?) -> Unit,
    onRarityChanged: (RiftRarity?) -> Unit,
    onTypeChanged: (CardType?) -> Unit,
    onSetChanged: (String?) -> Unit,
    onCostChanged: (Int?) -> Unit,
    onMightChanged: (Int?) -> Unit,
    onOwnedOnlyChanged: (Boolean) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onClearAll) { Text("Clear All") }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 1. Rarity
        Text("Rarity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            items(RiftRarity.values()) { rarity ->
                FilterIcon(
                    imageUrl = getRarityIconUrl(rarity),
                    isSelected = uiState.filterRarity == rarity,
                    onClick = { onRarityChanged(if (uiState.filterRarity == rarity) null else rarity) },
                    description = rarity.name
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 2. Domain
        Text("Domain", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            val domainsToShow = allowedDomains ?: Domain.values().toList()
            items(domainsToShow) { domain ->
                FilterIcon(
                    imageUrl = getDomainIconUrl(domain),
                    isSelected = uiState.filterDomain == domain,
                    onClick = { onDomainChanged(if (uiState.filterDomain == domain) null else domain) },
                    description = domain.name
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 3. Card Type
        Text("Card Type", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            items(CardType.values()) { type ->
                FilterChip(
                    selected = uiState.filterType == type,
                    onClick = { onTypeChanged(if (uiState.filterType == type) null else type) },
                    label = { Text(type.name.lowercase().replaceFirstChar { it.titlecase() }) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 4. Cost
        Text("Cost", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0..12).toList()) { cost ->
                FilterChip(
                    selected = uiState.filterCost == cost,
                    onClick = { onCostChanged(if (uiState.filterCost == cost) null else cost) },
                    label = { Text(cost.toString()) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 5. Might
        Text("Might", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0..10).toList()) { might ->
                FilterChip(
                    selected = uiState.filterMight == might,
                    onClick = { onMightChanged(if (uiState.filterMight == might) null else might) },
                    label = { Text(might.toString()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFD32F2F),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 6. Card Set
        Text("Set", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            items(RiftSet.values()) { set ->
                FilterChip(
                    selected = uiState.filterSet == set.code,
                    onClick = { onSetChanged(if (uiState.filterSet == set.code) null else set.code) },
                    label = { Text(set.displayName) }
                )
            }
        }

        // 7. Show Owned Only
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Show Owned Only", modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.showOwnedOnly,
                onCheckedChange = onOwnedOnlyChanged
            )
        }
    }
}

@Composable
fun FilterIcon(
    imageUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    description: String
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val containerAlpha = if (isSelected) 1f else 0f
    val iconAlpha = if (isSelected) 1f else 0.6f
    val scale = if (isSelected) 1.15f else 1f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(4.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(scale)
                .border(width = if (isSelected) 2.dp else 0.dp, color = borderColor, shape = CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = containerAlpha))
                .padding(6.dp)
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .size(coil.size.Size.ORIGINAL)
                    .build(),
                contentDescription = description,
                loading = { CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp) },
                modifier = Modifier.fillMaxSize().alpha(iconAlpha),
                contentScale = ContentScale.Fit
            )
        }
    }
}