package com.lburne.bounded.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lburne.bounded.data.model.Binder
import com.lburne.bounded.data.model.BinderEntry
import com.lburne.bounded.data.model.Deck
import com.lburne.bounded.data.model.DeckEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseUserRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val userId: String? get() = auth.currentUser?.uid

    // --- BINDERS ---
    /** Sub-binders, oldest first. The collection itself ("main") has no document, only entries. */
    fun getBinders(): Flow<List<Binder>> = callbackFlow {
        val uid = userId ?: run {
            // Signed out: emit nothing-found and finish. Returning without awaitClose throws.
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users").document(uid).collection("binders")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseRepo", "Snapshot error gracefully suppressed", error)
                    close() // Close gracefully without throwing a fatal exception to UI
                    return@addSnapshotListener
                }
                val binders = snapshot?.documents
                    ?.filter { it.id != Binder.MAIN_ID }
                    ?.mapNotNull { doc -> doc.toObject(Binder::class.java)?.copy(binderId = doc.id) }
                    ?.sortedBy { it.createdAt }
                    ?: emptyList()
                trySend(binders)
            }
        awaitClose { registration.remove() }
    }

    fun getBinderEntries(binderId: String): Flow<List<BinderEntry>> = callbackFlow {
        val uid = userId ?: run {
            // Signed out: emit nothing-found and finish. Returning without awaitClose throws.
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users").document(uid)
            .collection("binders").document(binderId).collection("entries")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseRepo", "Snapshot error gracefully suppressed", error)
                    close()
                    return@addSnapshotListener
                }
                val entries = snapshot?.documents?.mapNotNull { entryFrom(it, binderId) } ?: emptyList()
                trySend(entries)
            }
        awaitClose { registration.remove() }
    }

    fun saveBinder(binder: Binder) {
        val uid = userId ?: return
        firestore.collection("users").document(uid).collection("binders").document(binder.binderId).set(binder)
    }

    /** Deletes a sub-binder. Its cards stay in the collection. */
    fun deleteBinder(binderId: String) {
        val uid = userId ?: return
        if (binderId == Binder.MAIN_ID) return
        firestore.collection("users").document(uid).collection("binders").document(binderId).delete()
    }

    /** A collection entry from its Firestore document, including ones older builds wrote (see [BinderEntry.stored]). */
    private fun entryFrom(doc: com.google.firebase.firestore.DocumentSnapshot, binderId: String): BinderEntry? {
        val cardId = doc.getString("cardId") ?: doc.id
        val finishes = (doc.get("finishes") as? Map<*, *>)
            ?.mapNotNull { (key, value) -> (key as? String)?.let { k -> (value as? Number)?.let { k to it.toInt() } } }
            ?.toMap()
        return BinderEntry.stored(
            binderId = doc.getString("binderId") ?: binderId,
            cardId = cardId,
            quantity = doc.getLong("quantity")?.toInt() ?: 0,
            finishes = finishes,
            foil = doc.getLong("foil")?.toInt(),
            nexusNight = doc.getLong("nexusNight")?.toInt()
        )
    }

    /**
     * Where each card's copies are kept, by card ID: place name to count (see CardPlaces).
     * Stored at users/{uid}/locations/{cardId}, apart from the collection entries.
     * Locations are optional, so a refused read (e.g. before the Firestore rule is deployed)
     * gives no locations rather than an error.
     */
    fun getLocations(): Flow<Map<String, Map<String, Int>>> = callbackFlow {
        val uid = userId ?: run {
            trySend(emptyMap())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users").document(uid).collection("locations")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("FirebaseRepo", "Card locations unavailable", error)
                    trySend(emptyMap())
                    return@addSnapshotListener
                }
                val locations = snapshot?.documents?.mapNotNull { doc ->
                    val places = (doc.get("places") as? Map<*, *>)
                        ?.mapNotNull { (k, v) -> (k as? String)?.let { name -> (v as? Number)?.let { name to it.toInt() } } }
                        ?.toMap()
                    places?.let { (doc.getString("cardId") ?: doc.id) to it }
                }?.toMap() ?: emptyMap()
                trySend(locations)
            }
        awaitClose { registration.remove() }
    }

    /** Saves a card's places, or removes the document when there are none. */
    fun savePlaces(cardId: String, places: Map<String, Int>) {
        val uid = userId ?: return
        val ref = firestore.collection("users").document(uid).collection("locations").document(cardId)
        if (places.isEmpty()) ref.delete() else ref.set(mapOf("cardId" to cardId, "places" to places))
    }

    fun updateBinderEntry(entry: BinderEntry) {
        val uid = userId ?: return
        if (entry.quantity > 0) {
            // Written as a map so only these fields are stored; set() replaces the document, which
            // drops the foil and nexusNight fields older builds wrote.
            firestore.collection("users").document(uid)
                .collection("binders").document(entry.binderId)
                .collection("entries").document(entry.cardId)
                .set(
                    mapOf(
                        "binderId" to entry.binderId,
                        "cardId" to entry.cardId,
                        "quantity" to entry.quantity,
                        "finishes" to entry.finishes
                    )
                )
        } else {
            firestore.collection("users").document(uid)
                .collection("binders").document(entry.binderId)
                .collection("entries").document(entry.cardId)
                .delete()
        }
    }

    // --- DECKS ---
    fun getAllDecks(): Flow<List<Deck>> = callbackFlow {
        val uid = userId ?: run {
            // Signed out: emit nothing-found and finish. Returning without awaitClose throws.
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users").document(uid).collection("decks")
            .orderBy("lastModified", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseRepo", "Snapshot error gracefully suppressed", error)
                    close()
                    return@addSnapshotListener
                }
                val decks = snapshot?.documents?.mapNotNull { it.toObject(Deck::class.java) } ?: emptyList()
                trySend(decks)
            }
        awaitClose { registration.remove() }
    }

    fun getDeck(deckId: String): Flow<Deck?> = callbackFlow {
        val uid = userId ?: run {
            // Signed out: emit nothing-found and finish. Returning without awaitClose throws.
            trySend(null)
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users").document(uid).collection("decks").document(deckId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseRepo", "Snapshot error gracefully suppressed", error)
                    close()
                    return@addSnapshotListener
                }
                val deck = snapshot?.toObject(Deck::class.java)
                trySend(deck)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Every entry in a deck, main deck and sideboard. Older Android builds stored the
     * sideboard flag as "sideboard" and the web app as "isSideboard"; read either.
     */
    fun getDeckEntries(deckId: String): Flow<List<DeckEntry>> = callbackFlow {
        val uid = userId ?: run {
            // Signed out: emit nothing-found and finish. Returning without awaitClose throws.
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users").document(uid)
            .collection("decks").document(deckId).collection("entries")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseRepo", "Snapshot error gracefully suppressed", error)
                    close()
                    return@addSnapshotListener
                }
                val entries = snapshot?.documents?.mapNotNull { doc ->
                    val cardId = doc.getString("cardId") ?: return@mapNotNull null
                    DeckEntry(
                        deckId = doc.getString("deckId") ?: deckId,
                        cardId = cardId,
                        quantity = (doc.getLong("quantity") ?: 0L).toInt(),
                        isSideboard = doc.getBoolean("isSideboard") ?: doc.getBoolean("sideboard") ?: false
                    )
                } ?: emptyList()
                trySend(entries)
            }
        awaitClose { registration.remove() }
    }

    fun createDeck(deck: Deck) {
        val uid = userId ?: return
        firestore.collection("users").document(uid).collection("decks").document(deck.deckId).set(deck)
    }

    /** Creates a deck and all its entries in one batch, for imports and copies. */
    fun createDeckWithEntries(deck: Deck, entries: List<DeckEntry>) {
        val uid = userId ?: return
        val deckRef = firestore.collection("users").document(uid).collection("decks").document(deck.deckId)
        val batch = firestore.batch()
        batch.set(deckRef, deck)
        entries.filter { it.quantity > 0 }.forEach { entry ->
            batch.set(deckRef.collection("entries").document("${entry.cardId}_${entry.isSideboard}"), entryFields(entry.copy(deckId = deck.deckId)))
        }
        batch.commit()
    }

    private fun entryFields(entry: DeckEntry): Map<String, Any> = mapOf(
        "deckId" to entry.deckId,
        "cardId" to entry.cardId,
        "quantity" to entry.quantity,
        "isSideboard" to entry.isSideboard,
        // Read by app versions before 1.1.
        "sideboard" to entry.isSideboard
    )

    /** Updates only [fields] on the deck document, so a stale copy can't overwrite newer counts. */
    fun updateDeckFields(deckId: String, fields: Map<String, Any?>) {
        val uid = userId ?: return
        firestore.collection("users").document(uid).collection("decks").document(deckId)
            .update(fields + ("lastModified" to System.currentTimeMillis()))
    }

    fun deleteDeck(deckId: String) {
        val uid = userId ?: return
        val deckRef = firestore.collection("users").document(uid).collection("decks").document(deckId)
        
        // Delete all entries in the subcollection first to avoid orphaned data
        deckRef.collection("entries").get().addOnSuccessListener { snapshot ->
            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            // Finally delete the deck document itself
            batch.delete(deckRef)
            batch.commit()
        }.addOnFailureListener {
            // Fallback to just deleting the deck if the query fails
            deckRef.delete()
        }
    }

    fun renameDeck(deckId: String, newName: String) {
        val uid = userId ?: return
        firestore.collection("users").document(uid).collection("decks").document(deckId)
            .update("name", newName, "lastModified", System.currentTimeMillis())
    }

    /**
     * Sets one deck entry's quantity and updates [deckFields] (counts, legend, champion)
     * in a single batch, so the entry and the deck's counts can't disagree.
     */
    fun applyDeckEdit(entry: DeckEntry, deckFields: Map<String, Any?>) {
        val uid = userId ?: return
        val deckRef = firestore.collection("users").document(uid).collection("decks").document(entry.deckId)
        val entryRef = deckRef.collection("entries").document("${entry.cardId}_${entry.isSideboard}")
        val batch = firestore.batch()
        if (entry.quantity > 0) {
            batch.set(entryRef, entryFields(entry))
        } else {
            batch.delete(entryRef)
        }
        batch.update(deckRef, deckFields + ("lastModified" to System.currentTimeMillis()))
        batch.commit()
    }
}
