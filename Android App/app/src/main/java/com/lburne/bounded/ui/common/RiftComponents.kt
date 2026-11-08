package com.lburne.bounded.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.data.model.RiftRarity
import com.lburne.bounded.ui.theme.ChamferShape
import com.lburne.bounded.ui.theme.Rift
import com.lburne.bounded.ui.theme.RiftLabel
import com.lburne.bounded.ui.theme.RiftNumber

/** A section label in italic condensed capitals. */
@Composable
fun RiftLabelText(text: String, modifier: Modifier = Modifier, color: Color = Rift.Ivory, style: TextStyle = RiftLabel) {
    Text(text.uppercase(), style = style, color = color, modifier = modifier)
}

/** Progress in gold, with a diamond marking where you are. */
@Composable
fun GoldProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Rift.Gold, marker: Boolean = true) {
    BoxWithConstraints(modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
        val f = fraction.coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(6.dp).background(Rift.Track))
        Box(Modifier.fillMaxWidth(f).height(6.dp).background(color))
        if (marker) {
            Box(
                Modifier
                    .offset(x = (maxWidth * f - 6.dp).coerceIn(0.dp, maxWidth - 12.dp))
                    .size(10.dp)
                    .rotate(45f)
                    .background(Rift.Ivory)
            )
        }
    }
}

/** A thin progress line, for rows. */
@Composable
fun ThinProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(Rift.Track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(color))
    }
}

/** A set code as printed in the card footer, in a gold-edged badge. */
@Composable
fun SetCodeBadge(code: String, modifier: Modifier = Modifier, fontSize: Int = 13) {
    Box(
        modifier
            .background(Rift.Ink)
            .border(1.dp, Rift.GoldDeep)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(code, style = RiftNumber.copy(fontSize = fontSize.sp, letterSpacing = 0.8.sp), color = Rift.Gold)
    }
}

/**
 * A card's domains as a thin stripe, split for dual-domain cards. Faded for cards you
 * don't own.
 */
@Composable
fun DomainStripe(domains: List<Domain>, modifier: Modifier = Modifier, faded: Boolean = false) {
    val colours = domains.filter { it != Domain.COLORLESS }.map(::getDomainColor).ifEmpty { listOf(getDomainColor(Domain.COLORLESS)) }
    Row(modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        colours.forEach { colour ->
            Box(Modifier.weight(1f).height(4.dp).background(if (faded) colour.copy(alpha = 0.35f) else colour))
        }
    }
}

/** The main action on a screen: gold, with the card frame's cut corners. */
@Composable
fun GoldButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ChamferShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Rift.Gold,
            contentColor = Rift.OnGold,
            disabledContainerColor = Rift.Raised,
            disabledContentColor = Rift.Muted
        ),
        modifier = modifier.heightIn(min = 52.dp),
        content = content
    )
}

/** An energy cost, drawn like the cost token in a card's corner. */
@Composable
fun EnergyToken(cost: Int, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Box(
        modifier
            .size(size)
            .border(2.dp, Rift.Gold, CircleShape)
            .padding(2.dp)
            .clip(CircleShape)
            .background(Rift.Ivory)
            .semantics { contentDescription = "Energy cost $cost" },
        contentAlignment = Alignment.Center
    ) {
        Text(cost.toString(), style = RiftNumber.copy(fontSize = (size.value * 0.55f).sp), color = Rift.OnGold)
    }
}

/** The rarity gem printed at the foot of each card. */
@Composable
fun RarityGem(rarity: RiftRarity, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val colour = when (rarity) {
        RiftRarity.COMMON -> Color(0xFFE8E4DC)
        RiftRarity.UNCOMMON -> Color(0xFF2FB5A8)
        RiftRarity.RARE -> Color(0xFFD2338A)
        RiftRarity.EPIC -> Color(0xFFF08A24)
        RiftRarity.SHOWCASE -> Color(0xFFE9C24A)
    }
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        fun p(x: Float, y: Float) = Offset(w * x / 18f, h * y / 18f)
        val points = when (rarity) {
            RiftRarity.COMMON -> null
            RiftRarity.UNCOMMON -> listOf(p(9f, 2f), p(16f, 15f), p(2f, 15f))
            RiftRarity.RARE -> listOf(p(9f, 1f), p(17f, 9f), p(9f, 17f), p(1f, 9f))
            RiftRarity.EPIC -> listOf(p(9f, 1f), p(17f, 7f), p(14f, 16f), p(4f, 16f), p(1f, 7f))
            RiftRarity.SHOWCASE -> listOf(p(9f, 1f), p(16f, 5f), p(16f, 13f), p(9f, 17f), p(2f, 13f), p(2f, 5f))
        }
        if (points == null) {
            drawCircle(colour, radius = w * 7f / 18f)
        } else {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, colour)
        }
    }
}
