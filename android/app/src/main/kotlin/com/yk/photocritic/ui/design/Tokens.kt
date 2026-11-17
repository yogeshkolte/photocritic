package com.yk.photocritic.ui.design

import androidx.compose.ui.graphics.Color

/** Design tokens (design §12). The iOS app holds the same values. */
object Tokens {
    val bg = Color(0xFF0F1012)
    val surface = Color(0xFF17181B)
    val field = Color(0xFF1C1D21)
    val text = Color(0xFFF4F4F2)
    val muted = text.copy(alpha = 0.68f)
    val accent = Color(0xFFF5B13D)
    val accentOnLight = Color(0xFF8A5A00)
    val onAccent = Color(0xFF1A1206)
    val danger = Color(0xFFFF8A7A)
}
