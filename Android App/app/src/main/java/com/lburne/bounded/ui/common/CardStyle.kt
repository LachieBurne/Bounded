package com.lburne.bounded.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.lburne.bounded.data.model.Domain

/** Sets in release order. Add new sets here when the card data gains them. */
enum class RiftSet(val code: String, val displayName: String) {
    PROVING_GROUNDS("OGS", "Proving Grounds"),
    ORIGINS("OGN", "Origins"),
    SPIRITFORGED("SFD", "Spiritforged"),
    UNLEASHED("UNL", "Unleashed"),
    VENDETTA("VEN", "Vendetta"),
    RADIANCE("RAD", "Radiance");

    companion object {
        val namesByCode: Map<String, String> = values().associate { it.code to it.displayName }
        fun nameOf(code: String): String = namesByCode[code] ?: code
    }
}

fun getDomainColor(domain: Domain): Color = when (domain) {
    Domain.FURY -> Color(0xFFE53935)
    Domain.CALM -> Color(0xFF43A047)
    Domain.MIND -> Color(0xFF1E88E5)
    Domain.BODY -> Color(0xFFFB8C00)
    Domain.CHAOS -> Color(0xFF8E24AA)
    Domain.ORDER -> Color(0xFFFDD835)
    Domain.COLORLESS -> Color.Gray
}

/** A solid colour for one domain, a gradient for several. */
fun getCardBorderBrush(domains: List<Domain>): Brush = when (domains.size) {
    0 -> SolidColor(Color.Gray)
    1 -> SolidColor(getDomainColor(domains.first()))
    else -> Brush.linearGradient(domains.map { getDomainColor(it) })
}

/**
 * A rounded search box. It keeps its own text so typing never lags behind the filtering,
 * and follows [query] when it's changed elsewhere (for example by "Reset").
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var value by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    LaunchedEffect(query) {
        if (query != value.text) value = TextFieldValue(query, TextRange(query.length))
    }
    TextField(
        value = value,
        onValueChange = {
            value = it
            onQueryChange(it.text)
        },
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (value.text.isNotEmpty()) {
            {
                IconButton(onClick = {
                    value = TextFieldValue("")
                    onQueryChange("")
                }) { Icon(Icons.Default.Close, contentDescription = "Clear search") }
            }
        } else null,
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
    )
}
