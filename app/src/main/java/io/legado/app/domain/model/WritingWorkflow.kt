package io.legado.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class WritingWorkflowStage {
    IDEA_INPUT,
    BLUEPRINT_REVIEW,
    NARRATIVE_PLANNING,
    NARRATIVE_REVIEW,
    READY_TO_WRITE,
}

/**
 * Persisted approval state for the writing pipeline.
 *
 * READY_TO_WRITE is the compatibility default so projects created before this workflow was added
 * remain editable. New writing projects explicitly start at IDEA_INPUT.
 */
@Serializable
data class WritingWorkflow(
    val stage: WritingWorkflowStage = WritingWorkflowStage.READY_TO_WRITE,
    val blueprintGeneratedAt: Long? = null,
    val blueprintApprovedAt: Long? = null,
    val narrativePlanGeneratedAt: Long? = null,
    val narrativePlanApprovedAt: Long? = null,
)

object WritingWorkflowPolicy {

    val blueprintSections = setOf(
        PreWritingSectionKey.DETAILED_OUTLINE,
        PreWritingSectionKey.WORLD_BIBLE,
        PreWritingSectionKey.PLOT_THREADS,
        PreWritingSectionKey.CHARACTER_BIBLE,
    )

    val narrativeSections = setOf(
        PreWritingSectionKey.ARC_VOLUME_OUTLINE,
        PreWritingSectionKey.TIMELINE,
        PreWritingSectionKey.CHAPTER_ROADMAP,
    )

    fun canGenerateBlueprint(project: AuthoringProject): Boolean =
        project.writingWorkflow.stage in setOf(
            WritingWorkflowStage.IDEA_INPUT,
            WritingWorkflowStage.BLUEPRINT_REVIEW,
        ) &&
        project.preproduction.premise.content.isNotBlank() &&
            project.preproduction.outline.content.isNotBlank()

    fun canApproveBlueprint(project: AuthoringProject): Boolean =
        project.writingWorkflow.stage == WritingWorkflowStage.BLUEPRINT_REVIEW &&
        blueprintSections.all { project.preproduction.section(it).content.isNotBlank() }

    fun canGenerateNarrativePlan(project: AuthoringProject): Boolean =
        project.writingWorkflow.stage in setOf(
            WritingWorkflowStage.NARRATIVE_PLANNING,
            WritingWorkflowStage.NARRATIVE_REVIEW,
        ) &&
            canApproveBlueprint(project)

    fun canApproveNarrativePlan(project: AuthoringProject): Boolean =
        project.writingWorkflow.stage == WritingWorkflowStage.NARRATIVE_REVIEW &&
        narrativeSections.all { project.preproduction.section(it).content.isNotBlank() }

    fun canWriteManuscript(project: AuthoringProject): Boolean =
        project.writingWorkflow.stage == WritingWorkflowStage.READY_TO_WRITE

    fun invalidateForSectionEdit(
        project: AuthoringProject,
        key: PreWritingSectionKey,
    ): AuthoringProject {
        val workflow = project.writingWorkflow
        val nextWorkflow = when {
            key == PreWritingSectionKey.PREMISE || key == PreWritingSectionKey.OUTLINE -> {
                workflow.copy(
                    stage = WritingWorkflowStage.IDEA_INPUT,
                    blueprintApprovedAt = null,
                    narrativePlanApprovedAt = null,
                )
            }

            key in blueprintSections && workflow.stage.ordinal > WritingWorkflowStage.BLUEPRINT_REVIEW.ordinal -> {
                workflow.copy(
                    stage = WritingWorkflowStage.BLUEPRINT_REVIEW,
                    blueprintApprovedAt = null,
                    narrativePlanApprovedAt = null,
                )
            }

            key in narrativeSections && workflow.stage == WritingWorkflowStage.READY_TO_WRITE -> {
                workflow.copy(
                    stage = WritingWorkflowStage.NARRATIVE_REVIEW,
                    narrativePlanApprovedAt = null,
                )
            }

            else -> workflow
        }
        return if (nextWorkflow == workflow) project else project.copy(writingWorkflow = nextWorkflow)
    }
}
