package io.legado.app.ui.workspace

import io.legado.app.data.entities.AiAgentRun
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkspaceStateTest {

    @Test
    fun buildsModuleBadgesAndOrdersRecentItemsByUpdateTime() {
        val writing = project("writing", AuthoringProjectKind.WRITING, 100L)
        val ebook = project("ebook", AuthoringProjectKind.EBOOK_EDITOR, 300L)
        val run = agentRun("agent", 200L)

        val state = buildWorkspaceUiState(listOf(writing), listOf(ebook), listOf(run))

        assertFalse(state.isLoading)
        assertFalse(state.hasError)
        assertEquals(5, state.modules.size)
        assertEquals(1, state.modules.count { it.module == WorkspaceModule.STORY_WIKI })
        assertEquals(1, state.modules.first { it.module == WorkspaceModule.WRITING }.badgeCount)
        assertEquals(1, state.modules.first { it.module == WorkspaceModule.AGENT }.badgeCount)
        assertEquals(
            listOf(WorkspaceModule.EBOOK_EDITOR, WorkspaceModule.AGENT, WorkspaceModule.WRITING),
            state.recentItems.map { it.module },
        )
    }

    @Test
    fun emptyWorkspaceStillExposesAllModules() {
        val state = buildWorkspaceUiState(emptyList(), emptyList(), emptyList())

        assertEquals(5, state.modules.size)
        assertEquals(0, state.recentItems.size)
    }

    private fun project(
        id: String,
        kind: AuthoringProjectKind,
        updatedAt: Long,
    ) = AuthoringProject(
        id = id,
        kind = kind,
        title = id,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun agentRun(id: String, updatedAt: Long) = AiAgentRun(
        id = id,
        conversationId = null,
        status = "COMPLETED",
        taskType = "Agent task",
        providerId = "provider",
        modelId = "model",
        finalTextPreview = "",
        errorMessage = null,
        pendingProposalId = null,
        startedAt = updatedAt,
        updatedAt = updatedAt,
        traceCount = 0,
        toolResultCount = 0,
    )
}
