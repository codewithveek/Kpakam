package app.kpakam.core.prompts

/**
 * One versioned prompt file in the `prompts` folder: a header between `---` lines with
 * `id`, `task`, and `output`, then the body.
 */
public data class PromptTemplate(
    val id: String,
    val task: String,
    val output: String,
    val body: String,
) {
    public companion object {
        private const val FENCE = "---"

        /**
         * Parses a prompt file. The body is everything after the closing `---`, trimmed.
         * Throws [IllegalArgumentException] when the header is missing or incomplete.
         */
        public fun parse(text: String): PromptTemplate {
            val lines = text.replace("\r\n", "\n").split('\n')
            require(lines.firstOrNull()?.trim() == FENCE) { "Prompt file must start with ---" }
            val close = lines.drop(1).indexOfFirst { it.trim() == FENCE } + 1
            require(close > 0) { "Prompt header is not closed with ---" }
            val header =
                lines
                    .subList(1, close)
                    .filter { it.contains(':') }
                    .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }

            fun field(name: String) = requireNotNull(header[name]) { "Prompt header has no $name" }
            return PromptTemplate(
                id = field("id"),
                task = field("task"),
                output = field("output"),
                body = lines.drop(close + 1).joinToString("\n").trim(),
            )
        }
    }
}
