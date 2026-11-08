package com.lburne.bounded.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lburne.bounded.ui.theme.ChamferSmall
import com.lburne.bounded.ui.theme.Rift
import com.lburne.bounded.ui.theme.RiftLabel
import com.lburne.bounded.ui.theme.RiftNumber
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
    SheetWithAction(
        action = {
            GoldButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (resultCount) {
                        0 -> "No cards match"
                        1 -> "Show 1 card"
                        else -> "Show $resultCount cards"
                    }.uppercase(),
                    style = RiftNumber.copy(fontSize = 17.sp, letterSpacing = 1.sp)
                )
            }
        }
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RiftLabelText("Filters", style = RiftLabel.copy(fontSize = 22.sp), modifier = Modifier.weight(1f))
            TextButton(
                onClick = { onFilterChange(CardFilter(query = filter.query)) },
                enabled = filter.activeCount > 0
            ) { Text("RESET", style = RiftNumber.copy(fontSize = 15.sp, letterSpacing = 1.sp)) }
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

        SectionTitle("Domain · any of")
        val domains = allowedDomains ?: Domain.values().toList()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            domains.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { domain ->
                        DomainToggle(domain, domain in filter.domains, Modifier.weight(1f)) {
                            onFilterChange(filter.copy(domains = filter.domains.toggle(domain)))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        SectionTitle("Rarity")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterableRarities.forEach { rarity ->
                ToggleChip(rarity.label(), rarity in filter.rarities, leading = { RarityGem(rarity) }) {
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatValues.forEach { cost ->
                StatToken(statLabel(cost), cost in filter.costs, "Energy cost") {
                    onFilterChange(filter.copy(costs = filter.costs.toggle(cost)))
                }
            }
        }

        SectionTitle("Might")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatValues.forEach { might ->
                StatToken(statLabel(might), might in filter.mights, "Might") {
                    onFilterChange(filter.copy(mights = filter.mights.toggle(might)))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Bottom sheet content that scrolls, with [action] pinned underneath so it's always in reach
 * however long the content is. Use it directly inside a ModalBottomSheet.
 */
@Composable
fun SheetWithAction(
    action: @Composable () -> Unit,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = verticalArrangement,
            content = content
        )
        HorizontalDivider()
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) { action() }
    }
}

@Composable
private fun SectionTitle(text: String) {
    RiftLabelText(text, color = Rift.Muted, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

/**
 * A filter option. Selected options get a gold edge on a gold wash. [iconUrl] or [leading]
 * puts an icon before the label.
 */
@Composable
fun ToggleChip(
    label: String,
    selected: Boolean,
    iconUrl: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .background(if (selected) Rift.GoldWash else Rift.Raised)
            .border(1.dp, if (selected) Rift.Gold else Rift.Line)
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when {
            leading != null -> leading()
            iconUrl != null -> AsyncImage(model = iconUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(20.dp))
        }
        Text(label, fontSize = 14.sp, color = if (selected) Rift.Ivory else Rift.Parchment)
    }
}

/** A domain option: its sigil and name, filled with the domain's band colour when chosen. */
@Composable
private fun DomainToggle(domain: Domain, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .background(if (selected) getDomainBand(domain) else Rift.Raised, ChamferSmall)
            .border(1.dp, if (selected) getDomainColor(domain) else Rift.Line, ChamferSmall)
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AsyncImage(model = domainIconUrl(domain), contentDescription = null, modifier = Modifier.size(26.dp))
        ShrinkToFitText(domain.label(), maxSize = 15f, color = Rift.Ivory)
    }
}

/** One line of text that steps its size down until it fits, e.g. "Colorless" in a narrow toggle. */
@Composable
private fun ShrinkToFitText(text: String, maxSize: Float, color: Color, minSize: Float = 11f) {
    var size by remember(text) { mutableFloatStateOf(maxSize) }
    var fitted by remember(text) { mutableStateOf(false) }
    Text(
        text,
        fontSize = size.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && size > minSize) size -= 1f else fitted = true
        },
        // Hidden until it fits, so the clipped first attempt never shows.
        modifier = Modifier.drawWithContent { if (fitted) drawContent() }
    )
}

/** A cost or might value as a round token, filled ivory when chosen. */
@Composable
private fun StatToken(label: String, selected: Boolean, stat: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) Rift.Ivory else Rift.Raised)
            .border(if (selected) 2.dp else 1.dp, if (selected) Rift.Gold else Rift.Line, CircleShape)
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = "$stat $label" },
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = RiftNumber.copy(fontSize = 17.sp), color = if (selected) Rift.OnGold else Rift.Parchment)
    }
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
