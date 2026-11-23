package app.kpakam.core.schema

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Validates JSON against the contract's JSON Schemas in the `schemas` folder.
 *
 * It supports the keywords the contract uses: `$ref` (local), `type`, `enum`,
 * `const`, `not`, `oneOf`, `properties`, `required`, `additionalProperties: false`,
 * `items`, `minItems`, `maxItems`, `minLength`, `maxLength`, `pattern`,
 * `minimum`, `maximum`, and `exclusiveMinimum`. `format` is ignored, as JSON
 * Schema allows. Errors are short English sentences that a model can act on.
 */
public class JsonSchemaValidator(private val root: JsonObject) {
    /** Returns every problem found in [instance]; an empty list means valid. */
    public fun validate(instance: JsonElement): List<String> {
        val errors = mutableListOf<String>()
        check(root, instance, "", errors)
        return errors
    }

    private fun check(schema: JsonObject, value: JsonElement, path: String, errors: MutableList<String>) {
        val ref = schema.string("\$ref")
        if (ref != null) {
            check(resolve(ref), value, path, errors)
            return
        }
        val before = errors.size
        checkType(schema, value, path, errors)
        if (errors.size > before) return
        checkValueKeywords(schema, value, path, errors)
        when (value) {
            is JsonObject -> checkObject(schema, value, path, errors)
            is JsonArray -> checkArray(schema, value, path, errors)
            is JsonPrimitive -> checkPrimitive(schema, value, path, errors)
        }
    }

    private fun checkType(schema: JsonObject, value: JsonElement, path: String, errors: MutableList<String>) {
        val types =
            when (val type = schema["type"]) {
                is JsonPrimitive -> listOf(type.content)
                is JsonArray -> type.map { (it as JsonPrimitive).content }
                else -> return
            }
        if (types.none { matchesType(it, value) }) {
            errors += "${label(path)} must be ${types.joinToString(" or ")}"
        }
    }

    private fun checkValueKeywords(schema: JsonObject, value: JsonElement, path: String, errors: MutableList<String>) {
        (schema["enum"] as? JsonArray)?.let { options ->
            if (options.none { sameValue(it, value) }) {
                errors += "${label(path)} must be one of ${options.joinToString(", ")}"
            }
        }
        schema["const"]?.let { if (!sameValue(it, value)) errors += "${label(path)} must be $it" }
        (schema["not"] as? JsonObject)?.let { notSchema ->
            if (validateAgainst(notSchema, value, path).isEmpty()) errors += "${label(path)} has a forbidden value"
        }
        (schema["oneOf"] as? JsonArray)?.let { options ->
            val matches = options.count { validateAgainst(it.jsonObject, value, path).isEmpty() }
            if (matches != 1) errors += "${label(path)} does not match exactly one allowed form"
        }
    }

    private fun checkObject(schema: JsonObject, value: JsonObject, path: String, errors: MutableList<String>) {
        val properties = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
        (schema["required"] as? JsonArray)?.forEach { key ->
            val name = (key as JsonPrimitive).content
            if (name !in value) errors += "${label(join(path, name))} is required"
        }
        val noExtras = (schema["additionalProperties"] as? JsonPrimitive)?.booleanOrNull == false
        for ((name, child) in value) {
            val childSchema = properties[name] as? JsonObject
            when {
                childSchema != null -> check(childSchema, child, join(path, name), errors)
                noExtras -> errors += "${label(join(path, name))} is not allowed"
            }
        }
    }

    private fun checkArray(schema: JsonObject, value: JsonArray, path: String, errors: MutableList<String>) {
        schema.int("minItems")?.let { if (value.size < it) errors += "${label(path)} must have at least $it items" }
        schema.int("maxItems")?.let { if (value.size > it) errors += "${label(path)} must have at most $it items" }
        val items = schema["items"] as? JsonObject ?: return
        value.forEachIndexed { index, item -> check(items, item, "$path[$index]", errors) }
    }

    private fun checkPrimitive(schema: JsonObject, value: JsonPrimitive, path: String, errors: MutableList<String>) {
        if (value.isString) {
            checkString(schema, value.content, label(path), errors)
        } else {
            value.doubleOrNull?.let { checkNumber(schema, it, label(path), errors) }
        }
    }

    private fun checkString(schema: JsonObject, text: String, at: String, errors: MutableList<String>) {
        val length = text.codePointCount(0, text.length)
        schema.int("minLength")?.let { if (length < it) errors += "$at is too short" }
        schema.int("maxLength")?.let { if (length > it) errors += "$at must be at most $it characters" }
        schema.string("pattern")?.let { if (!Regex(it).containsMatchIn(text)) errors += "$at has the wrong format" }
    }

    private fun checkNumber(schema: JsonObject, number: Double, at: String, errors: MutableList<String>) {
        schema.double("minimum")?.let { if (number < it) errors += "$at must be at least ${fmt(it)}" }
        schema.double("maximum")?.let { if (number > it) errors += "$at must be at most ${fmt(it)}" }
        schema.double("exclusiveMinimum")?.let { if (number <= it) errors += "$at must be greater than ${fmt(it)}" }
    }

    private fun validateAgainst(schema: JsonObject, value: JsonElement, path: String): List<String> =
        mutableListOf<String>().also { check(schema, value, path, it) }

    private fun resolve(ref: String): JsonObject {
        require(ref.startsWith("#/")) { "Only local refs are supported: $ref" }
        return ref
            .removePrefix("#/")
            .split('/')
            .fold(root as JsonElement) { node, part ->
                requireNotNull(node.jsonObject[part]) { "Unresolved ref $ref" }
            }.jsonObject
    }

    private companion object {
        val TYPE_CHECKS: Map<String, (JsonElement) -> Boolean> =
            mapOf(
                "null" to { it is JsonNull },
                "object" to { it is JsonObject },
                "array" to { it is JsonArray },
                "string" to { it is JsonPrimitive && it.isString },
                "boolean" to { it is JsonPrimitive && !it.isString && it.booleanOrNull != null },
                "number" to ::isNumber,
                "integer" to {
                    isNumber(it) &&
                        (it as JsonPrimitive).doubleOrNull?.let { n ->
                            n % 1.0 == 0.0
                        } == true
                },
            )

        fun matchesType(type: String, value: JsonElement): Boolean = TYPE_CHECKS[type]?.invoke(value) == true

        fun isNumber(value: JsonElement): Boolean =
            value is JsonPrimitive && !value.isString && value !is JsonNull && value.doubleOrNull != null

        fun isInteger(value: JsonElement): Boolean =
            isNumber(value) && (value as JsonPrimitive).doubleOrNull?.let { it % 1.0 == 0.0 } == true

        fun sameValue(a: JsonElement, b: JsonElement): Boolean {
            if (isNumber(a) &&
                isNumber(b)
            ) {
                return (a as JsonPrimitive).doubleOrNull == (b as JsonPrimitive).doubleOrNull
            }
            return a == b
        }

        fun join(path: String, name: String) = if (path.isEmpty()) name else "$path.$name"

        fun label(path: String) = if (path.isEmpty()) "The reply" else path

        fun fmt(number: Double): String = if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()

        fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

        fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    }
}
