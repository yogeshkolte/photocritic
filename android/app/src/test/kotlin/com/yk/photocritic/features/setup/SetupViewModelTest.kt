package com.yk.photocritic.features.setup

import com.yk.photocritic.FakeKeyStore
import com.yk.photocritic.FakeTelemetryBackend
import com.yk.photocritic.TestConfig
import com.yk.photocritic.core.providers.ProviderId
import com.yk.photocritic.core.providers.fake.FakeProvider
import com.yk.photocritic.testContainer
import com.yk.photocritic.testSnapshot
import com.yk.photocritic.waitFor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Setup (file 38, CUJ 1, G10, G18). */
@RunWith(RobolectricTestRunner::class)
class SetupViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val keys = FakeKeyStore()
    private val telemetry = FakeTelemetryBackend()
    private val c by lazy { testContainer(tmp.root, TestConfig.source(), keys, telemetry, FakeProvider(ProviderId.GEMINI)) }

    private fun vm(firstRun: Boolean = true) =
        SetupViewModel(c, testSnapshot(), firstRun).also { vm ->
            waitFor("provider") {
                vm.ui.value.provider !=
                    null
            }
        }

    @Test
    fun onlyProvidersWithAClientAreListedAndNoModelIsPickedForTheUser() {
        val vm = vm()
        assertEquals(
            listOf(ProviderId.GEMINI),
            vm.ui.value.providers
                .map { it.id },
        )
        assertEquals(
            listOf("gemini-4-hydrogen", "gemini-4-argon"),
            vm.ui.value.models
                .map { it.model.id },
        )
        assertNull(vm.ui.value.modelId)
        assertFalse(vm.canSave("AQ.key"))
        vm.selectModel("gemini-4-hydrogen")
        assertTrue(vm.canSave("AQ.key"))
        assertFalse("no key typed and none saved", vm.canSave(" "))
    }

    @Test
    fun aGoodKeyIsCheckedThenStoredWithTheChoiceAndTheSharingSwitch() =
        runBlocking {
            val vm = vm()
            vm.selectModel("gemini-4-argon")
            vm.setShareUsage(true)
            vm.save("  AQ.good-key  ")
            waitFor("saved") { vm.ui.value.status == SetupStatus.Saved }
            assertEquals("AQ.good-key", keys.keys[ProviderId.GEMINI])
            val settings = c.settings.read()
            assertEquals(ProviderId.GEMINI, settings.providerId)
            assertEquals("gemini-4-argon", settings.modelFor(ProviderId.GEMINI))
            assertTrue(settings.shareUsage)
            assertTrue(settings.onboardingCompletedAt != null)
            assertEquals(true, telemetry.collection)
        }

    @Test
    fun aRejectedKeyShowsTheErrorAndStoresNothing() =
        runBlocking {
            val vm = vm()
            vm.selectModel("gemini-4-hydrogen")
            vm.save(FakeProvider.REJECTED_KEY)
            waitFor("failed") { vm.ui.value.status is SetupStatus.Failed }
            assertEquals(401, (vm.ui.value.status as SetupStatus.Failed).error.httpStatus)
            assertTrue(keys.keys.isEmpty())
            assertNull(c.settings.read().providerId)
            vm.dismissError()
            assertEquals(SetupStatus.Idle, vm.ui.value.status)
        }

    @Test
    fun editModeKeepsTheSavedKeyAndReportsAModelChange() =
        runBlocking {
            keys.keys[ProviderId.GEMINI] = "AQ.saved"
            c.settings.select(ProviderId.GEMINI, "gemini-4-hydrogen")
            val vm = vm(firstRun = false)
            waitFor("saved model") { vm.ui.value.modelId == "gemini-4-hydrogen" }
            assertTrue(vm.ui.value.hasSavedKey)
            assertTrue(vm.canSave(""))
            assertFalse(vm.changesSelection())
            vm.selectModel("gemini-4-argon")
            assertTrue(vm.changesSelection())
            vm.save("")
            waitFor("saved") { vm.ui.value.status == SetupStatus.Saved }
            assertEquals("AQ.saved", keys.keys[ProviderId.GEMINI])
            assertEquals("gemini-4-argon", c.settings.read().modelFor(ProviderId.GEMINI))
            assertNull("edit mode does not touch onboarding", c.settings.read().onboardingCompletedAt)
            assertNull("no telemetry change outside first run", telemetry.collection)
        }
}
