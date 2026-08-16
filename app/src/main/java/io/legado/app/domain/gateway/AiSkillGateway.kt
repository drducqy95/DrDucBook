package io.legado.app.domain.gateway

import io.legado.app.domain.agent.AgentSkillDraft
import io.legado.app.domain.agent.AgentSkillSnapshot
import kotlinx.coroutines.flow.Flow

interface AiSkillGateway {
    fun observeSkills(): Flow<List<AgentSkillSnapshot>>
    suspend fun getEnabledSkills(): List<AgentSkillSnapshot>
    suspend fun createDraft(
        draft: AgentSkillDraft,
        availableTools: Set<String>,
    ): AgentSkillSnapshot
    suspend fun setEnabled(skillId: String, enabled: Boolean): AgentSkillSnapshot
    suspend fun activateVersion(skillId: String, versionId: String): AgentSkillSnapshot
    suspend fun rollback(skillId: String): AgentSkillSnapshot
}
