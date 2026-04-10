package com.lburne.bounded.ui.binder

import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.data.model.RiftRarity

enum class RiftSet(val code: String, val displayName: String) {
    PROVING_GROUNDS("OGS", "Proving Grounds"),
    ORIGINS("OGN", "Origins"),
    SPIRITFORGED("SFD", "Spiritforged")
}
data class BinderUiState(
    val cards: List<CardWithQuantity> = emptyList(),
    val currentBinderId: String = "main",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,

    // --- FILTER STATES ---
    val searchQuery: String = "",
    val filterDomain: Domain? = null,
    val filterRarity: RiftRarity? = null,
    val filterType: CardType? = null,
    val filterCost: Int? = null,
    val filterMight: Int? = null,
    val filterSet: String? = null,
    val showOwnedOnly: Boolean = false
) {
    val isFiltering: Boolean
        get() = filterDomain != null || filterRarity != null ||
                filterType != null || filterCost != null || filterMight != null || showOwnedOnly
}