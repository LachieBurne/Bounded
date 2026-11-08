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
    val effect: String? = null
)
