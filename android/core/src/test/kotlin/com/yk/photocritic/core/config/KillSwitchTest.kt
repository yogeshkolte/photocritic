package com.yk.photocritic.core.config

import com.yk.photocritic.core.config.ConfigFixtures.snapshot
import com.yk.photocritic.core.config.ConfigFixtures.templateWith
import com.yk.photocritic.core.providers.ProviderId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The file 26 display rules and the cold-start route. */
class KillSwitchTest {
    private val saved = SavedChoice(ProviderId.GEMINI, "gemini-4-hydrogen", hasKey = true)

    private fun with(
        path: String,
        value: JsonElement,
    ) = snapshot(templateWith(path, value))

    private fun list(vararg items: String) = JsonArray(items.map(::JsonPrimitive))

    @Test
    fun `a saved provider, model and key go home`() {
        assertEquals(StartRoute.Home, KillSwitch.evaluate(snapshot(), saved))
    }

    @Test
    fun `nothing saved or no key opens setup without a message`() {
        assertEquals(StartRoute.Setup(null), KillSwitch.evaluate(snapshot(), SavedChoice(null, null, false)))
        assertEquals(StartRoute.Setup(null), KillSwitch.evaluate(snapshot(), saved.copy(hasKey = false)))
        assertEquals(StartRoute.Setup(null), KillSwitch.evaluate(snapshot(), saved.copy(modelId = null)))
    }

    @Test
    fun `app disabled comes first and carries the message`() {
        val s = snapshot(templateWith("kill_switch.appDisabled", JsonPrimitive(true)))
        assertEquals(StartRoute.AppDisabled(""), KillSwitch.evaluate(s, saved))
    }

    @Test
    fun `an app below the minimum version must update`() {
        val s = with("kill_switch.minSupportedAppVersion", JsonPrimitive("0.2.0"))
        assertEquals(StartRoute.UpdateRequired(""), KillSwitch.evaluate(s, saved))
    }

    @Test
    fun `a hidden saved model opens setup with the default or configured message`() {
        val disabled = with("kill_switch.disabledModels", list("gemini-4-hydrogen"))
        assertEquals(StartRoute.Setup(KillSwitch.DEFAULT_MESSAGE), KillSwitch.evaluate(disabled, saved))
        val providerOff = with("kill_switch.disabledProviders", list("gemini"))
        assertEquals(StartRoute.Setup(KillSwitch.DEFAULT_MESSAGE), KillSwitch.evaluate(providerOff, saved))
        assertEquals(StartRoute.Setup(KillSwitch.DEFAULT_MESSAGE), KillSwitch.evaluate(snapshot(), saved.copy(modelId = "gemini-1")))
        val withMessage = with("kill_switch.message", JsonPrimitive("Gemini is paused."))
        assertEquals(StartRoute.Setup("Gemini is paused."), KillSwitch.evaluate(withMessage, saved.copy(modelId = "gone")))
    }

    @Test
    fun `model visibility rules`() {
        val base = snapshot()
        val hydrogen = base.model(ProviderId.GEMINI, "gemini-4-hydrogen")!!
        assertTrue(base.isModelVisible(hydrogen))
        assertFalse(with("models[0].status", JsonPrimitive("retired")).let { it.isModelVisible(it.values.models[0]) })
        assertFalse(with("models[0].minAppVersion", JsonPrimitive("0.1.1")).let { it.isModelVisible(it.values.models[0]) })
        assertFalse(with("providers.gemini.enabled", JsonPrimitive(false)).let { it.isModelVisible(it.values.models[0]) })
        assertEquals(
            listOf(ProviderId.OPENAI, ProviderId.ANTHROPIC),
            with("providers.gemini.enabled", JsonPrimitive(false)).enabledProviders,
        )
    }

    @Test
    fun `model tags`() {
        val preview = with("models[0].status", JsonPrimitive("preview"))
        assertEquals(ModelTag.Preview, preview.modelChoices(ProviderId.GEMINI)[0].tag)
        val deprecated =
            snapshot(
                templateWith(
                    "models[0].status" to JsonPrimitive("deprecated"),
                    "models[0].replacementId" to JsonPrimitive("gemini-4-argon"),
                ),
            )
        assertEquals(ModelTag.EndingSoon("Gemini 4 Argon"), deprecated.modelChoices(ProviderId.GEMINI)[0].tag)
        assertEquals(ModelTag.None, deprecated.modelChoices(ProviderId.GEMINI)[1].tag)
        assertEquals(emptyList<ModelChoice>(), snapshot().modelChoices(ProviderId.OPENAI))
    }
}
