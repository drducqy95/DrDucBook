package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceDomainIndexTest {

    @Test
    fun exactHostBeatsParentDomainMatch() {
        val exact = entry(
            key = SourceKey(SourceKeyType.BOOK, "exact"),
            sourceUrl = "https://m.example.com/home",
            homeUrl = "https://m.example.com/home",
        )
        val parent = entry(
            key = SourceKey(SourceKeyType.BOOK, "parent"),
            sourceUrl = "https://example.com/home",
            homeUrl = "https://example.com/home",
        )

        val context = SourceDomainIndex(listOf(parent, exact)).match("https://m.example.com/home")

        assertEquals(exact.key, context?.key)
        assertEquals(SourceDomainMatchReason.DIRECT, context?.matchReason)
    }

    @Test
    fun moreSpecificPathBeatsDomainRoot() {
        val root = entry(
            key = SourceKey(SourceKeyType.RSS, "root"),
            sourceUrl = "https://example.com/",
            homeUrl = "https://example.com/",
        )
        val reader = entry(
            key = SourceKey(SourceKeyType.RSS, "reader"),
            sourceUrl = "https://example.com/reader",
            homeUrl = "https://example.com/reader",
        )

        val context = SourceDomainIndex(listOf(root, reader)).match("https://example.com/reader/chapter-1")

        assertEquals(reader.key, context?.key)
        assertEquals(SourceDomainMatchReason.DIRECT, context?.matchReason)
    }

    @Test
    fun observedRedirectUrlIsMatchedAndMarked() {
        val entry = entry(
            key = SourceKey(SourceKeyType.BOOK, "redirect"),
            sourceUrl = "https://example.com/home",
            homeUrl = "https://example.com/home",
            observedRedirectUrls = listOf("https://example.com/landing"),
        )

        val context = SourceDomainIndex(listOf(entry)).match("https://example.com/landing")

        assertEquals(entry.key, context?.key)
        assertEquals(SourceDomainMatchReason.OBSERVED_REDIRECT, context?.matchReason)
    }

    @Test
    fun preferredKeyKeepsCurrentSourceOnEquivalentRules() {
        val first = entry(
            key = SourceKey(SourceKeyType.BOOK, "first"),
            sourceUrl = "https://example.com/home",
            homeUrl = "https://example.com/home",
        )
        val second = entry(
            key = SourceKey(SourceKeyType.BOOK, "second"),
            sourceUrl = "https://example.com/home",
            homeUrl = "https://example.com/home",
        )

        val context = SourceDomainIndex(listOf(first, second)).match(
            "https://example.com/home",
            preferredKey = second.key,
        )

        assertEquals(second.key, context?.key)
    }

    @Test
    fun preferredSourceIdKeepsExplicitBrowserSourceOnSameHost() {
        val first = entry(
            key = SourceKey(SourceKeyType.BOOK, "first"),
            sourceUrl = "https://example.com/source-a",
            homeUrl = "https://example.com/home",
        )
        val second = entry(
            key = SourceKey(SourceKeyType.BOOK, "second"),
            sourceUrl = "https://example.com/source-b",
            homeUrl = "https://example.com/home",
        )

        val context = SourceDomainIndex(listOf(first, second)).match(
            url = "https://example.com/home",
            preferredSourceId = second.sourceUrl,
        )

        assertEquals(second.key, context?.key)
    }

    @Test
    fun nonHttpVbookSourcesDoNotCreateHttpMatches() {
        val vbook = entry(
            key = SourceKey(SourceKeyType.BOOK, "vbook"),
            sourceUrl = "vbook://plugin/drduc",
            homeUrl = null,
            isVbook = true,
        )

        val context = SourceDomainIndex(listOf(vbook)).match("https://example.com/home")

        assertNull(context)
    }

    @Test
    fun normalizedRulesStayStableAcrossInputOrder() {
        val a = entry(
            key = SourceKey(SourceKeyType.BOOK, "a"),
            sourceUrl = "https://alpha.example.com/home",
            homeUrl = "https://alpha.example.com/home",
        )
        val b = entry(
            key = SourceKey(SourceKeyType.RSS, "b"),
            sourceUrl = "https://beta.example.com/home",
            homeUrl = "https://beta.example.com/home",
        )

        val forward = SourceDomainIndex(listOf(a, b)).normalizedRuleKeys()
        val reversed = SourceDomainIndex(listOf(b, a)).normalizedRuleKeys()

        assertEquals(forward, reversed)
    }

    private fun entry(
        key: SourceKey,
        sourceUrl: String,
        homeUrl: String?,
        observedRedirectUrls: List<String> = emptyList(),
        isVbook: Boolean = false,
    ): SourceDomainEntry = SourceDomainEntry(
        key = key,
        name = key.id,
        sourceUrl = sourceUrl,
        homeUrl = homeUrl,
        enabled = true,
        isVbook = isVbook,
        observedRedirectUrls = observedRedirectUrls,
    )
}
