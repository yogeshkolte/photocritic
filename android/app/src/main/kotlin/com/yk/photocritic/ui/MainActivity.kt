package com.yk.photocritic.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yk.photocritic.PhotoCriticApp

/** Hosts the Compose UI; startup runs once per process in [StartupViewModel] (file 38). */
class MainActivity : ComponentActivity() {
    private val startup: StartupViewModel by viewModels {
        viewModelFactory { initializer { StartupViewModel(Startup((application as PhotoCriticApp).container)) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val metrics = resources.displayMetrics
        // Room for pinch to zoom without decoding a 48 MP original (§6).
        val displayMaxPx = (maxOf(metrics.widthPixels, metrics.heightPixels) * 2).coerceAtMost(MAX_DISPLAY_PX)
        setContent { AppNavigation(startup, (application as PhotoCriticApp).container, displayMaxPx, ::openUrl) }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // No browser or store app: nothing to open.
        }
    }

    private companion object {
        const val MAX_DISPLAY_PX = 4096
    }
}
