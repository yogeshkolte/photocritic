package com.yk.photocritic.core.providers.fake

import com.yk.photocritic.core.providers.KeyCheckResult
import com.yk.photocritic.core.providers.ModelSettings
import com.yk.photocritic.core.providers.PcError
import com.yk.photocritic.core.providers.PcPhase
import com.yk.photocritic.core.providers.PromptKind
import com.yk.photocritic.core.providers.ProviderId
import com.yk.photocritic.core.providers.ProviderRegistry
import com.yk.photocritic.core.providers.SendCopy
import com.yk.photocritic.core.providers.Streaming
import com.yk.photocritic.core.providers.TurnEvent
import com.yk.photocritic.core.providers.TurnRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakeProviderTest {
    private val model = ModelSettings("fake-model", 100, 1000)
    private val request = TurnRequest(model, emptyList(), "What is it?", SendCopy(byteArrayOf(1), 1, 1, "high"), PromptKind.FREE)

    @Test
    fun `whole answers, scripted failures and the echo default`() =
        runBlocking {
            val fake = FakeProvider()
            assertEquals(Streaming.WHOLE, fake.capabilities.streaming)
            assertTrue(fake.capabilities.dataLeavesDevice)
            assertEquals(
                listOf(
                    TurnEvent.Text("You asked: What is it?"),
                    TurnEvent.Done(com.yk.photocritic.core.providers.FinishReason.STOP, "fake-model", "fake-1"),
                ),
                fake.send(request, "k").toList(),
            )
            val error = PcError(PcPhase.MID_STREAM, null, "x", null, "d")
            fake.enqueue(FakeAnswer.Fail(error, "part"))
            assertEquals(listOf(TurnEvent.Text("part"), TurnEvent.Error(error)), fake.send(request, "k").toList())
            assertEquals(2, fake.requests.size)
        }

    @Test
    fun `the rejected key fails the check and others pass`() =
        runBlocking {
            val fake = FakeProvider()
            assertSame(KeyCheckResult.Ok, fake.checkKey("good", model))
            assertEquals(401, (fake.checkKey(FakeProvider.REJECTED_KEY, model) as KeyCheckResult.Failed).error.httpStatus)
        }

    @Test
    fun `the registry finds providers by id`() {
        val fake = FakeProvider(ProviderId.ANTHROPIC)
        val registry = ProviderRegistry(listOf(fake))
        assertSame(fake, registry.get(ProviderId.ANTHROPIC))
        assertNull(registry.get(ProviderId.GEMINI))
        assertEquals(setOf(ProviderId.ANTHROPIC), registry.ids)
    }
}
