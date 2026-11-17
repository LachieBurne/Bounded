package com.lburne.bounded.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// Loaded from assets/riftbound_data.json, which scripts/generate_card_data.py generates.
@Entity(tableName = "card_definitions")
data class Card(
    @PrimaryKey val cardId: String, // e.g. "OGN-001", "OGN-299*", "UNL-T01"
    val name: String, // Display name, e.g. "Master Yi, Honed" or "Master Yi - Wuju Bladesman"
    val setCode: String,
    val collectorNumber: Int,
    val imageUrl: String,
    val domain: List<Domain>,
    val rarity: RiftRarity,
    val type: CardType,
    val cost: Int,
    val might: Int?,
    val artistName: String?,
    val isToken: Boolean = false,
    val baseName: String = "", // Name without subtitle, e.g. "Master Yi"
    val subtitle: String? = null,
    val setName: String = "",
    val sortOrder: Int = 0, // Release order across all sets
    val orientation: String = "portrait",
    val power: Int? = null,
    val mightBonus: Int? = null,
    val tags: List<String> = emptyList(), // Champion and region tags, e.g. ["Master Yi", "Ionia"]
    val championTag: String? = null, // Legends only: the tag their champion units carry
    val isSignature: Boolean = false, // Carries a champion's tag without being that champion, e.g. Tibbers
    val text: String? = null,
    val effect: String? = null,
    /** Formats Riot bans this card in: "STANDARD" (1v1) and/or "2V2". From data/banned_cards.json. */
    val bannedIn: List<String>? = null,
    /** When the ban takes effect, e.g. "2026-03-31". Until then the card is still legal. */
    val bannedSince: String? = null,
    /** Promo printings counted as finishes, by [Finish] name, e.g. ["NEXUS_NIGHT"]. From data/promos.json. */
    val promos: List<String>? = null
) {
    /** Banned in Standard today, the format the deck builder checks against. */
    val isBannedInStandard: Boolean get() = isBannedIn(BAN_STANDARD)
    val isBannedIn2v2: Boolean get() = isBannedIn(BAN_2V2)

    /** Banned in [format] on [on] (YYYY-MM-DD): listed for that format, and the ban has started. */
    fun isBannedIn(format: String, on: String = today()): Boolean =
        bannedIn?.contains(format) == true && (bannedSince == null || bannedSince <= on)

    /** The date an announced ban starts, while it hasn't started yet. */
    fun upcomingBan(on: String = today()): String? =
        bannedSince?.takeIf { !bannedIn.isNullOrEmpty() && it > on }

    companion object {
        const val BAN_STANDARD = "STANDARD"
        const val BAN_2V2 = "2V2"

        /** Today in the phone's time zone, in the format [bannedSince] uses. */
        fun today(): String = java.time.LocalDate.now().toString()
    }
}
