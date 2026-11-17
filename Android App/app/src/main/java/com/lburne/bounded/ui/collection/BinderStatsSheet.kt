package com.lburne.bounded.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lburne.bounded.ui.common.RarityGem
import com.lburne.bounded.ui.common.RiftLabelText
import com.lburne.bounded.ui.common.RiftSet
import com.lburne.bounded.ui.common.SetCodeBadge
import com.lburne.bounded.ui.common.ThinProgressBar
import com.lburne.bounded.ui.common.label
import com.lburne.bounded.ui.theme.Rift
import com.lburne.bounded.ui.theme.RiftLabel
import com.lburne.bounded.ui.theme.RiftNumber

/** Opens the stats sheet, from the progress panel. */
@Composable
fun StatsButton(onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 32.dp)
            .border(1.dp, Rift.GoldDeep)
            .clickable(role = Role.Button, onClickLabel = "Show stats") { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.BarChart, contentDescription = null, tint = Rift.Gold, modifier = Modifier.size(16.dp))
        Text("STATS", style = RiftNumber.copy(fontSize = 13.sp, letterSpacing = 0.8.sp), color = Rift.Ivory)
    }
}

/**
 * A binder broken down: headline tiles, then owned-of-total rows by rarity, type and set,
 * foil and promo counts, and the most valuable cards you own. Bars are one gold hue; the
 * label (and the rarity gem's shape) says what each row is, and every row prints its numbers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BinderStatsSheet(title: String, stats: BinderStats, currencySymbol: String, onCardClick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(9.dp).rotate(45f).background(Rift.Gold))
            RiftLabelText("$title stats", style = RiftLabel.copy(fontSize = 20.sp))
        }

        val p = stats.progress
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Cards owned", "%,d".format(p.owned), "of ${"%,d".format(p.total)} · ${p.percent}%")
                StatTile("Copies", "%,d".format(p.copies), "including duplicates")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Value", if (p.value > 0) formatMoney(currencySymbol, p.value) else "–", "TCGplayer market")
                StatTile("Playsets", "%,d".format(stats.playsets.owned), "of ${"%,d".format(stats.playsets.total)} units, spells and gear with $PLAYSET+")
            }
        }

        StatSection("By rarity") {
            stats.byRarity.forEach { (rarity, progress) ->
                StatRow(rarity.label(), progress, leading = { RarityGem(rarity, size = 14.dp) })
            }
        }
        StatSection("By type") {
            stats.byType.forEach { (type, progress) -> StatRow(type.label(), progress) }
        }
        if (stats.bySet.isNotEmpty()) {
            StatSection("By set") {
                stats.bySet.forEach { (code, progress) ->
                    StatRow(RiftSet.nameOf(code), progress, leading = { SetCodeBadge(code, fontSize = 11) })
                }
            }
        }

        StatSection("Foils and promos") {
            if (stats.finishes.isEmpty()) {
                Text("None yet. Mark copies as foil or promo from a card's detail.", fontSize = 14.sp, color = Rift.Muted)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.finishes.forEach { (finish, count) ->
                        Row(
                            Modifier.border(1.dp, Rift.Line).background(Rift.Raised).padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(finish.label, fontSize = 14.sp, color = Rift.Parchment)
                            Text("%,d".format(count), style = RiftNumber.copy(fontSize = 15.sp), color = Rift.Ivory)
                        }
                    }
                }
            }
        }

        if (stats.mostValuable.isNotEmpty()) {
            StatSection("Most valuable") {
                stats.mostValuable.forEachIndexed { i, (item, price) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clickable(onClickLabel = "Open ${item.card.name}") { onCardClick(item.card.cardId) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("${i + 1}", style = RiftNumber.copy(fontSize = 15.sp), color = Rift.Muted, modifier = Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.card.name, fontSize = 15.sp, color = Rift.Ivory, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.card.cardId, fontSize = 12.sp, color = Rift.Muted)
                        }
                        if (item.quantity > 1) Text("×${item.quantity}", fontSize = 13.sp, color = Rift.Muted)
                        Text(formatMoney(currencySymbol, price), style = RiftNumber.copy(fontSize = 15.sp), color = Rift.Gold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatTile(label: String, value: String, detail: String) {
    Column(
        Modifier
            .weight(1f)
            .background(Rift.Raised)
            .border(1.dp, Rift.Line)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        RiftLabelText(label, color = Rift.Muted, style = RiftLabel.copy(fontSize = 12.sp))
        Text(value, style = RiftNumber.copy(fontSize = 26.sp), color = Rift.Ivory, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, fontSize = 12.sp, color = Rift.Muted, maxLines = 2)
    }
}

@Composable
private fun StatSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RiftLabelText(title, color = Rift.Gold, style = RiftLabel.copy(fontSize = 14.sp))
        content()
    }
}

/** One category: its label, owned of total, percent, and a thin gold bar. */
@Composable
private fun StatRow(label: String, progress: BinderProgress, leading: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: ${progress.owned} of ${progress.total} owned, ${progress.percent}%"
        },
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (leading != null) leading()
            Text(label, fontSize = 15.sp, color = Rift.Ivory, modifier = Modifier.weight(1f))
            Text("${"%,d".format(progress.owned)} of ${"%,d".format(progress.total)}", fontSize = 13.sp, color = Rift.Muted)
            Spacer(Modifier.width(2.dp))
            Text("${progress.percent}%", style = RiftNumber.copy(fontSize = 15.sp), color = Rift.Ivory, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        ThinProgressBar(progress.fraction, Rift.Gold)
    }
}
