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
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
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
 * Fills the band behind a card's name: one domain's band colour, or for two domains the
 * first giving way to the second the way a dual-domain card's banner is printed.
 */
fun Modifier.domainBand(domains: List<Domain>, shape: Shape): Modifier {
    val bands = domains.filter { it != Domain.COLORLESS }.ifEmpty { listOf(Domain.COLORLESS) }.map(::getDomainBand)
    if (bands.size == 1) return background(bands.first(), shape)
    val (first, second) = bands
    return clip(shape).drawWithCache {
        val w = size.width
        val h = size.height
        // Like the printed banner, the second colour takes over on a "/" slant a little
        // left of centre, and its edge is sprayed back over the first colour as grain.
        val lean = h * 0.8f
        val centre = Offset(w * 0.45f, h / 2)
        val len = sqrt(h * h + lean * lean)
        val normal = Offset(h / len, lean / len) // across the slant, towards the second colour
        val soft = 1.5.dp.toPx()
        val edge = Brush.linearGradient(
            0f to first, 1f to second,
            start = centre - normal * soft, end = centre + normal * soft
        )

        val dot = 1.dp.toPx()
        val sprayIn = 40.dp.toPx()  // how far the second colour's grain reaches into the first
        val sprayOut = 8.dp.toPx()  // and the first colour's few flecks into the second
        val random = Random(first.hashCode() * 31 + second.hashCode())
        val left = centre.x - lean / 2 - sprayIn / normal.x
        val right = centre.x + lean / 2 + sprayOut / normal.x
        val count = ((right - left) * h / (dot * dot) * 1.6f).toInt().coerceAtMost(40_000)
        val firstSpecks = ArrayList<Offset>()
        val secondSpecks = ArrayList<Offset>()
        repeat(count) {
            val p = Offset(left + random.nextFloat() * (right - left), random.nextFloat() * h)
            val d = p - centre
            val across = d.x * normal.x + d.y * normal.y
            if (across < 0) {
                val near = 1 + across / sprayIn
                if (near > 0 && random.nextFloat() < near.pow(1.6f)) secondSpecks += p
            } else {
                val near = 1 - across / sprayOut
                if (near > 0 && random.nextFloat() < 0.35f * near * near) firstSpecks += p
            }
        }

        onDrawBehind {
            drawRect(edge)
            drawPoints(firstSpecks, PointMode.Points, first, strokeWidth = dot, cap = StrokeCap.Round)
            drawPoints(secondSpecks, PointMode.Points, second, strokeWidth = dot, cap = StrokeCap.Round)
        }
    }
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
