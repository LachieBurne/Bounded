package com.lburne.bounded.data.decklist

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lburne.bounded.data.model.Card
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs shared/decklist-import-cases.json against [DecklistParser]. */
class DecklistParserTest {

    private fun sharedFile(name: String): File {
        var dir: File? = System.getProperty("deckRulesCases")?.let { File(it).parentFile } ?: File("").absoluteFile
        while (dir != null) {
            val candidate = if (dir.name == "shared") File(dir, name) else File(dir, "shared/$name")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        error("shared/$name not found")
    }

    private val gson = Gson()
    private val cards: List<Card> = JsonParser.parseString(sharedFile("deck-rules-test-cases.json").readText())
        .asJsonObject.getAsJsonArray("cards").map { gson.fromJson(it, Card::class.java) }

    private fun JsonObject.counts(key: String): Map<String, Int> =
        getAsJsonObject(key).entrySet().associate { it.key to it.value.asInt }

    private fun JsonObject.str(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString

    @Test
    fun sharedCases() {
        val cases = JsonParser.parseString(sharedFile("decklist-import-cases.json").readText())
            .asJsonObject.getAsJsonArray("cases").map { it.asJsonObject }
        val failures = cases.mapNotNull { case ->
            val expect = case.getAsJsonObject("expect")
            val expected = ParsedDecklist(
                legendId = expect.str("legendId"),
                championId = expect.str("championId"),
                main = expect.counts("main"),
                side = expect.counts("side"),
                unmatched = expect.getAsJsonArray("unmatched").map { it.asString }
            )
            val actual = DecklistParser.parse(case.str("text")!!, cards)
            if (actual == expected) null else "${case.str("name")}:\n  expected $expected\n  got      $actual"
        }
        assertTrue("Decklist import failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
