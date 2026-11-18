package com.yk.photocritic.ui

import com.yk.photocritic.FakeKeyStore
import com.yk.photocritic.FakeTelemetryBackend
import com.yk.photocritic.TestConfig
import com.yk.photocritic.core.providers.ProviderId
import com.yk.photocritic.platform.config.FetchResult
import com.yk.photocritic.testContainer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The cold-start sequence and its routes (file 38, CUJs 1, 7 and 9). */
@RunWith(RobolectricTestRunner::class)
class AppNavigationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val keys = FakeKeyStore()
    private val telemetry = FakeTelemetryBackend()

    private fun container(values: Map<String, String>? = TestConfig.templateValues()) =
        testContainer(
            tmp.root,
            TestConfig.source(
                values,
                if (values ==
                    null
                ) {
                    FetchResult.TIMEOUT
                } else {
                    FetchResult.SUCCESS
                },
            ),
            keys,
            telemetry,
        )

    @Test
    fun firstLaunchOpensSetupAndWipesKeys() =
        runBlocking {
            keys.keys[ProviderId.GEMINI] = "from an earlier install"
            val route = Startup(container()).run()
            assertTrue(route is AppRoute.Setup && route.killSwitchMessage == null)
            assertEquals(1, keys.deleteAllCalls)
        }

    @Test
    fun aSavedChoiceWithAKeyGoesHome() =
        runBlocking {
            val c = container()
            Startup(c).run()
            c.settings.select(ProviderId.GEMINI, "gemini-4-hydrogen")
            keys.keys[ProviderId.GEMINI] = "AQ.key"
            assertTrue(Startup(c).run() is AppRoute.Home)
            assertEquals(1, keys.deleteAllCalls)
        }

    @Test
    fun aDisabledSavedModelOpensSetupWithTheMessage() =
        runBlocking {
            val values = TestConfig.templateValues().toMutableMap()
            values["kill_switch"] =
                values.getValue("kill_switch").replace("\"disabledModels\":[]", "\"disabledModels\":[\"gemini-4-hydrogen\"]")
            val c = container(values)
            c.settings.select(ProviderId.GEMINI, "gemini-4-hydrogen")
            keys.keys[ProviderId.GEMINI] = "AQ.key"
            val route = Startup(c).run()
            assertTrue(route.toString(), route is AppRoute.Setup && route.killSwitchMessage != null)
        }

    @Test
    fun noConfigShowsTheUnavailableScreen() =
        runBlocking {
            assertEquals(AppRoute.Disabled(DisabledReason.ConfigUnavailable), Startup(container(null)).run())
        }

    @Test
    fun appDisabledAndUpdateRequired() =
        runBlocking {
            val disabled = TestConfig.templateValues().toMutableMap()
            disabled["kill_switch"] = disabled.getValue("kill_switch").replace("\"appDisabled\":false", "\"appDisabled\":true")
            assertEquals(AppRoute.Disabled(DisabledReason.AppDisabled("")), Startup(container(disabled)).run())
            val update = TestConfig.templateValues().toMutableMap()
            update["kill_switch"] =
                update.getValue("kill_switch").replace("\"minSupportedAppVersion\":\"0.1.0\"", "\"minSupportedAppVersion\":\"9.0.0\"")
            assertEquals(AppRoute.Disabled(DisabledReason.UpdateRequired("")), Startup(container(update)).run())
        }

    @Test
    fun telemetryStartsOnlyWhenOptedInAndLogsTheFetch() =
        runBlocking {
            val c = container()
            Startup(c).run()
            assertEquals(null, telemetry.collection)
            assertTrue(telemetry.events.isEmpty())
            c.settings.setShareUsage(true)
            Startup(c).run()
            assertEquals(true, telemetry.collection)
            val (name, params) = telemetry.events.single()
            assertEquals("pc_config_fetch", name)
            assertEquals("success", params.getString("result"))
            assertEquals(1L, params.getLong("config_version"))
        }

    @Test
    fun launchRemovesLeftoverPhotoFiles() =
        runBlocking {
            val c = container()
            val leftover = File(c.cleanup.sendDir, "send-old.jpg").apply { parentFile!!.mkdirs() }
            leftover.writeText("x")
            Startup(c).run()
            assertFalse(leftover.exists())
        }
}
