package io.legado.app.ui.widget.components.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Segment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.drducbook.app.R

enum class BundledIconCategory(val titleRes: Int) {
    NAVIGATION(R.string.icon_category_nav),
    READER(R.string.icon_category_reader),
    AI_TOOLS(R.string.icon_category_ai),
    SYSTEM(R.string.icon_category_system),
}

data class BundledIconDef(
    val key: String,
    val title: String,
    val category: BundledIconCategory,
    val icon: ImageVector,
)

object BundledIconRegistry {

    val all: List<BundledIconDef> = listOf(
        // === NAVIGATION & BOOKSHELF ===
        BundledIconDef("home_classic", "Nhà cổ điển", BundledIconCategory.NAVIGATION, Icons.Default.Home),
        BundledIconDef("home_modern", "Nhà hiện đại", BundledIconCategory.NAVIGATION, Icons.Default.HomeWork),
        BundledIconDef("home_library", "Thư viện trang trọng", BundledIconCategory.NAVIGATION, Icons.Default.LocalLibrary),
        BundledIconDef("home_domain", "Tòa nhà học thuật", BundledIconCategory.NAVIGATION, Icons.Default.Domain),

        BundledIconDef("bookshelf_lib", "Kệ sách thư viện", BundledIconCategory.NAVIGATION, Icons.AutoMirrored.Filled.LibraryBooks),
        BundledIconDef("bookshelf_open", "Sách mở trang", BundledIconCategory.NAVIGATION, Icons.Default.AutoStories),
        BundledIconDef("bookshelf_single", "Sách kinh điển", BundledIconCategory.NAVIGATION, Icons.Default.Book),
        BundledIconDef("bookshelf_collection", "Tuyển tập sách", BundledIconCategory.NAVIGATION, Icons.Default.CollectionsBookmark),
        BundledIconDef("bookshelf_archive", "Thư khố lưu trữ", BundledIconCategory.NAVIGATION, Icons.Default.Archive),

        BundledIconDef("explore_compass", "La bàn thám hiểm", BundledIconCategory.NAVIGATION, Icons.Default.Explore),
        BundledIconDef("explore_travel", "Viễn chinh thế giới", BundledIconCategory.NAVIGATION, Icons.Default.TravelExplore),
        BundledIconDef("explore_globe", "Địa cầu toàn năng", BundledIconCategory.NAVIGATION, Icons.Default.Public),
        BundledIconDef("explore_radar", "Radar tầm soát", BundledIconCategory.NAVIGATION, Icons.Default.MyLocation),

        BundledIconDef("workspace_dash", "Bàn làm việc", BundledIconCategory.NAVIGATION, Icons.Default.DashboardCustomize),
        BundledIconDef("workspace_feather", "Bút lông sáng tác", BundledIconCategory.NAVIGATION, Icons.Default.Draw),
        BundledIconDef("workspace_note", "Ghi chép tác phẩm", BundledIconCategory.NAVIGATION, Icons.Default.EditNote),
        BundledIconDef("workspace_science", "Phòng nghiên cứu", BundledIconCategory.NAVIGATION, Icons.Default.Science),

        BundledIconDef("my_person", "Bạn đọc", BundledIconCategory.NAVIGATION, Icons.Default.Person),
        BundledIconDef("my_account", "Tài khoản cá nhân", BundledIconCategory.NAVIGATION, Icons.Default.AccountCircle),
        BundledIconDef("my_badge", "Huy hiệu thành viên", BundledIconCategory.NAVIGATION, Icons.Default.Badge),
        BundledIconDef("my_fingerprint", "Dấu ấn độc giả", BundledIconCategory.NAVIGATION, Icons.Default.Fingerprint),

        BundledIconDef("rss_feed", "Dòng tin RSS", BundledIconCategory.NAVIGATION, Icons.Default.RssFeed),
        BundledIconDef("rss_tower", "Đài phát tín hiệu", BundledIconCategory.NAVIGATION, Icons.Default.CellTower),

        BundledIconDef("download_box", "Tải xuống lưu trữ", BundledIconCategory.NAVIGATION, Icons.Default.Download),
        BundledIconDef("download_cloud", "Đám mây dữ liệu", BundledIconCategory.NAVIGATION, Icons.Default.CloudDownload),

        // === READER & CONTROLS ===
        BundledIconDef("reader_toc_list", "Mục lục chương", BundledIconCategory.READER, Icons.AutoMirrored.Filled.List),
        BundledIconDef("reader_toc_bullets", "Danh sách phân cấp", BundledIconCategory.READER, Icons.Default.FormatListBulleted),
        BundledIconDef("reader_toc_segment", "Phân đoạn tiểu thuyết", BundledIconCategory.READER, Icons.Default.Segment),

        BundledIconDef("reader_theme_palette", "Bảng màu giao diện", BundledIconCategory.READER, Icons.Default.Palette),
        BundledIconDef("reader_theme_night", "Chế độ ban đêm", BundledIconCategory.READER, Icons.Default.Brightness4),
        BundledIconDef("reader_theme_day_night", "Chuyển ngày đêm", BundledIconCategory.READER, Icons.Default.Brightness6),
        BundledIconDef("reader_theme_sunny", "Độ sáng ban ngày", BundledIconCategory.READER, Icons.Default.WbSunny),

        BundledIconDef("reader_tts_headphone", "Tai nghe đọc to", BundledIconCategory.READER, Icons.Default.Headphones),
        BundledIconDef("reader_tts_voice", "Giọng nói đọc truyện", BundledIconCategory.READER, Icons.Default.RecordVoiceOver),
        BundledIconDef("reader_tts_wave", "Sóng âm thanh sống động", BundledIconCategory.READER, Icons.Default.GraphicEq),

        BundledIconDef("reader_search", "Tìm kiếm trong sách", BundledIconCategory.READER, Icons.Default.Search),
        BundledIconDef("reader_bookmark", "Kẹp sách", BundledIconCategory.READER, Icons.Default.Bookmark),
        BundledIconDef("reader_bookmark_add", "Thêm dấu trang", BundledIconCategory.READER, Icons.Default.BookmarkAdd),
        BundledIconDef("reader_auto_page", "Tự động lật trang", BundledIconCategory.READER, Icons.Default.PlayCircle),
        BundledIconDef("reader_swap", "Đổi chương nhanh", BundledIconCategory.READER, Icons.Default.SwapHoriz),
        BundledIconDef("reader_settings", "Cài đặt đọc sách", BundledIconCategory.READER, Icons.Default.Settings),
        BundledIconDef("reader_tune", "Tinh chỉnh tham số", BundledIconCategory.READER, Icons.Default.Tune),

        // === AI TOOLS & TRANSLATION ===
        BundledIconDef("tool_ai_sparkle", "Tinh cầu AI", BundledIconCategory.AI_TOOLS, Icons.Default.AutoAwesome),
        BundledIconDef("tool_ai_brain", "Trí tuệ nhân tạo", BundledIconCategory.AI_TOOLS, Icons.Default.Psychology),
        BundledIconDef("tool_ai_bot", "Trợ lý AI", BundledIconCategory.AI_TOOLS, Icons.Default.SmartToy),
        BundledIconDef("tool_ai_edit", "Biên tập trau chuốt", BundledIconCategory.AI_TOOLS, Icons.Default.Edit),

        BundledIconDef("tool_translate_globe", "Dịch thuật toàn năng", BundledIconCategory.AI_TOOLS, Icons.Default.Public),
        BundledIconDef("tool_purify_clean", "Thanh lọc nội dung", BundledIconCategory.AI_TOOLS, Icons.Default.CleaningServices),
        BundledIconDef("tool_purify_filter", "Bộ lọc quảng cáo", BundledIconCategory.AI_TOOLS, Icons.Default.FilterAlt),
        BundledIconDef("tool_purify_replace", "Quy tắc thay thế", BundledIconCategory.AI_TOOLS, Icons.Default.FindReplace),
    )

    private val mapByKey: Map<String, BundledIconDef> = all.associateBy { it.key }

    fun find(key: String): BundledIconDef? = mapByKey[key]

    fun getVector(key: String): ImageVector {
        // Backward compatibility mappings
        val normalizedKey = when (key) {
            "book" -> "bookshelf_open"
            "sparkles" -> "tool_ai_sparkle"
            "rss" -> "rss_feed"
            else -> key
        }
        return mapByKey[normalizedKey]?.icon ?: Icons.Default.AutoStories
    }
}
