package io.legado.app.ui.authoring.writing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drducbook.app.R
import io.legado.app.domain.model.PreWritingSectionKey
import io.legado.app.domain.model.WritingWorkflowPolicy
import io.legado.app.domain.model.WritingWorkflowStage
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import androidx.compose.ui.res.stringResource

@Composable
fun PreWritingWorkspace(
    state: WritingUiState,
    onIntent: (WritingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = state.project ?: return
    val stage = project.writingWorkflow.stage
    val visibleSections = when (stage) {
        WritingWorkflowStage.IDEA_INPUT -> listOf(
            PreWritingSectionKey.PREMISE,
            PreWritingSectionKey.OUTLINE,
        )
        WritingWorkflowStage.BLUEPRINT_REVIEW -> WritingWorkflowPolicy.blueprintSections.toList()
        WritingWorkflowStage.NARRATIVE_PLANNING,
        WritingWorkflowStage.NARRATIVE_REVIEW -> WritingWorkflowPolicy.narrativeSections.toList()
        WritingWorkflowStage.READY_TO_WRITE -> listOf(
            PreWritingSectionKey.DETAILED_OUTLINE,
            PreWritingSectionKey.WORLD_BIBLE,
            PreWritingSectionKey.PLOT_THREADS,
            PreWritingSectionKey.CHARACTER_BIBLE,
            PreWritingSectionKey.ARC_VOLUME_OUTLINE,
            PreWritingSectionKey.TIMELINE,
            PreWritingSectionKey.CHAPTER_ROADMAP,
        )
    }
    val section = project.preproduction.section(state.selectedPreWritingSection)
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WorkflowStatus(stage)
        Text(
            text = workflowInstruction(stage),
            style = LegadoTheme.typography.bodyMedium,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleSections) { key ->
                FilterChip(
                    selected = key == state.selectedPreWritingSection,
                    onClick = { onIntent(WritingIntent.SelectPreWritingSection(key)) },
                    enabled = !state.isGenerating,
                    label = { Text(sectionLabel(key)) },
                )
            }
        }
        NormalCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        sectionLabel(state.selectedPreWritingSection),
                        style = LegadoTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.writing_workflow_revision, section.revision),
                        style = LegadoTheme.typography.labelMedium,
                    )
                }
                OutlinedTextField(
                    value = state.preWritingContent,
                    onValueChange = { onIntent(WritingIntent.UpdatePreWritingContent(it)) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    enabled = !state.isGenerating,
                    label = { Text(stringResource(R.string.writing_workflow_content)) },
                )
            }
        }
        NormalCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (stage) {
                    WritingWorkflowStage.IDEA_INPUT -> WorkflowGenerateButton(
                        label = stringResource(R.string.writing_workflow_generate_blueprint),
                        loadingLabel = stringResource(R.string.writing_workflow_generating_blueprint),
                        isGenerating = state.isGenerating,
                        enabled = sectionContent(state, PreWritingSectionKey.PREMISE).isNotBlank() &&
                            sectionContent(state, PreWritingSectionKey.OUTLINE).isNotBlank(),
                        onClick = { onIntent(WritingIntent.GenerateStoryBlueprint) },
                    )
                    WritingWorkflowStage.BLUEPRINT_REVIEW -> {
                        WorkflowGenerateButton(
                            label = stringResource(R.string.writing_workflow_regenerate_blueprint),
                            loadingLabel = stringResource(R.string.writing_workflow_generating_blueprint),
                            isGenerating = state.isGenerating,
                            enabled = true,
                            onClick = { onIntent(WritingIntent.GenerateStoryBlueprint) },
                        )
                        Button(
                            onClick = { onIntent(WritingIntent.ApproveStoryBlueprint) },
                            enabled = !state.isGenerating && WritingWorkflowPolicy.blueprintSections.all {
                                sectionContent(state, it).isNotBlank()
                            },
                        ) {
                            Text(stringResource(R.string.writing_workflow_approve_blueprint))
                        }
                    }
                    WritingWorkflowStage.NARRATIVE_PLANNING -> WorkflowGenerateButton(
                        label = stringResource(R.string.writing_workflow_generate_narrative),
                        loadingLabel = stringResource(R.string.writing_workflow_generating_narrative),
                        isGenerating = state.isGenerating,
                        enabled = true,
                        onClick = { onIntent(WritingIntent.GenerateNarrativePlan) },
                    )
                    WritingWorkflowStage.NARRATIVE_REVIEW -> {
                        WorkflowGenerateButton(
                            label = stringResource(R.string.writing_workflow_regenerate_narrative),
                            loadingLabel = stringResource(R.string.writing_workflow_generating_narrative),
                            isGenerating = state.isGenerating,
                            enabled = true,
                            onClick = { onIntent(WritingIntent.GenerateNarrativePlan) },
                        )
                        Button(
                            onClick = { onIntent(WritingIntent.ApproveNarrativePlan) },
                            enabled = !state.isGenerating && WritingWorkflowPolicy.narrativeSections.all {
                                sectionContent(state, it).isNotBlank()
                            },
                        ) {
                            Text(stringResource(R.string.writing_workflow_approve_narrative))
                        }
                    }
                    WritingWorkflowStage.READY_TO_WRITE -> Button(
                        onClick = {
                            onIntent(WritingIntent.SelectWorkspaceMode(WritingWorkspaceMode.MANUSCRIPT))
                        },
                    ) {
                        Text(stringResource(R.string.writing_workflow_start_writing))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowStatus(stage: WritingWorkflowStage) {
    val currentStep = when (stage) {
        WritingWorkflowStage.IDEA_INPUT -> 0
        WritingWorkflowStage.BLUEPRINT_REVIEW -> 1
        WritingWorkflowStage.NARRATIVE_PLANNING,
        WritingWorkflowStage.NARRATIVE_REVIEW -> 2
        WritingWorkflowStage.READY_TO_WRITE -> 3
    }
    val labels = listOf(
        stringResource(R.string.writing_workflow_step_idea),
        stringResource(R.string.writing_workflow_step_blueprint),
        stringResource(R.string.writing_workflow_step_narrative),
        stringResource(R.string.writing_workflow_step_writing),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels.indices.toList()) { index ->
            FilterChip(
                selected = index == currentStep,
                onClick = {},
                enabled = false,
                label = { Text("${index + 1}. ${labels[index]}") },
            )
        }
    }
}

@Composable
private fun WorkflowGenerateButton(
    label: String,
    loadingLabel: String,
    isGenerating: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled && !isGenerating) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null)
        Text(if (isGenerating) loadingLabel else label)
    }
}

@Composable
private fun workflowInstruction(stage: WritingWorkflowStage): String = stringResource(
    when (stage) {
        WritingWorkflowStage.IDEA_INPUT -> R.string.writing_workflow_instruction_idea
        WritingWorkflowStage.BLUEPRINT_REVIEW -> R.string.writing_workflow_instruction_blueprint
        WritingWorkflowStage.NARRATIVE_PLANNING -> R.string.writing_workflow_instruction_narrative_plan
        WritingWorkflowStage.NARRATIVE_REVIEW -> R.string.writing_workflow_instruction_narrative_review
        WritingWorkflowStage.READY_TO_WRITE -> R.string.writing_workflow_instruction_ready
    }
)

@Composable
private fun sectionLabel(key: PreWritingSectionKey): String = stringResource(
    when (key) {
        PreWritingSectionKey.PREMISE -> R.string.writing_section_idea
        PreWritingSectionKey.KEY_POINTS -> R.string.writing_section_key_points
        PreWritingSectionKey.WORLD_BIBLE -> R.string.writing_section_world_view
        PreWritingSectionKey.CHARACTER_BIBLE -> R.string.writing_section_characters
        PreWritingSectionKey.PLOT_THREADS -> R.string.writing_section_main_plot
        PreWritingSectionKey.OUTLINE -> R.string.writing_section_user_outline
        PreWritingSectionKey.DETAILED_OUTLINE -> R.string.writing_section_detailed_outline
        PreWritingSectionKey.ARC_VOLUME_OUTLINE -> R.string.writing_section_acts_volumes
        PreWritingSectionKey.CHAPTER_ROADMAP -> R.string.writing_section_chapter_roadmap
        PreWritingSectionKey.TIMELINE -> R.string.writing_section_plot_progression
        PreWritingSectionKey.STYLE_TONE -> R.string.writing_section_style_tone
    }
)

private fun sectionContent(state: WritingUiState, key: PreWritingSectionKey): String {
    if (state.selectedPreWritingSection == key) return state.preWritingContent
    return state.project?.preproduction?.section(key)?.content.orEmpty()
}
