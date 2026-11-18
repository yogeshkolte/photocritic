package com.yk.photocritic.core.config

import com.yk.photocritic.core.providers.ProviderId

/** The provider, model and key state saved on the device, read at cold start. */
data class SavedChoice(
    val provider: ProviderId?,
    val modelId: String?,
    val hasKey: Boolean,
)

/** Where the app goes after the config is loaded (file 38). */
sealed interface StartRoute {
    /** `appDisabled`; [message] may be empty, and the screen then shows its own text. */
    data class AppDisabled(
        val message: String,
    ) : StartRoute

    /** The app version is below `minSupportedAppVersion`; [updateUrl] may be empty. */
    data class UpdateRequired(
        val updateUrl: String,
    ) : StartRoute

    /** [killSwitchMessage] is set when the saved provider or model is hidden (G18). */
    data class Setup(
        val killSwitchMessage: String?,
    ) : StartRoute

    data object Home : StartRoute
}

/** Evaluated once at cold start; there is no live update (G18, design §8 step 5). */
object KillSwitch {
    const val DEFAULT_MESSAGE = "The model you used is no longer available. Pick another one."

    fun evaluate(
        snapshot: Snapshot,
        saved: SavedChoice,
    ): StartRoute {
        val ks = snapshot.killSwitch
        if (ks.appDisabled) return StartRoute.AppDisabled(ks.message)
        if (snapshot.appVersion < ks.minSupportedAppVersion) return StartRoute.UpdateRequired(ks.updateUrl)
        val provider = saved.provider
        val model = provider?.let { p -> saved.modelId?.let { snapshot.model(p, it) } }
        val hidden =
            provider != null &&
                (!snapshot.isProviderEnabled(provider) || (saved.modelId != null && (model == null || !snapshot.isModelVisible(model))))
        return when {
            hidden -> StartRoute.Setup(ks.message.ifEmpty { DEFAULT_MESSAGE })
            model == null || !saved.hasKey -> StartRoute.Setup(null)
            else -> StartRoute.Home
        }
    }
}
