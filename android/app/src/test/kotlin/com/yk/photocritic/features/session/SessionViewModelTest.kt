package com.yk.photocritic.features.session

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.yk.photocritic.FakeKeyStore
import com.yk.photocritic.FakeTelemetryBackend
import com.yk.photocritic.TestConfig
import com.yk.photocritic.core.providers.PcError
import com.yk.photocritic.core.providers.PcPhase
import com.yk.photocritic.core.providers.PromptKind
import com.yk.photocritic.core.providers.ProviderId
import com.yk.photocritic.core.providers.fake.FakeAnswer
import com.yk.photocritic.core.providers.fake.FakeProvider
import com.yk.photocritic.core.session.MessageStatus
import com.yk.photocritic.core.session.Phase
import com.yk.photocritic.platform.storage.ConsentStore
import com.yk.photocritic.testContainer
import com.yk.photocritic.testSnapshot
import com.yk.photocritic.waitFor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** One conversation (file 37, §5, CUJs 1–5) with the fake provider. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val keys = FakeKeyStore()
    private val telemetry = FakeTelemetryBackend()
    private val provider = FakeProvider(ProviderId.GEMINI)
    private val c by lazy { testContainer(tmp.root, TestConfig.source(), keys, telemetry, provider) }

    private fun photo(): Uri {
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val file = tmp.newFile("photo.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file)
    }

    private fun ready(): SessionViewModel {
        runBlocking { c.settings.select(ProviderId.GEMINI, "gemini-4-hydrogen") }
        keys.keys[ProviderId.GEMINI] = "AQ.key"
        val vm = SessionViewModel(c, testSnapshot(), displayMaxPx = 512)
        vm.photoChosen(photo(), null)
        waitFor("ready") { vm.session.value.phase == Phase.READY }
        return vm
    }

    private fun sendCopies() = File(tmp.root, "cache/send").listFiles().orEmpty().toList()

    @Test
    fun aPhotoIsDecodedForTheScreenAndASendCopyIsMade() {
        val vm = ready()
        assertNotNull(vm.photo.value)
        assertEquals(1, sendCopies().size)
    }

    @Test
    fun theFirstSendAsksForConsentThenAnswersAndLaterSendsDoNot() =
        runBlocking {
            val vm = ready()
            vm.ask("Identify", PromptKind.IDENTIFY)
            waitFor("consent") { vm.session.value.phase == Phase.AWAITING_CONSENT }
            assertEquals("Google Gemini", vm.consent()?.providerName)
            vm.agreeConsent()
            waitFor("answer") {
                vm.session.value.phase == Phase.READY &&
                    vm.session.value.messages
                        .isNotEmpty()
            }
            val message =
                vm.session.value.messages
                    .single()
            assertEquals("You asked: Identify", message.answer)
            assertEquals(MessageStatus.DONE, message.status)
            val record = c.consent.current(ProviderId.GEMINI)!!
            assertEquals(ConsentStore.GRANTED, record.decision)
            assertEquals(1, record.consentTextVersion)
            assertEquals(PromptKind.IDENTIFY, provider.requests.single().promptKind)

            vm.ask("And the colour?", PromptKind.FREE)
            waitFor("second answer") { vm.session.value.messages.size == 2 && vm.session.value.phase == Phase.READY }
            assertEquals(
                1,
                provider.requests
                    .last()
                    .history.size,
            )
            assertTrue("sharing is off, so nothing is logged", telemetry.events.isEmpty())
        }

    @Test
    fun notNowRecordsNothingAndSendsNothing() =
        runBlocking {
            val vm = ready()
            vm.ask("Q", PromptKind.FREE)
            waitFor("consent") { vm.session.value.phase == Phase.AWAITING_CONSENT }
            vm.declineConsent()
            assertEquals(Phase.READY, vm.session.value.phase)
            assertTrue(c.consent.current(ProviderId.GEMINI) == null)
            assertTrue(provider.requests.isEmpty())
        }

    @Test
    fun stopKeepsThePartialAnswerAndAFailureShowsItsError() =
        runBlocking {
            val vm = ready()
            c.consent.record(ProviderId.GEMINI, 1, granted = true)
            provider.enqueue(FakeAnswer.Hang("part"))
            vm.ask("Long", PromptKind.FREE)
            waitFor("streaming") { vm.session.value.phase == Phase.STREAMING }
            vm.stop()
            assertEquals(
                MessageStatus.STOPPED,
                vm.session.value.messages
                    .last()
                    .status,
            )
            assertEquals(
                "part",
                vm.session.value.messages
                    .last()
                    .answer,
            )

            val error = PcError(PcPhase.BEFORE_RESPONSE, 429, "RESOURCE_EXHAUSTED", null, "quota")
            provider.enqueue(FakeAnswer.Fail(error))
            vm.ask("Again", PromptKind.FREE)
            waitFor("error") { vm.session.value.phase == Phase.ERROR }
            assertEquals(
                error,
                vm.session.value.messages
                    .last()
                    .error,
            )
            vm.dismiss()
            assertEquals(Phase.READY, vm.session.value.phase)
            assertTrue(
                vm.session.value.history
                    .isEmpty(),
            )
        }

    @Test
    fun newPhotoEndsTheConversationAndDeletesTheSendCopy() {
        val vm = ready()
        assertEquals(1, sendCopies().size)
        vm.newPhoto()
        assertEquals(Phase.NO_PHOTO, vm.session.value.phase)
        assertTrue(sendCopies().isEmpty())
        assertTrue(vm.photo.value == null)
    }

    @Test
    fun aFileThatIsNotAnImageGivesTheImageError() {
        val c = testContainer(tmp.root, TestConfig.source(), keys, telemetry, provider, sdkInt = 27)
        runBlocking { c.settings.select(ProviderId.GEMINI, "gemini-4-hydrogen") }
        val vm = SessionViewModel(c, testSnapshot(), displayMaxPx = 512)
        val text = tmp.newFile("notes.txt").apply { writeText("not a photo") }
        vm.photoChosen(Uri.fromFile(text), null)
        waitFor("image error") { vm.session.value.phase == Phase.IMAGE_ERROR }
        vm.dismiss()
        assertEquals(Phase.NO_PHOTO, vm.session.value.phase)
        assertFalse(vm.session.value.hasPhoto)
    }
}
