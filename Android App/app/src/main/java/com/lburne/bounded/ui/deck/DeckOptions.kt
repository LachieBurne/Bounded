package com.lburne.bounded.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lburne.bounded.ui.common.RiftLabelText
import com.lburne.bounded.ui.theme.Rift
import com.lburne.bounded.ui.theme.RiftLabel

/** One action in [DeckOptionsSheet]. [destructive] draws it in the ban red. */
data class DeckOption(val label: String, val icon: ImageVector, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * A deck's actions in a bottom sheet: what long-pressing a deck, its ⋮ button, or the ⋮ in the
 * deck editor opens. Options with a null action are left out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckOptionsSheet(
    deckName: String,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    val options = listOfNotNull(
        onShare?.let { DeckOption("Share decklist", Icons.Default.Share, onClick = it) },
        onCopy?.let { DeckOption("Copy decklist", Icons.Default.ContentCopy, onClick = it) },
        onRename?.let { DeckOption("Rename", Icons.Default.Edit, onClick = it) },
        onDuplicate?.let { DeckOption("Duplicate", Icons.Default.FileCopy, onClick = it) },
        DeckOption("Delete deck", Icons.Default.Delete, destructive = true, onClick = onDelete)
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Rift.Surface
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(9.dp).rotate(45f).background(Rift.Gold))
                RiftLabelText(deckName, style = RiftLabel.copy(fontSize = 20.sp), modifier = Modifier.weight(1f, fill = false))
            }
            options.forEachIndexed { i, option ->
                if (option.destructive && i > 0) HorizontalDivider(color = Rift.Line, modifier = Modifier.padding(vertical = 4.dp))
                val colour = if (option.destructive) Rift.Banned else Rift.Ivory
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) {
                            onDismiss()
                            option.onClick()
                        }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(option.icon, contentDescription = null, tint = if (option.destructive) Rift.Banned else Rift.Gold)
                    Text(option.label, color = colour, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Asks before deleting a deck, and says the cards themselves are kept. */
@Composable
fun DeleteDeckDialog(deckName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Rift.Surface,
        shape = RectangleShape,
        title = { RiftLabelText("Delete \"$deckName\"?", style = RiftLabel.copy(fontSize = 20.sp)) },
        text = { Text("The deck is deleted from every device. Your cards stay in your collection.", color = Rift.Parchment) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Rift.Banned, contentColor = Color.White)
            ) { Text("Delete deck") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Rift.Parchment) } }
    )
}

@Composable
fun RenameDeckDialog(currentName: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Rift.Surface,
        shape = RectangleShape,
        title = { RiftLabelText("Rename deck", style = RiftLabel.copy(fontSize = 20.sp)) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
                Spacer(Modifier.size(4.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = { onRename(name.trim()) },
                enabled = name.isNotBlank(),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Rift.Gold, contentColor = Rift.OnGold)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Rift.Parchment) } }
    )
}
