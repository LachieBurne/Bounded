package com.lburne.bounded.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.model.CardPlaces
import com.lburne.bounded.ui.common.RiftLabelText
import com.lburne.bounded.ui.theme.Rift
import com.lburne.bounded.ui.theme.RiftLabel
import com.lburne.bounded.ui.theme.RiftNumber

/**
 * Where you keep a card's copies: places like "Red binder" or "Deck box" with a count each, and
 * how many aren't anywhere yet. Optional, and empty until you add a place. Adding a place puts
 * every unplaced copy there in one tap; the steppers move copies between a place and unplaced.
 * Same as the web app's LocationPanel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationPanel(
    item: CardWithQuantity,
    stored: Map<String, Int>?,
    known: List<String>,
    onAdd: (String) -> Unit,
    onSetCount: (String, Int) -> Unit
) {
    val quantity = item.quantity
    if (quantity == 0) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Place, contentDescription = null, tint = Rift.Muted, modifier = Modifier.size(16.dp))
            Text("Add a copy to note where you keep it.", fontSize = 14.sp, color = Rift.Muted)
        }
        return
    }

    val placed = CardPlaces.normalised(stored, quantity)
    val free = CardPlaces.unplaced(stored, quantity)
    val onCard = placed.map { it.first.lowercase() }.toSet()
    val suggestions = known.filter { it.lowercase() !in onCard }.take(6)
    var draft by rememberSaveable(item.card.cardId) { mutableStateOf("") }
    fun add(name: String) {
        if (name.isBlank() || free == 0) return
        draft = ""
        onAdd(name)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Rift.Surface)
            .border(1.dp, Rift.Line)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Place, contentDescription = null, tint = Rift.Gold, modifier = Modifier.size(16.dp))
            RiftLabelText("Location", color = Rift.Muted, style = RiftLabel.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
            Text(if (free == 0) "All copies placed" else "$free unplaced", fontSize = 13.sp, color = Rift.Muted)
        }

        placed.forEach { (name, count) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, fontSize = 15.sp, color = Rift.Ivory, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                FilledIconButton(
                    onClick = { onSetCount(name, count - 1) },
                    shape = RectangleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Rift.Raised, contentColor = Rift.Ivory),
                    modifier = Modifier.size(40.dp)
                ) { Icon(Icons.Default.Remove, contentDescription = "One fewer in $name") }
                Text("$count", style = RiftNumber.copy(fontSize = 18.sp), color = Rift.Ivory, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                FilledIconButton(
                    onClick = { onSetCount(name, count + 1) },
                    enabled = free > 0,
                    shape = RectangleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Rift.Gold, contentColor = Rift.OnGold),
                    modifier = Modifier.size(40.dp)
                ) { Icon(Icons.Default.Add, contentDescription = "One more in $name") }
                IconButton(onClick = { onSetCount(name, 0) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove $name", tint = Rift.Muted, modifier = Modifier.size(18.dp))
                }
            }
        }

        if (free > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(40) },
                    placeholder = { Text(if (placed.isEmpty()) "Red binder, deck box…" else "Another place", color = Rift.Muted) },
                    singleLine = true,
                    shape = RectangleShape,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add(draft) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Rift.Gold,
                        unfocusedBorderColor = Rift.Line,
                        focusedTextColor = Rift.Ivory,
                        unfocusedTextColor = Rift.Ivory,
                        cursorColor = Rift.Gold
                    ),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { add(draft) }, enabled = draft.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (free == quantity) "Put all here" else "Put $free here", color = if (draft.isNotBlank()) Rift.Gold else Rift.Muted)
                }
            }
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { name ->
                        TextButton(
                            onClick = { add(name) },
                            shape = RectangleShape,
                            modifier = Modifier.border(1.dp, Rift.Line).heightIn(min = 36.dp)
                        ) { Text(name, fontSize = 14.sp, color = Rift.Parchment) }
                    }
                }
            }
        } else if (placed.isNotEmpty()) {
            Text("Lower a place to move copies somewhere else.", fontSize = 13.sp, color = Rift.Muted)
        }
    }
}
