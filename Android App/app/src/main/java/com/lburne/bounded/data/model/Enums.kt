package com.lburne.bounded.data.model

enum class RiftRarity {
    COMMON, UNCOMMON, RARE, EPIC, SHOWCASE
}

enum class CardType {
    LEGEND, UNIT, SPELL, GEAR, RUNE, BATTLEFIELD,
    OTHER // Token helpers the gallery gives no type, e.g. "Buff" and "XP Tracker"
}

enum class Domain {
    FURY, CALM, MIND, BODY, CHAOS, ORDER, COLORLESS
}

enum class CardCondition {
    NEAR_MINT, LIGHTLY_PLAYED, MODERATELY_PLAYED, HEAVY_PLAYED, DAMAGED
}