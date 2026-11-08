package com.lburne.bounded.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lburne.bounded.data.model.Binder
import com.lburne.bounded.ui.binder.BinderEditorSheet
import com.lburne.bounded.ui.binder.DeleteBinderDialog
import com.lburne.bounded.ui.common.CardImage
import com.lburne.bounded.ui.common.EmptyState

/**
 * The Collection tab: the whole collection first, then each sub-binder with its progress.
 * Sets without a master set binder are offered as one-tap suggestions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel,
    onOpenBinder: (String) -> Unit,
    onScan: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<Binder?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Binder?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(title = { Text("Collection") }, scrollBehavior = scrollBehavior)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                text = { Text("Scan cards") }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            state.collection?.let { summary ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "collection") {
                    BinderRow(summary, state.currencySymbol, prominent = true, onClick = { onOpenBinder(Binder.MAIN_ID) })
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "binders-header") {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Binders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("New binder", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            if (state.binders.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "binders-empty") {
                    EmptyState(
                        title = "No binders yet",
                        body = "Make a binder for each set you're completing, or for any group of cards: a domain, a rarity, your battlefields."
                    )
                }
            }

            items(state.binders, key = { it.binder!!.binderId }) { summary ->
                val binder = summary.binder!!
                BinderRow(
                    summary,
                    state.currencySymbol,
                    prominent = false,
                    onClick = { onOpenBinder(binder.binderId) },
                    onEdit = { editing = binder },
                    onDelete = { deleting = binder }
                )
            }

            if (state.suggestedSets.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "suggestions") {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("Track a master set", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Every printing in the set, including showcase cards.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            state.suggestedSets.forEach { set ->
                                AssistChip(
                                    onClick = { viewModel.createMasterSet(set) },
                                    label = { Text(set.displayName) },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        BinderEditorSheet(
            initial = editing,
            countFor = viewModel::countFor,
            onSave = {
                viewModel.saveBinder(it)
                creating = false
                editing = null
            },
            onDismiss = {
                creating = false
                editing = null
            }
        )
    }

    deleting?.let { binder ->
        DeleteBinderDialog(
            name = binder.name,
            onConfirm = {
                viewModel.deleteBinder(binder.binderId)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun BinderRow(
    summary: BinderSummary,
    currencySymbol: String,
    prominent: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            summary.cover?.let { CardImage(card = it, modifier = Modifier.width(if (prominent) 72.dp else 56.dp)) }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    summary.name,
                    style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    summary.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val p = summary.progress
                LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                Text(
                    buildString {
                        append("${p.owned} of ${p.total} collected · ${p.percent}%")
                        if (prominent && p.value > 0) append(" · ${formatMoney(currencySymbol, p.value)}")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onEdit != null || onDelete != null) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "${summary.name} options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        onEdit?.let { DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; it() }) }
                        onDelete?.let { DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; it() }) }
                    }
                }
            }
        }
    }
    if (prominent) ElevatedCard(Modifier.fillMaxWidth()) { content() } else OutlinedCard(Modifier.fillMaxWidth()) { content() }
}

fun formatMoney(symbol: String, amount: Double): String = "$symbol${String.format(java.util.Locale.US, "%,.2f", amount)}"
