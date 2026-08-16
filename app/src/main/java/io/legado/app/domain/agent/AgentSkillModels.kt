package io.legado.app.domain.agent

data class AgentSkillDraft(
    val slug: String,
    val name: String,
    val description: String,
    val version: String,
    val instructions: String,
    val allowedTools: List<String> = emptyList(),
    val requirements: List<String> = emptyList(),
)

data class AgentSkillVersionSnapshot(
    val id: String,
    val version: String,
    val name: String,
    val description: String,
    val instructions: String,
    val allowedTools: List<String>,
    val requirements: List<String>,
    val valid: Boolean,
    val validationMessage: String,
    val createdAt: Long,
)

data class AgentSkillSnapshot(
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val activeVersionId: String?,
    val versions: List<AgentSkillVersionSnapshot>,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val activeVersion: AgentSkillVersionSnapshot?
        get() = versions.firstOrNull { it.id == activeVersionId }

    val latestVersion: AgentSkillVersionSnapshot?
        get() = versions.maxByOrNull { it.createdAt }
}

data class AgentSkillValidationResult(
    val valid: Boolean,
    val errors: List<String>,
) {
    val message: String
        get() = errors.joinToString("\n")
}

object AgentSkillValidator {

    fun validate(
        draft: AgentSkillDraft,
        availableTools: Set<String>,
    ): AgentSkillValidationResult {
        val errors = buildList {
            if (!SLUG_PATTERN.matches(draft.slug)) {
                add("Skill id must use 3-64 lowercase letters, numbers, '_' or '-'")
            }
            if (draft.name.isBlank() || draft.name.length > MAX_NAME_CHARS) {
                add("Skill name must contain 1-$MAX_NAME_CHARS characters")
            }
            if (draft.description.length > MAX_DESCRIPTION_CHARS) {
                add("Skill description exceeds $MAX_DESCRIPTION_CHARS characters")
            }
            if (!SEMVER_PATTERN.matches(draft.version)) {
                add("Skill version must be semantic versioning, for example 1.0.0")
            }
            if (draft.instructions.isBlank() || draft.instructions.length > MAX_INSTRUCTION_CHARS) {
                add("SKILL.md must contain 1-$MAX_INSTRUCTION_CHARS characters")
            }
            if ('\u0000' in draft.instructions) {
                add("SKILL.md contains an invalid null character")
            }
            val tools = draft.allowedTools.map(String::trim).filter(String::isNotEmpty)
            if (tools.size > MAX_ALLOWED_TOOLS || tools.size != tools.distinct().size) {
                add("allowedTools must contain at most $MAX_ALLOWED_TOOLS unique tools")
            }
            val unknownTools = tools.filterNot(availableTools::contains)
            if (unknownTools.isNotEmpty()) {
                add("Unknown tools: ${unknownTools.sorted().joinToString()}")
            }
            val requirements = draft.requirements.map(String::trim).filter(String::isNotEmpty)
            if (requirements.size > MAX_REQUIREMENTS ||
                requirements.any { it.length > MAX_REQUIREMENT_CHARS }
            ) {
                add("requirements must contain at most $MAX_REQUIREMENTS short entries")
            }
            if (requirements.any(String::isUnsafeSkillRequirement)) {
                add("requirements must not contain paths, URLs, null bytes, or executable dependencies")
            }
            if (SECRET_PATTERN.containsMatchIn(draft.instructions)) {
                add("SKILL.md appears to contain a secret and cannot be stored")
            }
        }
        return AgentSkillValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    private val SLUG_PATTERN = Regex("[a-z][a-z0-9_-]{2,63}")
    private val SEMVER_PATTERN = Regex(
        "(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)" +
            "(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?" +
            "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"
    )
    private val SECRET_PATTERN = Regex(
        "(?i)(?:sk-[a-z0-9_-]{16,}|ghp_[a-z0-9]{16,}|AIza[a-z0-9_-]{20,}|bearer\\s+[a-z0-9._-]{20,})"
    )
    private const val MAX_NAME_CHARS = 120
    private const val MAX_DESCRIPTION_CHARS = 1_000
    private const val MAX_INSTRUCTION_CHARS = 40_000
    private const val MAX_ALLOWED_TOOLS = 64
    private const val MAX_REQUIREMENTS = 32
    private const val MAX_REQUIREMENT_CHARS = 200
}

private fun String.isUnsafeSkillRequirement(): Boolean {
    val normalized = trim().replace('\\', '/')
    return '\u0000' in this ||
        normalized.startsWith('/') ||
        normalized.contains("../") ||
        DRIVE_PATH_PATTERN.containsMatchIn(normalized) ||
        URL_PATTERN.containsMatchIn(normalized)
}

private val DRIVE_PATH_PATTERN = Regex("^[A-Za-z]:/")
private val URL_PATTERN = Regex("(?i)^(?:https?|file|content|jar):")
