package io.legado.app.ui.translation.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.drducbook.app.R
import io.legado.app.domain.model.AiTranslationStoryWikiRecord
import io.legado.app.domain.model.CharacterProfileDetails
import io.legado.app.domain.model.StoryWikiRelationshipTag
import io.legado.app.domain.model.characterProfile
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterDossierSheet(
    record: AiTranslationStoryWikiRecord,
    allRecords: List<AiTranslationStoryWikiRecord> = emptyList(),
    tags: List<StoryWikiRelationshipTag> = emptyList(),
    timeline: List<AiTranslationStoryWikiRecord> = emptyList(),
    onDismissRequest: () -> Unit,
    onOpenBook: () -> Unit,
    onSelectRelatedRecord: (AiTranslationStoryWikiRecord) -> Unit = {},
) {
    val profile = remember(record) { record.characterProfile() }
    var selectedTab by remember { mutableIntStateOf(0) }

    val relatedTags = remember(record, tags) {
        tags.filter {
            it.sourceRaw.equals(record.raw, true) || it.targetRaw.equals(record.raw, true)
        }
    }

    val characterTimeline = remember(record, timeline) {
        timeline.filter {
            it.raw.contains(record.raw, ignoreCase = true) ||
                it.title.contains(record.title, ignoreCase = true) ||
                it.subtitle.contains(record.title, ignoreCase = true) ||
                it.description.contains(record.title, ignoreCase = true)
        }.sortedBy { it.chapterIndex ?: Int.MAX_VALUE }
    }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.character_dossier_title),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header Profile Card
            CharacterHeaderSection(
                record = record,
                profile = profile,
            )

            // 4 Tabs: Overview, Power & Artifacts, Relationships, Timeline
            val tabTitles = listOf(
                stringResource(R.string.character_dossier_tab_overview),
                stringResource(R.string.character_dossier_tab_power),
                stringResource(R.string.character_dossier_tab_relations),
                stringResource(R.string.character_dossier_tab_timeline),
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 0.dp,
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> OverviewTabContent(record = record, profile = profile)
                1 -> PowerTabContent(
                    profile = profile,
                    allRecords = allRecords,
                    onSelectRelatedRecord = onSelectRelatedRecord,
                )
                2 -> RelationshipsTabContent(
                    currentRecord = record,
                    relatedTags = relatedTags,
                    allRecords = allRecords,
                    onSelectRelatedRecord = onSelectRelatedRecord,
                )
                3 -> TimelineTabContent(timelineEvents = characterTimeline)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = onOpenBook,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.story_memory_open_book))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterHeaderSection(
    record: AiTranslationStoryWikiRecord,
    profile: CharacterProfileDetails?,
) {
    NormalCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Portrait Avatar
            if (!record.imagePath.isNullOrBlank() && File(record.imagePath).exists()) {
                AsyncImage(
                    model = File(record.imagePath),
                    contentDescription = record.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }

            // Name and Status
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = record.raw,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Realm & Sect Badges
                val realm = profile?.realm?.takeIf(String::isNotBlank)
                val sect = profile?.sect?.takeIf(String::isNotBlank)

                if (realm != null || sect != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        realm?.let {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        sect?.let {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Titles / Aliases chips
        val allTitles = remember(profile, record) {
            val list = mutableListOf<String>()
            profile?.titles?.filter(String::isNotBlank)?.let { list.addAll(it) }
            record.subtitle.takeIf(String::isNotBlank)?.let { list.add(it) }
            list.distinct()
        }

        if (allTitles.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                allTitles.forEach { title ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OverviewTabContent(
    record: AiTranslationStoryWikiRecord,
    profile: CharacterProfileDetails?,
) {
    NormalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DossierField(
                label = stringResource(R.string.character_dossier_identity),
                value = profile?.identity?.takeIf(String::isNotBlank) ?: record.subtitle.takeIf(String::isNotBlank),
            )
            DossierField(
                label = stringResource(R.string.character_dossier_appearance),
                value = profile?.appearance?.takeIf(String::isNotBlank),
            )
            DossierField(
                label = stringResource(R.string.character_dossier_personality),
                value = profile?.personality?.takeIf(String::isNotBlank),
            )
            DossierField(
                label = stringResource(R.string.character_dossier_aptitude),
                value = profile?.aptitude?.takeIf(String::isNotBlank),
            )
            if (record.description.isNotBlank()) {
                DossierField(
                    label = "Mô tả / Tóm tắt",
                    value = record.description,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PowerTabContent(
    profile: CharacterProfileDetails?,
    allRecords: List<AiTranslationStoryWikiRecord>,
    onSelectRelatedRecord: (AiTranslationStoryWikiRecord) -> Unit,
) {
    NormalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Realm & Sect summary
            DossierField(
                label = stringResource(R.string.character_dossier_realm),
                value = profile?.realm?.takeIf(String::isNotBlank),
            )
            DossierField(
                label = stringResource(R.string.character_dossier_sect),
                value = profile?.sect?.takeIf(String::isNotBlank),
            )

            // Artifacts
            val artifacts = profile?.artifacts?.filter(String::isNotBlank).orEmpty()
            Text(
                text = stringResource(R.string.character_dossier_artifacts),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (artifacts.isEmpty()) {
                Text(
                    text = stringResource(R.string.character_dossier_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    artifacts.forEach { artifact ->
                        val target = allRecords.firstOrNull {
                            it.title.equals(artifact, true) || it.raw.equals(artifact, true)
                        }
                        ClickableTagChip(
                            text = artifact,
                            hasTarget = target != null,
                            onClick = { target?.let(onSelectRelatedRecord) },
                        )
                    }
                }
            }

            // Techniques & Divine Abilities
            val techniques = profile?.techniques?.filter(String::isNotBlank).orEmpty()
            val abilities = profile?.divineAbilities?.filter(String::isNotBlank).orEmpty()
            val allSkills = (techniques + abilities).distinct()

            Text(
                text = stringResource(R.string.character_dossier_techniques),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (allSkills.isEmpty()) {
                Text(
                    text = stringResource(R.string.character_dossier_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    allSkills.forEach { skill ->
                        val target = allRecords.firstOrNull {
                            it.title.equals(skill, true) || it.raw.equals(skill, true)
                        }
                        ClickableTagChip(
                            text = skill,
                            hasTarget = target != null,
                            onClick = { target?.let(onSelectRelatedRecord) },
                        )
                    }
                }
            }

            // Beasts / Companions
            val beasts = profile?.spiritBeasts?.filter(String::isNotBlank).orEmpty()
            if (beasts.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.character_dossier_beasts),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    beasts.forEach { beast ->
                        ClickableTagChip(
                            text = beast,
                            hasTarget = false,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationshipsTabContent(
    currentRecord: AiTranslationStoryWikiRecord,
    relatedTags: List<StoryWikiRelationshipTag>,
    allRecords: List<AiTranslationStoryWikiRecord>,
    onSelectRelatedRecord: (AiTranslationStoryWikiRecord) -> Unit,
) {
    if (relatedTags.isEmpty()) {
        NormalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.character_dossier_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            relatedTags.forEach { tag ->
                val isSource = tag.sourceRaw.equals(currentRecord.raw, true)
                val otherRaw = if (isSource) tag.targetRaw else tag.sourceRaw
                val otherTarget = if (isSource) tag.targetTarget.ifBlank { tag.targetRaw } else tag.sourceTarget.ifBlank { tag.sourceRaw }
                val targetRecord = allRecords.firstOrNull {
                    it.raw.equals(otherRaw, true) || it.title.equals(otherTarget, true)
                }

                NormalCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { targetRecord?.let(onSelectRelatedRecord) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = tag.relation,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = otherTarget,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            if (otherRaw != otherTarget) {
                                Text(
                                    text = otherRaw,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (targetRecord != null) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineTabContent(
    timelineEvents: List<AiTranslationStoryWikiRecord>,
) {
    if (timelineEvents.isEmpty()) {
        NormalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.character_dossier_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            timelineEvents.forEach { event ->
                NormalCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (event.subtitle.isNotBlank()) {
                            Text(
                                text = event.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (event.description.isNotBlank()) {
                            Text(
                                text = event.description,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DossierField(
    label: String,
    value: String?,
) {
    if (!value.isNullOrBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ClickableTagChip(
    text: String,
    hasTarget: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (hasTarget) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.then(
            if (hasTarget) Modifier.clickable(onClick = onClick) else Modifier
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (hasTarget) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (hasTarget) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (hasTarget) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
