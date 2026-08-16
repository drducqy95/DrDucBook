package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingWorkflowPolicyTest {

    @Test
    fun newWorkflowRequiresIdeaAndOutlineBeforeBlueprintGeneration() {
        val empty = project()
        val ready = empty.copy(
            preproduction = empty.preproduction
                .update(PreWritingSectionKey.PREMISE, "Ý tưởng", PreWritingSectionSource.USER, 1L)
                .update(PreWritingSectionKey.OUTLINE, "Dàn ý", PreWritingSectionSource.USER, 1L)
        )

        assertFalse(WritingWorkflowPolicy.canGenerateBlueprint(empty))
        assertTrue(WritingWorkflowPolicy.canGenerateBlueprint(ready))
    }

    @Test
    fun manuscriptIsLockedUntilNarrativePlanIsApproved() {
        val project = project().copy(
            writingWorkflow = WritingWorkflow(stage = WritingWorkflowStage.NARRATIVE_REVIEW)
        )

        assertFalse(WritingWorkflowPolicy.canWriteManuscript(project))
        assertTrue(
            WritingWorkflowPolicy.canWriteManuscript(
                project.copy(
                    writingWorkflow = project.writingWorkflow.copy(
                        stage = WritingWorkflowStage.READY_TO_WRITE
                    )
                )
            )
        )
    }

    @Test
    fun editingApprovedBlueprintInvalidatesNarrativeApproval() {
        val project = project().copy(
            writingWorkflow = WritingWorkflow(
                stage = WritingWorkflowStage.READY_TO_WRITE,
                blueprintApprovedAt = 10L,
                narrativePlanApprovedAt = 20L,
            )
        )

        val updated = WritingWorkflowPolicy.invalidateForSectionEdit(
            project,
            PreWritingSectionKey.WORLD_BIBLE,
        )

        assertEquals(WritingWorkflowStage.BLUEPRINT_REVIEW, updated.writingWorkflow.stage)
        assertEquals(null, updated.writingWorkflow.blueprintApprovedAt)
        assertEquals(null, updated.writingWorkflow.narrativePlanApprovedAt)
    }

    @Test
    fun editingNarrativePlanOnlyRequiresNarrativeReapproval() {
        val project = project().copy(
            writingWorkflow = WritingWorkflow(
                stage = WritingWorkflowStage.READY_TO_WRITE,
                blueprintApprovedAt = 10L,
                narrativePlanApprovedAt = 20L,
            )
        )

        val updated = WritingWorkflowPolicy.invalidateForSectionEdit(
            project,
            PreWritingSectionKey.ARC_VOLUME_OUTLINE,
        )

        assertEquals(WritingWorkflowStage.NARRATIVE_REVIEW, updated.writingWorkflow.stage)
        assertEquals(10L, updated.writingWorkflow.blueprintApprovedAt)
        assertEquals(null, updated.writingWorkflow.narrativePlanApprovedAt)
    }

    private fun project() = AuthoringProject(
        id = "project",
        kind = AuthoringProjectKind.WRITING,
        title = "Story",
        writingWorkflow = WritingWorkflow(stage = WritingWorkflowStage.IDEA_INPUT),
        createdAt = 1L,
        updatedAt = 1L,
    )
}
