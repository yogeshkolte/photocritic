package com.yk.photocritic.core.config

import com.yk.photocritic.core.providers.ProviderId

/** How a model is labelled in the model list (file 26). */
sealed interface ModelTag {
    data object None : ModelTag

    data object Preview : ModelTag

    data class EndingSoon(
        val replacementName: String,
    ) : ModelTag
}

data class ModelChoice(
    val model: ModelConfig,
    val tag: ModelTag,
)

/**
 * The validated config for this session (design §8 step 4). Built once at
 * cold start; screens read only this. Applies the file 26 display rules.
 */
class Snapshot(
    val values: RemoteConfigValues,
    val appVersion: AppVersion,
) {
    val configVersion: Int get() = values.meta.configVersion
    val killSwitch: KillSwitchConfig get() = values.killSwitch

    fun provider(id: ProviderId): ProviderConfig = values.providers.getValue(id)

    fun consentText(id: ProviderId): ConsentText = values.consentTexts.getValue(id)

    /** Disabled when `enabled` is false or the kill switch lists it. */
    fun isProviderEnabled(id: ProviderId): Boolean = provider(id).enabled && id !in killSwitch.disabledProviders

    val enabledProviders: List<ProviderId> get() = ProviderId.entries.filter(::isProviderEnabled)

    fun model(
        provider: ProviderId,
        modelId: String,
    ): ModelConfig? = values.models.firstOrNull { it.provider == provider && it.id == modelId }

    fun isModelVisible(model: ModelConfig): Boolean =
        model.status != ModelStatus.RETIRED &&
            model.id !in killSwitch.disabledModels &&
            model.minAppVersion <= appVersion &&
            isProviderEnabled(model.provider)

    /** The models the setup screen lists for [provider], in config order. */
    fun modelChoices(provider: ProviderId): List<ModelChoice> =
        values.models
            .filter { it.provider == provider && isModelVisible(it) }
            .map { ModelChoice(it, tag(it)) }

    private fun tag(model: ModelConfig): ModelTag =
        when (model.status) {
            ModelStatus.PREVIEW -> ModelTag.Preview
            ModelStatus.DEPRECATED ->
                ModelTag.EndingSoon(values.models.first { it.id == model.replacementId }.displayName)
            else -> ModelTag.None
        }
}
