package app.kpakam.core

import app.kpakam.core.draft.DraftValidator
import app.kpakam.core.ids.Ulid
import app.kpakam.core.language.LanguageScorer
import app.kpakam.core.language.Lexicon
import app.kpakam.core.ledger.InferenceSource
import app.kpakam.core.ledger.JournalSource
import app.kpakam.core.prompts.PromptAssembler
import app.kpakam.core.prompts.PromptSet
import app.kpakam.core.prompts.PromptTemplate
import app.kpakam.core.schema.JsonSchemaValidator
import java.time.Instant
import java.util.Random

/** Shared objects built from the real contract files. */
object TestFixtures {
    val instant: Instant = Instant.parse("2026-11-24T08:41:00Z")
    val deviceSource = JournalSource(InferenceSource.ON_DEVICE, "DEFAULT_DEVICE", "parse-v1")
    val manualSource = JournalSource(InferenceSource.MANUAL, "REVERSAL", null)

    val draftValidator: DraftValidator by lazy { DraftValidator(ContractFiles.obj("schemas/draft.schema.json")) }
    val journalSchema: JsonSchemaValidator by lazy {
        JsonSchemaValidator(
            ContractFiles.obj("schemas/journal.schema.json"),
        )
    }
    val scorer: LanguageScorer by lazy { LanguageScorer(Lexicon.parse(ContractFiles.text("language-lexicon.json"))) }

    val prompts: PromptSet by lazy {
        fun load(id: String) = PromptTemplate.parse(ContractFiles.text("prompts/$id.md"))
        PromptSet(
            parse = load("parse-v2"),
            parseRetry = load("parse-retry-v1"),
            voice = load("voice-v1"),
            receipt = load("receipt-v1"),
            summary = load("summary-v1"),
            insights = load("insights-v1"),
        )
    }
    val assembler: PromptAssembler by lazy { PromptAssembler(prompts) }

    private val random = Random(42)

    fun ulid(): Ulid = Ulid.generate(instant.toEpochMilli(), random)
}
