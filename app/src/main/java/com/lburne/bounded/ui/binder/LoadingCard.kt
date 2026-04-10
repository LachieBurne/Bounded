package com.lburne.bounded.ui.binder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun LoadingCardPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A)), // Dark card back color
        contentAlignment = Alignment.Center
    ) {
        // Simple, elegant spinner
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            strokeWidth = 2.dp
        )
    }
}

@Composable
fun ErrorCardPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A)),
        contentAlignment = Alignment.Center
    ) {
        // Instead of the ugly triangle, just show a subtle "X" or nothing
        // Or you could use your App Logo here if you have one
        Icon(
            painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
            contentDescription = "Missing",
            tint = Color.White.copy(alpha = 0.2f)
        )
    }
}