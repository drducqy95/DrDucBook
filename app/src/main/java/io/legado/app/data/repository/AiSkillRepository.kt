package io.legado.app.data.repository

import android.content.Context
import io.legado.app.data.dao.AiSkillDao
import io.legado.app.data.entities.AiSkill
import io.legado.app.data.entities.AiSkillVersion
import io.legado.app.domain.agent.AgentSkillDraft
import io.legado.app.domain.agent.AgentSkillSnapshot
import io.legado.app.domain.agent.AgentSkillValidator
import io.legado.app.domain.agent.AgentSkillVersionSnapshot
import io.legado.app.domain.gateway.AiSkillGateway
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class AiSkillRepository(
    private val context: Context,
    private val dao: AiSkillDao,
) : AiSkillGateway {

    override fun observeSkills(): Flow<List<AgentSkillSnapshot>> = combine(
        dao.observeSkills(),
        dao.observeVersions(),
    ) { skills, versions ->
        val versionsBySkill = versions.groupBy(AiSkillVersion::skillId)
        skills.map { skill -> skill.toSnapshot(versionsBySkill[skill.id].orEmpty()) }
    }.onStart {
        ensureDefaultSkills()
    }

    override suspend fun getEnabledSkills(): List<AgentSkillSnapshot> = withContext(Dispatchers.IO) {
        ensureDefaultSkills()
        dao.getEnabledSkills().mapNotNull { skill ->
            val snapshot = skill.toSnapshot(dao.getVersions(skill.id))
            snapshot.takeIf { it.activeVersion?.valid == true }
        }
    }

    override suspend fun ensureDefaultSkills(): Unit = withContext(Dispatchers.IO) {
        val availableTools = AiToolRepository.toolDefinitions.map { it.name }.toSet()
        val defaultSkills = listOf(
            defaultBookSourceEngineerDraft,
            defaultStoryWikiArtDirectorDraft,
        )
        for (draft in defaultSkills) {
            val existing = dao.getSkillBySlug(draft.slug)
            if (existing == null) {
                runCatching {
                    val snapshot = createDraft(draft, availableTools)
                    setEnabled(snapshot.id, true)
                    LogUtils.d("AiSkillRepository", "Successfully seeded default skill: ${draft.slug}")
                }.onFailure { error ->
                    LogUtils.e("AiSkillRepository", "Failed to seed default skill ${draft.slug}: ${error.message ?: error.javaClass.simpleName}")
                }
            } else {
                val versions = dao.getVersions(existing.id)
                val existingTargetVersion = versions.firstOrNull { it.version == draft.version }
                if (existingTargetVersion != null) {
                    runCatching {
                        if (existing.activeVersionId != existingTargetVersion.id) {
                            activateVersion(existing.id, existingTargetVersion.id)
                        }
                        if (!existing.enabled) {
                            setEnabled(existing.id, true)
                        }
                        LogUtils.d("AiSkillRepository", "Ensured default skill activated to version ${draft.version}: ${draft.slug}")
                    }.onFailure { error ->
                        LogUtils.e("AiSkillRepository", "Failed to activate default skill ${draft.slug}: ${error.message ?: error.javaClass.simpleName}")
                    }
                } else {
                    val activeVersion = versions.firstOrNull { it.id == existing.activeVersionId }
                    if (activeVersion == null || activeVersion.version != draft.version || activeVersion.skillMarkdown != draft.instructions) {
                        runCatching {
                            val snapshot = createDraft(draft, availableTools)
                            val targetVersion = snapshot.versions.firstOrNull { it.version == draft.version }
                                ?: snapshot.versions.maxByOrNull { it.createdAt }
                            if (targetVersion != null) {
                                activateVersion(existing.id, targetVersion.id)
                            }
                            setEnabled(existing.id, true)
                            LogUtils.d("AiSkillRepository", "Successfully updated default skill to version ${draft.version}: ${draft.slug}")
                        }.onFailure { error ->
                            LogUtils.e("AiSkillRepository", "Failed to update default skill ${draft.slug}: ${error.message ?: error.javaClass.simpleName}")
                        }
                    } else if (!existing.enabled && existing.activeVersionId != null) {
                        runCatching {
                            setEnabled(existing.id, true)
                            LogUtils.d("AiSkillRepository", "Ensured default skill enabled: ${draft.slug}")
                        }.onFailure { error ->
                            LogUtils.e("AiSkillRepository", "Failed to enable default skill ${draft.slug}: ${error.message ?: error.javaClass.simpleName}")
                        }
                    }
                }
            }
        }
    }

    override suspend fun createDraft(
        draft: AgentSkillDraft,
        availableTools: Set<String>,
    ): AgentSkillSnapshot = withContext(Dispatchers.IO) {
        val normalizedDraft = draft.copy(
            slug = draft.slug.trim().lowercase(),
            name = draft.name.trim(),
            description = draft.description.trim(),
            version = draft.version.trim(),
            instructions = draft.instructions.trim(),
            allowedTools = draft.allowedTools.map(String::trim).filter(String::isNotBlank).distinct(),
            requirements = draft.requirements.map(String::trim).filter(String::isNotBlank).distinct(),
        )
        val validation = AgentSkillValidator.validate(normalizedDraft, availableTools)
        require(SKILL_SLUG_PATTERN.matches(normalizedDraft.slug)) {
            validation.message.ifBlank { "Invalid skill id" }
        }
        val existing = dao.getSkillBySlug(normalizedDraft.slug)
        val previousVersionTime = existing
            ?.let { dao.getVersions(it.id).maxOfOrNull(AiSkillVersion::createdAt) }
            ?: Long.MIN_VALUE
        val now = maxOf(System.currentTimeMillis(), previousVersionTime + 1L)
        val skillId = existing?.id ?: "skill_${UUID.randomUUID().compact()}"
        val versionId = "skill_version_${UUID.randomUUID().compact()}"
        val manifestJson = GSON.toJson(
            linkedMapOf(
                "schemaVersion" to 1,
                "id" to normalizedDraft.slug,
                "name" to normalizedDraft.name,
                "description" to normalizedDraft.description,
                "version" to normalizedDraft.version,
                "requirements" to normalizedDraft.requirements,
                "allowedTools" to normalizedDraft.allowedTools,
                "provenance" to linkedMapOf(
                    "source" to "agent_skill_draft",
                    "createdBy" to "ai_agent",
                    "compatibilitySurface" to "agent_skill_v1",
                    "storage" to "$SKILL_ROOT/$skillId/versions/$versionId",
                ),
                "lifecycle" to linkedMapOf(
                    "state" to "draft",
                    "enabledByDefault" to false,
                    "activationRequired" to true,
                ),
            )
        )
        val version = AiSkillVersion(
            id = versionId,
            skillId = skillId,
            version = normalizedDraft.version,
            name = normalizedDraft.name,
            description = normalizedDraft.description,
            manifestJson = manifestJson,
            skillMarkdown = normalizedDraft.instructions,
            allowedToolsJson = GSON.toJson(normalizedDraft.allowedTools),
            requirementsJson = GSON.toJson(normalizedDraft.requirements),
            validationStatus = if (validation.valid) {
                AiSkillVersion.STATUS_VALID
            } else {
                AiSkillVersion.STATUS_INVALID
            },
            validationMessage = validation.message,
            createdAt = now,
        )
        writeVersionFiles(skillId, version)
        val skill = existing?.copy(updatedAt = now) ?: AiSkill(
            id = skillId,
            slug = normalizedDraft.slug,
            name = normalizedDraft.name,
            description = normalizedDraft.description,
            enabled = false,
            activeVersionId = versionId,
            createdAt = now,
            updatedAt = now,
        )
        try {
            dao.saveDraft(skill, version)
        } catch (error: Throwable) {
            versionDirectory(skillId, versionId).deleteRecursively()
            throw error
        }
        requireNotNull(loadSnapshot(skillId))
    }

    override suspend fun setEnabled(
        skillId: String,
        enabled: Boolean,
    ): AgentSkillSnapshot = withContext(Dispatchers.IO) {
        val current = requireNotNull(loadSnapshot(skillId)) { "Skill not found" }
        if (enabled) {
            val active = requireNotNull(current.activeVersion) { "Skill has no active version" }
            require(active.valid) { active.validationMessage.ifBlank { "Skill version is invalid" } }
            requireVersionFiles(skillId, active.id)
        }
        check(dao.updateEnabled(skillId, enabled, System.currentTimeMillis()) == 1) {
            "Skill state was not updated"
        }
        requireNotNull(loadSnapshot(skillId))
    }

    override suspend fun activateVersion(
        skillId: String,
        versionId: String,
    ): AgentSkillSnapshot = withContext(Dispatchers.IO) {
        val current = requireNotNull(loadSnapshot(skillId)) { "Skill not found" }
        val target = requireNotNull(current.versions.firstOrNull { it.id == versionId }) {
            "Skill version not found"
        }
        require(target.valid) { target.validationMessage.ifBlank { "Skill version is invalid" } }
        requireVersionFiles(skillId, target.id)
        check(
            dao.updateActiveVersion(
                skillId = skillId,
                versionId = target.id,
                name = target.name,
                description = target.description,
                updatedAt = System.currentTimeMillis(),
            ) == 1
        ) { "Skill version was not activated" }
        requireNotNull(loadSnapshot(skillId))
    }

    override suspend fun rollback(skillId: String): AgentSkillSnapshot = withContext(Dispatchers.IO) {
        val current = requireNotNull(loadSnapshot(skillId)) { "Skill not found" }
        val ordered = current.versions.sortedByDescending(AgentSkillVersionSnapshot::createdAt)
        val activeIndex = ordered.indexOfFirst { it.id == current.activeVersionId }
        require(activeIndex >= 0) { "Skill has no active version" }
        val target = ordered.drop(activeIndex + 1).firstOrNull(AgentSkillVersionSnapshot::valid)
            ?: error("Skill has no older valid version")
        activateVersion(skillId, target.id)
    }

    private suspend fun loadSnapshot(skillId: String): AgentSkillSnapshot? {
        val skill = dao.getSkill(skillId) ?: return null
        return skill.toSnapshot(dao.getVersions(skillId))
    }

    private fun AiSkill.toSnapshot(versions: List<AiSkillVersion>): AgentSkillSnapshot {
        return AgentSkillSnapshot(
            id = id,
            slug = slug,
            name = name,
            description = description,
            enabled = enabled,
            activeVersionId = activeVersionId,
            versions = versions.map { it.toSnapshot() },
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun AiSkillVersion.toSnapshot(): AgentSkillVersionSnapshot {
        return AgentSkillVersionSnapshot(
            id = id,
            version = version,
            name = name,
            description = description,
            instructions = skillMarkdown,
            allowedTools = allowedToolsJson.toStringList(),
            requirements = requirementsJson.toStringList(),
            valid = validationStatus == AiSkillVersion.STATUS_VALID,
            validationMessage = validationMessage,
            createdAt = createdAt,
        )
    }

    private fun writeVersionFiles(skillId: String, version: AiSkillVersion) {
        val target = versionDirectory(skillId, version.id)
        require(!target.exists()) { "Skill version already exists" }
        val parent = target.parentFile ?: throw IOException("Invalid skill version directory")
        check(parent.mkdirs() || parent.isDirectory) { "Cannot create skill directory" }
        val staging = File(parent, ".${version.id}.tmp")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Cannot create skill staging directory" }
        try {
            File(staging, MANIFEST_FILE).writeText(version.manifestJson)
            File(staging, SKILL_FILE).writeText(version.skillMarkdown)
            if (!staging.renameTo(target)) {
                staging.copyRecursively(target, overwrite = false)
                staging.deleteRecursively()
            }
            requireVersionFiles(skillId, version.id)
        } catch (error: Throwable) {
            staging.deleteRecursively()
            target.deleteRecursively()
            throw error
        }
    }

    private fun requireVersionFiles(skillId: String, versionId: String) {
        val directory = versionDirectory(skillId, versionId)
        require(File(directory, MANIFEST_FILE).isFile && File(directory, SKILL_FILE).isFile) {
            "Skill version files are incomplete"
        }
    }

    private fun versionDirectory(skillId: String, versionId: String): File {
        require(SAFE_ID_PATTERN.matches(skillId) && SAFE_ID_PATTERN.matches(versionId)) {
            "Unsafe skill path"
        }
        val root = File(context.filesDir, SKILL_ROOT).canonicalFile
        val directory = File(root, "$skillId/versions/$versionId").canonicalFile
        require(directory.path.startsWith(root.path + File.separator)) { "Unsafe skill path" }
        return directory
    }

    private fun String.toStringList(): List<String> {
        return runCatching { GSON.fromJson(this, Array<String>::class.java).toList() }
            .getOrDefault(emptyList())
    }

    private fun UUID.compact(): String = toString().replace("-", "")

    companion object {
        private const val SKILL_ROOT = "agent_skills"
        private const val MANIFEST_FILE = "manifest.json"
        private const val SKILL_FILE = "SKILL.md"
        private val SKILL_SLUG_PATTERN = Regex("[a-z][a-z0-9_-]{2,63}")
        private val SAFE_ID_PATTERN = Regex("[a-z0-9_-]{3,96}")

        val defaultBookSourceEngineerDraft = AgentSkillDraft(
            slug = "book-source-engineer",
            name = "Book Source Engineer",
            description = "Quy trình chuyên sâu phân tích, tạo mới, kiểm thử và sửa lỗi nguồn truyện cho cả chuẩn Legado BookSource và vBook Extension (Darkrai9x).",
            version = "1.0.0",
            instructions = """
                # KỸ NĂNG: BOOK SOURCE ENGINEER (CHUYÊN GIA NGUỒN TRUYỆN LEGADO & VBOOK)

                1. Khảo sát trước khi viết mã: Luôn dùng fetch_internet_page để xem HTML thực tế, xác định DOM, API và phương thức phân trang.
                2. Quy trình kiểm thử độc lập qua test_book_source_rule:
                   - SEARCH: kiểm tra từ khóa, tên truyện, tác giả, link, bìa.
                   - TOC: kiểm tra danh sách chương, thứ tự từ 1 đến hết, tên chương sạch.
                   - CONTENT: kiểm tra nội dung chương đủ đoạn văn, sạch quảng cáo/watermark.
                3. Hỗ trợ chuẩn vBook Extension (Darkrai9x):
                   - Cấu trúc: plugin.json, icon.png, src/ (config.js, home.js, gen.js, search.js, detail.js, toc.js, chap.js).
                   - Dùng create_vbook_plugin_draft và install_vbook_plugin.
                4. Hỗ trợ chuẩn Legado BookSource JSON:
                   - Cấu trúc: bookSourceName, bookSourceUrl, searchUrl, ruleSearch, ruleBookInfo, ruleToc, ruleContent.
                   - Hỗ trợ CSS, XPath, JSONPath, Rhino JS (@js:), Regex (##).
                   - Dùng create_legado_book_source_draft và install_legado_book_source.
                5. Sửa lỗi nguồn có sẵn:
                   - Dùng diagnose_book_source để quét lỗi từng giai đoạn (search/detail/toc/content).
                   - Dùng repair_book_source để sửa trực tiếp URL, headers, selectors.
            """.trimIndent(),
            allowedTools = listOf(
                "fetch_internet_page",
                "search_internet",
                "search_book_sources",
                "diagnose_book_source",
                "repair_book_source",
                "test_book_source_rule",
                "create_vbook_plugin_draft",
                "install_vbook_plugin",
                "create_legado_book_source_draft",
                "install_legado_book_source",
                "save_book_source",
            ),
        )

        val defaultStoryWikiArtDirectorDraft = AgentSkillDraft(
            slug = "story-wiki-art-director",
            name = "Story Wiki & Art Director",
            description = "Chuyên sâu trích xuất và hoàn thiện bộ nhớ dịch, biên tập Story Wiki (hồ sơ nhân vật, thiết lập thế giới, niên biểu diễn tiến theo chương thực tế và tiêu đề dịch), chỉ đạo nghệ thuật tạo hình ảnh nhân vật, bản đồ thế giới và bìa truyện.",
            version = "1.0.1",
            instructions = """
                # KỸ NĂNG: STORY WIKI & ART DIRECTOR (BỘ NHỚ DỊCH, STORY WIKI & NGHỆ THUẬT)

                1. Nguyên tắc cốt lõi:
                   - Dữ liệu hoàn toàn bám sát nguyên tác (Fact Grounding), không tự bịa đặt.
                   - Niên biểu đại sự ký: bắt buộc ghi đúng số chương thực tế (chapterIndex) và hiển thị bản dịch tiếng Việt của tiêu đề chương (không để raw). Tóm tắt các giai đoạn/thời kỳ theo bước ngoặt cốt truyện, không đánh số thứ tự cứng nhắc 1, 2, 3, 4.
                   - Thuật ngữ chuẩn hóa: luôn lưu cặp từ gốc Hán tự (raw) và bản dịch tiếng Việt chuẩn (target).
                2. Quản lý Bộ nhớ dịch & Wiki:
                   - Dùng create_story_memory để trích xuất danh từ riêng mới từ chương.
                   - Dùng retrofit_story_translations để đồng bộ sửa đổi thuật ngữ vào các chương đã dịch.
                   - Dùng upsert_story_wiki_entity / world / relationship để cập nhật bách khoa toàn thư.
                   - Dùng synthesize_story_chronicle và save_story_chronicle để tổng hợp niên biểu.
                3. Chỉ đạo nghệ thuật tạo hình ảnh:
                   - Tạo chân dung nhân vật: gọi generate_character_image(bookUrl, entityRaw) dựa trên verified facts trong Story Memory.
                   - Tạo bìa truyện: bắt buộc phải có tên Truyện đã dịch (lấy theo tên người dùng đã sửa trong app, hoặc tự xác nhận rõ ràng với người dùng khi yêu cầu chatbot) và tên tác giả. Gọi generate_book_cover(bookUrl, title, author, prompt) khổ dọc 2:3, tích hợp nghệ thuật tên sách và tác giả trang nhã, tự động cập nhật vào kệ sách.
                   - Tạo minh họa thế giới & bản đồ: gọi generate_world_image(bookUrl, raw, category).
                   - Tránh chữ rác ngẫu nhiên, logo mờ hay watermark lạ trên ảnh.
            """.trimIndent(),
            allowedTools = listOf(
                "create_story_memory",
                "get_story_memory",
                "retrofit_story_translations",
                "get_story_chronicle",
                "synthesize_story_chronicle",
                "save_story_chronicle",
                "upsert_story_wiki_entity",
                "delete_story_wiki_entity",
                "upsert_story_wiki_world",
                "delete_story_wiki_world",
                "upsert_story_wiki_relationship",
                "delete_story_wiki_relationship",
                "get_story_wiki",
                "generate_character_image",
                "generate_book_cover",
                "generate_world_image",
                "get_book_detail",
                "list_book_chapters",
                "get_chapter_content",
            ),
        )
    }
}
