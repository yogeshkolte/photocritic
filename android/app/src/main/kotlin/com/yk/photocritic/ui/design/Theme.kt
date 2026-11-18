package com.yk.photocritic.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** Dark only (G21): Material 3 colours from the §12 tokens, system fonts. */
@Composable
fun PhotoCriticTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = Tokens.accent,
                onPrimary = Tokens.onAccent,
                background = Tokens.bg,
                onBackground = Tokens.text,
                surface = Tokens.surface,
                onSurface = Tokens.text,
                surfaceVariant = Tokens.field,
                onSurfaceVariant = Tokens.muted,
                surfaceContainer = Tokens.surface,
                surfaceContainerLow = Tokens.surface,
                surfaceContainerHigh = Tokens.field,
                surfaceContainerHighest = Tokens.field,
                outline = Tokens.muted,
                error = Tokens.danger,
            ),
        content = content,
    )
}
