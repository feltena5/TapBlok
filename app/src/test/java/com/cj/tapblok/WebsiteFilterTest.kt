package com.cj.tapblok

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteFilterTest {

    private fun blocked(url: String, user: List<String> = emptyList(), adult: Boolean = true) =
        WebsiteFilter.matches(url, adult, user)

    @Test
    fun blocksKnownAdultSites() {
        assertTrue(blocked("https://www.pornhub.com/view_video"))
        assertTrue(blocked("xvideos.com"))
        assertTrue(blocked("m.xhamster.com/videos"))
        assertTrue(blocked("google.com/search?q=free+porn"))
        assertTrue(blocked("sex.com"))
        assertTrue(blocked("free-sexy-videos.net"))
        assertTrue(blocked("reddit.com/r/nsfw"))
    }

    @Test
    fun allowsFalsePositiveCandidates() {
        assertFalse(blocked("https://www.sussex.ac.uk"))
        assertFalse(blocked("essex.gov.uk/news"))
        assertFalse(blocked("en.wikipedia.org/wiki/Middlesex"))
        assertFalse(blocked("who.int/health-topics/sexual-health"))
        assertFalse(blocked("google.com/search?q=nudge+theory"))
        assertFalse(blocked("github.com"))
        assertFalse(blocked(""))
    }

    @Test
    fun adultFilterCanBeDisabled() {
        assertFalse(blocked("pornhub.com", adult = false))
    }

    @Test
    fun userDomainMatchesHostAndSubdomains() {
        val user = listOf("example.com")
        assertTrue(blocked("https://example.com/page", user, adult = false))
        assertTrue(blocked("m.example.com", user, adult = false))
        assertFalse(blocked("notexample.com", user, adult = false))
        assertFalse(blocked("google.com/search?q=example.com", user, adult = false))
    }

    @Test
    fun userDomainIsNormalized() {
        assertTrue(blocked("example.com", listOf("https://www.Example.com/"), adult = false))
    }

    @Test
    fun userKeywordMatchesAnywhere() {
        assertTrue(blocked("youtube.com/watch?v=casino123", listOf("casino"), adult = false))
        assertFalse(blocked("youtube.com", listOf("casino"), adult = false))
    }
}
