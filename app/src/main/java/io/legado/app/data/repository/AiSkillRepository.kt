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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    }

    override suspend fun getEnabledSkills(): List<AgentSkillSnapshot> = withContext(Dispatchers.IO) {
        dao.getEnabledSkills().mapNotNull { skill ->
            val snapshot = skill.toSnapshot(dao.getVersions(skill.id))
            snapshot.takeIf { it.activeVersion?.valid == true }
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
    }
}
