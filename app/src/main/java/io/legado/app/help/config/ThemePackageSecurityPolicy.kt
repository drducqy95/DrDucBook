package io.legado.app.help.config

import io.legado.app.data.repository.AppearanceAssetPolicy
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object ThemePackageSecurityPolicy {
    const val CURRENT_VERSION = 2
    const val MIN_SUPPORTED_VERSION = 1
    const val MAX_ENTRY_COUNT = 512
    const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 128L * 1024 * 1024

    data class Metadata(
        val checksums: Map<String, String>,
        val mimeTypes: Map<String, String>,
    )

    fun validateVersion(version: Int) {
        require(version in MIN_SUPPORTED_VERSION..CURRENT_VERSION) {
            "Unsupported theme package version: $version"
        }
    }

    fun validateRelativePath(relativePath: String): String {
        val normalized = relativePath.removeSuffix("/")
        require(normalized.isNotBlank()) { "Theme package contains an empty path" }
        require(!normalized.startsWith("/") && '\\' !in normalized && ':' !in normalized) {
            "Invalid theme package path: $relativePath"
        }
        require(normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "Invalid theme package path: $relativePath"
        }
        return normalized
    }

    fun referencedPaths(manifest: ThemePackageManifest): Set<String> = buildSet {
        addAll(manifest.assets.values)
        addAll(manifest.appearanceAssets.values)
        manifest.coverAlbums.forEach { album ->
            addAll(album.lightImages.map(ThemePackageCoverImage::path))
            addAll(album.darkImages.map(ThemePackageCoverImage::path))
        }
    }.map(::validateRelativePath).toSet()

    fun buildMetadata(root: File, referencedPaths: Set<String>): Metadata {
        val checksums = linkedMapOf<String, String>()
        val mimeTypes = linkedMapOf<String, String>()
        referencedPaths.sorted().forEach { path ->
            val file = resolve(root, path)
            require(file.isFile) { "Missing theme package entry: $path" }
            checksums[path] = sha256(file)
            mimeTypes[path] = detectMimeType(file)
        }
        return Metadata(checksums, mimeTypes)
    }

    fun validateExtracted(root: File, manifest: ThemePackageManifest) {
        validateVersion(manifest.formatVersion)
        val profileAssetIds = manifest.appearanceProfile?.let { profile ->
            buildSet {
                addAll(profile.iconSlots.values.map { it.assetId }.filter(String::isNotBlank))
                addAll(profile.lightWallpapers.values.map { it.assetId }.filter(String::isNotBlank))
                addAll(profile.darkWallpapers.values.map { it.assetId }.filter(String::isNotBlank))
            }
        }.orEmpty()
        require(profileAssetIds == manifest.appearanceAssets.keys) {
            "Appearance profile assets do not match the theme manifest"
        }
        val referenced = referencedPaths(manifest)
        val actual = root.walkTopDown()
            .filter(File::isFile)
            .map { file ->
                root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
            }
            .filterNot { it == "manifest.json" }
            .map(::validateRelativePath)
            .toSet()
        require(actual == referenced) {
            "Theme package contains missing or unreferenced files"
        }

        referenced.forEach { path ->
            val file = resolve(root, path)
            val detectedMime = detectMimeType(file)
            if (detectedMime == "image/svg+xml") {
                AppearanceAssetPolicy.sanitizeSvg(file.readBytes())
            }
            if (manifest.formatVersion >= CURRENT_VERSION) {
                val expectedChecksum = manifest.checksums[path]
                    ?: error("Missing checksum for $path")
                require(expectedChecksum.matches(Regex("^[a-f0-9]{64}$"))) {
                    "Invalid checksum for $path"
                }
                require(sha256(file) == expectedChecksum) { "Checksum mismatch for $path" }
                require(manifest.mimeTypes[path] == detectedMime) { "MIME mismatch for $path" }
            }
        }
    }

    fun detectMimeType(file: File): String {
        val header = FileInputStream(file).use { input ->
            ByteArray(512).let { bytes ->
                val size = input.read(bytes)
                if (size > 0) bytes.copyOf(size) else byteArrayOf()
            }
        }
        return detectMimeType(header, file.name)
    }

    fun detectMimeType(bytes: ByteArray, fileName: String): String {
        val header = bytes.take(512).toByteArray()
        val text = header.toString(Charsets.UTF_8).trimStart()
        return when {
            header.size >= 8 && header.copyOfRange(0, 8).contentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            ) -> "image/png"
            header.size >= 3 &&
                header[0] == 0xFF.toByte() &&
                header[1] == 0xD8.toByte() &&
                header[2] == 0xFF.toByte() -> "image/jpeg"
            header.size >= 12 &&
                header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
                header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" -> "image/webp"
            text.startsWith("<svg", ignoreCase = true) ||
                text.startsWith("<?xml", ignoreCase = true) &&
                text.contains("<svg", ignoreCase = true) -> "image/svg+xml"
            header.size >= 4 &&
                header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "OTTO" -> "font/otf"
            header.size >= 4 &&
                header[0] == 0.toByte() &&
                header[1] == 1.toByte() &&
                header[2] == 0.toByte() &&
                header[3] == 0.toByte() -> "font/ttf"
            else -> error("Executable or unsupported content in theme package: " + fileName)
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun resolve(root: File, path: String): File {
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, validateRelativePath(path)).canonicalFile
        require(target.path.startsWith(canonicalRoot.path + File.separator)) {
            "Theme package path escapes its root: $path"
        }
        return target
    }
}
