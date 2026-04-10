package com.lburne.bounded.ui.binder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lburne.bounded.data.repository.CardRepository
import com.lburne.bounded.data.repository.PriceRepository

class BinderViewModelFactory(
    private val repository: CardRepository,
    private val priceRepository: PriceRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BinderViewModel::class.java)) {
            return BinderViewModel(repository, priceRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}