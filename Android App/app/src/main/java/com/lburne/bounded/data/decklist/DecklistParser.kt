package com.lburne.bounded.data.decklist

import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.rules.DeckContents
import com.lburne.bounded.data.rules.DeckRules

/** A pasted decklist turned into card IDs, plus the lines that couldn't be read. */
data class ParsedDecklist(
    val legendId: String? = null,
    val championId: String? = null,
    /** Main deck card IDs and quantities, including the legend, champion, runes and battlefields. */
    val main: Map<String, Int> = emptyMap(),
    val side: Map<String, Int> = emptyMap(),
    val unmatched: List<String> = emptyList()
) {
    val contents: DeckContents get() = DeckContents(legendId, championId, main, side)
    val isEmpty: Boolean get() = main.isEmpty() && side.isEmpty()
}

/**
 * Reads the text decklists the apps export, and the similar lists other Riftbound sites use:
 *
 * ```
 * Legend:
 * 1 Master Yi - Wuju Bladesman
 * Champion:
 * 1 Master Yi, Honed
 * MainDeck:
 * 2 Master Yi, Honed
 * 3x Twin Stance
 * Sideboard:
 * 1 OGN-036
 * ```
 *
 * Section headers are optional. A line is "count name" ("3 Name" or "3x Name"), or a name on
 * its own for one copy. A card can be named by its card ID instead. Names ignore case,
 * punctuation and the "Name - Title" / "Name, Title" difference. When several printings share
 * a name, the base printing is used. The shared cases in shared/decklist-import-cases.json
 * describe the exact behaviour.
 */
object DecklistParser {

    private enum class Section { NONE, LEGEND, CHAMPION, MAIN, SIDEBOARD }

    private val HEADERS = mapOf(
        "legend" to Section.LEGEND,
        "champion" to Section.CHAMPION,
        "chosen champion" to Section.CHAMPION,
        "maindeck" to Section.MAIN,
        "main deck" to Section.MAIN,
        "main" to Section.MAIN,
        "deck" to Section.MAIN,
        "runes" to Section.MAIN,
        "rune deck" to Section.MAIN,
        "battlefields" to Section.MAIN,
        "battlefield" to Section.MAIN,
        "sideboard" to Section.SIDEBOARD,
        "side deck" to Section.SIDEBOARD,
        "side" to Section.SIDEBOARD
    )

    // "Main Deck (40):" -> "main deck"
    private val HEADER_RE = Regex("""^([A-Za-z ]+?)\s*(\(\d+\))?\s*:?$""")
    private val COUNT_RE = Regex("""^(\d+)\s*[xX]?\s+(.+)$""")
    private val VARIANT_RE = Regex("""[a-z*]$""")

    fun normalize(name: String): String =
        name.lowercase()
            .replace(Regex("[’'`]"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    fun parse(text: String, cards: Collection<Card>): ParsedDecklist {
        val byId = cards.associateBy { it.cardId.lowercase() }
        // Base printings first, so "Master Yi, Honed" means YI-1 rather than its alt art YI-1a.
        val ordered = cards.sortedWith(
            compareBy<Card> { VARIANT_RE.containsMatchIn(it.cardId) }.thenBy { it.sortOrder }.thenBy { it.cardId }
        )
        val byName = LinkedHashMap<String, Card>().apply {
            ordered.forEach { putIfAbsent(normalize(it.name), it) }
        }
        val byBaseName = LinkedHashMap<String, MutableList<Card>>().apply {
            ordered.forEach { if (it.baseName.isNotBlank()) getOrPut(normalize(it.baseName)) { mutableListOf() }.add(it) }
        }

        fun resolve(name: String, section: Section): Card? {
            byId[name.trim().lowercase()]?.let { return it }
            val key = normalize(name)
            byName[key]?.let { return it }
            // Some sites list the legend or champion by champion name alone ("Master Yi").
            return when (section) {
                Section.LEGEND -> ordered.firstOrNull {
                    it.type == CardType.LEGEND && (normalize(it.championTag ?: "") == key || normalize(it.baseName) == key)
                }
                Section.CHAMPION -> byBaseName[key]?.firstOrNull { it.type == CardType.UNIT }
                else -> null
            }
        }

        var section = Section.NONE
        var legendId: String? = null
        var championId: String? = null
        val main = LinkedHashMap<String, Int>()
        val side = LinkedHashMap<String, Int>()
        val unmatched = mutableListOf<String>()

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue

            val header = HEADER_RE.matchEntire(line)?.let { HEADERS[it.groupValues[1].trim().lowercase()] }
            if (header != null) {
                section = header
                continue
            }

            val countMatch = COUNT_RE.matchEntire(line)
            val quantity = countMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val name = countMatch?.groupValues?.get(2) ?: line
            val card = resolve(name, section)
            if (card == null || quantity <= 0) {
                unmatched += line
                continue
            }

            when {
                card.type == CardType.LEGEND -> {
                    // One legend; a second one can't be placed anywhere.
                    if (legendId == null && section != Section.SIDEBOARD) {
                        legendId = card.cardId
                        main[card.cardId] = 1
                    } else {
                        unmatched += line
                    }
                }
                section == Section.SIDEBOARD -> side.merge(card.cardId, quantity, Int::plus)
                else -> {
                    if (section == Section.CHAMPION && championId == null) championId = card.cardId
                    main.merge(card.cardId, quantity, Int::plus)
                }
            }
        }

        // No "Champion:" section: choose the first card that can be the legend's champion.
        if (championId == null && legendId != null) {
            val legend = byId[legendId.lowercase()]
            championId = main.keys.firstOrNull { id -> byId[id.lowercase()]?.let { DeckRules.canBeChampion(it, legend) } == true }
        }

        return ParsedDecklist(legendId, championId, main, side, unmatched)
    }
}
