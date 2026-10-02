package io.legado.app.data.repository.online_source

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.domain.model.OnlineBookSourceItem
import io.legado.app.domain.model.OnlineSourceCollectionItem
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

class OnlineBookSourceRepository {

    suspend fun searchYckceo(
        query: String = "",
        page: Int = 1,
        category: String = "ALL",
    ): List<OnlineBookSourceItem> = withContext(Dispatchers.IO) {
        val encodedQuery = if (query.isNotBlank()) URLEncoder.encode(query.trim(), "UTF-8") else ""
        val categoryParam = when (category) {
            "EXPLORE" -> "&faxian=1"
            "SEARCH" -> "&sousuo=1"
            "COMIC" -> "&tu=1"
            "AUDIO" -> "&shengyin=1"
            else -> ""
        }
        val url = "https://www.yckceo.com/yuedu/shuyuan/index.html?page=$page&keys=$encodedQuery&order1=time&order2=1$categoryParam"

        val html = try {
            okHttpClient.newCallStrResponse {
                url(url)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }.body ?: return@withContext emptyList()
        } catch (_: Throwable) {
            return@withContext emptyList()
        }

        parseYckceoHtml(html)
    }

    private fun parseYckceoHtml(html: String): List<OnlineBookSourceItem> {
        val doc = Jsoup.parse(html)
        val items = mutableListOf<OnlineBookSourceItem>()
        val elements = doc.select(".ylist, .list_item, div[class*='ylist']")
        for (el in elements) {
            val titleLink = el.selectFirst("a[href*='/yuedu/shuyuan/']") ?: continue
            val href = titleLink.attr("href")
            val idMatch = Regex("""/yuedu/shuyuan/(\d+)\.html""").find(href)
            val id = idMatch?.groupValues?.get(1) ?: continue
            val name = titleLink.text().trim()
            if (name.isBlank()) continue

            val text = el.text()
            val badges = el.select(".badge, span, button").map { it.text().trim() }
            val isVersion3 = badges.any { it.contains("3.") || it.contains("3.X") } || text.contains("3.X")
            val hasExplore = badges.any { it == "发" || it.contains("发现") } || text.contains("发现")
            val hasSearch = badges.any { it == "搜" || it.contains("搜索") } || text.contains("搜索")
            val hasImage = badges.any { it == "图" || it.contains("正文图") } || text.contains("图片")
            val hasAudio = badges.any { it == "声" || it.contains("音频") } || text.contains("音频")

            val domainMatch = Regex("""域名[：:]\s*([^\s<]+)""").find(text)
            val originUrl = domainMatch?.groupValues?.get(1) ?: ""

            val authorMatch = Regex("""作者[：:]\s*([^\s<]+)""").find(text)
            val author = authorMatch?.groupValues?.get(1)

            val downMatch = Regex("""下载[：:]\s*([^\s<]+)""").find(text)
            val downloadCount = downMatch?.groupValues?.get(1)

            val timeMatch = Regex("""时间[：:]\s*([^\s<]+)""").find(text)
            val updateTime = timeMatch?.groupValues?.get(1)

            val downloadUrl = "https://www.yckceo.com/yuedu/shuyuan/jsons?id=$id"

            items.add(
                OnlineBookSourceItem(
                    id = "yckceo:$id",
                    name = name,
                    originUrl = originUrl,
                    author = author,
                    updateTime = updateTime,
                    downloadCount = downloadCount,
                    isVersion3 = isVersion3,
                    hasExplore = hasExplore,
                    hasSearch = hasSearch,
                    hasImage = hasImage,
                    hasAudio = hasAudio,
                    downloadUrl = downloadUrl,
                )
            )
        }
        return items
    }

    suspend fun getMiaoGongZiBundles(): List<OnlineSourceCollectionItem> = withContext(Dispatchers.IO) {
        listOf(
            OnlineSourceCollectionItem(
                title = "喵公子全量精品书源",
                description = "Kho nguồn chất lượng cao tổng hợp từ Miao Gong Zi, duy trì cập nhật thường xuyên, lọc sạch quảng cáo.",
                sourceCount = 1850,
                updateTime = "2026-09",
                downloadUrl = "https://yuedu.miaogongzi.net/shuyuan/all.json",
                author = "喵公子 (Miao Gong Zi)",
            ),
            OnlineSourceCollectionItem(
                title = "XIU2 精品书源 (Toàn diện)",
                description = "Nguồn truyện chất lượng cao số 1 cộng đồng Legado, tốc độ tải nhanh, hỗ trợ đầy đủ tìm kiếm và khám phá.",
                sourceCount = 360,
                updateTime = "2026-09",
                downloadUrl = "https://fastly.jsdelivr.net/gh/XIU2/Yuedu@master/shuyuan.json",
                author = "XIU2",
            ),
            OnlineSourceCollectionItem(
                title = "一程书源 (Yicheng)",
                description = "Nguồn tuyển chọn kỹ lưỡng, độ ổn định cực cao, parse mục lục và nội dung chuẩn xác.",
                sourceCount = 420,
                updateTime = "2026-09",
                downloadUrl = "https://gitee.com/yc-0/yuedu/raw/master/yicheng.json",
                author = "一程",
            ),
            OnlineSourceCollectionItem(
                title = "喵公子发现精选源",
                description = "Tổng hợp các nguồn có trang Khám phá (Discovery) phong phú, phân loại bảng xếp hạng chi tiết.",
                sourceCount = 280,
                updateTime = "2026-09",
                downloadUrl = "https://yuedu.miaogongzi.net/shuyuan/faxian.json",
                author = "喵公子",
            ),
            OnlineSourceCollectionItem(
                title = "破冰精品书源 (Pobing)",
                description = "Gói nguồn truyện chuyên văn học, tiểu thuyết mạng, tiên hiệp, huyền huyễn chọn lọc.",
                sourceCount = 310,
                updateTime = "2026-08",
                downloadUrl = "https://yuedu.miaogongzi.net/shuyuan/pobing.json",
                author = "破冰",
            ),
            OnlineSourceCollectionItem(
                title = "小寒漫画精选 (Manga/Comic)",
                description = "Chuyên nguồn truyện tranh tranh màu, manga, manhwa chất lượng cao.",
                sourceCount = 95,
                updateTime = "2026-09",
                downloadUrl = "https://yuedu.miaogongzi.net/shuyuan/manhua.json",
                author = "小寒",
            ),
            OnlineSourceCollectionItem(
                title = "有声听书精选 (Audiobook)",
                description = "Tổng hợp nguồn sách nói, audio kịch truyền thanh, radio online chất lượng cao.",
                sourceCount = 68,
                updateTime = "2026-09",
                downloadUrl = "https://yuedu.miaogongzi.net/shuyuan/yousheng.json",
                author = "喵公子",
            )
        )
    }

    suspend fun importFromUrl(url: String): Int = withContext(Dispatchers.IO) {
        val json = okHttpClient.newCallStrResponse {
            url(url)
            header("User-Agent", "Mozilla/5.0")
        }.body ?: throw IllegalStateException("Không nhận được dữ liệu từ máy chủ")

        importFromJson(json)
    }

    suspend fun importFromJson(jsonStr: String): Int = withContext(Dispatchers.IO) {
        val trimmed = jsonStr.trim()
        val sources = when {
            trimmed.startsWith("[") -> {
                GSON.fromJsonArray<BookSource>(trimmed).getOrThrow()
            }
            trimmed.startsWith("{") -> {
                val single = GSON.fromJsonObject<BookSource>(trimmed).getOrThrow()
                listOf(single)
            }
            else -> throw IllegalArgumentException("Dữ liệu không phải JSON nguồn truyện hợp lệ")
        }

        val validSources = sources.filter { it.bookSourceUrl.isNotBlank() }
        if (validSources.isEmpty()) {
            throw IllegalArgumentException("Không tìm thấy nguồn truyện hợp lệ trong dữ liệu")
        }

        appDb.bookSourceDao.insert(*validSources.toTypedArray())
        validSources.size
    }
}
