package com.azrael.pixivdumpsync

object InputParser {
    private val userUrlRegex = Regex("(?:pixiv\\.net/(?:en/)?users/)(\\d+)", RegexOption.IGNORE_CASE)
    private val digitsRegex = Regex("^\\d+$")

    fun parseUserIds(input: String): List<String> {
        return input
            .split(Regex("[\\s,;]+"))
            .mapNotNull { raw ->
                val token = raw.trim().trimEnd('/', '?', '#')
                when {
                    token.isEmpty() -> null
                    digitsRegex.matches(token) -> token
                    else -> userUrlRegex.find(token)?.groupValues?.getOrNull(1)
                }
            }
            .distinct()
    }
}
