package io.legado.app.domain.model

object VbookContentLockPolicy {

    const val VBOOK_SOURCE_PREFIX = "vbook://plugin/"
    const val REQUIRED_UNLOCK_CODE = "VBOOK-EDITOR-2026"
    const val LOCKED_MESSAGE =
        "Sách tải từ nguồn VBook bên ngoài đang bị khóa xuất ebook và biên tập ebook"

    fun isExternalVbookSource(origin: String?): Boolean =
        origin?.trim()?.startsWith(VBOOK_SOURCE_PREFIX) == true

    fun isUnlocked(configuredCode: String?): Boolean =
        configuredCode?.trim() == REQUIRED_UNLOCK_CODE

    fun isLocked(origin: String?, configuredCode: String?): Boolean =
        isExternalVbookSource(origin) && !isUnlocked(configuredCode)

    fun requireUnlocked(origin: String?, configuredCode: String?) {
        require(!isLocked(origin, configuredCode)) { LOCKED_MESSAGE }
    }
}
