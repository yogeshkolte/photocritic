package com.yk.photocritic.core.config

import com.yk.photocritic.core.net.Allowlist
import com.yk.photocritic.core.providers.ProviderId
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

sealed interface ConfigResult {
    data class Valid(
        val snapshot: Snapshot,
    ) : ConfigResult

    /** Shown as the config-unavailable screen (Q9). [path] names the first failing value. */
    data class Unavailable(
        val path: String,
        val reason: String,
    ) : ConfigResult
}

/**
 * Checks the Remote Config values against `shared/remote-config/schema.json`
 * and the cross-field rules of `tools/validate-config.ts` (design §8, file
 * 26), by hand: there is no schema library at run time. Any invalid value
 * makes the whole config unavailable; there is no per-value fallback (Q9).
 */
object ConfigValidator {
    val PARAMETERS = setOf("config_meta", "kill_switch", "providers", "models", "consent_texts", "privacy_policy")

    private class Invalid(
        val path: String,
        val reason: String,
        cause: Throwable? = null,
    ) : Exception("$path: $reason", cause)

    private fun failIf(
        condition: Boolean,
        path: String,
        reason: String,
    ) {
        if (condition) throw Invalid(path, reason)
    }

    /** [raw] maps each Remote Config parameter to its JSON string value. */
    fun validate(
        raw: Map<String, String>,
        appVersion: AppVersion,
    ): ConfigResult =
        try {
            ConfigResult.Valid(Snapshot(read(raw), appVersion))
        } catch (e: Invalid) {
            ConfigResult.Unavailable(e.path, e.reason)
        }

    private fun read(raw: Map<String, String>): RemoteConfigValues {
        (raw.keys - PARAMETERS).sorted().firstOrNull()?.let { throw Invalid(it, "unknown parameter") }
        (PARAMETERS - raw.keys).sorted().firstOrNull()?.let { throw Invalid(it, "missing") }

        fun parsed(name: String): JsonElement =
            try {
                Json.parseToJsonElement(raw.getValue(name))
            } catch (e: SerializationException) {
                throw Invalid(name, "not JSON", e)
            }
        val providers = providers(Node("providers", parsed("providers")).obj())
        val consentTexts = consentTexts(Node("consent_texts", parsed("consent_texts")).obj())
        val models = models(Node("models", parsed("models")))
        crossCheck(providers, consentTexts, models)
        return RemoteConfigValues(
            meta = meta(Node("config_meta", parsed("config_meta")).obj()),
            killSwitch = killSwitch(Node("kill_switch", parsed("kill_switch")).obj()),
            providers = providers,
            models = models,
            consentTexts = consentTexts,
            privacyPolicy = privacyPolicy(Node("privacy_policy", parsed("privacy_policy")).obj()),
        )
    }

    private fun meta(o: Obj): ConfigMeta {
        o.keys("configVersion", "publishedAt")
        return ConfigMeta(o["configVersion"].int(min = 1), o["publishedAt"].string(pattern = DATE_TIME))
    }

    private fun killSwitch(o: Obj): KillSwitchConfig {
        o.keys(
            "appDisabled",
            "message",
            "minSupportedAppVersion",
            "updateUrl",
            "disabledProviders",
            "disabledModels",
            "bannerMessage",
            "reportsDisabled",
        )
        return KillSwitchConfig(
            appDisabled = o["appDisabled"].bool(),
            message = o["message"].string(maxLength = 500),
            minSupportedAppVersion = o["minSupportedAppVersion"].semver(),
            updateUrl = o["updateUrl"].httpsUrlOrEmpty(),
            disabledProviders =
                o["disabledProviders"]
                    .uniqueStrings()
                    .map {
                        Node(
                            it.first,
                            JsonPrimitive(it.second),
                        ).providerId()
                    }.toSet(),
            disabledModels = o["disabledModels"].uniqueStrings(minLength = 1, maxLength = 100).map { it.second }.toSet(),
            bannerMessage = o["bannerMessage"].string(maxLength = 300),
            reportsDisabled = o["reportsDisabled"].bool(),
        )
    }

    private fun providers(o: Obj): Map<ProviderId, ProviderConfig> {
        o.keys(PROVIDER_KEYS)
        return ProviderId.entries.associateWith { id ->
            val p = o[id.wire].obj()
            p.keys("enabled", "displayName", "baseUrl", "keyHelpUrl", "consentTextVersion")
            ProviderConfig(
                id = id,
                enabled = p["enabled"].bool(),
                displayName = p["displayName"].nonEmpty(),
                baseUrl = p["baseUrl"].httpsUrl(),
                keyHelpUrl = p["keyHelpUrl"].httpsUrl(),
                consentTextVersion = p["consentTextVersion"].int(min = 1),
            )
        }
    }

    private fun models(node: Node): List<ModelConfig> =
        node.array().map { item ->
            val m = item.obj()
            m.keys(
                "provider",
                "id",
                "displayName",
                "label",
                "maxLongEdgePx",
                "maxBytes",
                "imageDetail",
                "maxOutputTokens",
                "contextTokens",
                "status",
                "replacementId",
                "minAppVersion",
            )
            ModelConfig(
                provider = m["provider"].providerId(),
                id = m["id"].string(pattern = MODEL_ID),
                displayName = m["displayName"].nonEmpty(),
                label = m["label"].oneOf(LABELS),
                maxLongEdgePx = m["maxLongEdgePx"].int(min = 256, max = 8192),
                maxBytes = m["maxBytes"].int(min = 10_000, max = 20_000_000),
                imageDetail = m["imageDetail"].oneOf(IMAGE_DETAILS),
                maxOutputTokens = m["maxOutputTokens"].int(min = 1, max = 128_000),
                contextTokens = m["contextTokens"].int(min = 1000, max = 10_000_000),
                status = m["status"].oneOf(ModelStatus.entries.map { it.wire }).let { s -> ModelStatus.entries.first { it.wire == s } },
                replacementId = m["replacementId"].string(maxLength = 100),
                minAppVersion = m["minAppVersion"].semver(),
            )
        }

    private fun consentTexts(o: Obj): Map<ProviderId, ConsentText> {
        o.keys(PROVIDER_KEYS)
        return ProviderId.entries.associateWith { id ->
            val c = o[id.wire].obj()
            c.keys("version", "effectiveDate", "summary", "policyUrl")
            val summary = c["summary"].array()
            if (summary.size !in 1..MAX_SUMMARY_ITEMS) throw Invalid(c["summary"].path, "needs 1 to $MAX_SUMMARY_ITEMS items")
            ConsentText(
                version = c["version"].int(min = 1),
                effectiveDate = c["effectiveDate"].string(pattern = DATE),
                summary = summary.map { it.nonEmpty() },
                policyUrl = c["policyUrl"].httpsUrl(),
            )
        }
    }

    private fun privacyPolicy(o: Obj): PrivacyPolicyConfig {
        o.keys("version", "url")
        return PrivacyPolicyConfig(o["version"].int(min = 1), o["url"].httpsUrlOrEmpty())
    }

    private fun crossCheck(
        providers: Map<ProviderId, ProviderConfig>,
        consentTexts: Map<ProviderId, ConsentText>,
        models: List<ModelConfig>,
    ) {
        for ((id, provider) in providers) {
            failIf(provider.baseUrl != Allowlist.BASE_URLS[id.wire], "providers.${id.wire}.baseUrl", "not the allowlisted host")
            failIf(
                provider.consentTextVersion != consentTexts.getValue(id).version,
                "providers.${id.wire}.consentTextVersion",
                "differs from consent_texts.${id.wire}.version",
            )
        }
        val active = models.filter { it.status == ModelStatus.ACTIVE }.map { it.id }.toSet()
        val seen = mutableSetOf<String>()
        for (model in models) {
            failIf(!seen.add(model.id), "models.${model.id}", "duplicate id")
            failIf(
                model.replacementId.isNotEmpty() && model.replacementId !in active,
                "models.${model.id}.replacementId",
                "names no active model",
            )
            failIf(
                model.status == ModelStatus.DEPRECATED && model.replacementId.isEmpty(),
                "models.${model.id}",
                "deprecated without a replacementId",
            )
        }
    }

    /** A JSON value and its path, for the error message. One reader per schema type. */
    @Suppress("TooManyFunctions")
    private class Node(
        val path: String,
        val json: JsonElement?,
    ) {
        private fun fail(reason: String): Nothing = throw Invalid(path, reason)

        private fun primitive(): JsonPrimitive = json as? JsonPrimitive ?: fail("missing or not a value")

        fun obj(): Obj = Obj(path, json as? JsonObject ?: fail("not an object"))

        fun array(): List<Node> = (json as? JsonArray ?: fail("not an array")).mapIndexed { i, e -> Node("$path[$i]", e) }

        fun bool(): Boolean = primitive().takeUnless { it.isString }?.booleanOrNull ?: fail("not a boolean")

        fun int(
            min: Int,
            max: Int = Int.MAX_VALUE,
        ): Int {
            val p = primitive()
            val value = p.takeUnless { it.isString || '.' in it.content || 'e' in it.content.lowercase() }?.longOrNull
            return when {
                value == null -> fail("not an integer")
                value !in min..max -> fail("out of range $min..$max")
                else -> value.toInt()
            }
        }

        fun string(
            minLength: Int = 0,
            maxLength: Int = Int.MAX_VALUE,
            pattern: Regex? = null,
        ): String {
            val p = primitive()
            if (!p.isString) fail("not a string")
            val s = p.content
            if (s.length !in minLength..maxLength) fail("length outside $minLength..$maxLength")
            if (pattern != null && !pattern.matches(s)) fail("wrong format")
            return s
        }

        fun nonEmpty(): String = string(minLength = 1, maxLength = 500)

        fun httpsUrl(): String = string(maxLength = 500, pattern = HTTPS_URL)

        fun httpsUrlOrEmpty(): String = string(maxLength = 500).also { if (it.isNotEmpty()) httpsUrl() }

        fun semver(): AppVersion = AppVersion.parse(string(pattern = SEMVER)) ?: fail("not a version")

        fun oneOf(allowed: Collection<String>): String = string().also { if (it !in allowed) fail("not one of $allowed") }

        fun providerId(): ProviderId = oneOf(ProviderId.entries.map { it.wire }).let { s -> ProviderId.entries.first { it.wire == s } }

        /** String items with no repeats, as (path, value). */
        fun uniqueStrings(
            minLength: Int = 0,
            maxLength: Int = Int.MAX_VALUE,
        ): List<Pair<String, String>> {
            val items = array().map { it.path to it.string(minLength, maxLength) }
            if (items.map { it.second }.toSet().size != items.size) fail("repeated item")
            return items
        }
    }

    private class Obj(
        val path: String,
        val json: JsonObject,
    ) {
        operator fun get(name: String) = Node("$path.$name", json[name])

        fun keys(vararg names: String) = keys(names.asList())

        /** The object must have exactly these keys. */
        fun keys(names: Collection<String>) {
            (json.keys - names.toSet()).sorted().firstOrNull()?.let { throw Invalid("$path.$it", "unknown field") }
            names.firstOrNull { it !in json }?.let { throw Invalid("$path.$it", "missing") }
        }
    }

    private const val MAX_SUMMARY_ITEMS = 8
    private val PROVIDER_KEYS = ProviderId.entries.map { it.wire }
    private val LABELS = listOf("Fast", "Best", "")
    private val IMAGE_DETAILS =
        listOf("low", "high", "auto", "MEDIA_RESOLUTION_LOW", "MEDIA_RESOLUTION_MEDIUM", "MEDIA_RESOLUTION_HIGH", "none")
    private val HTTPS_URL = Regex("""https://\S+""")
    private val SEMVER = Regex("""\d+\.\d+\.\d+""")
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val DATE_TIME = Regex("""\d{4}-\d{2}-\d{2}[Tt ]\d{2}:\d{2}:\d{2}(\.\d+)?([Zz]|[+-]\d{2}:\d{2})""")
    private val MODEL_ID = Regex("""[a-z0-9][a-z0-9._-]{0,99}""")
}
