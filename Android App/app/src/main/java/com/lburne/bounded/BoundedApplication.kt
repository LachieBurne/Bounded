package com.lburne.bounded

import android.app.Application
import com.lburne.bounded.data.local.AppDatabase
import com.lburne.bounded.data.repository.CardRepository
import com.lburne.bounded.data.repository.FirebaseUserRepository
import com.lburne.bounded.data.repository.PriceRepository

class BoundedApplication : Application() {
    // 1. Initialize the Database lazily (only when first accessed)
    val database by lazy { AppDatabase.getDatabase(this) }
    
    val firebaseUserRepository by lazy { FirebaseUserRepository() }

    // 2. Initialize the Repository lazily, passing in the DAO
    val repository by lazy { CardRepository(database.cardDao(), firebaseUserRepository, this) }

    val priceRepository by lazy { PriceRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // 2. Trigger fetch on app start
        priceRepository.init()
    }
}