package com.lburne.bounded.data.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs shared/finish-test-cases.json, the same cases the web app's lib/finishes.test.ts runs,
 * so the two apps count foils and promos the same way.
 */
class FinishTest {

    private val root: JsonObject = JsonParser.parseString(casesFile().readText()).asJsonObject

    private fun casesFile(): File {
        System.getProperty("finishCases")?.let { return File(it) }
        // Fallback when run outside Gradle: walk up to the repo root.
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "shared/finish-test-cases.json")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        error("shared/finish-test-cases.json not found")
    }

    private fun cases(name: String) = root.getAsJsonArray(name).map { it.asJsonObject }

    private fun JsonObject.int(key: String): Int? = get(key)?.takeUnless { it.isJsonNull }?.asInt

    private fun counts(json: JsonElement?): Map<String, Int>? =
        json?.takeUnless { it.isJsonNull }?.asJsonObject?.entrySet()?.associate { it.key to it.value.asInt }

    /** An entry as stored, through the same path the app reads Firestore documents with. */
    private fun entry(json: JsonObject) = BinderEntry.stored(
        binderId = "main",
        cardId = "TST-001",
        quantity = json.int("quantity") ?: 0,
        finishes = counts(json.get("finishes")),
        foil = json.int("foil"),
        nexusNight = json.int("nexusNight")
    )

    /** What a case expects, compared as quantity plus finishes. */
    private fun expected(json: JsonElement): Any =
        if (json.isJsonNull) "null" else json.asJsonObject.let { it.int("quantity") to counts(it.get("finishes")).orEmpty() }

    private fun actual(entry: BinderEntry?): Any = entry?.let { it.quantity to it.finishes } ?: "null"

    /** Runs every case and reports all failures together. */
    private fun check(group: String, run: (JsonObject) -> Pair<Any, Any>) {
        val failures = cases(group).mapNotNull { case ->
            val (expected, actual) = run(case)
            if (expected == actual) null else "${case.get("name").asString}: expected $expected, got $actual"
        }
        assertTrue("$group failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun finishesOf() = check("finishesOf") { case ->
        val json = case.getAsJsonObject("card")
        val card = Card(
            cardId = "TST-001", name = "Test", setCode = "TST", collectorNumber = 1, imageUrl = "",
            domain = emptyList(), rarity = RiftRarity.valueOf(json.get("rarity").asString),
            type = CardType.valueOf(json.get("type").asString), cost = 1, might = null, artistName = null,
            isToken = json.get("isToken").asBoolean,
            promos = json.getAsJsonArray("promos")?.map { it.asString }
        )
        case.getAsJsonArray("expect").map { it.asString } to card.finishes.map { it.name }
    }

    @Test
    fun normalised() = check("normalised") { case ->
        expected(case.get("expect")) to actual(entry(case.getAsJsonObject("entry")))
    }

    @Test
    fun withTotal() = check("withTotal") { case ->
        expected(case.get("expect")) to actual(entry(case.getAsJsonObject("entry")).withTotal(case.int("total")!!))
    }

    @Test
    fun adding() = check("adding") { case ->
        val finish = Finish.valueOf(case.get("finish").asString)
        expected(case.get("expect")) to actual(entry(case.getAsJsonObject("entry")).adding(finish, case.int("delta")!!))
    }

    @Test
    fun countOf() = check("countOf") { case ->
        case.int("expect")!! to entry(case.getAsJsonObject("entry")).count(Finish.valueOf(case.get("finish").asString))
    }
}
