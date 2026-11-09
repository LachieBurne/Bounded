package com.lburne.bounded.ui.deck

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lburne.bounded.data.model.Deck
import com.lburne.bounded.data.model.DeckFormat
import com.lburne.bounded.data.rules.DeckRules
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.sp
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.ui.common.EmptyState
import com.lburne.bounded.ui.common.RiftLabelText
import com.lburne.bounded.ui.common.domainBand
import com.lburne.bounded.ui.common.domainIconUrl
import com.lburne.bounded.ui.common.label
import com.lburne.bounded.ui.theme.BandShape
import com.lburne.bounded.ui.theme.ChamferShape
import com.lburne.bounded.ui.theme.Rift
import com.lburne.bounded.ui.theme.RiftLabel
import com.lburne.bounded.ui.theme.RiftNumber
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

fun shareDeckText(context: Context, text: String, title: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, title))
}

/** The Decks tab: every deck, newest first, with create and import up front. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    viewModel: DeckListViewModel,
    onDeckClick: (String) -> Unit,
    onImport: () -> Unit
) {
    val decks by viewModel.decks.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { newDeckId ->
            showCreateDialog = false
            onDeckClick(newDeckId)
        }
    }

    Scaffold(
        containerColor = Rift.Ink,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(11.dp).rotate(45f).background(Rift.Gold))
                        RiftLabelText("Decks", style = RiftLabel.copy(fontSize = 24.sp), modifier = Modifier.padding(start = 12.dp))
                    }
                },
                actions = {
                    TextButton(onClick = onImport) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("IMPORT", style = RiftNumber.copy(fontSize = 15.sp, letterSpacing = 1.sp), modifier = Modifier.padding(start = 6.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Rift.Ink)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                shape = ChamferShape,
                containerColor = Rift.Gold,
                contentColor = Rift.OnGold,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("NEW DECK", style = RiftNumber.copy(fontSize = 17.sp, letterSpacing = 1.2.sp)) }
            )
        }
    ) { padding ->
        val list = decks
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "No decks yet",
                    body = "Build one step by step, starting with your legend, or paste a decklist from another site.",
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Button(onClick = { showCreateDialog = true }) { Text("New deck") }
                            OutlinedButton(onClick = onImport) { Text("Import") }
                        }
                    }
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp // clear the FAB
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (list.size == 1) "1 deck" else "${list.size} decks",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(list, key = { it.deck.deckId }) { summary ->
                    DeckGridItem(
                        summary = summary,
                        onClick = { onDeckClick(summary.deck.deckId) },
                        onDelete = { viewModel.deleteDeck(summary.deck) },
                        onRename = { id, name -> viewModel.renameDeck(id, name) },
                        onDuplicate = { viewModel.duplicateDeck(summary.deck) },
                        onShare = {
                            scope.launch {
                                shareDeckText(context, viewModel.getExportText(summary.deck.deckId), "${summary.deck.name} decklist")
                            }
                        },
                        onCopy = {
                            scope.launch {
                                clipboard.setText(AnnotatedString(viewModel.getExportText(summary.deck.deckId)))
                                Toast.makeText(context, "Decklist copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateDeckDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, format -> viewModel.createDeck(name, format) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckGridItem(
    summary: DeckSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String, String) -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit
) {
    val deck = summary.deck
    var showOptions by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .background(Rift.Surface)
            .border(1.dp, Rift.Line)
            // Tap opens the deck; long-press shows its options, like the ⋮ button.
            .combinedClickable(
                onClickLabel = "Open deck",
                onLongClickLabel = "Deck options",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showOptions = true
                },
                onClick = onClick
            )
    ) {
        if (deck.coverCardUrl != null) {
            // The legend's art, framed on its upper half where the character usually is.
            AsyncImage(
                model = deck.coverCardUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -0.55f),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Style, contentDescription = null, tint = Rift.Muted, modifier = Modifier.size(48.dp))
            }
        }

        // Shade the bottom so the name stays readable over any art.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.25f to Color.Transparent, 0.8f to Rift.Ink.copy(alpha = 0.9f)))
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (summary.legendDomains.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    summary.legendDomains.filter { it != Domain.COLORLESS }.forEach { domain ->
                        AsyncImage(model = domainIconUrl(domain), contentDescription = domain.label(), modifier = Modifier.size(22.dp))
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .then(
                        if (summary.legendDomains.isEmpty()) Modifier.background(Rift.Raised, BandShape)
                        else Modifier.domainBand(summary.legendDomains, BandShape)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = deck.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = deckStatus(deck),
                    style = RiftNumber.copy(fontSize = 14.sp),
                    color = Color.White
                )
            }
        }

        IconButton(
            onClick = { showOptions = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(40.dp)
                .background(Rift.Ink.copy(alpha = 0.7f), CircleShape)
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "${deck.name} options", tint = Rift.Ivory)
        }
    }

    if (showOptions) {
        DeckOptionsSheet(
            deckName = deck.name,
            onDismiss = { showOptions = false },
            onShare = onShare,
            onCopy = onCopy,
            onRename = { showRenameDialog = true },
            onDuplicate = onDuplicate,
            onDelete = { showDeleteDialog = true }
        )
    }

    if (showRenameDialog) {
        RenameDeckDialog(
            currentName = deck.name,
            onRename = { name ->
                onRename(deck.deckId, name)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog) {
        DeleteDeckDialog(
            deckName = deck.name,
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

/** "No legend yet", "32 of 40 cards" or "40 cards · 8 sideboard". */
private fun deckStatus(deck: Deck): String = when {
    deck.legendCardId == null -> "No legend yet"
    deck.mainCount < DeckRules.MAIN_DECK_MIN -> "${deck.mainCount} of ${DeckRules.MAIN_DECK_MIN} cards"
    deck.mainCount > DeckRules.MAIN_DECK_MAX -> "${deck.mainCount} cards · over ${DeckRules.MAIN_DECK_MAX}"
    deck.sideCount > 0 -> "${deck.mainCount} cards · ${deck.sideCount} sideboard"
    else -> "${deck.mainCount} cards"
}

@Composable
fun CreateDeckDialog(
    onDismiss: () -> Unit,
    onCreate: (String, DeckFormat) -> Unit
) {
    var deckName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New deck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = deckName,
                    onValueChange = { deckName = it },
                    label = { Text("Deck name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Constructed. Next you'll choose a legend, then your champion, runes, battlefields and main deck.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(deckName, DeckFormat.CONSTRUCTED) },
                enabled = deckName.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
