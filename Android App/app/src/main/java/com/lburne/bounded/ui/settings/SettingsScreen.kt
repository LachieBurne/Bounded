package com.lburne.bounded.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.lburne.bounded.data.repository.GridDensity
import com.lburne.bounded.ui.binder.CurrencySettingsSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currency: String,
    onCurrencyChange: (String) -> Unit,
    gridDensity: GridDensity,
    onGridDensityChange: (GridDensity) -> Unit,
    onSignOut: () -> Unit
) {
    val user = remember { FirebaseAuth.getInstance().currentUser }
    var showCurrency by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Readable line length on tablets.
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                SectionHeader("Account")
                ListItem(
                    headlineContent = { Text(user?.displayName ?: "Signed in") },
                    supportingContent = { Text(user?.email ?: "") },
                    leadingContent = {
                        val photo = user?.photoUrl
                        if (photo != null) {
                            AsyncImage(
                                model = photo,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                            )
                        } else {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(40.dp))
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text("Sign out") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    modifier = Modifier.clickable { confirmSignOut = true }
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionHeader("Display")
                ListItem(
                    headlineContent = { Text("Card size") },
                    supportingContent = { Text("The number of columns follows your screen width.") },
                    leadingContent = { Icon(Icons.Default.GridView, contentDescription = null) }
                )
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    GridDensity.values().forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == gridDensity,
                            onClick = { onGridDensityChange(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, GridDensity.values().size)
                        ) { Text(option.label) }
                    }
                }

                HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 8.dp))
                SectionHeader("Prices")
                ListItem(
                    headlineContent = { Text("Currency") },
                    supportingContent = { Text("$currency · converted from TCGplayer's US prices") },
                    leadingContent = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                    modifier = Modifier.clickable { showCurrency = true }
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionHeader("About")
                ListItem(
                    headlineContent = { Text("Bounded $version") },
                    supportingContent = { Text("Card data from the official Riftbound card gallery. Not affiliated with Riot Games.") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }
        }
    }

    if (showCurrency) {
        ModalBottomSheet(onDismissRequest = { showCurrency = false }) {
            CurrencySettingsSheet(
                currentCurrency = currency,
                onCurrencySelected = {
                    onCurrencyChange(it)
                    showCurrency = false
                }
            )
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your collection and decks stay saved to your account.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}
