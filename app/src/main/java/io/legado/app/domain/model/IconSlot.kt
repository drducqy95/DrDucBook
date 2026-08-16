package io.legado.app.domain.model

enum class IconSlot(
    val key: String,
    val group: IconSlotGroup,
) {
    NAV_HOME("navigation.home", IconSlotGroup.NAVIGATION),
    NAV_BOOKSHELF("navigation.bookshelf", IconSlotGroup.NAVIGATION),
    NAV_EXPLORE("navigation.explore", IconSlotGroup.NAVIGATION),
    NAV_WORKSPACE("navigation.workspace", IconSlotGroup.NAVIGATION),
    NAV_MY("navigation.my", IconSlotGroup.NAVIGATION),
    WORKSPACE_WRITING("workspace.writing", IconSlotGroup.WORKSPACE),
    WORKSPACE_EBOOK("workspace.ebook", IconSlotGroup.WORKSPACE),
    WORKSPACE_AGENT("workspace.agent", IconSlotGroup.WORKSPACE),
    WORKSPACE_RSS("workspace.rss", IconSlotGroup.WORKSPACE),
    TOOLBAR_SEARCH("toolbar.search", IconSlotGroup.TOOLBAR),
    TOOLBAR_REFRESH("toolbar.refresh", IconSlotGroup.TOOLBAR),
    TOOLBAR_MORE("toolbar.more", IconSlotGroup.TOOLBAR),
    TOOLBAR_BROWSER_EXIT("toolbar.browser_exit", IconSlotGroup.TOOLBAR),
    SHORTCUT_WRITING("shortcut.writing", IconSlotGroup.SHORTCUT),
    SHORTCUT_EBOOK("shortcut.ebook", IconSlotGroup.SHORTCUT),
    SHORTCUT_AGENT("shortcut.agent", IconSlotGroup.SHORTCUT),
    SHORTCUT_RSS("shortcut.rss", IconSlotGroup.SHORTCUT),
    READER_TOC("reader.toc", IconSlotGroup.READER),
    READER_THEME("reader.theme", IconSlotGroup.READER),
    READER_TTS("reader.tts", IconSlotGroup.READER),
    READER_MORE("reader.more", IconSlotGroup.READER),
}

enum class IconSlotGroup {
    NAVIGATION,
    WORKSPACE,
    TOOLBAR,
    SHORTCUT,
    READER,
}

object IconSlotRegistry {
    val all: List<IconSlot> = IconSlot.entries

    fun fromKey(key: String): IconSlot? = all.firstOrNull { it.key == key }
}

