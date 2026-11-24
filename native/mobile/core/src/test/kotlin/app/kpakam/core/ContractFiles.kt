package app.kpakam.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/** Reads the shared contract from `packages/contract`. Gradle passes the folder as `contractDir`. */
object ContractFiles {
    val dir: File by lazy {
        val path = System.getProperty("contractDir") ?: error("Set the contractDir system property")
        File(path).also { check(it.isDirectory) { "Contract folder not found: $it" } }
    }

    fun text(name: String): String = dir.resolve(name).readText()

    fun json(name: String): JsonElement = Json.parseToJsonElement(text(name))

    fun obj(name: String): JsonObject = json(name).jsonObject
}
