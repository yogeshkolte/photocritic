package com.yk.photocritic.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yk.photocritic.R
import com.yk.photocritic.core.providers.PcError
import com.yk.photocritic.core.providers.PromptKind
import com.yk.photocritic.core.session.MessageStatus
import com.yk.photocritic.core.session.Phase
import com.yk.photocritic.core.session.SessionState
import com.yk.photocritic.features.image.Pick
import com.yk.photocritic.features.image.rememberCamera
import com.yk.photocritic.features.image.rememberPhotoPicker
import com.yk.photocritic.features.session.SessionViewModel
import com.yk.photocritic.ui.design.ConfirmClearDialog
import com.yk.photocritic.ui.design.ErrorBlock
import com.yk.photocritic.ui.design.ErrorDetailsDialog
import com.yk.photocritic.ui.design.PhotoIconButton
import com.yk.photocritic.ui.design.PrimaryButton
import com.yk.photocritic.ui.design.QuietButton
import com.yk.photocritic.ui.design.Radii
import com.yk.photocritic.ui.design.Spacing
import com.yk.photocritic.ui.design.Tokens
import kotlinx.coroutines.launch

private const val COLLAPSED_SHARE = 0.45f
private const val EXPANDED_SHARE = 0.92f
private const val COLLAPSED_TINT = 0.72f
private const val EXPANDED_TINT = 0.94f

/**
 * Home, phone layout (file 38, G19): the photo fills the screen; the top
 * bar has only Settings and New photo; the conversation panel has two
 * heights, collapsed (sized to its content up to a limit) and expanded,
 * on a 72% or 94% dark tint. The wide two-pane layout arrives in Phase 1.4.
 */
@Composable
fun HomeScreen(
    vm: SessionViewModel,
    appPolicyUrl: String,
    onOpenSettings: () -> Unit,
    openUrl: (String) -> Unit,
) {
    val session by vm.session.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val photo by vm.photo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var question by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var details by remember { mutableStateOf<PcError?>(null) }
    var confirmNewPhoto by remember { mutableStateOf(false) }
    val noCamera = stringResource(R.string.camera_unavailable)
    val pick = rememberPhotoPicker { vm.photoChosen(it, null) }
    val camera =
        rememberCamera(onCaptured = { vm.photoChosen(it, Pick.fileFor(context, it)) }, onUnavailable = {
            scope.launch { snackbar.showSnackbar(noCamera) }
        })
    // The question leaves the composer once its message exists; "Not now" keeps it (§5).
    LaunchedEffect(session.messages.size) {
        if (session.messages.lastOrNull()?.question == question.trim()) question = ""
    }
    val panel =
        PanelActions(
            onQuestion = { question = it },
            onAsk = { text, kind ->
                if (kind == PromptKind.EXTRACT) expanded = true
                vm.ask(text, kind)
            },
            onStop = vm::stop,
            onDismiss = vm::dismiss,
            onDetails = { details = it },
        )

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        if (session.phase == Phase.NO_PHOTO) EmptyState(onTake = camera, onChoose = pick) else photo?.let { PhotoView(it) }
        TopControls(
            hasPhoto = session.phase != Phase.NO_PHOTO,
            banner = vm.bannerMessage,
            online = online,
            onSettings = onOpenSettings,
            onNewPhoto = { if (session.messages.isNotEmpty()) confirmNewPhoto = true else vm.newPhoto() },
        )
        if (session.phase != Phase.NO_PHOTO) {
            ConversationPanel(
                maxHeight = maxHeight,
                expanded = expanded,
                onExpanded = { expanded = it },
                session = session,
                online = online,
                question = question,
                messageActions = rememberMessageActions(snackbar, vm::dismiss.takeIf { session.phase == Phase.ERROR }) { details = it },
                panel = panel,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (session.phase == Phase.AWAITING_CONSENT) {
        vm.consent()?.let { ConsentSheet(it, appPolicyUrl, onAgree = vm::agreeConsent, onNotNow = vm::declineConsent, openUrl = openUrl) }
    }
    details?.let { ErrorDetailsDialog(it) { details = null } }
    if (confirmNewPhoto) {
        ConfirmClearDialog(
            onConfirm = {
                confirmNewPhoto = false
                expanded = false
                vm.newPhoto()
            },
            onCancel = { confirmNewPhoto = false },
        )
    }
}

@Composable
private fun rememberMessageActions(
    snackbar: SnackbarHostState,
    dismiss: (() -> Unit)?,
    showDetails: (PcError) -> Unit,
): MessageActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.copied)
    return MessageActions(
        copy = { text ->
            copy(context, text)
            // Android 13 and later show their own confirmation.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) scope.launch { snackbar.showSnackbar(copied) }
        },
        share = { share(context, it) },
        showDetails = showDetails,
        dismiss = dismiss,
    )
}

@Composable
private fun TopControls(
    hasPhoto: Boolean,
    banner: String,
    online: Boolean,
    onSettings: () -> Unit,
    onNewPhoto: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(Modifier.fillMaxWidth()) {
            PhotoIconButton(R.drawable.ic_settings, stringResource(R.string.settings), onSettings)
            Spacer(Modifier.weight(1f))
            if (hasPhoto) PhotoIconButton(R.drawable.ic_image_plus, stringResource(R.string.new_photo), onNewPhoto)
        }
        banner.takeIf { it.isNotEmpty() }?.let { Banner(it, null) }
        if (!online) Banner(stringResource(R.string.offline), R.drawable.ic_wifi_off)
    }
}

@Suppress("LongParameterList") // The panel's state, passed down from the screen.
@Composable
private fun BoxWithConstraintsScope.ConversationPanel(
    maxHeight: Dp,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    session: SessionState,
    online: Boolean,
    question: String,
    messageActions: MessageActions,
    panel: PanelActions,
) {
    val height by animateDpAsState(maxHeight * (if (expanded) EXPANDED_SHARE else COLLAPSED_SHARE), label = "panel")
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .then(if (expanded) Modifier.height(height) else Modifier.heightIn(max = height))
            .background(Tokens.bg.copy(alpha = if (expanded) EXPANDED_TINT else COLLAPSED_TINT), Radii.sheet)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Spacing.l),
    ) {
        Handle(expanded, onExpanded)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m), modifier = Modifier.padding(bottom = Spacing.m)) {
            MessageList(session.messages, messageActions, Modifier.weight(1f, fill = false))
            PhaseStatus(session, panel)
            if (session.hasPhoto) {
                val canSend = session.canSend && online
                if (!session.answering) StarterChips(enabled = canSend) { label, kind -> panel.onAsk(label, kind) }
                Composer(
                    text = question,
                    onText = panel.onQuestion,
                    canSend = canSend,
                    answering = session.answering,
                    onSend = { panel.onAsk(question.trim(), PromptKind.FREE) },
                    onStop = panel.onStop,
                )
            }
        }
    }
}

/** Preparing, the image error, or an error with no failed message (a consent record that could not be saved). */
@Composable
private fun PhaseStatus(
    session: SessionState,
    panel: PanelActions,
) {
    when (session.phase) {
        Phase.PREPARING -> {
            val preparing = stringResource(R.string.preparing_photo)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = preparing }) {
                CircularProgressIndicator(color = Tokens.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Text(preparing, color = Tokens.muted, modifier = Modifier.padding(start = Spacing.s))
            }
        }
        Phase.IMAGE_ERROR -> session.error?.let { e -> ErrorBlock({ panel.onDetails(e) }, panel.onDismiss) }
        Phase.ERROR ->
            session.error?.takeIf { e -> session.messages.none { it.status == MessageStatus.FAILED && it.error == e } }?.let { e ->
                ErrorBlock({ panel.onDetails(e) }, panel.onDismiss)
            }
        else -> Unit
    }
}

/** Tap or drag to switch between the two heights. */
@Composable
private fun Handle(
    expanded: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clickable { onChange(!expanded) }
            .pointerInput(expanded) {
                detectVerticalDragGestures { _, drag ->
                    if (drag < -DRAG_THRESHOLD) onChange(true)
                    if (drag > DRAG_THRESHOLD) onChange(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.width(36.dp).height(4.dp).background(Tokens.muted, Radii.pill)) }
}

private const val DRAG_THRESHOLD = 8f

@Composable
private fun Banner(
    text: String,
    icon: Int?,
) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.72f), Radii.small).padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(painterResource(it), null, tint = Tokens.text, modifier = Modifier.size(18.dp).padding(end = Spacing.xs)) }
        Text(text, color = Tokens.text, fontSize = 14.sp, modifier = Modifier.padding(start = if (icon != null) Spacing.s else 0.dp))
    }
}

@Composable
private fun EmptyState(
    onTake: () -> Unit,
    onChoose: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Tokens.bg).padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_scan_text), null, tint = Tokens.accent, modifier = Modifier.size(56.dp))
        Text(stringResource(R.string.home_empty_title), color = Tokens.text, fontSize = 24.sp, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.home_empty_body), color = Tokens.muted, fontSize = 15.sp, textAlign = TextAlign.Center)
        PrimaryButton(stringResource(R.string.take_photo), onTake, Modifier.fillMaxWidth().heightIn(min = 48.dp))
        QuietButton(stringResource(R.string.choose_photo), onChoose, Modifier.fillMaxWidth().heightIn(min = 48.dp))
    }
}

private fun copy(
    context: Context,
    text: String,
) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("PhotoCritic", text))
}

private fun share(
    context: Context,
    text: String,
) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
