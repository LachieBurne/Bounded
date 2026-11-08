package com.lburne.bounded.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lburne.bounded.data.filter.CardFilter
import com.lburne.bounded.data.filter.Ownership
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.data.model.RiftRarity
import com.lburne.bounded.ui.common.RiftSet

private const val ASSETS_URL = "https://lachieburne.github.io/Bounded"

fun domainIconUrl(domain: Domain) = "$ASSETS_URL/domain_images/${domain.name.lowercase()}.png"
fun rarityIconUrl(rarity: RiftRarity) = "$ASSETS_URL/rarity_images/${rarity.name.lowercase()}.png"

/** "SHOWCASE" -> "Showcase". */
fun Enum<*>.label(): String = name.lowercase().replaceFirstChar { it.titlecase() }

fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

/** Card types you can filter by. Token helpers ("OTHER") are left out. */
val FilterableTypes = CardType.values().filter { it != CardType.OTHER }

/** Rarities in the order they're printed. */
val FilterableRarities = RiftRarity.values().toList()

private val StatValues = (0 until CardFilter.STAT_MAX).toList() + CardFilter.STAT_MAX
private fun statLabel(value: Int) = if (value == CardFilter.STAT_MAX) "$value+" else value.toString()

/**
 * Every filter for a card grid. Changes apply straight away, so the grid behind the sheet
 * updates as you tap; [resultCount] says how many cards match.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CardFilterSheet(
    filter: CardFilter,
    onFilterChange: (CardFilter) -> Unit,
    resultCount: Int,
    onDone: () -> Unit,
    showOwnership: Boolean = true,
    allowedDomains: List<Domain>? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(
                onClick = { onFilterChange(CardFilter(query = filter.query)) },
                enabled = filter.activeCount > 0
            ) { Text("Reset") }
        }

        if (showOwnership) {
            SectionTitle("Collection")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Ownership.values().forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = filter.ownership == option,
                        onClick = { onFilterChange(filter.copy(ownership = option)) },
                        shape = SegmentedButtonDefaults.itemShape(index, Ownership.values().size)
                    ) { Text(option.label) }
                }
            }
        }

        SectionTitle("Set")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RiftSet.values().forEach { set ->
                ToggleChip(set.displayName, set.code in filter.sets) {
                    onFilterChange(filter.copy(sets = filter.sets.toggle(set.code)))
                }
            }
        }

        SectionTitle("Domain")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (allowedDomains ?: Domain.values().toList()).forEach { domain ->
                ToggleChip(domain.label(), domain in filter.domains, iconUrl = domainIconUrl(domain)) {
                    onFilterChange(filter.copy(domains = filter.domains.toggle(domain)))
                }
            }
        }

        SectionTitle("Rarity")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterableRarities.forEach { rarity ->
                ToggleChip(rarity.label(), rarity in filter.rarities, iconUrl = rarityIconUrl(rarity)) {
                    onFilterChange(filter.copy(rarities = filter.rarities.toggle(rarity)))
                }
            }
        }

        SectionTitle("Type")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterableTypes.forEach { type ->
                ToggleChip(type.label(), type in filter.types) {
                    onFilterChange(filter.copy(types = filter.types.toggle(type)))
                }
            }
        }

        SectionTitle("Energy cost")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatValues.forEach { cost ->
                ToggleChip(statLabel(cost), cost in filter.costs) {
                    onFilterChange(filter.copy(costs = filter.costs.toggle(cost)))
                }
            }
        }

        SectionTitle("Might")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatValues.forEach { might ->
                ToggleChip(statLabel(might), might in filter.mights) {
                    onFilterChange(filter.copy(mights = filter.mights.toggle(might)))
                }
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp)
        ) {
            Text(
                when (resultCount) {
                    0 -> "No cards match"
                    1 -> "Show 1 card"
                    else -> "Show $resultCount cards"
                }
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun ToggleChip(label: String, selected: Boolean, iconUrl: String? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = when {
            iconUrl != null -> {
                {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            }
            selected -> {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            }
            else -> null
        }
    )
}

/** One removable chip for each active filter value, shown above a grid. */
data class ActiveFilter(val label: String, val remove: (CardFilter) -> CardFilter)

fun CardFilter.activeFilters(): List<ActiveFilter> = buildList {
    if (ownership != Ownership.ALL) add(ActiveFilter(ownership.label) { it.copy(ownership = Ownership.ALL) })
    sets.forEach { code ->
        val name = RiftSet.values().firstOrNull { it.code == code }?.displayName ?: code
        add(ActiveFilter(name) { it.copy(sets = it.sets - code) })
    }
    domains.forEach { d -> add(ActiveFilter(d.label()) { it.copy(domains = it.domains - d) }) }
    rarities.forEach { r -> add(ActiveFilter(r.label()) { it.copy(rarities = it.rarities - r) }) }
    types.forEach { t -> add(ActiveFilter(t.label()) { it.copy(types = it.types - t) }) }
    costs.sorted().forEach { c -> add(ActiveFilter("Cost ${statLabel(c)}") { it.copy(costs = it.costs - c) }) }
    mights.sorted().forEach { m -> add(ActiveFilter("Might ${statLabel(m)}") { it.copy(mights = it.mights - m) }) }
}

@Composable
fun ActiveFilterRow(
    filter: CardFilter,
    onFilterChange: (CardFilter) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    val active = filter.activeFilters()
    if (active.isEmpty() && leading == null) return
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        if (leading != null) item { leading() }
        items(active, key = { it.label }) { chip ->
            InputChip(
                selected = true,
                onClick = { onFilterChange(chip.remove(filter)) },
                label = { Text(chip.label) },
                trailingIcon = {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${chip.label} filter", modifier = Modifier.size(InputChipDefaults.IconSize))
                }
            )
        }
    }
}
