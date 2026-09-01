// Payload parsing for the default WebView bridge.
//
// Customize here if GTM posts a different shape than { action, userID, name, metadata }.
// Metadata values must be strings for Sideband.track; scalars are stringified and nested values are dropped.

package ai.sideband.sdk.webview

import org.json.JSONArray
import org.json.JSONObject

internal sealed class WebViewBridgeAction {
    data class TagUser(val userID: String) : WebViewBridgeAction()
    data object UntagUser : WebViewBridgeAction()
    data class Track(val name: String, val metadata: Map<String, String>) : WebViewBridgeAction()
}

internal object WebViewBridgeMessage {
    fun parse(body: Any?): WebViewBridgeAction? {
        val dictionary = dictionary(from = body) ?: return null
        val action = stringValue(dictionary["action"]) ?: return null

        return when (action) {
            "tagUser" -> {
                val userID = stringValue(dictionary["userID"]) ?: return null
                WebViewBridgeAction.TagUser(userID)
            }
            "untagUser" -> WebViewBridgeAction.UntagUser
            "track" -> {
                val name = stringValue(dictionary["name"]) ?: return null
                if (name.trim().isEmpty()) return null
                WebViewBridgeAction.Track(name = name, metadata = coercedMetadata(dictionary["metadata"]))
            }
            else -> null
        }
    }

    private fun dictionary(from: Any?): Map<String, Any?>? {
        return when (from) {
            is Map<*, *> -> from.toStringKeyedMap()
            is String -> from.toJsonDictionary()
            is JSONObject -> from.asBridgeDictionary()
            else -> null
        }
    }

    private fun coercedMetadata(value: Any?): Map<String, String> {
        val dictionary = dictionary(from = value ?: emptyMap<String, Any?>()) ?: return emptyMap()
        val metadata = linkedMapOf<String, String>()
        for ((key, rawValue) in dictionary) {
            val string = rawValue?.let(::scalarString) ?: continue
            metadata[key] = string
        }
        return metadata
    }

    private fun stringValue(value: Any?): String? = value as? String

    private fun scalarString(value: Any): String? {
        if (value == JSONObject.NULL) {
            return null
        }
        return when (value) {
            is String -> value
            is Boolean -> if (value) "true" else "false"
            is Double, is Float -> value.toString()
            is Number -> {
                val asDouble = value.toDouble()
                if (asDouble != kotlin.math.floor(asDouble)) {
                    asDouble.toString()
                } else {
                    value.toLong().toString()
                }
            }
            else -> null
        }
    }
}

private fun Map<*, *>.toStringKeyedMap(): Map<String, Any?> {
    val result = linkedMapOf<String, Any?>()
    for ((key, value) in this) {
        val stringKey = key as? String ?: continue
        result[stringKey] = value
    }
    return result
}

private fun String.toJsonDictionary(): Map<String, Any?>? {
    return try {
        JSONObject(this).asBridgeDictionary()
    } catch (_: Exception) {
        null
    }
}

private fun JSONObject.asBridgeDictionary(): Map<String, Any?> {
    val result = linkedMapOf<String, Any?>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        result[key] = jsonValue(opt(key))
    }
    return result
}

private fun jsonValue(value: Any?): Any? {
    return when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.asBridgeDictionary()
        is JSONArray -> (0 until value.length()).map { jsonValue(value.opt(it)) }
        else -> value
    }
}
