package com.yk.photocritic.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yk.photocritic.R
import com.yk.photocritic.ui.design.Tokens

/** Phase 0 placeholder; the startup sequence and routing arrive in Phase 1 (file 38). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Placeholder() }
    }
}

@Composable
fun Placeholder() {
    Box(
        modifier = Modifier.fillMaxSize().background(Tokens.bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.phase0_placeholder), color = Tokens.text)
    }
}
