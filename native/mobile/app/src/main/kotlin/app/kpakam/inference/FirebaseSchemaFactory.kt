package app.kpakam.inference

import com.google.firebase.ai.type.Schema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Builds the Firebase AI `responseSchema` from the contract's `draft.schema.json`, so the
 * structured-output schema and our validator always agree. Handles the subset the
 * contract uses: local `$ref`, nullable type arrays, enums, objects, arrays, numbers, strings.
 */
object FirebaseSchemaFactory {
    /**
     * Converts a JSON Schema [root] document to a Firebase AI [Schema].
     * Throws [IllegalArgumentException] on an unknown type.
     */
    fun fromJsonSchema(root: JsonObject): Schema = convert(root, root)

    private fun convert(node: JsonObject, root: JsonObject): Schema {
        val ref = (node["\$ref"] as? JsonPrimitive)?.content
        if (ref != null) return convert(resolve(ref, root), root)
        val types = typesOf(node)
        val nullable = "null" in types
        val description = (node["description"] as? JsonPrimitive)?.content
        val enumValues = (node["enum"] as? JsonArray)?.map { it.jsonPrimitive.content }
        return when (val type = types.firstOrNull { it != "null" }) {
            "object" -> objectSchema(node, root, description, nullable)
            "array" ->
                Schema.array(
                    items = convert(requireNotNull(node["items"]).jsonObject, root),
                    description = description,
                    nullable = nullable,
                    maxItems = (node["maxItems"] as? JsonPrimitive)?.intOrNull,
                )
            "string" ->
                if (enumValues != null) {
                    Schema.enumeration(enumValues, description = description, nullable = nullable)
                } else {
                    Schema.string(description = description, nullable = nullable)
                }
            "number" -> Schema.double(description = description, nullable = nullable)
            "integer" -> Schema.long(description = description, nullable = nullable)
            "boolean" -> Schema.boolean(description = description, nullable = nullable)
            else -> throw IllegalArgumentException("Unsupported schema type $type")
        }
    }

    private fun objectSchema(node: JsonObject, root: JsonObject, description: String?, nullable: Boolean): Schema {
        val properties =
            (node["properties"] as? JsonObject)
                .orEmpty()
                .mapValues { (_, child) -> convert(child.jsonObject, root) }
        val required = (node["required"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty().toSet()
        return Schema.obj(
            properties = properties,
            optionalProperties = properties.keys.filterNot { it in required },
            description = description,
            nullable = nullable,
        )
    }

    private fun typesOf(node: JsonObject): List<String> =
        when (val type = node["type"]) {
            is JsonPrimitive -> listOf(type.content)
            is JsonArray -> type.map { it.jsonPrimitive.content }
            else -> emptyList()
        }

    private fun resolve(ref: String, root: JsonObject): JsonObject =
        ref.removePrefix("#/").split('/').fold(root) { node, part -> requireNotNull(node[part]).jsonObject }
}
