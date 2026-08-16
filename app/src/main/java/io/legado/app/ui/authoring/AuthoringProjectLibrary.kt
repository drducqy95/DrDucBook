package io.legado.app.ui.authoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import java.text.DateFormat
import java.util.Date

@Composable
fun AuthoringProjectLibrary(
    projects: List<AuthoringProject>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onCreateProject: () -> Unit,
    title: String,
    description: String,
    createLabel: String,
    searchLabel: String,
    emptyTitle: String,
    emptyDescription: String,
    projectCountLabel: @Composable (Int) -> String,
    chapterCountLabel: @Composable (Int) -> String,
    wordCountLabel: @Composable (Int) -> String,
    updatedLabel: @Composable (String) -> String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.AutoStories,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val filteredProjects = projects.filter { project ->
        searchQuery.isBlank() || project.title.contains(searchQuery, ignoreCase = true) ||
            project.author.contains(searchQuery, ignoreCase = true)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(280.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            NormalCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = LegadoTheme.colorScheme.primaryContainer,
                contentColor = LegadoTheme.colorScheme.onPrimaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
                    Text(
                        text = title,
                        style = LegadoTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = description, style = LegadoTheme.typography.bodyMedium)
                    Text(
                        text = projectCountLabel(projects.size),
                        style = LegadoTheme.typography.labelMedium,
                        color = LegadoTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = onCreateProject) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text(createLabel)
                        }
                        if (secondaryActionLabel != null && onSecondaryAction != null) {
                            OutlinedButton(onClick = onSecondaryAction) {
                                Text(secondaryActionLabel)
                            }
                        }
                    }
                }
            }
        }

        if (projects.isNotEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(searchLabel) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = LegadoTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = LegadoTheme.colorScheme.surfaceContainer,
                    ),
                )
            }
        }

        if (filteredProjects.isEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            emptyTitle,
                            style = LegadoTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            emptyDescription,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            style = LegadoTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        } else {
            items(filteredProjects, key = AuthoringProject::id) { project ->
                AuthoringProjectCard(
                    project = project,
                    onClick = { onOpenProject(project.id) },
                    chapterCountLabel = chapterCountLabel,
                    wordCountLabel = wordCountLabel,
                    updatedLabel = updatedLabel,
                )
            }
        }
    }
}

@Composable
private fun AuthoringProjectCard(
    project: AuthoringProject,
    onClick: () -> Unit,
    chapterCountLabel: @Composable (Int) -> String,
    wordCountLabel: @Composable (Int) -> String,
    updatedLabel: @Composable (String) -> String,
) {
    val wordCount = project.chapters.sumOf { chapter ->
        chapter.content.trim().split(Regex("\\s+")).count(String::isNotBlank)
    }
    val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(project.updatedAt))
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.title,
                        style = LegadoTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (project.author.isNotBlank()) {
                        Text(
                            text = project.author,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            style = LegadoTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            Text(
                text = "${chapterCountLabel(project.chapters.size)}  ·  ${wordCountLabel(wordCount)}",
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.primary,
            )
            Text(
                text = updatedLabel(date),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
