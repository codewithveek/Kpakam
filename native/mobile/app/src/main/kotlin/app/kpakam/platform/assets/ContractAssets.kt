package app.kpakam.platform.assets

import android.content.Context
import app.kpakam.core.config.AppConfig
import app.kpakam.core.language.Lexicon
import app.kpakam.core.prompts.PromptSet
import app.kpakam.core.prompts.PromptTemplate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Contract files copied into `assets/contract` at build time (see app/build.gradle.kts).
 * The app never keeps its own copy of a prompt, schema, or default.
 */
@Singleton
class ContractAssets
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        val prompts: PromptSet by lazy {
            PromptSet(
                parse = prompt("parse-v2"),
                parseRetry = prompt("parse-retry-v1"),
                voice = prompt("voice-v1"),
                receipt = prompt("receipt-v1"),
                summary = prompt("summary-v1"),
                insights = prompt("insights-v1"),
            )
        }

        val draftSchema: JsonObject by lazy { Json.parseToJsonElement(read("schemas/draft.schema.json")).jsonObject }

        val lexicon: Lexicon by lazy { Lexicon.parse(read("language-lexicon.json")) }

        val defaultConfig: AppConfig by lazy { AppConfig.fromJsonText(read(DEFAULTS)) }

        /** Bundled Remote Config defaults as strings, the way Remote Config returns values. */
        val defaultValues: Map<String, String> by lazy {
            Json.parseToJsonElement(read(DEFAULTS)).jsonObject.mapValues { (_, value) ->
                (value as JsonPrimitive).content
            }
        }

        private fun prompt(id: String): PromptTemplate = PromptTemplate.parse(read("prompts/$id.md"))

        private fun read(path: String): String =
            context.assets
                .open("contract/$path")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

        private companion object {
            const val DEFAULTS = "remote-config.defaults.json"
        }
    }
