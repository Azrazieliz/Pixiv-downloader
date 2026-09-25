package com.azrael.pixivdumpsync

object PixivLoginVerifier {
    private val errorPattern = Regex("""["']error["']\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
    private val bodyPattern = Regex("""["']body["']\s*:\s*(?!null\b)""", RegexOption.IGNORE_CASE)

    fun isAuthenticatedResponse(raw: String): Boolean {
        val error = errorPattern.find(raw)?.groupValues?.getOrNull(1)?.lowercase()
            ?: return false
        if (error != "false") return false
        return bodyPattern.containsMatchIn(raw)
    }
}
