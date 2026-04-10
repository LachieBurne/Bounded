package com.lburne.bounded.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lburne.bounded.data.model.Card

// Define your entities and version here
@Database(
    entities = [
        Card::class
    ],
    version = 10, // Increment Version
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // Expose the DAO
    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // Return existing instance if available
            return INSTANCE ?: synchronized(this) {
                // Otherwise create the database
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "riftbounded_db"
                )
                    // NOTE: Wipes and rebuilds DB if you change the schema (useful for dev, remove for production)
                    .fallbackToDestructiveMigration(true)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}