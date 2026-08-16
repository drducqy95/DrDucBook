package io.legado.app.data.repository.sourcehealth

import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.domain.gateway.RssSourceHealthProbeGateway
import io.legado.app.domain.sourcehealth.SourceCheckProbeResult
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.domain.sourcehealth.SourceCheckStageEvidence
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.rss.Rss

class RssSourceHealthProbeRepository(
    private val now: () -> Long = System::currentTimeMillis,
    private val getArticles: suspend (
        sortName: String,
        sortUrl: String,
        source: RssSource,
        page: Int,
    ) -> Pair<MutableList<RssArticle>, String?> = { sortName, sortUrl, source, page ->
        Rss.getArticlesAwait(sortName, sortUrl, source, page)
    },
    private val getContent: suspend (RssArticle, String, RssSource) -> String = { article, rule, source ->
        Rss.getContentAwait(article, rule, source)
    },
) : RssSourceHealthProbeGateway {

    override suspend fun probe(
        source: RssSource,
        profile: SourceCheckProfile,
    ): SourceCheckProbeResult {
        val runner = SourceCheckStageRunner(now)
        val stages = mutableListOf<SourceCheckStageEvidence>()
        val sortName = source.sourceName.ifBlank { DEFAULT_SORT_NAME }
        val sortUrl = source.sortUrl?.takeIf(String::isNotBlank) ?: source.sourceUrl

        val feed = runner.run(STAGE_FEED, ORDER_FEED) {
            require(source.sourceUrl.isNotBlank()) { "RSS source URL is blank" }
            require(sortUrl.isNotBlank()) { "RSS feed URL is blank" }
            getArticles(sortName, sortUrl, source, FIRST_PAGE)
        }
        stages += feed.evidence

        val articles = feed.value?.first.orEmpty()
        val list = if (feed.evidence.passed()) {
            runner.run(STAGE_LIST, ORDER_LIST) {
                articles.firstValidArticle()
            }
        } else {
            SourceCheckStageOutcome(
                runner.skipped(STAGE_LIST, ORDER_LIST, "Feed stage did not complete"),
                null,
            )
        }
        stages += list.evidence

        if (!profile.includesStandard()) {
            return SourceCheckProbeResult(profile = profile, stages = stages)
        }

        val article = list.value
        val articleStage = if (article != null) {
            runner.run(STAGE_ARTICLE, ORDER_ARTICLE) {
                article.also {
                    require(it.title.isNotBlank()) { "RSS article title is blank" }
                    require(it.link.isNotBlank()) { "RSS article link is blank" }
                }
            }
        } else {
            SourceCheckStageOutcome(
                runner.skipped(STAGE_ARTICLE, ORDER_ARTICLE, "No RSS article is available"),
                null,
            )
        }
        stages += articleStage.evidence

        if (!profile.includesFull()) {
            return SourceCheckProbeResult(profile = profile, stages = stages)
        }

        val ruleContent = source.ruleContent?.takeIf(String::isNotBlank)
        if (ruleContent == null) {
            stages += runner.skipped(STAGE_CONTENT, ORDER_CONTENT, "RSS content rule is not configured")
        } else if (article == null) {
            stages += runner.skipped(STAGE_CONTENT, ORDER_CONTENT, "No RSS article is available")
        } else {
            stages += runner.run(STAGE_CONTENT, ORDER_CONTENT) {
                getContent(article, ruleContent, source).also {
                    if (it.isBlank()) {
                        throw NoStackTraceException("RSS content rule returned empty text")
                    }
                }
            }.evidence
        }

        return SourceCheckProbeResult(profile = profile, stages = stages)
    }

    private fun List<RssArticle>.firstValidArticle(): RssArticle {
        val article = firstOrNull()
            ?: throw NoStackTraceException("RSS feed returned no articles")
        if (article.title.isBlank() || article.link.isBlank()) {
            throw NoStackTraceException("RSS feed returned an article without title or link")
        }
        return article
    }

    private companion object {
        const val STAGE_FEED = "feed"
        const val STAGE_LIST = "list"
        const val STAGE_ARTICLE = "article"
        const val STAGE_CONTENT = "content"
        const val ORDER_FEED = 0
        const val ORDER_LIST = 1
        const val ORDER_ARTICLE = 2
        const val ORDER_CONTENT = 3
        const val FIRST_PAGE = 1
        const val DEFAULT_SORT_NAME = "RSS"
    }
}
