package com.lburne.bounded.data.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs shared/location-test-cases.json, the same cases the web app's lib/places.test.ts runs,
 * so the two apps place copies the same way.
 */
class CardPlacesTest {

    private val root: JsonObject = JsonParser.parseString(casesFile().readText()).asJsonObject

    private fun casesFile(): File {
        System.getProperty("locationCases")?.let { return File(it) }
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "shared/location-test-cases.json")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        error("shared/location-test-cases.json not found")
    }

    private fun cases(name: String) = root.getAsJsonArray(name).map { it.asJsonObject }
    private fun places(json: JsonElement): Map<String, Int> = json.asJsonObject.entrySet().associate { it.key to it.value.asInt }
    private fun pairs(json: JsonElement): List<Pair<String, Int>> = json.asJsonArray.map { it.asJsonArray.let { p -> p[0].asString to p[1].asInt } }

    private fun check(group: String, run: (JsonObject) -> Pair<Any, Any>) {
        val failures = cases(group).mapNotNull { case ->
            val (expected, actual) = run(case)
            if (expected == actual) null else "${case.get("name").asString}: expected $expected, got $actual"
        }
        assertTrue("$group failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun normalised() = check("normalised") { c ->
        pairs(c.get("expect")) to CardPlaces.normalised(places(c.get("places")), c.get("quantity").asInt)
    }

    @Test fun unplaced() = check("unplaced") { c ->
        c.get("expect").asInt to CardPlaces.unplaced(places(c.get("places")), c.get("quantity").asInt)
    }

    @Test fun setCount() = check("setCount") { c ->
        val q = c.get("quantity").asInt
        pairs(c.get("expect")) to CardPlaces.normalised(
            CardPlaces.setCount(places(c.get("places")), q, c.get("place").asString, c.get("count").asInt), q
        )
    }

    @Test fun addPlace() = check("addPlace") { c ->
        val q = c.get("quantity").asInt
        pairs(c.get("expect")) to CardPlaces.normalised(CardPlaces.addPlace(places(c.get("places")), q, c.get("place").asString), q)
    }

    @Test fun known() {
        assertEquals(
            listOf("Red binder", "Deck box"),
            CardPlaces.known(listOf(mapOf("Deck box" to 1), mapOf("Red binder" to 2), mapOf("red binder" to 1, "Trade" to 0)))
        )
    }
}
