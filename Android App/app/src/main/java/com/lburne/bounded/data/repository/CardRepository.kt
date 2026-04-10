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
import kotlinx.coroutines.flow.combine

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

    fun createBinder(name: String) {
        firebaseRepo.createBinder(Binder(name = name))
    }

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

    // Helper Flow combiners for Deck Cards
    fun getMainDeckCards(deckId: String): Flow<List<DeckCardView>> {
        return buildDeckCardView(deckId, false)
    }

    fun getSideboardCards(deckId: String): Flow<List<DeckCardView>> {
        return buildDeckCardView(deckId, true)
    }

    private fun buildDeckCardView(deckId: String, isSideboard: Boolean): Flow<List<DeckCardView>> {
        return combine(
            cardDao.getAllCards(),
            firebaseRepo.getDeckEntries(deckId, false), // Fetch Main
            firebaseRepo.getDeckEntries(deckId, true),  // Fetch Sideboard
            firebaseRepo.getBinderEntries("main") 
        ) { cards, mainEntries, sideEntries, binderEntries ->
            val mainEntryMap = mainEntries.associateBy { it.cardId }
            val sideEntryMap = sideEntries.associateBy { it.cardId }
            val binderEntryMap = binderEntries.associateBy { it.cardId }
            
            // Depending on which view we are building, we filter to that list
            val targetMap = if (isSideboard) sideEntryMap else mainEntryMap
            
            cards.filter { targetMap.containsKey(it.cardId) }.map { card ->
                val baseOwned = binderEntryMap[card.cardId]?.quantity ?: 0
                val mainAllocated = mainEntryMap[card.cardId]?.quantity ?: 0
                
                // If building Sideboard, subtract the amount already allocated to Main
                val effectiveOwned = if (isSideboard) {
                    maxOf(0, baseOwned - mainAllocated)
                } else {
                    baseOwned
                }

                DeckCardView(
                    card = card,
                    deckQuantity = targetMap[card.cardId]!!.quantity,
                    ownedQuantity = effectiveOwned
                )
            }
        }
    }

    fun getAllCardsWithOwnership(): Flow<List<CardWithQuantity>> {
        return getBinderCards("main")
    }

    suspend fun addCardToDeck(deck: Deck, cardId: String, currentQuantity: Int, isSideboard: Boolean = false) {
        val entry = DeckEntry(deck.deckId, cardId, currentQuantity + 1, isSideboard)
        firebaseRepo.updateDeckEntry(entry)
        if (!isSideboard) { // Usually only main deck counts towards total, but let's count both since its just cardCount
            firebaseRepo.incrementDeckCardCount(deck.deckId, 1)
        }
    }

    suspend fun removeCardFromDeck(deck: Deck, cardId: String, currentQuantity: Int, isSideboard: Boolean = false) {
        if (currentQuantity > 0) {
            val entry = DeckEntry(deck.deckId, cardId, currentQuantity - 1, isSideboard)
            firebaseRepo.updateDeckEntry(entry)
            if (!isSideboard) {
                firebaseRepo.incrementDeckCardCount(deck.deckId, -1)
            }
        }
    }

    fun setDeckLegend(deck: Deck, card: Card) {
        firebaseRepo.updateDeck(deck.copy(legendCardId = card.cardId, coverCardUrl = card.imageUrl))
    }

    fun setDeckChampion(deck: Deck, cardId: String?) {
        firebaseRepo.updateDeck(deck.copy(championCardId = cardId))
    }
}