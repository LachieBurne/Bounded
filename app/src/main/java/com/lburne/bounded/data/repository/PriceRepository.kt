package com.lburne.bounded.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class PriceRepository(context: Context) {
    private val client = OkHttpClient()
    private val prefs = context.getSharedPreferences("riftbinder_settings", Context.MODE_PRIVATE)

    // 1. Raw Prices (USD) from GitHub
    private val _prices = MutableStateFlow<Map<String, Double>>(emptyMap())
    val prices = _prices.asStateFlow()

    // 2. Exchange Rates (Base USD)
    private var exchangeRates = mutableMapOf<String, Double>("USD" to 1.0)

    // 3. User's Selected Currency
    private val _currency = MutableStateFlow(prefs.getString("currency_code", "USD") ?: "USD")
    val currency = _currency.asStateFlow()

    fun init() {
        fetchPrices()
        fetchExchangeRates()
    }

    private fun fetchPrices() {
        CoroutineScope(Dispatchers.IO).launch {
            val request = Request.Builder()
                .url("https://lachieburne.github.io/Riftbounded/prices.json")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val jsonString = response.body?.string() ?: return@use
                    val jsonObject = JSONObject(jsonString)

                    val newPrices = mutableMapOf<String, Double>()
                    val keys = jsonObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        newPrices[key] = jsonObject.getDouble(key)
                    }
                    _prices.emit(newPrices)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchExchangeRates() {
        CoroutineScope(Dispatchers.IO).launch {
            // Free API: Frankfurter (No Key Required)
            val request = Request.Builder()
                .url("https://api.frankfurter.app/latest?from=USD")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val jsonString = response.body?.string() ?: return@use
                    val json = JSONObject(jsonString)
                    val rates = json.getJSONObject("rates")

                    val newRates = mutableMapOf<String, Double>()
                    newRates["USD"] = 1.0 // Base

                    val keys = rates.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        newRates[key] = rates.getDouble(key)
                    }

                    exchangeRates = newRates
                }
            } catch (e: Exception) {
                Log.e("PriceRepo", "Failed to fetch rates", e)
            }
        }
    }

    fun setCurrency(code: String) {
        prefs.edit().putString("currency_code", code).apply()
        _currency.value = code
    }

    // Helper to get converted price
    fun convertPrice(usdPrice: Double): Double {
        val rate = exchangeRates[_currency.value] ?: 1.0
        return usdPrice * rate
    }

    // Helper to get symbol
    fun getCurrencySymbol(): String {
        return when (_currency.value) {
            "USD", "AUD", "CAD", "NZD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            else -> "$"
        }
    }
}