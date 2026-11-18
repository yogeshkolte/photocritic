package com.yk.photocritic.ui

import com.yk.photocritic.core.providers.PcError
import com.yk.photocritic.core.providers.PromptKind

/** Actions a message offers; the screen supplies them. */
class MessageActions(
    val copy: (String) -> Unit,
    val share: (String) -> Unit,
    val showDetails: (PcError) -> Unit,
    /** Dismiss for the current error, or null when there is none to dismiss. */
    val dismiss: (() -> Unit)?,
)

/** What the conversation panel can do. */
class PanelActions(
    val onQuestion: (String) -> Unit,
    val onAsk: (String, PromptKind) -> Unit,
    val onStop: () -> Unit,
    val onDismiss: () -> Unit,
    val onDetails: (PcError) -> Unit,
)
