package com.lburne.bounded.data.rules

import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.Domain

/*
 * Riftbound constructed deckbuilding rules (core rules section 103).
 *
 * The web app has the same rules in Web App/src/rules/deckRules.ts. Both must pass
 * every case in shared/deck-rules-test-cases.json; change the two together.
 * docs/deck-rules.md explains each rule.
 */

/** The deck builder's steps, in order. */
enum class DeckStep { LEGEND, CHAMPION, RUNES, BATTLEFIELDS, MAIN, SIDEBOARD, COMPLETE }

enum class DeckSection { MAIN, SIDEBOARD }

/**
 * A deck's contents. [main] holds every card outside the sideboard, including the
 * legend, the chosen champion's copy, runes and battlefields, keyed by card ID.
 */
data class DeckContents(
    val legendId: String?,
    val championId: String?,
    val main: Map<String, Int>,
    val side: Map<String, Int>
)

data class DeckCounts(
    val runes: Int,
    val battlefields: Int,
    /** Units, spells and gear in the main deck, including the chosen champion. */
    val mainDeck: Int,
    val sideboard: Int
)

/** Why a card can or can't be added, checked in this order. */
enum class AddResult(val message: String) {
    OK(""),
    UNKNOWN_CARD("That card isn't in the card list."),
    NOT_ALLOWED_IN_SIDEBOARD("Only units, spells and gear can go in the sideboard."),
    LEGEND_ALREADY_CHOSEN("Your deck already has a legend."),
    NO_LEGEND("Choose a legend first."),
    NOT_PLAYABLE("Tokens can't go in a deck."),
    OUTSIDE_DOMAIN_IDENTITY("That card's domains don't match your legend."),
    RUNE_DECK_FULL("Your rune deck already has 12 runes."),
    DUPLICATE_BATTLEFIELD("Your deck already has a battlefield with that name."),
    BATTLEFIELDS_FULL("Your deck already has 3 battlefields."),
    SIGNATURE_WRONG_CHAMPION("Signature cards must match your legend's champion."),
    COPY_LIMIT("A deck can have at most 3 copies of a card name."),
    SIGNATURE_LIMIT("A deck can have at most 3 signature cards."),
    SIDEBOARD_FULL("Your sideboard already has 8 cards.")
}

enum class IssueCode(val message: String) {
    NO_LEGEND("No legend chosen."),
    NO_CHAMPION("No champion chosen."),
    INVALID_CHAMPION("The chosen champion doesn't match the legend or isn't in the main deck."),
    RUNE_COUNT("The rune deck needs exactly 12 runes."),
    BATTLEFIELD_COUNT("The deck needs exactly 3 battlefields."),
    MAIN_DECK_TOO_SMALL("The main deck needs at least 40 cards, including the champion."),
    SIDEBOARD_TOO_LARGE("The sideboard can have at most 8 cards."),
    COPY_LIMIT("More than 3 copies of a card name."),
    SIGNATURE_LIMIT("More than 3 signature cards."),
    SIGNATURE_WRONG_CHAMPION("A signature card doesn't match the legend's champion."),
    OUTSIDE_DOMAIN_IDENTITY("A card's domains don't match the legend."),
    DUPLICATE_BATTLEFIELD("Two battlefields share a name."),
    NOT_PLAYABLE("A token is in the deck."),
    NOT_ALLOWED_IN_SIDEBOARD("Only units, spells and gear can go in the sideboard."),
    UNKNOWN_CARD("A card is no longer in the card list."),
    BANNED("A card is banned in Standard.")
}

/** A rule the deck breaks. [subject] is the card ID or, for name-wide rules, the card name. */
data class DeckIssue(val code: IssueCode, val subject: String? = null) {
    override fun toString(): String = if (subject == null) code.name else "${code.name}:$subject"
}

object DeckRules {
    const val MAIN_DECK_MIN = 40
    const val RUNE_DECK_SIZE = 12
    const val BATTLEFIELD_COUNT = 3
    const val SIDEBOARD_MAX = 8
    const val COPY_LIMIT = 3
    const val SIGNATURE_LIMIT = 3

    private val PLAYABLE_TYPES = setOf(CardType.UNIT, CardType.SPELL, CardType.GEAR)

    /** Units, spells and gear: the cards that make up the main deck and sideboard. */
    fun isPlayable(card: Card): Boolean = !card.isToken && card.type in PLAYABLE_TYPES

    private fun hasTag(card: Card, tag: String): Boolean = card.tags.any { it.equals(tag, ignoreCase = true) }

    /** Rule 103.2.b: every domain on the card must be one of the legend's. Colorless cards fit any deck. */
    fun fitsDomainIdentity(card: Card, legend: Card): Boolean =
        card.domain.filter { it != Domain.COLORLESS }.all { it in legend.domain }

    /** Rule 103.3.d.2: signature cards must carry the legend's champion tag. */
    fun signatureMatches(card: Card, legend: Card): Boolean {
        val tag = legend.championTag ?: return false
        return hasTag(card, tag)
    }

    /** Rule 103.3.a.2: a champion unit (not a signature unit) with the legend's champion tag. */
    fun canBeChampion(card: Card, legend: Card?): Boolean {
        val tag = legend?.championTag ?: return false
        return card.type == CardType.UNIT && !card.isToken && !card.isSignature && hasTag(card, tag)
    }

    fun counts(deck: DeckContents, cards: Map<String, Card>): DeckCounts {
        var runes = 0
        var battlefields = 0
        var mainDeck = 0
        for ((id, qty) in deck.main) {
            val card = cards[id] ?: continue
            when {
                card.type == CardType.RUNE -> runes += qty
                card.type == CardType.BATTLEFIELD -> battlefields += qty
                isPlayable(card) -> mainDeck += qty
            }
        }
        val sideboard = deck.side.values.sum()
        return DeckCounts(runes, battlefields, mainDeck, sideboard)
    }

    fun currentStep(deck: DeckContents, cards: Map<String, Card>): DeckStep {
        val c = counts(deck, cards)
        return when {
            deck.legendId == null -> DeckStep.LEGEND
            deck.championId == null -> DeckStep.CHAMPION
            c.runes < RUNE_DECK_SIZE -> DeckStep.RUNES
            c.battlefields < BATTLEFIELD_COUNT -> DeckStep.BATTLEFIELDS
            c.mainDeck < MAIN_DECK_MIN -> DeckStep.MAIN
            c.sideboard < SIDEBOARD_MAX -> DeckStep.SIDEBOARD
            else -> DeckStep.COMPLETE
        }
    }

    /** Whether the card picker shows [card] at [step]. */
    fun isCandidate(card: Card, step: DeckStep, legend: Card?): Boolean = when (step) {
        DeckStep.LEGEND -> card.type == CardType.LEGEND
        DeckStep.CHAMPION -> canBeChampion(card, legend)
        DeckStep.RUNES -> legend != null && card.type == CardType.RUNE && fitsDomainIdentity(card, legend)
        DeckStep.BATTLEFIELDS -> legend != null && card.type == CardType.BATTLEFIELD && fitsDomainIdentity(card, legend)
        DeckStep.MAIN, DeckStep.SIDEBOARD -> legend != null && isPlayable(card) &&
            fitsDomainIdentity(card, legend) && (!card.isSignature || signatureMatches(card, legend))
        DeckStep.COMPLETE -> false
    }

    private fun copiesOfName(name: String, deck: DeckContents, cards: Map<String, Card>): Int =
        (deck.main.entries + deck.side.entries).sumOf { (id, qty) -> if (cards[id]?.name == name) qty else 0 }

    private fun signatureTotal(deck: DeckContents, cards: Map<String, Card>): Int =
        (deck.main.entries + deck.side.entries).sumOf { (id, qty) -> if (cards[id]?.isSignature == true) qty else 0 }

    /** Whether one more copy of [cardId] can go in [section]. */
    fun checkAdd(deck: DeckContents, cardId: String, section: DeckSection, cards: Map<String, Card>): AddResult {
        val card = cards[cardId] ?: return AddResult.UNKNOWN_CARD
        val toSideboard = section == DeckSection.SIDEBOARD

        if (card.type == CardType.LEGEND) {
            return when {
                toSideboard -> AddResult.NOT_ALLOWED_IN_SIDEBOARD
                deck.legendId != null -> AddResult.LEGEND_ALREADY_CHOSEN
                else -> AddResult.OK
            }
        }
        val legend = deck.legendId?.let { cards[it] } ?: return AddResult.NO_LEGEND
        if (card.isToken || card.type == CardType.OTHER) return AddResult.NOT_PLAYABLE
        if (!fitsDomainIdentity(card, legend)) return AddResult.OUTSIDE_DOMAIN_IDENTITY

        val c = counts(deck, cards)
        when (card.type) {
            CardType.RUNE -> return when {
                toSideboard -> AddResult.NOT_ALLOWED_IN_SIDEBOARD
                c.runes >= RUNE_DECK_SIZE -> AddResult.RUNE_DECK_FULL
                else -> AddResult.OK
            }
            CardType.BATTLEFIELD -> return when {
                toSideboard -> AddResult.NOT_ALLOWED_IN_SIDEBOARD
                deck.main.any { (id, qty) -> qty > 0 && cards[id]?.type == CardType.BATTLEFIELD && cards[id]?.name == card.name } ->
                    AddResult.DUPLICATE_BATTLEFIELD
                c.battlefields >= BATTLEFIELD_COUNT -> AddResult.BATTLEFIELDS_FULL
                else -> AddResult.OK
            }
            else -> {}
        }

        if (card.isSignature && !signatureMatches(card, legend)) return AddResult.SIGNATURE_WRONG_CHAMPION
        if (copiesOfName(card.name, deck, cards) >= COPY_LIMIT) return AddResult.COPY_LIMIT
        if (card.isSignature && signatureTotal(deck, cards) >= SIGNATURE_LIMIT) return AddResult.SIGNATURE_LIMIT
        if (toSideboard && c.sideboard >= SIDEBOARD_MAX) return AddResult.SIDEBOARD_FULL
        return AddResult.OK
    }

    /** Every rule the deck breaks. An empty list means the deck is legal. */
    fun validate(deck: DeckContents, cards: Map<String, Card>): List<DeckIssue> {
        val issues = linkedSetOf<DeckIssue>()
        val legend = deck.legendId?.let { cards[it] }
        if (legend == null) issues += DeckIssue(IssueCode.NO_LEGEND)

        if (deck.championId == null) {
            issues += DeckIssue(IssueCode.NO_CHAMPION)
        } else if (legend != null) {
            val champion = cards[deck.championId]
            if (champion == null || (deck.main[deck.championId] ?: 0) < 1 || !canBeChampion(champion, legend)) {
                issues += DeckIssue(IssueCode.INVALID_CHAMPION)
            }
        }

        val c = counts(deck, cards)
        if (c.runes != RUNE_DECK_SIZE) issues += DeckIssue(IssueCode.RUNE_COUNT)
        if (c.battlefields != BATTLEFIELD_COUNT) issues += DeckIssue(IssueCode.BATTLEFIELD_COUNT)
        if (c.mainDeck < MAIN_DECK_MIN) issues += DeckIssue(IssueCode.MAIN_DECK_TOO_SMALL)
        if (c.sideboard > SIDEBOARD_MAX) issues += DeckIssue(IssueCode.SIDEBOARD_TOO_LARGE)

        val copiesByName = mutableMapOf<String, Int>()
        val battlefieldNames = mutableMapOf<String, Int>()
        var signatures = 0
        for ((section, entries) in listOf(DeckSection.MAIN to deck.main, DeckSection.SIDEBOARD to deck.side)) {
            for ((id, qty) in entries) {
                if (qty <= 0) continue
                val card = cards[id]
                if (card == null) {
                    issues += DeckIssue(IssueCode.UNKNOWN_CARD, id)
                    continue
                }
                if (section == DeckSection.SIDEBOARD && !isPlayable(card) && !card.isToken) {
                    issues += DeckIssue(IssueCode.NOT_ALLOWED_IN_SIDEBOARD, id)
                    continue
                }
                if (card.isBannedInStandard) issues += DeckIssue(IssueCode.BANNED, id)
                if (card.type == CardType.LEGEND) continue
                if (card.isToken || card.type == CardType.OTHER) {
                    issues += DeckIssue(IssueCode.NOT_PLAYABLE, id)
                    continue
                }
                if (legend != null && !fitsDomainIdentity(card, legend)) {
                    issues += DeckIssue(IssueCode.OUTSIDE_DOMAIN_IDENTITY, id)
                }
                if (card.type == CardType.BATTLEFIELD) {
                    battlefieldNames[card.name] = (battlefieldNames[card.name] ?: 0) + qty
                    continue
                }
                if (card.type == CardType.RUNE) continue
                copiesByName[card.name] = (copiesByName[card.name] ?: 0) + qty
                if (card.isSignature) {
                    signatures += qty
                    if (legend != null && !signatureMatches(card, legend)) {
                        issues += DeckIssue(IssueCode.SIGNATURE_WRONG_CHAMPION, id)
                    }
                }
            }
        }
        copiesByName.filterValues { it > COPY_LIMIT }.keys.forEach { issues += DeckIssue(IssueCode.COPY_LIMIT, it) }
        battlefieldNames.filterValues { it > 1 }.keys.forEach { issues += DeckIssue(IssueCode.DUPLICATE_BATTLEFIELD, it) }
        if (signatures > SIGNATURE_LIMIT) issues += DeckIssue(IssueCode.SIGNATURE_LIMIT)
        return issues.toList()
    }
}
