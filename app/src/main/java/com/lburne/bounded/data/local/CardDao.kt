package com.lburne.bounded.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lburne.bounded.data.model.Card
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM card_definitions ORDER BY CASE setCode WHEN 'OGS' THEN 1 WHEN 'OGN' THEN 2 WHEN 'SPD' THEN 3 ELSE 4 END ASC, collectorNumber ASC")
    fun getAllCards(): Flow<List<Card>>

    @Query("SELECT * FROM card_definitions WHERE cardId = :cardId")
    suspend fun getCardDefinition(cardId: String): Card?

    @Query("SELECT * FROM card_definitions WHERE cardId = :cardId")
    fun getCardFlow(cardId: String): Flow<Card?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDefinitions(cards: List<Card>)
}