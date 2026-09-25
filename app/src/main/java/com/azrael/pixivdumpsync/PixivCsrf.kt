package com.azrael.pixivdumpsync

import org.json.JSONObject

object PixivCsrf {
    fun extract(html: String): String? {
        val simplePatterns = listOf(
            Regex("""["']token["']\s*:\s*["']([^"']+)["']"""),
            Regex(
                """name=["']csrf-token["']\s+content=["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            )
        )
        for (pattern in simplePatterns) {
            val token = pattern.find(html)?.groupValues?.getOrNull(1)
            if (!token.isNullOrBlank()) return token
        }

        val nextMatch = Regex(
            """<script[^>]+id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)

        val nextText = nextMatch?.groupValues?.getOrNull(1)
        if (!nextText.isNullOrBlank()) {
            try {
                val next = JSONObject(nextText)
                val serialized = next
                    .optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optString("serverSerializedPreloadedState")
                    .orEmpty()

                if (serialized.isNotBlank()) {
                    val state = JSONObject(serialized)
                    val token = state
                        .optJSONObject("api")
                        ?.optString("token")
                        .orEmpty()
                    if (token.isNotBlank()) return token
                }
            } catch (_: Throwable) {
                // Ignore malformed page state and return null.
            }
        }

        return null
    }
}
