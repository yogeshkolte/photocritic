package com.yk.photocritic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yk.photocritic.AppContainer
import com.yk.photocritic.R
import com.yk.photocritic.core.config.ConfigResult
import com.yk.photocritic.core.config.KillSwitch
import com.yk.photocritic.core.config.SavedChoice
import com.yk.photocritic.core.config.Snapshot
import com.yk.photocritic.core.config.StartRoute
import com.yk.photocritic.features.session.SessionViewModel
import com.yk.photocritic.features.setup.SetupViewModel
import com.yk.photocritic.platform.telemetry.PcEvent
import com.yk.photocritic.ui.design.PhotoCriticTheme
import com.yk.photocritic.ui.design.Tokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Where the app is after startup (file 38). */
sealed interface AppRoute {
    data object Loading : AppRoute

    data class Disabled(
        val reason: DisabledReason,
    ) : AppRoute

    /** [edit] is setup opened from home; [visit] makes each opening a fresh screen. */
    data class Setup(
        val snapshot: Snapshot,
        val killSwitchMessage: String?,
        val edit: Boolean = false,
        val visit: Int = 0,
    ) : AppRoute

    data class Home(
        val snapshot: Snapshot,
    ) : AppRoute
}

sealed interface DisabledReason {
    /** Fetch failed, timed out, or a value is invalid (CUJ 9). */
    data object ConfigUnavailable : DisabledReason

    /** [message] from `kill_switch.message`; empty shows the default text. */
    data class AppDisabled(
        val message: String,
    ) : DisabledReason

    /** [updateUrl] may be empty; then there is no Update button. */
    data class UpdateRequired(
        val updateUrl: String,
    ) : DisabledReason
}

/**
 * The cold-start sequence, in the file 38 order: clean up photo files;
 * install marker and key wipe; settings; config fetch and validation;
 * kill switch once; telemetry if opted in. Runs once per process.
 */
class Startup(
    private val c: AppContainer,
) {
    suspend fun run(): AppRoute {
        c.cleanup.atLaunch()
        try {
            c.installMarker.ensure(c.appVersionName)
        } catch (_: IOException) {
            // The marker is written again at the next start; the app can still run.
        }
        val settings = c.settings.read()
        val load = c.config.load(c.appVersion)
        val snapshot = (load.config as? ConfigResult.Valid)?.snapshot
        val route =
            if (snapshot == null) {
                AppRoute.Disabled(DisabledReason.ConfigUnavailable)
            } else {
                val provider = settings.providerId
                val saved = SavedChoice(provider, provider?.let(settings::modelFor), provider?.let { c.keys.get(it) } != null)
                when (val start = KillSwitch.evaluate(snapshot, saved)) {
                    is StartRoute.AppDisabled -> AppRoute.Disabled(DisabledReason.AppDisabled(start.message))
                    is StartRoute.UpdateRequired -> AppRoute.Disabled(DisabledReason.UpdateRequired(start.updateUrl))
                    is StartRoute.Setup -> AppRoute.Setup(snapshot, start.killSwitchMessage)
                    StartRoute.Home -> AppRoute.Home(snapshot)
                }
            }
        if (settings.shareUsage) c.telemetry.setSharing(true)
        c.telemetry.log(
            PcEvent.CONFIG_FETCH,
            mapOf("result" to load.result.wire, "config_version" to snapshot?.configVersion, "ms" to load.elapsedMs),
        )
        return route
    }
}

class StartupViewModel(
    startup: Startup,
) : ViewModel() {
    private val state = MutableStateFlow<AppRoute>(AppRoute.Loading)
    val route: StateFlow<AppRoute> = state.asStateFlow()

    init {
        viewModelScope.launch { state.value = startup.run() }
    }

    private var setupVisits = 0

    fun open(route: AppRoute) {
        state.value = route
    }

    /** Settings opens setup in edit mode until the Settings screen arrives in Phase 1.4. */
    fun openSettings(snapshot: Snapshot) = open(AppRoute.Setup(snapshot, null, edit = true, visit = ++setupVisits))
}

@Composable
fun AppNavigation(
    viewModel: StartupViewModel,
    container: AppContainer,
    displayMaxPx: Int,
    openUrl: (String) -> Unit,
) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    PhotoCriticTheme {
        when (val r = route) {
            AppRoute.Loading -> LaunchScreen()
            is AppRoute.Disabled -> DisabledScreen(r.reason, openUrl)
            is AppRoute.Setup -> SetupRoute(r, viewModel, container, displayMaxPx, openUrl)
            is AppRoute.Home -> {
                val session = sessionViewModel(container, r.snapshot, displayMaxPx)
                HomeScreen(
                    vm = session,
                    appPolicyUrl = r.snapshot.values.privacyPolicy.url,
                    onOpenSettings = { viewModel.openSettings(r.snapshot) },
                    openUrl = openUrl,
                )
            }
        }
    }
}

@Composable
private fun SetupRoute(
    r: AppRoute.Setup,
    nav: StartupViewModel,
    container: AppContainer,
    displayMaxPx: Int,
    openUrl: (String) -> Unit,
) {
    val setup: SetupViewModel =
        viewModel(
            key = "setup-${r.visit}",
            factory =
                viewModelFactory {
                    initializer { SetupViewModel(container, r.snapshot, firstRun = !r.edit) }
                },
        )
    // In edit mode the conversation lives on behind setup; on first run there is none yet.
    val session = if (r.edit) sessionViewModel(container, r.snapshot, displayMaxPx) else null
    val active =
        session
            ?.session
            ?.collectAsStateWithLifecycle()
            ?.value
            ?.messages
            ?.isNotEmpty() == true
    val scope = rememberCoroutineScope()
    SetupScreen(
        vm = setup,
        killSwitchMessage = r.killSwitchMessage,
        conversationActive = active,
        onSaved = { changed ->
            scope.launch {
                if (changed) session?.newPhoto()
                session?.refreshSelection()
                nav.open(AppRoute.Home(r.snapshot))
            }
        },
        onBack = if (r.edit) ({ nav.open(AppRoute.Home(r.snapshot)) }) else null,
        openUrl = openUrl,
    )
}

/** One conversation for the activity, kept across rotation and the setup screen (§5). */
@Composable
private fun sessionViewModel(
    container: AppContainer,
    snapshot: Snapshot,
    displayMaxPx: Int,
): SessionViewModel =
    viewModel(key = "session", factory = viewModelFactory { initializer { SessionViewModel(container, snapshot, displayMaxPx) } })

/** Shown while the config loads, up to `CONFIG_FETCH_TIMEOUT_MS`. */
@Composable
fun LaunchScreen() {
    val loading = stringResource(R.string.loading)
    Box(Modifier.fillMaxSize().background(Tokens.bg).semantics { contentDescription = loading })
}
