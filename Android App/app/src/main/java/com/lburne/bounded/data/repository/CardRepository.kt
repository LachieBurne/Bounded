package com.lburne.bounded.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lburne.bounded.data.local.CardDao
import com.lburne.bounded.data.local.CardWithQuantity
import com.lburne.bounded.data.local.DeckCardView
import com.lburne.bounded.data.model.Binder
import com.lburne.bounded.data.model.BinderEntry
import com.lburne.bounded.data.model.Card
import com.lburne.bounded.data.model.Deck
import com.lburne.bounded.data.model.DeckEntry
import com.lburne.bounded.data.model.DeckFormat
import kotlinx.coroutines.flow.Flow
import com.lburne.bounded.data.rules.DeckContents
import com.lburne.bounded.data.rules.DeckRules
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.UUID
import kotlinx.coroutines.flow.map

class CardRepository(
    private val cardDao: CardDao,
    private val firebaseRepo: FirebaseUserRepository,
    private val context: Context
) {

    // --- INITIALIZATION ---
    suspend fun initializeMasterList() {
        val jsonString = context.assets.open("riftbound_data.json")
            .bufferedReader().use { it.readText() }
        val listType = object : TypeToken<List<Card>>() {}.type
        val cards: List<Card> = Gson().fromJson(jsonString, listType)
        cardDao.insertAllDefinitions(cards)
    }

    // --- BINDERS ---
    val binders: Flow<List<Binder>> = firebaseRepo.getBinders()

    fun getBinderCards(binderId: String): Flow<List<CardWithQuantity>> {
        return combine(cardDao.getAllCards(), firebaseRepo.getBinderEntries(binderId)) { cards, entries ->
            val entryMap = entries.associateBy { it.cardId }
            cards.map { card ->
                CardWithQuantity(card = card, quantity = entryMap[card.cardId]?.quantity ?: 0)
            }
        }
    }

    fun getCardWithQuantity(cardId: String, binderId: String): Flow<CardWithQuantity> {
        return combine(cardDao.getCardFlow(cardId), firebaseRepo.getBinderEntries(binderId)) { card, entries ->
            val qty = entries.find { it.cardId == cardId }?.quantity ?: 0
            if (card != null) CardWithQuantity(card, qty) else throw IllegalStateException("Card Not Found")
        }
    }

    suspend fun updateCardQuantity(binderId: String, cardId: String, newQuantity: Int) {
        if (newQuantity < 0) return
        firebaseRepo.updateBinderEntry(BinderEntry(binderId, cardId, newQuantity))
    }

    fun saveBinder(binder: Binder) = firebaseRepo.saveBinder(binder)

    fun deleteBinder(binderId: String) = firebaseRepo.deleteBinder(binderId)

    // --- DECKS ---
    val allDecks: Flow<List<Deck>> = firebaseRepo.getAllDecks()

    fun createDeck(name: String, format: DeckFormat = DeckFormat.CONSTRUCTED): String {
        val deck = Deck(name = name, format = format)
        firebaseRepo.createDeck(deck)
        return deck.deckId
    }

    fun deleteDeck(deck: Deck) {
        firebaseRepo.deleteDeck(deck.deckId)
    }

    fun renameDeck(deckId: String, newName: String) {
        firebaseRepo.renameDeck(deckId, newName)
    }

    fun getDeck(deckId: String): Flow<Deck?> = firebaseRepo.getDeck(deckId)

    /** Creates a deck from [contents], with its counts and cover set, in one write. Returns the new ID. */
    fun createDeckFrom(name: String, contents: DeckContents, cards: Map<String, Card>): String {
        val counts = DeckRules.counts(contents, cards)
        val deck = Deck(
            name = name,
            legendCardId = contents.legendId,
            championCardId = contents.championId,
            coverCardUrl = contents.legendId?.let { cards[it]?.imageUrl },
            mainCount = counts.mainDeck,
            sideCount = counts.sideboard
        )
        val entries = contents.main.map { (id, qty) -> DeckEntry(deck.deckId, id, qty, isSideboard = false) } +
            contents.side.map { (id, qty) -> DeckEntry(deck.deckId, id, qty, isSideboard = true) }
        firebaseRepo.createDeckWithEntries(deck, entries)
        return deck.deckId
    }

    /** Copies a deck and its cards under a new name. Returns the new ID. */
    suspend fun duplicateDeck(deck: Deck, newName: String): String {
        val entries = firebaseRepo.getDeckEntries(deck.deckId).first()
        val copy = deck.copy(deckId = UUID.randomUUID().toString(), name = newName, lastModified = System.currentTimeMillis())
        firebaseRepo.createDeckWithEntries(copy, entries)
        return copy.deckId
    }

    /** A deck's main deck and sideboard, built from one snapshot so the two always agree. */
    fun getDeckCards(deckId: String): Flow<DeckCards> {
        return combine(
            cardDao.getAllCards(),
            firebaseRepo.getDeckEntries(deckId),
            firebaseRepo.getBinderEntries("main")
        ) { cards, entries, binderEntries ->
            val mainEntryMap = entries.filter { !it.isSideboard && it.quantity > 0 }.associateBy { it.cardId }
            val sideEntryMap = entries.filter { it.isSideboard && it.quantity > 0 }.associateBy { it.cardId }
            val owned = binderEntries.associate { it.cardId to it.quantity }

            val main = cards.filter { it.cardId in mainEntryMap }.map { card ->
                DeckCardView(card, mainEntryMap.getValue(card.cardId).quantity, owned[card.cardId] ?: 0)
            }
            // Sideboard copies can only use what the main deck hasn't already claimed.
            val side = cards.filter { it.cardId in sideEntryMap }.map { card ->
                val claimed = mainEntryMap[card.cardId]?.quantity ?: 0
                DeckCardView(card, sideEntryMap.getValue(card.cardId).quantity, maxOf(0, (owned[card.cardId] ?: 0) - claimed))
            }
            DeckCards(main, side)
        }
    }

    fun getMainDeckCards(deckId: String): Flow<List<DeckCardView>> = getDeckCards(deckId).map { it.main }

    fun getSideboardCards(deckId: String): Flow<List<DeckCardView>> = getDeckCards(deckId).map { it.side }

    fun getAllCardsWithOwnership(): Flow<List<CardWithQuantity>> {
        return getBinderCards("main")
    }

    val allCards: Flow<List<Card>> = cardDao.getAllCards()

    /** Sets a deck entry's quantity and the deck's [deckFields] together (see FirebaseUserRepository.applyDeckEdit). */
    fun applyDeckEdit(deckId: String, cardId: String, quantity: Int, isSideboard: Boolean, deckFields: Map<String, Any?>) {
        firebaseRepo.applyDeckEdit(DeckEntry(deckId, cardId, quantity.coerceAtLeast(0), isSideboard), deckFields)
    }

    fun updateDeckFields(deckId: String, fields: Map<String, Any?>) {
        firebaseRepo.updateDeckFields(deckId, fields)
    }
}

data class DeckCards(val main: List<DeckCardView>, val side: List<DeckCardView>)
