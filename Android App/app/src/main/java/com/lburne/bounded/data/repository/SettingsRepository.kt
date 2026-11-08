package com.lburne.bounded.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How big cards are in the grids. The column count follows from the screen width. */
enum class GridDensity(val label: String, val targetWidthDp: Int) {
    SMALL("Small", 88),
    MEDIUM("Medium", 118),
    LARGE("Large", 168)
}

/** Display preferences, kept on the device. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("riftbinder_settings", Context.MODE_PRIVATE)

    private val _gridDensity = MutableStateFlow(
        GridDensity.values().firstOrNull { it.name == prefs.getString(KEY_GRID_DENSITY, null) } ?: GridDensity.MEDIUM
    )
    val gridDensity: StateFlow<GridDensity> = _gridDensity.asStateFlow()

    fun setGridDensity(density: GridDensity) {
        prefs.edit().putString(KEY_GRID_DENSITY, density.name).apply()
        _gridDensity.value = density
    }

    private companion object {
        const val KEY_GRID_DENSITY = "grid_density"
    }
}
