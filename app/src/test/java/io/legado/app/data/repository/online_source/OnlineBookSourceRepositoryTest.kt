package io.legado.app.data.repository.online_source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineBookSourceRepositoryTest {

    private val repository = OnlineBookSourceRepository()

    @Test
    fun parseYckceoHtmlExtractsSourcesAccurately() {
        val sampleHtml = """
            <div class="layui-card-body layui-row layui-col-space20">
                <div class="layui-col-xs12 layui-col-sm6 layui-col-md4">
                    <div class="ylist">
                        <p class="checkboxclass">
                            <input type="checkbox" class="class_one" name="ids[]" value="7720" title="" lay-skin="primary">
                        </p>
                        <h2><a href="/yuedu/shuyuan/content/id/7720.html">🌟卡拉漫画 https://www.kalamanhua.com</a>
                            <p class="m-right" style="top: 3px;">1小时前</p>
                        </h2>
                        <span class="layui-badge-rim layui-bg-gray layui-font-black">3.X</span>
                        <span class="layui-badge-rim layui-bg-gray layui-font-orange">发 搜 图 </span>
                        <span class="layui-badge-rim layui-bg-gray layui-font-red" title="UID:13654">用户: langzaier</span>
                        <span class="layui-badge-rim layui-bg-gray layui-font-purple">下载:197</span>
                    </div>
                </div>
                <div class="layui-col-xs12 layui-col-sm6 layui-col-md4">
                    <div class="ylist">
                        <p class="checkboxclass">
                            <input type="checkbox" class="class_one" name="ids[]" value="6659" title="" lay-skin="primary">
                        </p>
                        <h2><a href="/yuedu/shuyuan/content/id/6659.html">UU看书 https://uukanshu.cc</a>
                            <p class="m-right" style="top: 3px;">2025/11/06</p>
                        </h2>
                        <span class="layui-badge-rim layui-bg-gray layui-font-black">3.X</span>
                        <span class="layui-badge-rim layui-bg-gray layui-font-orange">发 搜 </span>
                        <span class="layui-badge-rim layui-bg-gray layui-font-red" title="UID:11471">用户: bennettChina</span>
                        <span class="layui-badge-rim layui-bg-gray layui-font-purple">下载:103358</span>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val items = repository.parseYckceoHtml(sampleHtml)

        assertEquals(2, items.size)

        val first = items[0]
        assertEquals("yckceo:7720", first.id)
        assertEquals("🌟卡拉漫画", first.name)
        assertEquals("https://www.kalamanhua.com", first.originUrl)
        assertEquals("langzaier", first.author)
        assertEquals("197", first.downloadCount)
        assertEquals("1小时前", first.updateTime)
        assertTrue(first.isVersion3)
        assertTrue(first.hasExplore)
        assertTrue(first.hasSearch)
        assertTrue(first.hasImage)
        assertFalse(first.hasAudio)
        assertEquals("https://www.yckceo.com/yuedu/shuyuan/json/id/7720.json", first.downloadUrl)

        val second = items[1]
        assertEquals("yckceo:6659", second.id)
        assertEquals("UU看书", second.name)
        assertEquals("https://uukanshu.cc", second.originUrl)
        assertEquals("bennettChina", second.author)
        assertEquals("103358", second.downloadCount)
        assertEquals("2025/11/06", second.updateTime)
        assertTrue(second.isVersion3)
        assertTrue(second.hasExplore)
        assertTrue(second.hasSearch)
        assertFalse(second.hasImage)
        assertEquals("https://www.yckceo.com/yuedu/shuyuan/json/id/6659.json", second.downloadUrl)
    }
}
