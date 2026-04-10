package com.lburne.bounded.data.model

import java.util.UUID

data class Binder(
    val binderId: String = UUID.randomUUID().toString(),
    val name: String = "",
    val colorTheme: Long = 0xFF6650a4
)