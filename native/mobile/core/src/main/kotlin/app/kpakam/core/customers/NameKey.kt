package app.kpakam.core.customers

import java.util.Locale

private val WHITESPACE = Regex("\\s+")

/**
 * Returns the match key for a customer name: lower-case, trimmed, single spaces.
 *
 * `"  Mama   Bisi "` and `"mama bisi"` give the same key, so a draft's
 * `customerName` matches an existing customer.
 */
public fun nameKey(name: String): String = name.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")

/** Returns the display name with surrounding spaces removed and inner spaces collapsed. */
public fun cleanDisplayName(name: String): String = name.trim().replace(WHITESPACE, " ")
