package com.keyforge.chat.files.validate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Thin org.json-style accessor wrapper around kotlinx.serialization's JsonObject.
 *
 * core:files must run its unit tests on a plain JVM, where `org.json.JSONObject`
 * is only an android.jar stub that throws "Stub!". kotlinx.serialization IS on
 * the classpath, so we parse with it and expose the few accessor methods the
 * validators use (optString, optInt, optJSONObject, optJSONArray, has, keys,
 * getString). Returns null/0/empty for missing or wrong-typed values, matching
 * org.json's opt* semantics.
 */
internal class JsonAccess(val obj: JsonObject?) {

    fun has(key: String): Boolean = obj?.containsKey(key) == true

    fun keys(): Set<String> = obj?.keys ?: emptySet()

    fun optString(key: String): String {
        val p = obj?.get(key) as? JsonPrimitive ?: return ""
        return p.contentOrNull ?: ""
    }

    fun getString(key: String): String = optString(key)

    fun optInt(key: String, default: Int = 0): Int {
        val p = obj?.get(key) as? JsonPrimitive ?: return default
        return p.intOrNull ?: default
    }

    fun optJSONObject(key: String): JsonAccess {
        val inner = obj?.get(key) as? JsonObject
        return if (inner != null) JsonAccess(inner) else EMPTY
    }

    fun optJSONArray(key: String): JsonArray? = obj?.get(key) as? JsonArray

    companion object {
        val EMPTY = JsonAccess(null)
        private val json = Json { ignoreUnknownKeys = true; isLenient = false }

        fun parse(text: String): JsonAccess = try {
            JsonAccess(json.parseToJsonElement(text).jsonObject)
        } catch (e: Exception) {
            throw JsonParseException(e.message ?: "Invalid JSON", e)
        }
    }
}

internal class JsonParseException(message: String, cause: Throwable) : Exception(message, cause)

/** Iterate a JsonArray of strings (content_scripts[].js etc.). */
internal fun JsonArray.strings(): List<String> =
    mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
