package com.yk.photocritic.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        setContent { AppNavigation(startup, ::openUrl) }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // No browser or store app: nothing to open.
        }
    }
}
