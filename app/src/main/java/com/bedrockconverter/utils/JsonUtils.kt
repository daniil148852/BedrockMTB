// app/src/main/java/com/bedrockconverter/utils/JsonUtils.kt
package com.bedrockconverter.utils

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Utility class for JSON operations
 */
object JsonUtils {

    /**
     * Convert a map to JSONObject
     */
    fun mapToJson(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        for ((key, value) in map) {
            json.put(key, convertValue(value))
        }
        return json
    }

    /**
     * Convert a list to JSONArray
     */
    fun listToJson(list: List<Any?>): JSONArray {
        val json = JSONArray()
        for (item in list) {
            json.put(convertValue(item))
        }
        return json
    }

    /**
     * Convert any value to JSON-compatible format
     */
    private fun convertValue(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                mapToJson(value as Map<String, Any?>)
            }
            is List<*> -> {
                @Suppress("UNCHECKED_CAST")
                listToJson(value as List<Any?>)
            }
            is Array<*> -> {
                listToJson(value.toList())
            }
            is IntArray -> listToJson(value.toList())
            is FloatArray -> listToJson(value.toList())
            is DoubleArray -> listToJson(value.toList())
            is BooleanArray -> listToJson(value.toList())
            else -> value
        }
    }

    /**
     * Convert JSONObject to map
     */
    fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = convertJsonValue(json.get(key))
        }
        return map
    }

    /**
     * Convert JSONArray to list
     */
    fun jsonToList(json: JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until json.length()) {
            list.add(convertJsonValue(json.get(i)))
        }
        return list
    }

    /**
     * Convert JSON value to Kotlin type
     */
    private fun convertJsonValue(value: Any?): Any? {
        return when (value) {
            JSONObject.NULL -> null
            is JSONObject -> jsonToMap(value)
            is JSONArray -> jsonToList(value)
            else -> value
        }
    }

    /**
     * Pretty print JSON with indentation
     */
    fun prettyPrint(json: JSONObject, indent: Int = 2): String {
        return json.toString(indent)
    }

    /**
     * Pretty print JSON array with indentation
     */
    fun prettyPrint(json: JSONArray, indent: Int = 2): String {
        return json.toString(indent)
    }

    /**
     * Parse JSON string to JSONObject safely
     */
    fun parseObject(jsonString: String): JSONObject? {
        return try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parse JSON string to JSONArray safely
     */
    fun parseArray(jsonString: String): JSONArray? {
        return try {
            JSONArray(jsonString)
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get string from JSONObject safely
     */
    fun getString(json: JSONObject, key: String, default: String = ""): String {
        return try {
            json.optString(key, default)
        } catch (e: Exception) {
            default
        }
    }

    /**
     * Get int from JSONObject safely
     */
    fun getInt(json: JSONObject, key: String, default: Int = 0): Int {
        return try {
            json.optInt(key, default)
        } catch (e: Exception) {
            default
        }
    }

    /**
     * Get float from JSONObject safely
     */
    fun getFloat(json: JSONObject, key: String, default: Float = 0f): Float {
        return try {
            json.optDouble(key, default.toDouble()).toFloat()
        } catch (e: Exception) {
            default
        }
    }

    /**
     * Get boolean from JSONObject safely
     */
    fun getBoolean(json: JSONObject, key: String, default: Boolean = false): Boolean {
        return try {
            json.optBoolean(key, default)
        } catch (e: Exception) {
            default
        }
    }

    /**
     * Get JSONObject from JSONObject safely
     */
    fun getObject(json: JSONObject, key: String): JSONObject? {
        return try {
            json.optJSONObject(key)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get JSONArray from JSONObject safely
     */
    fun getArray(json: JSONObject, key: String): JSONArray? {
        return try {
            json.optJSONArray(key)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get float array from JSONArray
     */
    fun getFloatArray(json: JSONArray): FloatArray {
        return FloatArray(json.length()) { i ->
            json.optDouble(i, 0.0).toFloat()
        }
    }

    /**
     * Get int array from JSONArray
     */
    fun getIntArray(json: JSONArray): IntArray {
        return IntArray(json.length()) { i ->
            json.optInt(i, 0)
        }
    }

    /**
     * Get string array from JSONArray
     */
    fun getStringArray(json: JSONArray): Array<String> {
        return Array(json.length()) { i ->
            json.optString(i, "")
        }
    }

    /**
     * Merge two JSONObjects
     */
    fun merge(base: JSONObject, override: JSONObject): JSONObject {
        val result = JSONObject(base.toString())
        val keys = override.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = override.get(key)

            if (value is JSONObject && result.has(key)) {
                val baseValue = result.optJSONObject(key)
                if (baseValue != null) {
                    result.put(key, merge(baseValue, value))
                    continue
                }
            }

            result.put(key, value)
        }
        return result
    }

    /**
     * Deep clone a JSONObject
     */
    fun clone(json: JSONObject): JSONObject {
        return JSONObject(json.toString())
    }

    /**
     * Deep clone a JSONArray
     */
    fun clone(json: JSONArray): JSONArray {
        return JSONArray(json.toString())
    }

    /**
     * Check if JSONObject has all required keys
     */
    fun hasKeys(json: JSONObject, vararg keys: String): Boolean {
        return keys.all { json.has(it) }
    }

    /**
     * Remove null values from JSONObject
     */
    fun removeNulls(json: JSONObject): JSONObject {
        val result = JSONObject()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)

            if (value != JSONObject.NULL) {
                when (value) {
                    is JSONObject -> result.put(key, removeNulls(value))
                    is JSONArray -> result.put(key, removeNulls(value))
                    else -> result.put(key, value)
                }
            }
        }
        return result
    }

    /**
     * Remove null values from JSONArray
     */
    fun removeNulls(json: JSONArray): JSONArray {
        val result = JSONArray()
        for (i in 0 until json.length()) {
            val value = json.get(i)

            if (value != JSONObject.NULL) {
                when (value) {
                    is JSONObject -> result.put(removeNulls(value))
                    is JSONArray -> result.put(removeNulls(value))
                    else -> result.put(value)
                }
            }
        }
        return result
    }

    /**
     * Escape string for JSON
     */
    fun escapeString(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
    }

    /**
     * Build a JSON string manually (for performance-critical code)
     */
    class JsonBuilder {
        private val sb = StringBuilder()
        private var isFirstElement = true

        fun beginObject(): JsonBuilder {
            sb.append("{")
            isFirstElement = true
            return this
        }

        fun endObject(): JsonBuilder {
            sb.append("}")
            isFirstElement = false
            return this
        }

        fun beginArray(): JsonBuilder {
            sb.append("[")
            isFirstElement = true
            return this
        }

        fun endArray(): JsonBuilder {
            sb.append("]")
            isFirstElement = false
            return this
        }

        fun key(key: String): JsonBuilder {
            if (!isFirstElement) {
                sb.append(",")
            }
            sb.append("\"").append(escapeString(key)).append("\":")
            isFirstElement = false
            return this
        }

        fun value(value: String): JsonBuilder {
            if (!isFirstElement && sb.lastOrNull() != ':') {
                sb.append(",")
            }
            sb.append("\"").append(escapeString(value)).append("\"")
            isFirstElement = false
            return this
        }

        fun value(value: Number): JsonBuilder {
            if (!isFirstElement && sb.lastOrNull() != ':') {
                sb.append(",")
            }
            sb.append(value)
            isFirstElement = false
            return this
        }

        fun value(value: Boolean): JsonBuilder {
            if (!isFirstElement && sb.lastOrNull() != ':') {
                sb.append(",")
            }
            sb.append(value)
            isFirstElement = false
            return this
        }

        fun nullValue(): JsonBuilder {
            if (!isFirstElement && sb.lastOrNull() != ':') {
                sb.append(",")
            }
            sb.append("null")
            isFirstElement = false
            return this
        }

        fun raw(json: String): JsonBuilder {
            if (!isFirstElement && sb.lastOrNull() != ':') {
                sb.append(",")
            }
            sb.append(json)
            isFirstElement = false
            return this
        }

        override fun toString(): String = sb.toString()
    }
}