package com.yk.photocritic.features.session

import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yk.photocritic.AppContainer
import com.yk.photocritic.core.config.ConsentText
import com.yk.photocritic.core.config.ModelConfig
import com.yk.photocritic.core.config.Snapshot
import com.yk.photocritic.core.consent.ConsentRules
import com.yk.photocritic.core.providers.PcError
import com.yk.photocritic.core.providers.PcPhase
import com.yk.photocritic.core.providers.PromptKind
import com.yk.photocritic.core.providers.Provider
import com.yk.photocritic.core.providers.ProviderId
import com.yk.photocritic.core.providers.TurnEvent
import com.yk.photocritic.core.providers.TurnRequest
import com.yk.photocritic.core.session.SessionController
import com.yk.photocritic.core.session.SessionEvent
import com.yk.photocritic.core.session.SessionState
import com.yk.photocritic.features.image.PhotoRecord
import com.yk.photocritic.features.image.PrepareResult
import com.yk.photocritic.platform.storage.ConsentStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** What the consent sheet shows (G13). */
data class ConsentUi(
    val providerName: String,
    val text: ConsentText,
)

/**
 * One conversation (file 37): owns the file 29 controller and connects the
 * photo, consent, key and provider. The conversation ends, and its files
 * are deleted, on New photo, on a provider or model change, and when the
 * ViewModel is cleared (§5: nothing is restored after process death).
 */
@Suppress("TooManyFunctions") // One method per user action in §5.
class SessionViewModel(
    private val c: AppContainer,
    private val snapshot: Snapshot,
    /** The longest edge the photo is decoded to for the screen (§6). */
    private val displayMaxPx: Int,
) : ViewModel() {
    private val controller = SessionController(viewModelScope)
    private val events = SessionTelemetry(c.telemetry, snapshot.configVersion)
    val session: StateFlow<SessionState> = controller.state
    val online: StateFlow<Boolean> = c.connectivity?.online ?: MutableStateFlow(true)

    private val displayed = MutableStateFlow<Bitmap?>(null)

    /** The original photo for the screen, or null while it loads or when there is none. */
    val photo: StateFlow<Bitmap?> = displayed.asStateFlow()

    private var record: PhotoRecord? = null
    private var cameraFile: File? = null
    private var providerId: ProviderId? = null
    private var model: ModelConfig? = null

    /** Completes once the saved provider and model are read; photo and Send wait for it. */
    private val selectionLoaded = CompletableDeferred<Unit>()

    init {
        viewModelScope.launch {
            refreshSelection()
            selectionLoaded.complete(Unit)
        }
    }

    /** Reads the saved provider and model; called at start and after setup saves. */
    suspend fun refreshSelection() {
        val settings = c.settings.read()
        providerId = settings.providerId
        model = providerId?.let { p -> settings.modelFor(p)?.let { snapshot.model(p, it) } }
    }

    val bannerMessage: String get() = snapshot.killSwitch.bannerMessage

    fun consent(): ConsentUi? = providerId?.let { ConsentUi(snapshot.provider(it).displayName, snapshot.consentText(it)) }

    /** A photo from the picker, or from the camera with its cache [captureFile]. */
    fun photoChosen(
        uri: Uri,
        captureFile: File?,
    ) {
        endConversation()
        val next = PhotoRecord(uri)
        record = next
        cameraFile = captureFile
        controller.dispatch(SessionEvent.PhotoChosen(next.imageVersionId))
        viewModelScope.launch {
            displayed.value = c.imagePipeline.decodeForDisplay(uri, displayMaxPx)
            prepare(next, if (captureFile != null) "camera" else "picker")
        }
    }

    private suspend fun prepare(
        photo: PhotoRecord,
        source: String,
    ) {
        selectionLoaded.await()
        val p = providerId
        val m = model
        if (p == null || m == null) return controller.dispatch(SessionEvent.PhotoFailed(localError("no model selected")))
        when (val result = c.imagePipeline.prepare(photo.originalUri, m.maxLongEdgePx, m.maxBytes, m.imageDetail)) {
            is PrepareResult.Ready -> {
                if (record?.imageVersionId != photo.imageVersionId) return c.cleanup.replaced(result.copy)
                record = photo.copy(sendCopy = result.copy)
                controller.dispatch(SessionEvent.PhotoReady)
                events.photoReady(p, m, source, result)
            }
            is PrepareResult.Failed ->
                if (record?.imageVersionId == photo.imageVersionId) {
                    controller.dispatch(SessionEvent.PhotoFailed(localError(result.reason)))
                }
        }
    }

    /** Send, or a starter button. The consent sheet comes first when the rules need it (G13). */
    fun ask(
        question: String,
        kind: PromptKind,
    ) {
        viewModelScope.launch {
            selectionLoaded.await()
            val p = providerId ?: return@launch
            val provider = c.providers.get(p) ?: return@launch
            val current = c.consent.current(p)
            val granted = current?.takeIf { it.decision == ConsentStore.GRANTED }?.consentTextVersion
            val needsConsent = !ConsentRules.canSend(provider.capabilities, granted, snapshot.consentText(p).version)
            controller.dispatch(SessionEvent.Ask(question, kind, needsConsent))
            if (!needsConsent) startAnswer(provider)
        }
    }

    /** "Agree and send": the answer starts only after the record is saved (§5). */
    fun agreeConsent() {
        val p = providerId ?: return
        val provider = c.providers.get(p) ?: return
        viewModelScope.launch {
            if (c.consent.record(p, snapshot.consentText(p).version, granted = true)) {
                controller.dispatch(SessionEvent.ConsentAgreed)
                startAnswer(provider)
            } else {
                controller.dispatch(SessionEvent.ConsentWriteFailed(localError("consent-write")))
            }
        }
    }

    /** "Not now": nothing is recorded; the question stays in the composer. */
    fun declineConsent() = controller.dispatch(SessionEvent.ConsentDeclined)

    fun stop() = controller.stop()

    fun dismiss() = controller.dispatch(SessionEvent.Dismiss)

    /** New photo, after the confirmation when a question was asked (G1, CUJ 5). */
    fun newPhoto() = endConversation()

    private suspend fun startAnswer(provider: Provider) {
        val state = session.value
        val message = state.messages.lastOrNull() ?: return
        val m = model ?: return
        val key = c.keys.get(provider.id)
        val image =
            try {
                withContext(Dispatchers.IO) { record?.sendCopy?.load() }
            } catch (_: IOException) {
                null
            }
        if (key == null || image == null) {
            val missing = localError(if (key == null) "no key" else "send copy missing")
            controller.run(flowOf(TurnEvent.Error(missing))) { missing }
            return
        }
        events.questionSent(provider.id, m, message.promptKind, state.history.size)
        val start = SystemClock.elapsedRealtime()
        var firstMs: Long? = null
        val stream =
            provider.send(TurnRequest(m.settings, state.history, message.question, image, message.promptKind), key).onEach {
                if (firstMs == null && it is TurnEvent.Text) firstMs = SystemClock.elapsedRealtime() - start
            }
        controller.run(stream, { end -> events.answerEnded(provider.id, m, end, firstMs, SystemClock.elapsedRealtime() - start) }) {
            PcError(PcPhase.MID_STREAM, null, null, null, "The answer ended without a finish reason.")
        }
    }

    private fun localError(detail: String) = PcError(PcPhase.BEFORE_RESPONSE, null, "local", null, detail)

    private fun endConversation() {
        controller.clear()
        record?.let { c.cleanup.conversationEnded(it, cameraFile) }
        record = null
        cameraFile = null
        displayed.value = null
    }

    override fun onCleared() {
        endConversation()
    }
}
