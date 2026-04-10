package com.lburne.bounded.ui.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.text.Text
import com.lburne.bounded.data.local.AppDatabase
import com.lburne.bounded.data.model.Card
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val cardDao = AppDatabase.getDatabase(application).cardDao()

    private val _scannedCardsFlow = MutableStateFlow<List<Card>>(emptyList())
    val scannedCardsFlow: StateFlow<List<Card>> = _scannedCardsFlow

    private val _debugTextFlow = MutableStateFlow<String>("")
    val debugTextFlow: StateFlow<String> = _debugTextFlow

    // Load all definitions into memory for fast fuzzy matching
    private var allCards: List<Card> = emptyList()

    private var lastScannedText = ""
    private var lastScanTime = 0L

    // For multi-frame confirmation
    private var lastDetectedIds = listOf<String>()
    private var identicalFrameCount = 0

    init {
        viewModelScope.launch {
            cardDao.getAllCards().collect { list ->
                allCards = list
            }
        }
    }

    fun processVisionText(visionText: Text) {
        if (System.currentTimeMillis() - lastScanTime < 500) return
        
        val blocks = visionText.textBlocks

        // Filter out large text (titles, abilities) by only keeping text near the minimum height
        var minHeight = Int.MAX_VALUE
        val validLines = mutableListOf<com.google.mlkit.vision.text.Text.Line>()
        
        for (block in blocks) {
            for (line in block.lines) {
                if (line.text.isNotBlank()) {
                    val height = line.boundingBox?.height() ?: 0
                    if (height in 1 until minHeight) {
                        minHeight = height
                    }
                    validLines.add(line)
                }
            }
        }

        // Only keep lines that are no more than 1.5x the height of the smallest text line found
        val filteredTextBuilder = java.lang.StringBuilder()
        if (minHeight != Int.MAX_VALUE) {
            val maxHeightThreshold = (minHeight * 1.5).toInt()
            for (line in validLines) {
                val h = line.boundingBox?.height() ?: 0
                if (h <= maxHeightThreshold) {
                    filteredTextBuilder.append(line.text).append("\n")
                }
            }
        }

        // Use the filtered text instead of the entire raw text
        val rawText = if (filteredTextBuilder.isNotEmpty()) {
            filteredTextBuilder.toString().trim()
        } else {
            visionText.text
        }
        
        if (rawText.isBlank() || rawText == lastScannedText || allCards.isEmpty()) return
        lastScannedText = rawText
        
        // Push the filtered text to the debug overlay so the user can see what the algorithm is 'seeing'
        _debugTextFlow.value = "Smallest Text:\n$rawText"
        
        viewModelScope.launch {
            val detectedCards = mutableListOf<Card>()
            val rawTextLower = rawText.lowercase()

            // 1. Explicit Card ID Regex (e.g., OGN-066a, SFD-R04, OGN001)
            // But we ONLY allow Set Codes that actually exist in the database.
            val validSetCodes = allCards.map { it.setCode.lowercase() }.distinct()
            if (validSetCodes.isNotEmpty()) {
                val setCodePattern = validSetCodes.joinToString("|") // e.g. "ogn|sfd|chk"
                val idRegex = Regex("\\b($setCodePattern)\\s*[-_\\s]?\\s*([rR]?[0-9]{1,3}[a-z*]*)\\b")
                
                val matches = idRegex.findAll(rawTextLower)
                for (match in matches) {
                    val setCode = match.groupValues[1]
                    val rawCollectorNum = match.groupValues[2]
                    
                    // Pad to 3 digits (e.g. 66a -> 066a, r4 -> r004)
                    val hasR = rawCollectorNum.startsWith("r", ignoreCase = true)
                    val numericStartString = if (hasR) rawCollectorNum.drop(1) else rawCollectorNum
                    
                    val numericPart = numericStartString.takeWhile { it.isDigit() }
                    val suffixPart = numericStartString.dropWhile { it.isDigit() }
                    
                    val paddedNum = numericPart.padStart(3, '0') + suffixPart
                    val finalCollectorNum = if (hasR) "r$paddedNum" else paddedNum
                    
                    val reconstructedId = "$setCode-$finalCollectorNum"
                    
                    val matchedCard = allCards.firstOrNull { it.cardId.equals(reconstructedId, ignoreCase = true) }
                    if (matchedCard != null && !detectedCards.contains(matchedCard)) {
                        detectedCards.add(matchedCard)
                    }
                }

                // 2. Fallback: Identify cards by verified Set Code + Slash Number (e.g. "OGN ... 66/100")
                if (detectedCards.isEmpty()) {
                    val slashRegex = Regex("([rR]?[0-9]{1,3}[a-z*]*)\\s*/\\s*\\d+")
                    val possibleNumbers = slashRegex.findAll(rawTextLower).map { match ->
                        val rawNum = match.groupValues[1]
                        
                        val hasR = rawNum.startsWith("r", ignoreCase = true)
                        val numericStartString = if (hasR) rawNum.drop(1) else rawNum
                        
                        val numPart = numericStartString.takeWhile { it.isDigit() }
                        val suffPart = numericStartString.dropWhile { it.isDigit() }
                        
                        val paddedNum = numPart.padStart(3, '0') + suffPart
                        if (hasR) "r$paddedNum" else paddedNum
                    }.toList()
                    
                    val setCodeRegex = Regex("\\b($setCodePattern)\\b")
                    val setCodesInText = setCodeRegex.findAll(rawTextLower).map { it.groupValues[1] }.toList()

                    for (card in allCards) {
                        val cardSetCodeLower = card.setCode.lowercase()
                        val cardNumPart = card.cardId.substringAfter("-").lowercase()
                        if (setCodesInText.contains(cardSetCodeLower) && possibleNumbers.contains(cardNumPart)) {
                            if (!detectedCards.contains(card)) {
                                detectedCards.add(card)
                            }
                        }
                    }
                }
            }

            // Require consecutive frames of the EXACT SAME IDS to confirm and prevent hallucinations adding 6 cards at once
            val detectedIds = detectedCards.map { it.cardId }.sorted()
            if (detectedIds.isNotEmpty() && detectedIds == lastDetectedIds) {
                identicalFrameCount++
                if (identicalFrameCount >= 2) { // Requires 3 identical frames
                    val currentList = _scannedCardsFlow.value.toMutableList()
                    var addedNew = false
                    for (card in detectedCards) {
                        // Prevent immediate duplicate spams (check last 3 added to be safe)
                        val recentlyAdded = currentList.takeLast(3).map { it.cardId }
                        if (!recentlyAdded.contains(card.cardId)) {
                            currentList.add(card)
                            addedNew = true
                        }
                    }
                    if (addedNew) {
                        _scannedCardsFlow.value = currentList
                        lastScanTime = System.currentTimeMillis() // Start the 500ms debounce
                    }
                    // Reset to wait for the next unique sequence
                    identicalFrameCount = 0
                    lastDetectedIds = emptyList()
                }
            } else {
                lastDetectedIds = detectedIds
                identicalFrameCount = 1
            }
        }
    }

    fun removeCard(card: Card) {
        val currentList = _scannedCardsFlow.value.toMutableList()
        currentList.remove(card)
        _scannedCardsFlow.value = currentList
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                return ScannerViewModel(application) as T
            }
        }
    }
}
