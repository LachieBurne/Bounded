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

/**
 * A domain's sigil colour: the bright colour of its icon on the cards. For icons, stripes
 * and progress bars; too dark for small text on the ink ground.
 */
fun getDomainColor(domain: Domain): Color = when (domain) {
    Domain.FURY -> Color(0xFFDE141F)
    Domain.CALM -> Color(0xFF508C33)
    Domain.MIND -> Color(0xFF226A98)
    Domain.BODY -> Color(0xFFE0790D)
    Domain.CHAOS -> Color(0xFF683795)
    Domain.ORDER -> Color(0xFFD4B503)
    Domain.COLORLESS -> Color(0xFF9A9188)
}

/**
 * A domain's band colour: the deep colour behind a card's name. White text on it passes
 * 4.5:1 (Order's is a little darker than printed so it does).
 */
fun getDomainBand(domain: Domain): Color = when (domain) {
    Domain.FURY -> Color(0xFF8D0409)
    Domain.CALM -> Color(0xFF285F24)
    Domain.MIND -> Color(0xFF073D69)
    Domain.BODY -> Color(0xFFBB3C13)
    Domain.CHAOS -> Color(0xFF3D2272)
    Domain.ORDER -> Color(0xFF8C6610)
    Domain.COLORLESS -> Color(0xFF4A4440)
}

/** A solid colour for one domain, a gradient for several. */
fun getCardBorderBrush(domains: List<Domain>): Brush = when (domains.size) {
    0 -> SolidColor(Color.Gray)
    1 -> SolidColor(getDomainColor(domains.first()))
    else -> Brush.linearGradient(domains.map { getDomainColor(it) })
}

/**
 * The band behind a card's name: one domain's band colour, or two split down the middle
 * like a dual-domain card's banner.
 */
fun domainBandBrush(domains: List<Domain>): Brush {
    val bands = domains.filter { it != Domain.COLORLESS }.ifEmpty { listOf(Domain.COLORLESS) }.map(::getDomainBand)
    return if (bands.size == 1) SolidColor(bands.first())
    else Brush.horizontalGradient(0f to bands[0], 0.495f to bands[0], 0.505f to bands[1], 1f to bands[1])
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
