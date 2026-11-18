package com.yk.photocritic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yk.photocritic.R
import com.yk.photocritic.ui.design.Tokens

/** Config unavailable, app disabled, or update required (file 38). No Try again button (AR2). */
@Composable
fun DisabledScreen(
    reason: DisabledReason,
    openUrl: (String) -> Unit,
) {
    val text =
        when (reason) {
            DisabledReason.ConfigUnavailable -> stringResource(R.string.config_unavailable)
            is DisabledReason.AppDisabled -> reason.message.ifEmpty { stringResource(R.string.app_disabled_default) }
            is DisabledReason.UpdateRequired -> stringResource(R.string.update_required)
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Tokens.bg)
                .safeDrawingPadding()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = Tokens.text, fontSize = 17.sp, textAlign = TextAlign.Center)
        if (reason is DisabledReason.UpdateRequired && reason.updateUrl.isNotEmpty()) {
            Button(
                onClick = { openUrl(reason.updateUrl) },
                colors = ButtonDefaults.buttonColors(containerColor = Tokens.accent, contentColor = Tokens.onAccent),
            ) { Text(stringResource(R.string.update_button)) }
        }
    }
}
