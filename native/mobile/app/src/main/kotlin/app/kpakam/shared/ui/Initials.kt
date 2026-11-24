package app.kpakam.shared.ui

/** Initials for a customer tile: "Mama Bisi" → "MB", "Bisi Adeyemi" → "BA". Titles are kept only when needed. */
object Initials {
    private val titles = setOf("mama", "iya", "baba", "oga", "madam", "mr", "mrs")
    private val spaces = Regex("\\s+")

    /** Up to two upper-case letters, or "?" for an empty name. */
    fun of(name: String): String {
        val all = name.split(spaces).filter { it.isNotEmpty() }
        val named = all.filter { it.lowercase() !in titles }
        val parts = if (named.size >= 2) named else all
        return parts.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    }
}
