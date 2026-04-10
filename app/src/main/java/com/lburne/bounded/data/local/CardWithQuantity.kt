package com.lburne.bounded.data.local

import androidx.room.Embedded
import com.lburne.bounded.data.model.Card

data class CardWithQuantity(
    // @Embedded flattens the "cards" table columns into this object
    @Embedded val card: Card,

    // This matches the "quantity" column alias in your SQL query
    val quantity: Int
)