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
    fun getBinders(): Flow<List<Binder>> = callbackFlow {
        val uid = userId ?: return@callbackFlow
        val registration = firestore.collection("users").document(uid).collection("binders")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseRepo", "Snapshot error gracefully suppressed", error)
                    close() // Close gracefully without throwing a fatal exception to UI
                    return@addSnapshotListener
                }
                val binders = snapshot?.documents?.mapNotNull { it.toObject(Binder::class.java) } ?: emptyList()
                trySend(binders)
            }
        awaitClose { registration.remove() }
    }

    fun getBinderEntries(binderId: String): Flow<List<BinderEntry>> = callbackFlow {
        val uid = userId ?: return@callbackFlow
        val registration = firestore.collection("users").document(uid)
            .collection("binders").document(binderId).collection("entries")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseRepo", "Snapshot error gracefully suppressed", error)
                    close()
                    return@addSnapshotListener
                }
                val entries = snapshot?.documents?.mapNotNull { it.toObject(BinderEntry::class.java) } ?: emptyList()
                trySend(entries)
            }
        awaitClose { registration.remove() }
    }

    fun createBinder(binder: Binder) {
        val uid = userId ?: return
        firestore.collection("users").document(uid).collection("binders").document(binder.binderId).set(binder)
    }

    fun updateBinderEntry(entry: BinderEntry) {
        val uid = userId ?: return
        if (entry.quantity > 0) {
            firestore.collection("users").document(uid)
                .collection("binders").document(entry.binderId)
                .collection("entries").document(entry.cardId)
                .set(entry)
        } else {
            firestore.collection("users").document(uid)
                .collection("binders").document(entry.binderId)
                .collection("entries").document(entry.cardId)
                .delete()
        }
    }

    // --- DECKS ---
    fun getAllDecks(): Flow<List<Deck>> = callbackFlow {
        val uid = userId ?: return@callbackFlow
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
        val uid = userId ?: return@callbackFlow
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

    fun getDeckEntries(deckId: String, isSideboard: Boolean): Flow<List<DeckEntry>> = callbackFlow {
        val uid = userId ?: return@callbackFlow
        val registration = firestore.collection("users").document(uid)
            .collection("decks").document(deckId).collection("entries")
            // Firestore data-mapping converts Kotlin 'isSideboard' boolean properties to 'sideboard'.
            .whereEqualTo("sideboard", isSideboard)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseRepo", "Snapshot error gracefully suppressed", error)
                    close()
                    return@addSnapshotListener
                }
                val entries = snapshot?.documents?.mapNotNull { it.toObject(DeckEntry::class.java) } ?: emptyList()
                trySend(entries)
            }
        awaitClose { registration.remove() }
    }

    fun createDeck(deck: Deck) {
        val uid = userId ?: return
        firestore.collection("users").document(uid).collection("decks").document(deck.deckId).set(deck)
    }

    fun updateDeck(deck: Deck) {
        val uid = userId ?: return
        firestore.collection("users").document(uid).collection("decks").document(deck.deckId).set(deck)
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

    fun incrementDeckCardCount(deckId: String, amount: Long) {
        val uid = userId ?: return
        firestore.collection("users").document(uid).collection("decks").document(deckId)
            .update("cardCount", com.google.firebase.firestore.FieldValue.increment(amount), "lastModified", System.currentTimeMillis())
    }

    fun updateDeckEntry(entry: DeckEntry) {
        val uid = userId ?: return
        val docId = "${entry.cardId}_${entry.isSideboard}"
        if (entry.quantity > 0) {
            firestore.collection("users").document(uid)
                .collection("decks").document(entry.deckId)
                .collection("entries").document(docId)
                .set(entry)
        } else {
            firestore.collection("users").document(uid)
                .collection("decks").document(entry.deckId)
                .collection("entries").document(docId)
                .delete()
        }
    }
}
