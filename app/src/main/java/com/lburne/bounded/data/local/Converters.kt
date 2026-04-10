package com.lburne.bounded.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lburne.bounded.data.model.CardType
import com.lburne.bounded.data.model.DeckFormat
import com.lburne.bounded.data.model.Domain
import com.lburne.bounded.data.model.RiftRarity
import java.util.Collections.emptyList

class Converters {
    private val gson = Gson()

    // 1. Convert List<String> -> String (JSON) for the database
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value)
    }

    // 2. Convert String (JSON) -> List<String> for the app
    @TypeConverter
    fun toStringList(value: String?): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }

    // 1. DOMAIN (Fixes your current crash)
    @TypeConverter
    fun fromDomainList(domains: List<Domain>): String {
        return domains.joinToString(",") { it.name }
    }

    // 2. Convert String -> List<Domain> (when reading from Database)
    @TypeConverter
    fun toDomainList(data: String): List<Domain> {
        if (data.isEmpty()) return emptyList()
        return data.split(",").map {
            try {
                Domain.valueOf(it)
            } catch (e: IllegalArgumentException) {
                // Fallback for safety if an enum name changed
                Domain.COLORLESS
            }
        }
    }

    // 2. RARITY (Prevent future crashes)
    @TypeConverter
    fun fromRarity(rarity: RiftRarity?): String {
        return rarity?.name ?: "COMMON"
    }

    @TypeConverter
    fun toRarity(value: String?): RiftRarity {
        return try {
            if (value.isNullOrEmpty()) RiftRarity.COMMON
            else RiftRarity.valueOf(value)
        } catch (e: IllegalArgumentException) {
            RiftRarity.COMMON
        }
    }

    // 3. CARD TYPE
    @TypeConverter
    fun fromCardType(type: CardType?): String {
        return type?.name ?: "UNIT"
    }

    @TypeConverter
    fun toCardType(value: String?): CardType {
        return try {
            if (value.isNullOrEmpty()) CardType.UNIT
            else CardType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            CardType.UNIT
        }
    }

    // --- DECK FORMAT ---
    @TypeConverter
    fun fromDeckFormat(format: DeckFormat): String = format.name

    @TypeConverter
    fun toDeckFormat(value: String): DeckFormat = try {
        DeckFormat.valueOf(value)
    } catch (e: Exception) {
        DeckFormat.CONSTRUCTED // Fallback
    }
}