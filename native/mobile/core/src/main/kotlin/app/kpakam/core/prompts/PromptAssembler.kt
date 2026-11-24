package app.kpakam.core.prompts

/** The prompt templates the app ships, loaded from the contract. */
public data class PromptSet(
    val parse: PromptTemplate,
    val parseRetry: PromptTemplate,
    val voice: PromptTemplate,
    val receipt: PromptTemplate,
    val summary: PromptTemplate,
    val insights: PromptTemplate,
)

/** Assembles prompts exactly as `prompts/README.md` says. Pure functions; never does I/O. */
public class PromptAssembler(private val prompts: PromptSet) {
    /** The prompt version stored with drafts that come from [parse] or [retry]. */
    public val parseVersion: String get() = prompts.parse.id

    /** The prompt version stored with drafts that come from [voice]. */
    public val voiceVersion: String get() = prompts.voice.id

    /** The prompt version stored with drafts that come from [receipt]. */
    public val receiptVersion: String get() = prompts.receipt.id

    /**
     * Parse prompt: the parse body, then the note between `<<<` and `>>>`.
     * Every `<<<` and `>>>` is removed from [note] first, so it cannot close the section.
     */
    public fun parse(note: String): String = prompts.parse.body + "\n\nNote:\n<<<\n" + sanitizeNote(note) + "\n>>>"

    /**
     * Retry prompt: the parse prompt, the previous output (cut to 2000 characters),
     * and the retry body with one `- error` line per error.
     */
    public fun retry(note: String, previousOutput: String, errors: List<String>): String {
        val errorLines = errors.joinToString("\n") { "- $it" }
        return parse(note) + "\n\nYour last JSON:\n" + previousOutput.take(MAX_PREVIOUS_OUTPUT) + "\n\n" +
            prompts.parseRetry.body.replace("{{errors}}", errorLines)
    }

    /** Voice prompt: the voice body with `{{parseRules}}` replaced by the parse body. The audio follows as a part. */
    public fun voice(): String = prompts.voice.body.replace(PARSE_RULES, prompts.parse.body)

    /** Receipt prompt: the receipt body with `{{parseRules}}` replaced by the parse body. */
    public fun receipt(): String = prompts.receipt.body.replace(PARSE_RULES, prompts.parse.body)

    /** Summary prompt. [totalsJson] is compact JSON with totals and item names only. */
    public fun summary(totalsJson: String): String = prompts.summary.body.replace("{{totals}}", totalsJson)

    /** Insights prompt. [weekJson] is compact JSON for the week. */
    public fun insights(weekJson: String): String = prompts.insights.body.replace("{{week}}", weekJson)

    public companion object {
        /** The most characters of a previous output that go into a retry prompt. */
        public const val MAX_PREVIOUS_OUTPUT: Int = 2000
        private const val PARSE_RULES = "{{parseRules}}"
        private const val OPEN = "<<<"
        private const val CLOSE = ">>>"

        /**
         * Removes every `<<<` and `>>>` from [note]. It repeats until none is left, so a
         * removal can never join characters into a new delimiter (`<<>>><` becomes `<<<` once).
         */
        public fun sanitizeNote(note: String): String {
            var current = note
            while (current.contains(OPEN) || current.contains(CLOSE)) {
                current = current.replace(OPEN, "").replace(CLOSE, "")
            }
            return current
        }
    }
}
