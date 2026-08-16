package io.legado.app.data.repository

import com.google.gson.GsonBuilder
import io.legado.app.domain.model.APPEARANCE_SCHEMA_VERSION
import io.legado.app.domain.model.AppearancePresets
import io.legado.app.domain.model.AppearanceSnapshot
import java.io.File
import java.io.FileOutputStream

class AppearanceFileStore(
    private val root: File,
) {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    val profileFile = File(root, PROFILE_FILE)
    private val backupFile = File(root, BACKUP_FILE)

    fun readOrNull(): AppearanceSnapshot? =
        read(profileFile) ?: read(backupFile)

    fun write(snapshot: AppearanceSnapshot) {
        require(snapshot.schemaVersion == APPEARANCE_SCHEMA_VERSION)
        root.mkdirs()
        val tempFile = File(root, "$PROFILE_FILE.tmp")
        FileOutputStream(tempFile).use { output ->
            output.write(
                gson.toJson(AppearanceSnapshotDisk.fromDomain(snapshot)).toByteArray(Charsets.UTF_8)
            )
            output.fd.sync()
        }
        if (profileFile.isFile) {
            profileFile.copyTo(backupFile, overwrite = true)
        }
        require(tempFile.renameTo(profileFile) || run {
            tempFile.copyTo(profileFile, overwrite = true)
            tempFile.delete()
        }) { "Không thể lưu hồ sơ giao diện" }
    }

    fun validate(snapshot: AppearanceSnapshot): AppearanceSnapshot {
        require(snapshot.schemaVersion in 1..APPEARANCE_SCHEMA_VERSION) {
            "Phiên bản hồ sơ giao diện không được hỗ trợ"
        }
        val validProfiles = snapshot.profiles
            .filter { it.schemaVersion in 1..APPEARANCE_SCHEMA_VERSION }
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .distinctBy { it.id }
        require(validProfiles.isNotEmpty()) { "Không có hồ sơ giao diện hợp lệ" }
        val activeId = snapshot.activeProfileId.takeIf { id ->
            validProfiles.any { it.id == id } || AppearancePresets.all.any { it.id == id }
        } ?: validProfiles.first().id
        return snapshot.copy(activeProfileId = activeId, profiles = validProfiles)
    }

    private fun read(file: File): AppearanceSnapshot? {
        if (!file.isFile) return null
        return runCatching {
            val disk = gson.fromJson(file.readText(), AppearanceSnapshotDisk::class.java)
            validate(disk.toDomain())
        }.getOrNull()
    }

    companion object {
        const val PROFILE_FILE = "profiles.json"
        private const val BACKUP_FILE = "profiles.json.bak"
    }
}
