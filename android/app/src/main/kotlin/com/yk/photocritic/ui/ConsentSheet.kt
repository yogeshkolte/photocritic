package com.yk.photocritic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yk.photocritic.R
import com.yk.photocritic.features.session.ConsentUi
import com.yk.photocritic.ui.design.PrimaryButton
import com.yk.photocritic.ui.design.QuietButton
import com.yk.photocritic.ui.design.Spacing
import com.yk.photocritic.ui.design.Tokens

/**
 * The consent sheet (G13): before the first send to a provider and when its
 * text changes. Dismissing it is the same as "Not now", which records nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentSheet(
    consent: ConsentUi,
    appPolicyUrl: String,
    onAgree: () -> Unit,
    onNotNow: () -> Unit,
    openUrl: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onNotNow,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Tokens.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                stringResource(R.string.consent_title, consent.providerName),
                color = Tokens.text,
                fontSize = 20.sp,
                modifier = Modifier.semantics { heading() },
            )
            consent.text.summary.forEach { line ->
                Row {
                    Text("•", color = Tokens.accent, modifier = Modifier.padding(end = Spacing.s))
                    Text(line, color = Tokens.text, fontSize = 15.sp)
                }
            }
            Text(stringResource(R.string.consent_effective, consent.text.effectiveDate), color = Tokens.muted, fontSize = 13.sp)
            QuietButton(stringResource(R.string.consent_policy, consent.providerName), { openUrl(consent.text.policyUrl) })
            if (appPolicyUrl.isNotEmpty()) QuietButton(stringResource(R.string.consent_app_policy), { openUrl(appPolicyUrl) })
            Row(Modifier.fillMaxWidth().padding(bottom = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                QuietButton(stringResource(R.string.consent_not_now), onNotNow, Modifier.weight(1f).heightIn(min = 48.dp))
                PrimaryButton(stringResource(R.string.consent_agree), onAgree, Modifier.weight(1f).heightIn(min = 48.dp))
            }
        }
    }
}
