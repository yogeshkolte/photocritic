package com.yk.photocritic.core.config

import com.yk.photocritic.core.RepoRoot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Remote Config values from `shared/remote-config/`, as the loader passes them: parameter to JSON string. */
object ConfigFixtures {
    val APP_VERSION = AppVersion(0, 1, 0)

    fun template(): JsonObject = Json.parseToJsonElement(RepoRoot.shared("remote-config/template.json")).jsonObject

    fun sample(path: String): JsonObject = Json.parseToJsonElement(RepoRoot.shared("remote-config/samples/$path")).jsonObject

    fun raw(config: JsonObject): Map<String, String> = config.mapValues { it.value.toString() }

    /** The template with each path (dot-separated, `models[0]` style indexes) replaced by its value. */
    fun templateWith(vararg changes: Pair<String, JsonElement>): Map<String, String> =
        raw(
            changes
                .fold<Pair<String, JsonElement>, JsonElement>(
                    template(),
                ) { json, (path, value) -> replace(json, path.split('.'), value) }
                .jsonObject,
        )

    fun templateWith(
        path: String,
        value: JsonElement,
    ): Map<String, String> = templateWith(path to value)

    fun snapshot(config: Map<String, String> = raw(template())): Snapshot =
        (ConfigValidator.validate(config, APP_VERSION) as ConfigResult.Valid).snapshot

    private fun replace(
        element: JsonElement,
        path: List<String>,
        value: JsonElement,
    ): JsonElement {
        if (path.isEmpty()) return value
        val head = path.first()
        val index = Regex("""(.+)\[(\d+)]""").matchEntire(head)
        val obj = element.jsonObject
        return if (index == null) {
            JsonObject(obj + (head to replace(obj.getValue(head), path.drop(1), value)))
        } else {
            val (_, name, i) = index.groupValues
            val list = obj.getValue(name) as JsonArray
            val updated = list.toMutableList().also { it[i.toInt()] = replace(it[i.toInt()], path.drop(1), value) }
            JsonObject(obj + (name to JsonArray(updated)))
        }
    }
}
