package com.yk.photocritic.core.config

import com.yk.photocritic.core.providers.ModelSettings
import com.yk.photocritic.core.providers.ProviderId

/** The Remote Config parameters (design §8), after [ConfigValidator] has checked them. */
data class RemoteConfigValues(
    val meta: ConfigMeta,
    val killSwitch: KillSwitchConfig,
    val providers: Map<ProviderId, ProviderConfig>,
    val models: List<ModelConfig>,
    val consentTexts: Map<ProviderId, ConsentText>,
    val privacyPolicy: PrivacyPolicyConfig,
)

data class ConfigMeta(
    val configVersion: Int,
    val publishedAt: String,
)

data class KillSwitchConfig(
    val appDisabled: Boolean,
    val message: String,
    val minSupportedAppVersion: AppVersion,
    /** Empty when there is no update link. */
    val updateUrl: String,
    val disabledProviders: Set<ProviderId>,
    val disabledModels: Set<String>,
    val bannerMessage: String,
    val reportsDisabled: Boolean,
)

data class ProviderConfig(
    val id: ProviderId,
    val enabled: Boolean,
    val displayName: String,
    val baseUrl: String,
    val keyHelpUrl: String,
    val consentTextVersion: Int,
)

enum class ModelStatus(
    val wire: String,
) {
    ACTIVE("active"),
    PREVIEW("preview"),
    DEPRECATED("deprecated"),
    RETIRED("retired"),
}

data class ModelConfig(
    val provider: ProviderId,
    val id: String,
    val displayName: String,
    /** `Fast`, `Best` or empty; display only. */
    val label: String,
    val maxLongEdgePx: Int,
    val maxBytes: Int,
    val imageDetail: String,
    val maxOutputTokens: Int,
    /** `low`, `medium`, `high`, or empty for the provider's default. */
    val thinkingLevel: String,
    val contextTokens: Int,
    val status: ModelStatus,
    /** Empty when there is no replacement. */
    val replacementId: String,
    val minAppVersion: AppVersion,
) {
    val settings: ModelSettings get() = ModelSettings(id, maxOutputTokens, contextTokens, thinkingLevel)
}

data class ConsentText(
    val version: Int,
    val effectiveDate: String,
    val summary: List<String>,
    val policyUrl: String,
)

data class PrivacyPolicyConfig(
    val version: Int,
    /** Empty until a hosted copy exists. */
    val url: String,
)
