package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.data.local.LearningState
import com.example.data.repository.TopicWithStats
import com.example.data.repository.VocabularyWithProgress
import com.example.ui.VocabDetailAiUiState
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.StatusLearningColor
import com.example.ui.theme.StatusMasteredColor
import com.example.ui.theme.StatusNewColor
import com.example.ui.theme.StatusReviewColor

@Composable
fun VocabularyLibraryScreen(
    filteredVocab: List<VocabularyWithProgress>,
    totalMasterCount: Int,
    topicsWithStats: List<TopicWithStats>,
    searchQuery: String,
    selectedTopicId: String?,
    selectedDifficulty: String?,
    selectedState: String?,
    onSearchQueryChange: (String) -> Unit,
    onSelectTopic: (String?) -> Unit,
    onSelectDifficulty: (String?) -> Unit,
    onSelectState: (String?) -> Unit,
    onOpenVocabDetail: (String) -> Unit
) {
    val difficulties = listOf("Band 5.5–6.0", "Band 6.0–6.5", "Band 6.5–7.0+")
    val stateFilters = listOf(
        null to "Tất cả trạng thái",
        "DUE" to "Đến hạn ôn",
        LearningState.NEW.name to "Từ mới",
        LearningState.LEARNING.name to "Đang học",
        LearningState.REVIEW.name to "Đang ôn",
        LearningState.MASTERED.name to "Thành thạo"
    )
    val topicMap = remember(topicsWithStats) {
        topicsWithStats.associate { it.topic.id to it.topic.vietnameseName }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vocab_search_input"),
                    placeholder = { Text("Tìm từ tiếng Anh, nghĩa tiếng Việt hoặc định nghĩa...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Tìm kiếm") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa tìm kiếm")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                // Topic filter row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTopicId == null,
                        onClick = { onSelectTopic(null) },
                        label = { Text("Tất cả chủ đề (${totalMasterCount})") }
                    )
                    topicsWithStats.forEach { topicStat ->
                        FilterChip(
                            selected = selectedTopicId == topicStat.topic.id,
                            onClick = {
                                onSelectTopic(
                                    if (selectedTopicId == topicStat.topic.id) null else topicStat.topic.id
                                )
                            },
                            label = {
                                Text("${topicStat.topic.orderIndex}. ${topicStat.topic.vietnameseName} (${topicStat.totalCount})")
                            }
                        )
                    }
                }

                // Difficulty & Learning state filter row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    difficulties.forEach { diff ->
                        FilterChip(
                            selected = selectedDifficulty == diff,
                            onClick = {
                                onSelectDifficulty(if (selectedDifficulty == diff) null else diff)
                            },
                            label = { Text(diff) }
                        )
                    }
                    stateFilters.drop(1).forEach { (code, label) ->
                        FilterChip(
                            selected = selectedState == code,
                            onClick = {
                                onSelectState(if (selectedState == code) null else code)
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Text(
                    text = "Hiển thị ${filteredVocab.size} / $totalMasterCount từ vựng duy nhất (không trùng lặp bản ghi gốc)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (filteredVocab.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Không tìm thấy từ vựng nào khớp với bộ lọc hiện tại.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("vocab_library_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredVocab, key = { it.entry.id }) { item ->
                        VocabularyListItemCard(
                            item = item,
                            topicNames = item.topicIds.mapNotNull { topicMap[it] },
                            onClick = { onOpenVocabDetail(item.entry.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocabularyListItemCard(
    item: VocabularyWithProgress,
    topicNames: List<String>,
    onClick: () -> Unit
) {
    val stateColor = learningStateColor(item.progress.learningState)
    val stateLabel = learningStateVietnameseLabel(item.progress.learningState)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("vocab_item_${item.entry.id}"),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.entry.headword,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${item.entry.partOfSpeech})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!item.entry.ipa.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.entry.ipa,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = stateColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (item.isDue) "Đến hạn ôn" else stateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.isDue) AmberTertiary else stateColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = item.entry.vietnameseMeaning,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = item.entry.exampleSentence,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PillTag(item.entry.difficultyLevel, MaterialTheme.colorScheme.secondary)
                if (!item.entry.cefrLevel.isNullOrBlank()) {
                    PillTag("CEFR ${item.entry.cefrLevel} (tham khảo)", MaterialTheme.colorScheme.primary)
                }
                topicNames.take(2).forEach { tName ->
                    PillTag(tName, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PillTag(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VocabularyDetailScreen(
    item: VocabularyWithProgress,
    topicNames: List<String>,
    aiState: VocabDetailAiUiState,
    onBack: () -> Unit,
    onRequestEnrichment: () -> Unit,
    onRequestComparison: (String) -> Unit,
    onRequestSearchGrounding: () -> Unit
) {
    BackHandler(onBack = onBack)

    var comparisonWordInput by remember(item.entry.id) {
        mutableStateOf(item.synonyms.firstOrNull().orEmpty())
    }

    LaunchedEffect(item.entry.id) {
        comparisonWordInput = item.synonyms.firstOrNull().orEmpty()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("vocab_detail_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                    Text(
                        text = "Chi tiết từ vựng học thuật",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            // Main Entry Card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.entry.headword,
                                    style = MaterialTheme.typography.displayMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${item.entry.partOfSpeech}  •  ${item.entry.ipa ?: "Chưa có IPA"}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = learningStateColor(item.progress.learningState).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = learningStateVietnameseLabel(item.progress.learningState),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = learningStateColor(item.progress.learningState),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = "Nghĩa tiếng Việt",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = item.entry.vietnameseMeaning,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "Định nghĩa tiếng Anh (Academic Definition)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = item.entry.englishDefinition,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Ví dụ IELTS Academic:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "\"${item.entry.exampleSentence}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic
                                )
                                Text(
                                    text = "→ ${item.entry.vietnameseExampleTranslation}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (item.collocations.isNotEmpty()) {
                            Text(
                                text = "Collocations quan trọng:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                item.collocations.forEach { col ->
                                    AssistChip(onClick = {}, label = { Text(col) })
                                }
                            }
                        }

                        if (item.wordFamily.isNotEmpty()) {
                            Text(
                                text = "Họ từ (Word Family): ${item.wordFamily.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (item.synonyms.isNotEmpty()) {
                            Text(
                                text = "Từ đồng nghĩa (Synonyms): ${item.synonyms.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (item.antonyms.isNotEmpty()) {
                            Text(
                                text = "Từ trái nghĩa (Antonyms): ${item.antonyms.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!item.entry.commonMistakes.isNullOrBlank()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Lỗi thường gặp: ${item.entry.commonMistakes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Spaced Repetition & Source Verification Metadata Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Thông tin nguồn & Tiến độ Spaced Repetition",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Text(
                            text = "• Chủ đề: ${topicNames.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Mức độ mục tiêu: ${item.entry.difficultyLevel}" +
                                (item.entry.cefrLevel?.let { " | CEFR: $it (lưu ý: CEFR không tương đương trực tiếp điểm IELTS)" } ?: " | CEFR: Chưa xác thực (null)"),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Nguồn dữ liệu: ${item.entry.sourceName} (${item.entry.verificationStatus})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Lịch sử ôn tập: Đúng ${item.progress.correctReviews}/${item.progress.totalReviews} lần | Chuỗi đúng liên tiếp: ${item.progress.consecutiveSuccesses} | Khoảng cách hiện tại: ${item.progress.currentIntervalDays} ngày",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Gemini Intelligence Assistant Card (Enrichment, High-Thinking Comparison, Search Grounding)
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Trợ lý AI Học thuật (Gemini Intelligence)",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        // 1. Structured Enrichment (gemini-3.8-flash)
                        FilledTonalButton(
                            onClick = onRequestEnrichment,
                            enabled = !aiState.isLoadingEnrichment,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_enrich_button")
                        ) {
                            if (aiState.isLoadingEnrichment) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Bổ sung Collocations & Phân tích lỗi sai (Gemini 3.8 Flash)")
                        }

                        aiState.enrichment?.let { enrich ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Giải thích chuyên sâu: ${enrich.vietnameseExplanation}", style = MaterialTheme.typography.bodySmall)
                                    Text("Ví dụ bổ sung: \"${enrich.exampleSentence}\" (${enrich.vietnameseExampleTranslation})", style = MaterialTheme.typography.bodySmall)
                                    Text("Collocations: ${enrich.collocations.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                                    Text("Lưu ý lỗi sai: ${enrich.commonMistakes}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        HorizontalDivider()

                        // 2. High-Thinking Nuance Comparison (gemini-4-argon with ThinkingLevel.HIGH)
                        Text(
                            text = "So sánh sắc thái nghĩa chuyên sâu (Gemini 4 Argon • High Thinking)",
                            style = MaterialTheme.typography.labelLarge
                        )
                        val compareBringIntoView = remember { BringIntoViewRequester() }
                        val compareScope = rememberCoroutineScope()
                        val compareImeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
                        var isCompareFocused by remember { mutableStateOf(false) }

                        LaunchedEffect(isCompareFocused, compareImeBottom) {
                            if (isCompareFocused) {
                                delay(80)
                                compareBringIntoView.bringIntoView()
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(compareBringIntoView),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = comparisonWordInput,
                                onValueChange = { comparisonWordInput = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { focusState ->
                                        isCompareFocused = focusState.isFocused
                                        if (focusState.isFocused) {
                                            compareScope.launch {
                                                compareBringIntoView.bringIntoView()
                                            }
                                        }
                                    }
                                    .testTag("compare_word_input"),
                                label = { Text("So sánh '${item.entry.headword}' với từ...") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (!aiState.isLoadingComparison && comparisonWordInput.isNotBlank()) {
                                            onRequestComparison(comparisonWordInput)
                                        }
                                    }
                                )
                            )
                            Button(
                                onClick = { onRequestComparison(comparisonWordInput) },
                                enabled = !aiState.isLoadingComparison,
                                modifier = Modifier.testTag("compare_words_button")
                            ) {
                                if (aiState.isLoadingComparison) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Phân tích")
                            }
                        }

                        aiState.comparisonResult?.let { compText ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = compText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        HorizontalDivider()

                        // 3. Google Search Grounding (gemini-3.8-flash with googleSearch tool)
                        FilledTonalButton(
                            onClick = onRequestSearchGrounding,
                            enabled = !aiState.isLoadingGroundedUsage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_grounding_button")
                        ) {
                            if (aiState.isLoadingGroundedUsage) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Public, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tra cứu luận điểm IELTS thực tế (Google Search Grounding)")
                        }

                        aiState.groundedUsage?.let { grounded ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = grounded.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    if (grounded.sources.isNotEmpty()) {
                                        Text(
                                            text = "Nguồn: ${grounded.sources.joinToString(" | ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun learningStateVietnameseLabel(state: String): String = when (state) {
    LearningState.NEW.name -> "Từ mới"
    LearningState.LEARNING.name -> "Đang học"
    LearningState.REVIEW.name -> "Đang ôn tập"
    LearningState.MASTERED.name -> "Đã thành thạo"
    else -> state
}

fun learningStateColor(state: String): Color = when (state) {
    LearningState.NEW.name -> StatusNewColor
    LearningState.LEARNING.name -> StatusLearningColor
    LearningState.REVIEW.name -> StatusReviewColor
    LearningState.MASTERED.name -> StatusMasteredColor
    else -> StatusNewColor
}
