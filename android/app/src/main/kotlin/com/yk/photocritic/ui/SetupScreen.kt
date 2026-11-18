package com.yk.photocritic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yk.photocritic.R
import com.yk.photocritic.core.config.ModelChoice
import com.yk.photocritic.core.config.ModelTag
import com.yk.photocritic.core.providers.ProviderId
import com.yk.photocritic.features.setup.SetupStatus
import com.yk.photocritic.features.setup.SetupUi
import com.yk.photocritic.features.setup.SetupViewModel
import com.yk.photocritic.ui.design.ConfirmClearDialog
import com.yk.photocritic.ui.design.ErrorBlock
import com.yk.photocritic.ui.design.ErrorDetailsDialog
import com.yk.photocritic.ui.design.PrimaryButton
import com.yk.photocritic.ui.design.QuietButton
import com.yk.photocritic.ui.design.Radii
import com.yk.photocritic.ui.design.Spacing
import com.yk.photocritic.ui.design.Tokens
import com.yk.photocritic.ui.design.radioRole

/**
 * Setup (file 38): first run, or edit mode from Settings. [onSaved] gets
 * whether the provider or model changed. In edit mode, Back returns home
 * and a change that ends a conversation asks first (CUJ 5).
 */
@Composable
fun SetupScreen(
    vm: SetupViewModel,
    killSwitchMessage: String?,
    conversationActive: Boolean,
    onSaved: (changed: Boolean) -> Unit,
    onBack: (() -> Unit)?,
    openUrl: (String) -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // The typed key lives only here, never in saved state or the ViewModel.
    var key by remember { mutableStateOf("") }
    var details by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var changed by remember { mutableStateOf(false) }
    onBack?.let { BackHandler(onBack = it) }
    LaunchedEffect(ui.status) {
        if (ui.status == SetupStatus.Saved) {
            key = ""
            onSaved(changed)
        }
    }
    val save = {
        changed = vm.changesSelection()
        if (changed && conversationActive) confirm = true else vm.save(key)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Tokens.bg)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Header(vm.firstRun, onBack)
            killSwitchMessage?.let { Notice(it) }
            Text(stringResource(R.string.setup_byok), color = Tokens.muted, fontSize = 15.sp)
            ProviderSection(ui, vm::select)
            ui.provider?.let { provider ->
                ModelSection(ui, vm::selectModel)
                KeySection(ui, provider, key, {
                    key = it
                    if (ui.status is SetupStatus.Failed) vm.dismissError()
                }, openUrl)
            }
            if (vm.firstRun) ShareSwitch(ui.shareUsage, vm::setShareUsage)
            (ui.status as? SetupStatus.Failed)?.let { ErrorBlock(onShowDetails = { details = true }, onDismiss = vm::dismissError) }
            Footer(
                checking = ui.status == SetupStatus.Checking,
                newKey = key.isNotBlank() || !ui.hasSavedKey,
                enabled = vm.canSave(key),
                onSave = save,
            )
        }
    }

    if (details) (ui.status as? SetupStatus.Failed)?.let { ErrorDetailsDialog(it.error) { details = false } }
    if (confirm) {
        ConfirmClearDialog(
            onConfirm = {
                confirm = false
                vm.save(key)
            },
            onCancel = { confirm = false },
        )
    }
}

@Composable
private fun Header(
    firstRun: Boolean,
    onBack: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        onBack?.let {
            IconButton(onClick = it) {
                Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.back), tint = Tokens.text)
            }
        }
        Text(
            stringResource(if (firstRun) R.string.setup_title else R.string.setup_title_edit),
            color = Tokens.text,
            fontSize = 24.sp,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun ProviderSection(
    ui: SetupUi,
    onSelect: (ProviderId) -> Unit,
) {
    SectionLabel(stringResource(R.string.setup_provider))
    if (ui.loaded && ui.providers.isEmpty()) Text(stringResource(R.string.setup_no_providers), color = Tokens.muted)
    ui.providers.forEach { p ->
        Choice(title = p.displayName, subtitle = null, selected = p.id == ui.provider, onClick = { onSelect(p.id) })
    }
}

@Composable
private fun ModelSection(
    ui: SetupUi,
    onSelect: (String) -> Unit,
) {
    SectionLabel(stringResource(R.string.setup_model))
    if (ui.modelId == null) Text(stringResource(R.string.setup_pick_model), color = Tokens.muted, fontSize = 14.sp)
    ui.models.forEach { choice ->
        Choice(
            title = choice.model.displayName,
            subtitle = subtitle(choice),
            badge = choice.model.label.takeIf { it.isNotEmpty() },
            selected = choice.model.id == ui.modelId,
            onClick = { onSelect(choice.model.id) },
        )
    }
}

@Composable
private fun KeySection(
    ui: SetupUi,
    provider: ProviderId,
    key: String,
    onKey: (String) -> Unit,
    openUrl: (String) -> Unit,
) {
    var showKey by remember { mutableStateOf(false) }
    SectionLabel(stringResource(R.string.setup_key))
    OutlinedTextField(
        value = key,
        onValueChange = onKey,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { if (ui.hasSavedKey) Text(stringResource(R.string.setup_key_saved)) },
        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            QuietButton(
                stringResource(if (showKey) R.string.setup_key_hide else R.string.setup_key_show),
                { showKey = !showKey },
            )
        },
        enabled = ui.status != SetupStatus.Checking,
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
    if (ui.keyHelpUrl.isNotEmpty()) {
        val name =
            ui.providers
                .firstOrNull { it.id == provider }
                ?.displayName
                .orEmpty()
        QuietButton(stringResource(R.string.setup_key_help, name), { openUrl(ui.keyHelpUrl) })
    }
    if (provider == ProviderId.GEMINI) Text(stringResource(R.string.setup_gemini_cost), color = Tokens.muted, fontSize = 13.sp)
}

/** First run only (G16): off unless the user turns it on. */
@Composable
private fun ShareSwitch(
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setup_share), color = Tokens.text)
            Text(stringResource(R.string.setup_share_detail), color = Tokens.muted, fontSize = 13.sp)
        }
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Tokens.accent, checkedThumbColor = Tokens.onAccent),
        )
    }
}

@Composable
private fun Footer(
    checking: Boolean,
    newKey: Boolean,
    enabled: Boolean,
    onSave: () -> Unit,
) {
    if (checking) {
        val label = stringResource(R.string.setup_checking)
        Row(Modifier.fillMaxWidth().semantics { contentDescription = label }, horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(color = Tokens.accent, modifier = Modifier.size(32.dp))
        }
    } else {
        PrimaryButton(
            text = stringResource(if (newKey) R.string.setup_continue else R.string.setup_save),
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun subtitle(choice: ModelChoice): String? =
    when (val tag = choice.tag) {
        ModelTag.None -> null
        ModelTag.Preview -> stringResource(R.string.tag_preview)
        is ModelTag.EndingSoon -> stringResource(R.string.tag_ending_soon, tag.replacementName)
    }

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Tokens.muted, fontSize = 13.sp, modifier = Modifier.padding(top = Spacing.s).semantics { heading() })
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        color = Tokens.text,
        modifier = Modifier.fillMaxWidth().background(Tokens.field, Radii.small).padding(Spacing.m),
    )
}

@Composable
private fun Choice(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (selected) Tokens.field else Tokens.surface, Radii.small)
            .border(1.dp, if (selected) Tokens.accent else Tokens.surface, Radii.small)
            .radioRole()
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Tokens.text, fontSize = 16.sp)
            subtitle?.let { Text(it, color = Tokens.muted, fontSize = 13.sp) }
        }
        badge?.let {
            Text(
                it,
                color = Tokens.onAccent,
                fontSize = 12.sp,
                modifier = Modifier.background(Tokens.accent, Radii.pill).padding(horizontal = Spacing.s, vertical = 2.dp),
            )
        }
    }
}
