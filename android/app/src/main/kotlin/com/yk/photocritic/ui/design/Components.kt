package com.yk.photocritic.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yk.photocritic.R
import com.yk.photocritic.core.providers.PcError
import com.yk.photocritic.core.providers.PcPhase

object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radii {
    val small = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val pill = RoundedCornerShape(999.dp)
}

/** The one main action on a screen, in the amber accent (G21). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = Radii.pill,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Tokens.accent,
                contentColor = Tokens.onAccent,
                disabledContainerColor = Tokens.field,
                disabledContentColor = Tokens.muted,
            ),
    ) { Text(text, fontSize = 16.sp) }
}

@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = Tokens.text),
    ) {
        Text(text, fontSize = 15.sp)
    }
}

/** A control over the photo: a round button on at least a 65% dark tint (G19). 48 dp touch target. */
@Composable
fun PhotoIconButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp).background(Color.Black.copy(alpha = 0.65f), CircleShape),
    ) { Icon(painterResource(icon), contentDescription = label, tint = Tokens.text) }
}

/** A small icon-and-label action, such as Copy or Share. */
@Composable
fun SmallAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = Tokens.muted)) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(label, modifier = Modifier.padding(start = Spacing.xs), fontSize = 13.sp)
    }
}

/** The generic message with Dismiss and "Show details" (G12, §7). */
@Composable
fun ErrorBlock(
    onShowDetails: () -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().background(Tokens.field, Radii.small).padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row {
            Icon(
                painterResource(R.drawable.ic_circle_alert),
                contentDescription = null,
                tint = Tokens.danger,
                modifier = Modifier.size(20.dp),
            )
            Text(stringResource(R.string.generic_error), color = Tokens.text, modifier = Modifier.padding(start = Spacing.s))
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            QuietButton(stringResource(R.string.show_details), onShowDetails)
            onDismiss?.let { QuietButton(stringResource(R.string.dismiss), it) }
        }
    }
}

/** "Show details": where it failed, status, provider code, request ID and the redacted detail; never the key. */
@Composable
fun ErrorDetailsDialog(
    error: PcError,
    onClose: () -> Unit,
) {
    val where =
        when (error.phase) {
            PcPhase.BEFORE_RESPONSE -> R.string.phase_before_response
            PcPhase.MID_STREAM -> R.string.phase_mid_stream
            PcPhase.FINISH -> R.string.phase_finish
        }
    val rows =
        listOfNotNull(
            stringResource(R.string.details_where) to stringResource(where),
            error.httpStatus?.let { stringResource(R.string.details_http_status) to it.toString() },
            error.providerCode?.let { stringResource(R.string.details_provider_code) to it },
            error.requestId?.let { stringResource(R.string.details_request_id) to it },
            error.detail.takeIf { it.isNotBlank() }?.let { stringResource(R.string.details_detail) to it },
        )
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Tokens.surface,
        title = { Text(stringResource(R.string.details_title), color = Tokens.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                rows.forEach { (label, value) ->
                    Column {
                        Text(label, color = Tokens.muted, fontSize = 13.sp)
                        Text(value, color = Tokens.text)
                    }
                }
            }
        },
        confirmButton = { QuietButton(stringResource(R.string.ok), onClose) },
    )
}

/** CUJ 5: "This conversation will be cleared." */
@Composable
fun ConfirmClearDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Tokens.surface,
        text = { Text(stringResource(R.string.confirm_clear), color = Tokens.text) },
        confirmButton = { QuietButton(stringResource(R.string.confirm_continue), onConfirm) },
        dismissButton = { QuietButton(stringResource(R.string.cancel), onCancel) },
    )
}

/** A selectable row with a radio role, for providers and models. */
fun Modifier.radioRole(): Modifier = semantics { role = Role.RadioButton }
