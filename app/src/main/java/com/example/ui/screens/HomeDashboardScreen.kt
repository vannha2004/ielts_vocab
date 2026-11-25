package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.LearningState
import com.example.data.local.ReviewSessionEntity
import com.example.data.local.UserSettingsEntity
import com.example.data.repository.TopicStudyMode
import com.example.data.repository.TopicWithStats
import com.example.data.repository.VocabularyWithProgress
import com.example.domain.QuizQuestionType
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.StatusLearningColor
import com.example.ui.theme.StatusMasteredColor
import com.example.ui.theme.StatusNewColor
import com.example.ui.theme.StatusReviewColor
import com.example.ui.theme.TealSecondary

@Composable
fun HomeDashboardScreen(
    vocabList: List<VocabularyWithProgress>,
    topicsWithStats: List<TopicWithStats>,
    settings: UserSettingsEntity,
    activeFlashcardSession: ReviewSessionEntity?,
    currentDateFormatted: String,
    gateUnlockSummary: String?,
    onDismissGateSummary: () -> Unit,
    onStartFlashcards: (String?, TopicStudyMode, Boolean) -> Unit,
    onStartQuiz: (String?, TopicStudyMode, QuizQuestionType?) -> Unit,
    onOpenTopicLibrary: () -> Unit,
    onOpenVocabDetail: (String) -> Unit
) {
    val totalEntries = vocabList.size
    val newCount = vocabList.count {
        it.progress.learningState == LearningState.NEW.name ||
            (it.progress.learningState == LearningState.LEARNING.name && it.progress.consecutiveSuccesses == 0)
    }
    val learningCount = vocabList.count {
        (it.progress.learningState == LearningState.LEARNING.name && it.progress.consecutiveSuccesses > 0) ||
            it.progress.learningState == LearningState.REVIEW.name
    }
    val masteredCount = vocabList.count { it.progress.learningState == LearningState.MASTERED.name }
    val dueCount = vocabList.count { it.isDue }
    val learnedTotal = learningCount + masteredCount
    val dailyGoal = settings.dailyGoal.coerceAtLeast(1)
    val dailyProgressFraction = (learnedTotal.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)

    var selectedTopicForDialog by remember { mutableStateOf<TopicWithStats?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("home_dashboard_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Banner with custom Canvas illustration
            item {
                AcademicHeroBanner(
                    currentDateFormatted = currentDateFormatted,
                    simulatedDayOffset = settings.simulatedDayOffset,
                    learnedTotal = learnedTotal,
                    masteredCount = masteredCount,
                    totalEntries = totalEntries
                )
            }

            // Unlock summary banner if user just finished a Mandatory Review Gate
            if (!gateUnlockSummary.isNullOrBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gate_unlock_summary_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Hoàn thành",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = gateUnlockSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissGateSummary) {
                                Text("Đóng")
                            }
                        }
                    }
                }
            }

            // Resume active Flashcard session banner if available
            if (activeFlashcardSession != null) {
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resume_flashcard_card"),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Phiên học Flashcard đang tạm dừng",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Đang ở thẻ số ${activeFlashcardSession.currentIndex + 1} • Nhấn để tiếp tục",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val mode = runCatching {
                                        TopicStudyMode.valueOf(activeFlashcardSession.studyMode ?: "CONTINUE_LEARNING")
                                    }.getOrDefault(TopicStudyMode.CONTINUE_LEARNING)
                                    onStartFlashcards(activeFlashcardSession.topicId, mode, false)
                                },
                                modifier = Modifier.testTag("resume_flashcard_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tiếp tục")
                            }
                        }
                    }
                }
            }

            // KPI Metrics Grid (Due, Learned, Mastered, Daily Progress)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardMetricCard(
                            title = "Đến hạn ôn tập",
                            value = "$dueCount từ",
                            subtitle = if (dueCount == 0) "Đã hoàn thành hôm nay" else "Cần ôn ngay",
                            accentColor = if (dueCount > 0) AmberTertiary else StatusMasteredColor,
                            modifier = Modifier.weight(1f).testTag("metric_due_reviews")
                        )
                        DashboardMetricCard(
                            title = "Mục tiêu ngày",
                            value = "$learnedTotal / $dailyGoal",
                            subtitle = "${(dailyProgressFraction * 100).toInt()}% mục tiêu từ",
                            accentColor = TealSecondary,
                            modifier = Modifier.weight(1f).testTag("metric_daily_goal")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardMetricCard(
                            title = "Đang ghi nhớ",
                            value = "$learningCount từ",
                            subtitle = "Lặp lại ngắt quãng",
                            accentColor = StatusLearningColor,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardMetricCard(
                            title = "Đã thành thạo",
                            value = "$masteredCount từ",
                            subtitle = "Trên tổng $totalEntries từ",
                            accentColor = StatusMasteredColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Quick Action Buttons (Flashcards & Quiz)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Học và kiểm tra nhanh",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    onStartFlashcards(null, TopicStudyMode.CONTINUE_LEARNING, true)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("quick_start_flashcards_button")
                            ) {
                                Icon(Icons.Default.Style, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Học Flashcard")
                            }
                            FilledTonalButton(
                                onClick = {
                                    onStartQuiz(null, TopicStudyMode.STUDY_ALL, null)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("quick_start_quiz_button")
                            ) {
                                Icon(Icons.Default.Quiz, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Làm bài Quiz")
                            }
                        }
                    }
                }
            }

            // Featured IELTS Topics Preview
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "15 Chủ đề IELTS Academic",
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(
                        onClick = onOpenTopicLibrary,
                        modifier = Modifier.testTag("view_all_topics_button")
                    ) {
                        Text("Xem tất cả (${topicsWithStats.size})")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }

            items(topicsWithStats.take(5), key = { it.topic.id }) { topicStat ->
                TopicProgressCard(
                    topicStat = topicStat,
                    onClick = { selectedTopicForDialog = topicStat }
                )
            }
        }
    }

    selectedTopicForDialog?.let { chosen ->
        TopicModeSelectionDialog(
            topicStat = chosen,
            onDismiss = { selectedTopicForDialog = null },
            onSelectFlashcardMode = { mode ->
                selectedTopicForDialog = null
                onStartFlashcards(chosen.topic.id, mode, true)
            },
            onSelectQuizMode = { mode, qType ->
                selectedTopicForDialog = null
                onStartQuiz(chosen.topic.id, mode, qType)
            }
        )
    }
}

@Composable
private fun AcademicHeroBanner(
    currentDateFormatted: String,
    simulatedDayOffset: Int,
    learnedTotal: Int,
    masteredCount: Int,
    totalEntries: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(NavyPrimary, Color(0xFF134E4A))
                    )
                )
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = Color(0x222DD4BF),
                    radius = size.minDimension * 0.45f,
                    center = Offset(size.width * 0.88f, size.height * 0.25f)
                )
                drawCircle(
                    color = Color(0x22F59E0B),
                    radius = size.minDimension * 0.32f,
                    center = Offset(size.width * 0.15f, size.height * 0.85f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Lộ trình IELTS Academic 5.5 → 7.0+",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }

                    Text(
                        text = if (simulatedDayOffset > 0) "$currentDateFormatted (+$simulatedDayOffset ngày)" else currentDateFormatted,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Chinh phục 1.200 từ vựng học thuật",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = "Đã học $learnedTotal/$totalEntries từ trong bộ dữ liệu khởi đầu • Thành thạo $masteredCount từ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TopicLibraryScreen(
    topicsWithStats: List<TopicWithStats>,
    onStartFlashcards: (String?, TopicStudyMode, Boolean) -> Unit,
    onStartQuiz: (String?, TopicStudyMode, QuizQuestionType?) -> Unit
) {
    var selectedTopic by remember { mutableStateOf<TopicWithStats?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("topic_library_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "15 Chủ đề IELTS Academic trọng tâm",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Mục tiêu thiết kế: 80 từ/chủ đề (1.200 từ toàn bộ). Bộ dữ liệu khởi đầu hiện tại đã được xác thực từ Academic Word List (AWL) & English Vocabulary Profile. Chạm vào từng chủ đề để chọn chế độ học.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            items(topicsWithStats, key = { it.topic.id }) { topicStat ->
                TopicProgressCard(
                    topicStat = topicStat,
                    onClick = { selectedTopic = topicStat }
                )
            }
        }
    }

    selectedTopic?.let { chosen ->
        TopicModeSelectionDialog(
            topicStat = chosen,
            onDismiss = { selectedTopic = null },
            onSelectFlashcardMode = { mode ->
                selectedTopic = null
                onStartFlashcards(chosen.topic.id, mode, true)
            },
            onSelectQuizMode = { mode, qType ->
                selectedTopic = null
                onStartQuiz(chosen.topic.id, mode, qType)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicProgressCard(
    topicStat: TopicWithStats,
    onClick: () -> Unit
) {
    val progressRatio = if (topicStat.totalCount > 0) {
        (topicStat.learningCount + topicStat.masteredCount).toFloat() / topicStat.totalCount.toFloat()
    } else 0f

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("topic_card_${topicStat.topic.id}"),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${topicStat.topic.orderIndex}. ${topicStat.topic.vietnameseName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = topicStat.topic.englishName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${topicStat.totalCount}/${topicStat.topic.targetCount} từ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = topicStat.topic.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LinearProgressIndicator(
                progress = { progressRatio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = TealSecondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatBadge("Mới: ${topicStat.newCount}", StatusNewColor)
                StatBadge("Đang học: ${topicStat.learningCount}", StatusLearningColor)
                StatBadge("Thành thạo: ${topicStat.masteredCount}", StatusMasteredColor)
                if (topicStat.dueCount > 0) {
                    StatBadge("Đến hạn: ${topicStat.dueCount}", AmberTertiary)
                }
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun TopicModeSelectionDialog(
    topicStat: TopicWithStats,
    onDismiss: () -> Unit,
    onSelectFlashcardMode: (TopicStudyMode) -> Unit,
    onSelectQuizMode: (TopicStudyMode, QuizQuestionType?) -> Unit
) {
    val unmasteredCount = topicStat.totalCount - topicStat.masteredCount
    var selectedMode by remember(topicStat.topic.id) {
        mutableStateOf(
            if (unmasteredCount > 0) TopicStudyMode.CONTINUE_LEARNING else TopicStudyMode.STUDY_ALL
        )
    }

    fun countForMode(mode: TopicStudyMode): Int = when (mode) {
        TopicStudyMode.STUDY_ALL -> topicStat.totalCount
        TopicStudyMode.CONTINUE_LEARNING -> unmasteredCount
        TopicStudyMode.REVIEW_DUE_TODAY -> topicStat.dueCount
        TopicStudyMode.REVIEW_WEAK_WORDS -> topicStat.weakCount
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = topicStat.topic.vietnameseName,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${topicStat.totalCount} từ hiện có • Đã học: ${topicStat.learningCount + topicStat.masteredCount}/${topicStat.totalCount} từ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Chọn chế độ lọc từ vựng:",
                    style = MaterialTheme.typography.labelLarge
                )
                TopicStudyMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    val modeCount = countForMode(mode)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = mode }
                            .testTag("mode_option_${mode.name}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = mode.vietnameseTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    color = if (modeCount > 0) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "$modeCount từ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (modeCount > 0) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = mode.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { onSelectQuizMode(selectedMode, null) },
                    modifier = Modifier.testTag("dialog_start_quiz_button")
                ) {
                    Icon(Icons.Default.Quiz, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Làm Quiz")
                }
                Button(
                    onClick = { onSelectFlashcardMode(selectedMode) },
                    modifier = Modifier.testTag("dialog_start_flashcards_button")
                ) {
                    Icon(Icons.Default.Style, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Flashcard")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}
