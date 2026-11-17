package com.lburne.bounded.data.model

/**
 * Where you keep a card's copies: a map of place name ("Red binder", "Deck box") to how many
 * copies are there. Copies in no place are unplaced. Stored apart from the collection entry, at
 * users/{uid}/locations/{cardId}, so an app that only knows quantities can't erase it
 * (docs/binders.md, Locations). Mirrors lib/places.ts in the web app; both run
 * shared/location-test-cases.json.
 */
object CardPlaces {
    private fun key(name: String) = name.trim().lowercase()
    private val byName = compareBy<String>({ key(it) }, { it })

    /**
     * The places in name order, fitted to the copies owned: blank names and empty counts
     * dropped, names that differ only in case merged, and counts cut down so they never add up
     * to more than [quantity].
     */
    fun normalised(places: Map<String, Int>?, quantity: Int): List<Pair<String, Int>> {
        val merged = LinkedHashMap<String, Pair<String, Int>>()
        places.orEmpty().keys.sortedWith(byName).forEach { raw ->
            val name = raw.trim()
            val count = places?.get(raw) ?: 0
            if (name.isEmpty() || count <= 0) return@forEach
            val existing = merged[key(name)]
            merged[key(name)] = if (existing != null) existing.first to existing.second + count else name to count
        }
        var room = maxOf(0, quantity)
        return merged.values.mapNotNull { (name, count) ->
            val take = minOf(count, room)
            room -= take
            if (take > 0) name to take else null
        }
    }

    /** Copies owned that aren't in any place. */
    fun unplaced(places: Map<String, Int>?, quantity: Int): Int =
        maxOf(0, quantity - normalised(places, quantity).sumOf { it.second })

    /**
     * The places with [place] holding [count] copies, limited to the copies not placed
     * elsewhere. Zero removes the place. A name matching an existing place in any case updates
     * it, keeping its spelling.
     */
    fun setCount(places: Map<String, Int>?, quantity: Int, place: String, count: Int): Map<String, Int> {
        val current = normalised(places, quantity)
        val name = place.trim()
        if (name.isEmpty()) return current.toMap()
        val existing = current.firstOrNull { key(it.first) == key(name) }
        val others = current.filter { it !== existing }
        val room = maxOf(0, quantity) - others.sumOf { it.second }
        val next = count.coerceIn(0, maxOf(0, room))
        val placed = if (next > 0) others + ((existing?.first ?: name) to next) else others
        return normalised(placed.toMap(), quantity).toMap()
    }

    /** Puts every unplaced copy in [place]. Changes nothing when all copies are placed. */
    fun addPlace(places: Map<String, Int>?, quantity: Int, place: String): Map<String, Int> {
        val free = unplaced(places, quantity)
        val current = normalised(places, quantity)
        if (free == 0) return current.toMap()
        val existing = current.firstOrNull { key(it.first) == key(place) }
        return setCount(places, quantity, place, (existing?.second ?: 0) + free)
    }

    /**
     * Place names used anywhere in the collection, most-used first, for suggestions. Names that
     * differ only in case count as one, under the spelling seen first.
     */
    fun known(all: Collection<Map<String, Int>>): List<String> {
        val counts = LinkedHashMap<String, Pair<String, Int>>()
        all.forEach { places ->
            places.forEach { (raw, count) ->
                val name = raw.trim()
                if (name.isEmpty() || count <= 0) return@forEach
                val hit = counts[key(name)]
                counts[key(name)] = (hit?.first ?: name) to (hit?.second ?: 0) + 1
            }
        }
        return counts.values.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy(byName) { it.first }).map { it.first }
    }
}
