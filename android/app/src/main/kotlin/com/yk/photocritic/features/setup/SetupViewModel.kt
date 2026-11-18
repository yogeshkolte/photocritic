package com.yk.photocritic.features.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yk.photocritic.AppContainer
import com.yk.photocritic.core.config.ModelChoice
import com.yk.photocritic.core.config.Snapshot
import com.yk.photocritic.core.providers.KeyCheckResult
import com.yk.photocritic.core.providers.PcError
import com.yk.photocritic.core.providers.PcPhase
import com.yk.photocritic.core.providers.ProviderId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface SetupStatus {
    data object Idle : SetupStatus

    data object Checking : SetupStatus

    /** The generic message with "Show details" (G12). */
    data class Failed(
        val error: PcError,
    ) : SetupStatus

    data object Saved : SetupStatus
}

data class ProviderOption(
    val id: ProviderId,
    val displayName: String,
)

data class SetupUi(
    val providers: List<ProviderOption> = emptyList(),
    val provider: ProviderId? = null,
    val models: List<ModelChoice> = emptyList(),
    val modelId: String? = null,
    /** A key for [provider] is already stored, so the field may stay empty. */
    val hasSavedKey: Boolean = false,
    val keyHelpUrl: String = "",
    val shareUsage: Boolean = false,
    val status: SetupStatus = SetupStatus.Idle,
    val loaded: Boolean = false,
)

/**
 * The setup screen (file 38, CUJ 1, G9, G10): provider, model by the file 26
 * rules, key check, and on first run the sharing switch. It never picks a
 * model for the user (G18). Typed keys are not kept here: the screen passes
 * one to [save], which checks it before storing it.
 */
class SetupViewModel(
    private val c: AppContainer,
    private val snapshot: Snapshot,
    val firstRun: Boolean,
) : ViewModel() {
    private val mutable = MutableStateFlow(SetupUi())
    val ui: StateFlow<SetupUi> = mutable.asStateFlow()
    private var savedProvider: ProviderId? = null
    private var savedModels: Map<String, String> = emptyMap()
    private var check: Job? = null

    init {
        viewModelScope.launch {
            val settings = c.settings.read()
            savedProvider = settings.providerId
            savedModels = settings.models
            val options =
                snapshot.enabledProviders
                    .filter { it in c.providers.ids }
                    .map { ProviderOption(it, snapshot.provider(it).displayName) }
            mutable.update { it.copy(providers = options, shareUsage = settings.shareUsage, loaded = true) }
            (savedProvider?.takeIf { p -> options.any { it.id == p } } ?: options.singleOrNull()?.id)?.let { select(it) }
        }
    }

    fun select(provider: ProviderId) {
        viewModelScope.launch {
            val models = snapshot.modelChoices(provider)
            val saved = savedModels[provider.wire]?.takeIf { id -> models.any { it.model.id == id } }
            val hasKey = c.keys.get(provider) != null
            mutable.update {
                it.copy(
                    provider = provider,
                    models = models,
                    modelId = saved,
                    hasSavedKey = hasKey,
                    keyHelpUrl = snapshot.provider(provider).keyHelpUrl,
                    status = SetupStatus.Idle,
                )
            }
        }
    }

    fun selectModel(id: String) = mutable.update { it.copy(modelId = id, status = SetupStatus.Idle) }

    fun setShareUsage(on: Boolean) = mutable.update { it.copy(shareUsage = on) }

    fun dismissError() = mutable.update { it.copy(status = SetupStatus.Idle) }

    /** True when saving would change the provider or model in use, which ends the conversation (CUJ 5). */
    fun changesSelection(): Boolean {
        val s = ui.value
        return s.provider != savedProvider || (s.provider != null && s.modelId != savedModels[s.provider.wire])
    }

    fun canSave(typedKey: String): Boolean {
        val s = ui.value
        return s.provider != null && s.modelId != null && s.status != SetupStatus.Checking && (typedKey.isNotBlank() || s.hasSavedKey)
    }

    /** "Check key and continue": checks a typed key, then stores the key and the choice. */
    fun save(typedKey: String) {
        if (!canSave(typedKey)) return
        val s = ui.value
        val provider = s.provider ?: return
        val model = snapshot.model(provider, s.modelId ?: return) ?: return
        val key = typedKey.trim()
        check =
            viewModelScope.launch {
                mutable.update { it.copy(status = SetupStatus.Checking) }
                if (key.isNotEmpty()) {
                    val result =
                        c.providers.get(provider)?.checkKey(key, model.settings) ?: KeyCheckResult.Failed(storageError("no client"))
                    if (result is KeyCheckResult.Failed) {
                        mutable.update { it.copy(status = SetupStatus.Failed(result.error)) }
                        return@launch
                    }
                }
                try {
                    if (key.isNotEmpty()) c.keys.set(provider, key)
                    c.settings.select(provider, model.id)
                    if (firstRun) {
                        c.settings.setShareUsage(s.shareUsage)
                        c.settings.completeOnboarding()
                        c.telemetry.setSharing(s.shareUsage)
                    }
                    mutable.update { it.copy(status = SetupStatus.Saved, hasSavedKey = true) }
                } catch (e: IOException) {
                    mutable.update { it.copy(status = SetupStatus.Failed(storageError(e.javaClass.simpleName))) }
                }
            }
    }

    private fun storageError(detail: String) = PcError(PcPhase.BEFORE_RESPONSE, null, "storage", null, detail)

    override fun onCleared() {
        check?.cancel()
    }
}
