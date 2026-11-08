package com.lburne.bounded.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.CardType
import androidx.compose.ui.text.style.TextAlign
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.ui.common.EnergyToken
import com.lburne.bounded.ui.common.RarityGem
import com.lburne.bounded.ui.common.RiftLabelText
import com.lburne.bounded.ui.common.RiftSet
import com.lburne.bounded.ui.common.domainBand
import com.lburne.bounded.ui.common.domainIconUrl
import com.lburne.bounded.ui.common.getDomainBand
import com.lburne.bounded.ui.common.isLandscape
import com.lburne.bounded.ui.common.label
import com.lburne.bounded.ui.theme.BandShape
import com.lburne.bounded.ui.theme.Condensed
import com.lburne.bounded.ui.theme.Rift
import com.lburne.bounded.ui.theme.RiftLabel
import com.lburne.bounded.ui.theme.RiftNumber

/**
 * One card, large: its own art zoomed and blurred behind it, the name on its domain band,
 * the number you own, and the rules text on an ivory panel like the card's text box.
 * Wide or landscape windows put the art beside the details.
 */
@Composable
fun CardDetailScreen(
    viewModel: CardDetailViewModel,
    onBack: () -> Unit
) {
    val currencySymbol = viewModel.getCurrencySymbol()
    val state = viewModel.uiState.collectAsState().value

    if (state !is CardDetailUiState.Success) {
        Box(Modifier.fillMaxSize().background(Rift.Ink), contentAlignment = Alignment.Center) {
            if (state is CardDetailUiState.Loading) {
                CircularProgressIndicator()
            } else {
                Text("This card couldn't be loaded.", color = Rift.Ivory)
            }
        }
        return
    }

    val item = state.card
    val card = item.card

    BoxWithConstraints(Modifier.fillMaxSize().background(Rift.Ink)) {
        val sideBySide = maxWidth > maxHeight && maxWidth >= 560.dp
        val viewportHeight = maxHeight

        ArtBackdrop(card, Modifier.fillMaxWidth().height(if (sideBySide) viewportHeight else viewportHeight * 0.72f))

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Rift.Ivory)
                }
                Text(
                    "${card.setCode} · ${card.cardId.substringAfter('-')}",
                    style = RiftNumber.copy(fontSize = 15.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold),
                    color = Rift.Ivory,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
            }

            if (sideBySide) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        CardArt(card)
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                    ) {
                        CardInfo(item, state.price, currencySymbol, viewModel)
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Art at most about 45% of the screen height, so the details start on screen.
                    val artRatio = if (card.isLandscape) 88f / 63f else 63f / 88f
                    Box(Modifier.widthIn(max = viewportHeight * 0.45f * artRatio).padding(top = 4.dp, bottom = 8.dp)) {
                        CardArt(card)
                    }
                    CardInfo(item, state.price, currencySymbol, viewModel)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * The card's own art, zoomed into the middle of the art window and blurred, fading into the
 * ink ground. The zoom keeps the frame and text box mostly out of view, so framed and full-art
 * cards look alike. Blur needs Android 12; older phones get the zoomed art under a darker veil.
 */
@Composable
private fun ArtBackdrop(card: Card, modifier: Modifier) {
    val context = LocalContext.current
    val saturate = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.25f) }) }
    Box(modifier.clip(RectangleShape)) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(card.imageUrl).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = saturate,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 2.6f
                    scaleY = 2.6f
                    transformOrigin = TransformOrigin(0.5f, 0.3f)
                }
                .blur(26.dp)
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Rift.Ink.copy(alpha = 0.35f),
                        0.45f to Rift.Ink.copy(alpha = 0.45f),
                        1f to Rift.Ink
                    )
                )
        )
    }
}

@Composable
private fun CardArt(card: Card) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context).data(card.imageUrl).crossfade(true).build(),
        contentDescription = card.name,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .aspectRatio(if (card.isLandscape) 88f / 63f else 63f / 88f)
            .shadow(24.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Rift.GoldDeep, RoundedCornerShape(12.dp))
    )
}

@Composable
private fun CardInfo(item: CardWithQuantity, price: Double?, currencySymbol: String, viewModel: CardDetailViewModel) {
    val card = item.card
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            RiftLabelText(typeLine(card), color = Rift.Gold, modifier = Modifier.padding(start = 4.dp))
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .domainBand(card.domain, BandShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(card.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        StatsRow(card)

        OwnedPanel(item.quantity, price, currencySymbol, viewModel)

        val rules = card.text?.takeIf { it.isNotBlank() && it != "[NO TEXT]" }
        // Equipment prints what it gives the unit it's attached to in a box of its own,
        // which the card data keeps in `effect`.
        val granted = card.effect?.takeIf { card.type == CardType.GEAR && it.isNotBlank() && it != card.text }
        if (rules != null || granted != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Rift.Ivory)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rules?.let { RulesParagraph(it) }
                if (granted != null) {
                    if (rules != null) Box(Modifier.fillMaxWidth().height(1.dp).background(Rift.InkMuted.copy(alpha = 0.35f)))
                    RiftLabelText("Equipped unit has", color = Rift.InkMuted, style = RiftLabel.copy(fontSize = 13.sp))
                    RulesParagraph(granted)
                }
            }
        }

        Text(
            buildString {
                append(card.setName.ifBlank { RiftSet.nameOf(card.setCode) })
                card.artistName?.let { append(" · Art by $it") }
            },
            fontSize = 13.sp,
            color = Rift.Muted
        )
    }
}

@Composable
private fun RulesParagraph(text: String) {
    Text(rulesText(text), fontSize = 15.sp, lineHeight = 22.sp, color = Rift.InkText)
}

/** Cost, might, rarity and domain sigils, the way the card shows them. */
@Composable
private fun StatsRow(card: Card) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (card.type in setOf(CardType.UNIT, CardType.SPELL, CardType.GEAR)) {
            EnergyToken(card.cost)
        }
        val might = card.might?.let { "$it Might" } ?: card.mightBonus?.let { "+$it Might" }
        if (might != null) {
            Text(might, style = RiftNumber.copy(fontSize = 18.sp), color = Rift.Ivory)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RarityGem(card.rarity)
            Text(card.rarity.label(), fontSize = 14.sp, color = Rift.Parchment)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            card.domain.filter { it != Domain.COLORLESS }.forEach { domain ->
                AsyncImage(model = domainIconUrl(domain), contentDescription = domain.label(), modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun OwnedPanel(quantity: Int, price: Double?, currencySymbol: String, viewModel: CardDetailViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Rift.Surface)
            .border(1.dp, Rift.GoldDeep)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            RiftLabelText("Owned", color = Rift.Muted, style = RiftLabel.copy(fontSize = 13.sp))
            Text(
                if (price != null && price > 0) "$currencySymbol${String.format(java.util.Locale.US, "%,.2f", price)} each" else "No price yet",
                fontSize = 13.sp,
                color = if (price != null && price > 0) Rift.Gold else Rift.Muted
            )
        }
        FilledIconButton(
            onClick = { viewModel.decrementQuantity() },
            enabled = quantity > 0,
            shape = RectangleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Rift.Raised, contentColor = Rift.Ivory),
            modifier = Modifier.size(48.dp)
        ) { Icon(Icons.Default.Remove, "Remove a copy") }
        Text(
            quantity.toString(),
            style = RiftNumber.copy(fontSize = 30.sp),
            color = Rift.Ivory,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 36.dp)
        )
        FilledIconButton(
            onClick = { viewModel.incrementQuantity() },
            shape = RectangleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Rift.Gold, contentColor = Rift.OnGold),
            modifier = Modifier.size(48.dp)
        ) { Icon(Icons.Default.Add, "Add a copy") }
    }
}

/** "Signature gear · Riven · Ionia", "Legend · Jinx" or "Spell". */
private fun typeLine(card: Card): String {
    val type = (if (card.isSignature) "Signature " else "") + card.type.label().lowercase()
    val extras = if (card.type == CardType.LEGEND) listOfNotNull(card.championTag) else card.tags
    return (listOf(type.replaceFirstChar { it.uppercase() }) + extras).joinToString(" · ")
}

/** Keywords whose chip colour we know from the printed cards; the rest use the grey. */
private val KeywordColours = mapOf(
    "Action" to Color(0xFF1F7A5C),
    "Reaction" to Color(0xFF1F7A5C),
    "Deploy" to Color(0xFF1F7A5C),
    "Ganking" to Color(0xFF5E8A16),
    "Assault" to Color(0xFFB02E63)
)

/**
 * The card's rules text with its markers drawn: keywords as chips, energy costs as dark
 * tokens, runes in their domain's colour, and reminder text in italics.
 */
private fun rulesText(raw: String): AnnotatedString = buildAnnotatedString {
    val token = Regex("""\[([^\]]+)]|\(([^)]*)\)""")
    var last = 0
    token.findAll(raw).forEach { match ->
        append(raw.substring(last, match.range.first))
        val marker = match.groups[1]?.value
        val reminder = match.groups[2]?.value
        when {
            reminder != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Rift.InkMuted)) {
                append("(")
                append(rulesText(reminder))
                append(")")
            }
            marker == null -> Unit
            marker == ">" || marker == ">>" -> append("→")
            marker == "Might" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Might") }
            marker == "Exhaust" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Exhaust") }
            marker == "Rune Rainbow" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("any rune") }
            marker.startsWith("Rune ") -> {
                val domain = Domain.values().firstOrNull { it.name.equals(marker.removePrefix("Rune "), ignoreCase = true) }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = domain?.let(::getDomainBand) ?: Rift.InkText)) {
                    append("${marker.removePrefix("Rune ")} rune")
                }
            }
            marker.startsWith("Energy ") -> withStyle(SpanStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, color = Rift.Ivory, background = Rift.InkText)) {
                append(" ${marker.removePrefix("Energy ")} ")
            }
            else -> {
                val word = marker.substringBefore(' ')
                withStyle(
                    SpanStyle(
                        fontFamily = Condensed,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = Color.White,
                        background = KeywordColours[word] ?: Rift.InkMuted,
                        letterSpacing = 0.5.sp
                    )
                ) { append(" ${marker.uppercase()} ") }
            }
        }
        last = match.range.last + 1
    }
    append(raw.substring(last))
}
