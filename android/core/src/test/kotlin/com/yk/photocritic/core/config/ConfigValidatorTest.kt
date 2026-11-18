package com.yk.photocritic.core.config

import com.yk.photocritic.core.RepoRoot
import com.yk.photocritic.core.config.ConfigFixtures.APP_VERSION
import com.yk.photocritic.core.config.ConfigFixtures.raw
import com.yk.photocritic.core.config.ConfigFixtures.template
import com.yk.photocritic.core.config.ConfigFixtures.templateWith
import com.yk.photocritic.core.providers.ProviderId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class ConfigValidatorTest {
    private fun unavailable(config: Map<String, String>): ConfigResult.Unavailable =
        assertInstanceOf(ConfigResult.Unavailable::class.java, ConfigValidator.validate(config, APP_VERSION))

    private fun assertInvalid(
        path: String,
        value: JsonElement,
        expectedPath: String = path,
    ) {
        assertEquals(expectedPath, unavailable(templateWith(path, value)).path, "replacing $path with $value")
    }

    @Test
    fun `the template is valid and parsed`() {
        val values = ConfigFixtures.snapshot().values
        assertEquals(1, values.meta.configVersion)
        assertEquals(setOf(ProviderId.OPENAI, ProviderId.ANTHROPIC, ProviderId.GEMINI), values.providers.keys)
        assertEquals("gemini-4-hydrogen", values.models.first().id)
        assertEquals(ModelStatus.ACTIVE, values.models.first().status)
        assertEquals(AppVersion(0, 1, 0), values.killSwitch.minSupportedAppVersion)
        assertEquals(
            3,
            values.consentTexts
                .getValue(ProviderId.OPENAI)
                .summary.size,
        )
        assertEquals("", values.privacyPolicy.url)
    }

    @TestFactory
    fun `shared samples`(): List<DynamicTest> =
        listOf("valid", "invalid")
            .flatMap { kind ->
                RepoRoot.sharedJsonFiles("remote-config/samples/$kind").map { name ->
                    DynamicTest.dynamicTest("$kind/$name") {
                        val result = ConfigValidator.validate(raw(ConfigFixtures.sample("$kind/$name")), APP_VERSION)
                        val expected = if (kind == "valid") ConfigResult.Valid::class.java else ConfigResult.Unavailable::class.java
                        assertInstanceOf(expected, result, result.toString())
                    }
                }
            }.also { assertTrue(it.size >= 7, "samples missing from shared/remote-config/samples") }

    @Test
    fun `a missing or unknown parameter or non-JSON value is invalid`() {
        assertEquals("models", unavailable(raw(template()) - "models").path)
        assertEquals("extra", unavailable(raw(template()) + ("extra" to "{}")).path)
        assertEquals("providers", unavailable(raw(template()) + ("providers" to "{not json")).path)
    }

    @Test
    fun `wrong shapes are invalid`() {
        assertInvalid("config_meta", JsonPrimitive(1))
        assertInvalid("config_meta.configVersion", JsonPrimitive(0))
        assertInvalid("config_meta.configVersion", JsonPrimitive("1"))
        assertInvalid("config_meta.configVersion", JsonPrimitive(1.5))
        assertInvalid("config_meta.publishedAt", JsonPrimitive("2026-11-17"))
        assertInvalid("kill_switch.appDisabled", JsonPrimitive("false"))
        assertInvalid("kill_switch.message", JsonPrimitive("x".repeat(501)))
        assertInvalid("kill_switch.minSupportedAppVersion", JsonPrimitive("1.0"))
        assertInvalid("kill_switch.updateUrl", JsonPrimitive("http://example.com"))
        assertInvalid("kill_switch.disabledProviders", JsonArray(listOf(JsonPrimitive("mistral"))), "kill_switch.disabledProviders[0]")
        assertInvalid("kill_switch.disabledModels", JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("a"))))
        assertInvalid("kill_switch.disabledModels", JsonArray(listOf(JsonPrimitive(""))), "kill_switch.disabledModels[0]")
        assertInvalid("kill_switch.bannerMessage", JsonPrimitive("x".repeat(301)))
        assertInvalid("providers.gemini.displayName", JsonPrimitive(""))
        assertInvalid("providers.gemini.keyHelpUrl", JsonPrimitive("https://has space"))
        assertInvalid("models", buildJsonObject {}, "models")
        assertInvalid("models[0].provider", JsonPrimitive("mistral"))
        assertInvalid("models[0].id", JsonPrimitive("Upper"))
        assertInvalid("models[0].label", JsonPrimitive("Cheap"))
        assertInvalid("models[0].maxLongEdgePx", JsonPrimitive(100))
        assertInvalid("models[0].maxBytes", JsonPrimitive(30_000_000))
        assertInvalid("models[0].imageDetail", JsonPrimitive("ultra"))
        assertInvalid("models[0].status", JsonPrimitive("gone"))
        assertInvalid("models[0].minAppVersion", JsonPrimitive(""))
        assertInvalid("consent_texts.gemini.effectiveDate", JsonPrimitive("17 Nov 2026"))
        assertInvalid("consent_texts.gemini.summary", buildJsonArray {}, "consent_texts.gemini.summary")
        assertInvalid("consent_texts.gemini.summary", JsonArray(List(9) { JsonPrimitive("x") }))
        assertInvalid("privacy_policy.url", JsonPrimitive("ftp://x"))
    }

    @Test
    fun `missing and unknown fields are invalid`() {
        assertInvalid("privacy_policy", buildJsonObject { put("version", 1) }, "privacy_policy.url")
        assertInvalid(
            "privacy_policy",
            buildJsonObject {
                put("version", 1)
                put("url", "")
                put("extra", true)
            },
            "privacy_policy.extra",
        )
    }

    @Test
    fun `cross-field rules`() {
        assertInvalid("providers.openai.baseUrl", JsonPrimitive("https://api.openai.example"))
        assertInvalid("providers.openai.consentTextVersion", JsonPrimitive(2))
        assertInvalid("models[1].id", JsonPrimitive("gemini-4-hydrogen"), "models.gemini-4-hydrogen")
        assertInvalid("models[0].replacementId", JsonPrimitive("gemini-9"), "models.gemini-4-hydrogen.replacementId")
        assertInvalid("models[0].status", JsonPrimitive("deprecated"), "models.gemini-4-hydrogen")
    }

    @Test
    fun `optional empty urls are allowed`() {
        val update =
            templateWith("kill_switch.updateUrl", JsonPrimitive("https://play.google.com/store/apps/details?id=com.yk.photocritic"))
        assertInstanceOf(ConfigResult.Valid::class.java, ConfigValidator.validate(update, APP_VERSION))
    }
}
