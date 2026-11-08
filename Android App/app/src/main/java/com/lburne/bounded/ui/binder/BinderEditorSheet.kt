package com.lburne.bounded.ui.binder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lburne.bounded.data.model.Binder
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.data.model.RiftRarity
import com.lburne.bounded.ui.common.FilterableTypes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.sp
import com.lburne.bounded.ui.common.GoldButton
import com.lburne.bounded.ui.common.RiftLabelText
import com.lburne.bounded.ui.common.RiftSet
import com.lburne.bounded.ui.common.SetCodeBadge
import com.lburne.bounded.ui.common.SheetWithAction
import com.lburne.bounded.ui.theme.Rift
import com.lburne.bounded.ui.theme.RiftLabel
import com.lburne.bounded.ui.theme.RiftNumber
import com.lburne.bounded.ui.common.ToggleChip
import com.lburne.bounded.ui.common.domainIconUrl
import com.lburne.bounded.ui.common.label
import com.lburne.bounded.ui.common.RarityGem

/**
 * Creates or edits a sub-binder. Choosing a single set names the binder after it until you
 * type a name of your own. [countFor] says how many cards a definition covers, for the preview.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BinderEditorSheet(
    initial: Binder?,
    countFor: (Binder) -> Int,
    onSave: (Binder) -> Unit,
    onDismiss: () -> Unit,
    /** Sets without a master set binder, offered as one-tap binders when creating. */
    suggestedSets: List<RiftSet> = emptyList(),
    onQuickMasterSet: (RiftSet) -> Unit = {}
) {
    val start = initial ?: Binder()
    var name by rememberSaveable { mutableStateOf(start.name) }
    var nameEdited by rememberSaveable { mutableStateOf(initial != null) }
    var sets by rememberSaveable { mutableStateOf(start.sets) }
    var domains by rememberSaveable { mutableStateOf(start.domains) }
    var rarities by rememberSaveable { mutableStateOf(start.rarities) }
    var types by rememberSaveable { mutableStateOf(start.types) }
    var includeShowcase by rememberSaveable { mutableStateOf(start.includeShowcase) }

    /** Names the binder after what it covers, e.g. "Origins master set" or "Fury · Rare". */
    fun suggestedName(): String {
        if (sets.size == 1 && domains.isEmpty() && rarities.isEmpty() && types.isEmpty()) {
            return "${RiftSet.nameOf(sets.first())} master set"
        }
        val parts = buildList {
            if (sets.isNotEmpty()) add(sets.joinToString(" + ") { RiftSet.nameOf(it) })
            domains.mapNotNull { d -> Domain.values().firstOrNull { it.name == d } }.forEach { add(it.label()) }
            rarities.mapNotNull { r -> RiftRarity.values().firstOrNull { it.name == r } }.forEach { add(it.label()) }
            types.mapNotNull { t -> FilterableTypes.firstOrNull { it.name == t } }.forEach { add(it.label()) }
        }
        return parts.joinToString(" · ").ifEmpty { "New binder" }
    }

    fun toggle(list: List<String>, value: String) = if (value in list) list - value else list + value

    // With nothing chosen a binder is just All cards again, so don't allow it.
    val sameAsAllCards = sets.isEmpty() && domains.isEmpty() && rarities.isEmpty() && types.isEmpty() && includeShowcase

    val draft = start.copy(
        name = name.trim().ifEmpty { suggestedName() },
        sets = sets,
        domains = domains,
        rarities = rarities,
        types = types,
        includeShowcase = includeShowcase
    )
    val count = remember(draft) { countFor(draft) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Rift.Surface
    ) {
        SheetWithAction(
            action = {
                GoldButton(
                    onClick = { onSave(draft) },
                    enabled = count > 0 && !sameAsAllCards,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            sameAsAllCards -> "Pick a set or filter"
                            count == 0 -> "No cards match"
                            initial == null -> "Create binder · $count cards"
                            else -> "Save · $count cards"
                        }.uppercase(),
                        style = RiftNumber.copy(fontSize = 17.sp, letterSpacing = 1.sp)
                    )
                }
            },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RiftLabelText(if (initial == null) "New binder" else "Edit binder", style = RiftLabel.copy(fontSize = 22.sp))
            Text(
                "A binder is a view of your collection. Copies you add in any binder count everywhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (suggestedSets.isNotEmpty()) {
                SheetSection("Master set in one tap", "Every printing in the set, showcase cards included.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestedSets.forEach { set ->
                        OutlinedButton(
                            onClick = { onQuickMasterSet(set) },
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, Rift.GoldDeep),
                            contentPadding = PaddingValues(start = 6.dp, end = 12.dp)
                        ) {
                            SetCodeBadge(set.code, fontSize = 12)
                            Text(set.displayName, color = Rift.Ivory, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                SheetSection("Or build your own", "Pick any mix of sets, domains, rarities and types.")
            }

            OutlinedTextField(
                value = if (nameEdited) name else suggestedName(),
                onValueChange = {
                    name = it
                    nameEdited = true
                },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            SheetSection("Sets", "None selected means every set. All cards already covers everything, so pick at least one set or filter.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiftSet.values().forEach { set ->
                    ToggleChip(set.displayName, set.code in sets) { sets = toggle(sets, set.code) }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Include showcase cards", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Alt arts, overnumbered and signed cards",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = includeShowcase, onCheckedChange = { includeShowcase = it })
            }

            SheetSection("Domains", "Optional. A card with any chosen domain is included.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Domain.values().forEach { d ->
                    ToggleChip(d.label(), d.name in domains, iconUrl = domainIconUrl(d)) { domains = toggle(domains, d.name) }
                }
            }

            SheetSection("Rarities", "Optional.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiftRarity.values().filter { it != RiftRarity.SHOWCASE }.forEach { r ->
                    ToggleChip(r.label(), r.name in rarities, leading = { RarityGem(r) }) { rarities = toggle(rarities, r.name) }
                }
            }

            SheetSection("Card types", "Optional.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterableTypes.forEach { t ->
                    ToggleChip(t.label(), t.name in types) { types = toggle(types, t.name) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetSection(title: String, hint: String) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Asks before deleting a binder, and says the cards themselves are kept. */
@Composable
fun DeleteBinderDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$name\"?") },
        text = { Text("Only the binder is deleted. Your cards stay in your collection.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
