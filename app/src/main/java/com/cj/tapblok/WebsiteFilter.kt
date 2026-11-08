package com.cj.tapblok

/**
 * Decides whether a URL read from a browser's address bar should be blocked.
 * Pure Kotlin so it can be unit-tested without a device.
 */
object WebsiteFilter {

    /** Matched anywhere in the URL: specific enough that substring hits are safe. */
    val ADULT_KEYWORDS = listOf(
        "porn", "xxx", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "youjizz",
        "spankbang", "brazzers", "onlyfans", "chaturbate", "stripchat", "bongacams",
        "livejasmin", "cam4", "myfreecams", "hentai", "rule34", "nhentai", "e-hentai",
        "motherless", "tnaflix", "eporner", "fapello", "camsoda",
        "nsfw", "pornpics", "sexvid", "tube8", "drtuber", "hclips", "txxx", "porntrex"
    )

    /**
     * Short words that would cause false positives as plain substrings ("essex", "sussex",
     * "nudge"), so they only match at the start of a URL token: "sex.com", "free-sex",
     * "sexy", "nudes" match; "middlesex" does not.
     */
    val ADULT_TOKEN_PREFIXES = listOf("sex", "nude", "milf", "escort", "camgirl", "desnud", "putas", "erome", "beeg")

    /** Tokens that start with a prefix above but are not adult content. */
    private val TOKEN_EXCEPTIONS = setOf("sexual", "sexuality", "sexualidad", "sextant", "sexton", "sextet")

    private val TOKEN_SPLIT = Regex("[^a-z0-9]+")

    /** Lower-cases and strips scheme and leading "www." so patterns compare consistently. */
    fun normalize(url: String): String =
        url.trim().lowercase()
            .removePrefix("http://").removePrefix("https://")
            .removePrefix("www.")

    fun hostOf(normalizedUrl: String): String =
        normalizedUrl.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')

    /**
     * @param userPatterns entries added by the user. One containing a dot is a domain
     *   (matches that host and its subdomains); otherwise it is a keyword matched anywhere.
     */
    fun matches(url: String, adultFilterEnabled: Boolean, userPatterns: Collection<String>): Boolean {
        val normalized = normalize(url)
        if (normalized.isEmpty()) return false
        val host = hostOf(normalized)

        for (raw in userPatterns) {
            val pattern = normalize(raw).trimEnd('/')
            if (pattern.isEmpty()) continue
            if ('.' in pattern) {
                if (host == pattern || host.endsWith(".$pattern")) return true
            } else if (pattern in normalized) {
                return true
            }
        }

        if (!adultFilterEnabled) return false
        if (ADULT_KEYWORDS.any { it in normalized }) return true
        return normalized.split(TOKEN_SPLIT).any { token ->
            token !in TOKEN_EXCEPTIONS && ADULT_TOKEN_PREFIXES.any { token.startsWith(it) }
        }
    }
}
