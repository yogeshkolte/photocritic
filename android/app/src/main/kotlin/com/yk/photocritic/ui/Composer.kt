package com.yk.photocritic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yk.photocritic.R
import com.yk.photocritic.core.config.Tuning
import com.yk.photocritic.core.providers.PromptKind
import com.yk.photocritic.ui.design.Radii
import com.yk.photocritic.ui.design.Spacing
import com.yk.photocritic.ui.design.Tokens

/** Identify, Explain, Copy text (G2). Hidden while an answer is arriving. */
@Composable
fun StarterChips(
    enabled: Boolean,
    onStarter: (String, PromptKind) -> Unit,
) {
    val starters =
        listOf(
            stringResource(R.string.starter_identify) to PromptKind.IDENTIFY,
            stringResource(R.string.starter_explain) to PromptKind.EXPLAIN,
            stringResource(R.string.starter_copy_text) to PromptKind.EXTRACT,
        )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        items(starters.size) { i ->
            val (label, kind) = starters[i]
            AssistChip(
                onClick = { onStarter(label, kind) },
                label = { Text(label) },
                enabled = enabled,
                shape = Radii.pill,
                colors = AssistChipDefaults.assistChipColors(labelColor = Tokens.text, containerColor = Tokens.field),
                border = null,
            )
        }
    }
}

/** Multi-line question, at most `MAX_QUESTION_CHARS`; Send, or Stop while answering (G4). */
@Composable
fun Composer(
    text: String,
    onText: (String) -> Unit,
    canSend: Boolean,
    answering: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OutlinedTextField(
            value = text,
            onValueChange = { onText(it.take(Tuning.MAX_QUESTION_CHARS)) },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.composer_hint)) },
            maxLines = 5,
            shape = Radii.small,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Tokens.accent,
                    unfocusedBorderColor = Tokens.field,
                    focusedContainerColor = Tokens.field,
                    unfocusedContainerColor = Tokens.field,
                    cursorColor = Tokens.accent,
                ),
        )
        if (answering) {
            FilledIconButton(
                onClick = onStop,
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Tokens.text, contentColor = Tokens.bg),
            ) { Icon(painterResource(R.drawable.ic_square), stringResource(R.string.stop)) }
        } else {
            FilledIconButton(
                onClick = onSend,
                enabled = canSend && text.isNotBlank(),
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = Tokens.accent,
                        contentColor = Tokens.onAccent,
                        disabledContainerColor = Tokens.field,
                        disabledContentColor = Tokens.muted,
                    ),
            ) { Icon(painterResource(R.drawable.ic_send_horizontal), stringResource(R.string.send)) }
        }
    }
}
