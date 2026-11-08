package com.lburne.bounded.ui.common

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lburne.bounded.data.repository.GridDensity
import kotlin.math.max
import kotlin.math.roundToInt

/** The card size the user picked in Settings. Provided at the root of the app. */
val LocalGridDensity = compositionLocalOf { GridDensity.MEDIUM }

/** Riftbound cards are 63 x 88 mm. */
const val CARD_ASPECT_RATIO = 63f / 88f

/** Space between grid cells. */
val GridSpacing = 8.dp

/**
 * As many columns as fit cards close to [target] wide, rounding to the nearest count rather
 * than down, so a phone gets 3 comfortable columns instead of 2 huge ones. Never fewer
 * than [minColumns].
 */
class CardGridCells(private val target: Dp, private val minColumns: Int = 2) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = max(minColumns, ((availableSize + spacing).toFloat() / (target.roundToPx() + spacing)).roundToInt())
        val usable = availableSize - spacing * (count - 1)
        val cell = usable / count
        val remainder = usable % count
        return List(count) { cell + if (it < remainder) 1 else 0 }
    }

    override fun equals(other: Any?): Boolean =
        other is CardGridCells && other.target == target && other.minColumns == minColumns

    override fun hashCode(): Int = 31 * target.hashCode() + minColumns
}

/** Columns for card grids. Landscape cards (battlefields) get wider cells. */
@Composable
fun rememberCardGridCells(landscape: Boolean = false): GridCells {
    val density = LocalGridDensity.current
    return remember(density, landscape) {
        val width = if (landscape) density.targetWidthDp * 1.6f else density.targetWidthDp.toFloat()
        CardGridCells(width.dp, minColumns = if (landscape) 1 else 2)
    }
}
