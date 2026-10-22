package com.azrael.pixivdumpsync

import java.text.Normalizer
import java.util.Locale

/**
 * Exact, case-insensitive tag exclusion. A line is one tag (commas inside tag names
 * are preserved). NFKC harmonizes full-width/half-width variants without matching
 * unrelated words that merely contain a blocked term.
 */
object TagExclusionPolicy {
    fun parse(text: String): Set<String> =
        text.lineSequence()
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .toSet()

    fun firstBlockedTag(tags: Collection<String>, blocked: Set<String>): String? =
        tags.firstOrNull { normalize(it) in blocked }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
}
