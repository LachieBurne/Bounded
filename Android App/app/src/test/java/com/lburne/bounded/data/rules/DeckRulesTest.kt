package com.lburne.bounded.data.rules

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lburne.bounded.data.model.Card
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs shared/deck-rules-test-cases.json, the same cases the web app's deckRules.test.ts
 * runs, so the two implementations can't drift apart.
 */
class DeckRulesTest {

    private val root: JsonObject = JsonParser.parseString(casesFile().readText()).asJsonObject
    private val gson = Gson()
    private val cards: Map<String, Card> = root.getAsJsonArray("cards")
        .map { gson.fromJson(it, Card::class.java) }
        .associateBy { it.cardId }

    private fun casesFile(): File {
        System.getProperty("deckRulesCases")?.let { return File(it) }
        // Fallback when run outside Gradle: walk up to the repo root.
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "shared/deck-rules-test-cases.json")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        error("shared/deck-rules-test-cases.json not found")
    }

    private fun cases(name: String) = root.getAsJsonArray(name).map { it.asJsonObject }

    private fun JsonObject.str(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString

    private fun deckOf(json: JsonObject): DeckContents {
        fun section(key: String) = json.getAsJsonObject(key).entrySet().associate { it.key to it.value.asInt }
        return DeckContents(json.str("legendId"), json.str("championId"), section("main"), section("side"))
    }

    /** Runs every case and reports all failures together. */
    private fun check(group: String, run: (JsonObject) -> Pair<Any, Any>) {
        val failures = cases(group).mapNotNull { case ->
            val (expected, actual) = run(case)
            if (expected == actual) null else "${case.str("name")}: expected $expected, got $actual"
        }
        assertTrue("$group failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun currentStep() = check("step") { case ->
        case.str("expect")!! to DeckRules.currentStep(deckOf(case.getAsJsonObject("deck")), cards).name
    }

    @Test
    fun checkAdd() = check("checkAdd") { case ->
        val section = DeckSection.valueOf(case.str("section")!!)
        val result = DeckRules.checkAdd(deckOf(case.getAsJsonObject("deck")), case.str("cardId")!!, section, cards)
        case.str("expect")!! to result.name
    }

    @Test
    fun canBeChampion() = check("canBeChampion") { case ->
        val legend = cards.getValue(case.str("legendId")!!)
        case.get("expect").asBoolean to DeckRules.canBeChampion(cards.getValue(case.str("cardId")!!), legend)
    }

    @Test
    fun isCandidate() = check("isCandidate") { case ->
        val legend = case.str("legendId")?.let { cards.getValue(it) }
        val step = DeckStep.valueOf(case.str("step")!!)
        case.get("expect").asBoolean to DeckRules.isCandidate(cards.getValue(case.str("cardId")!!), step, legend)
    }

    @Test
    fun validate() = check("validate") { case ->
        val expected = case.getAsJsonArray("expect").map { it.asString }.sorted()
        val actual = DeckRules.validate(deckOf(case.getAsJsonObject("deck")), cards).map { it.toString() }.sorted()
        expected to actual
    }

    @Test
    fun everyAddResultAndIssueHasAMessage() {
        AddResult.values().filter { it != AddResult.OK }.forEach { assertTrue(it.name, it.message.isNotBlank()) }
        IssueCode.values().forEach { assertTrue(it.name, it.message.isNotBlank()) }
    }
}
