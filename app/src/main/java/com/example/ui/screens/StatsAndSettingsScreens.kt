package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.LearningState
import com.example.data.local.QuizAttemptEntity
import com.example.data.local.ReviewHistoryEntity
import com.example.data.local.UserSettingsEntity
import com.example.data.remote.LlmConfigStatus
import com.example.data.remote.LlmService
import com.example.data.repository.VocabularyWithProgress
import com.example.data.seed.SeedValidationReport
import com.example.notifications.StudyReminderScheduler
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.StatusDangerColor
import com.example.ui.theme.StatusMasteredColor
import com.example.ui.theme.TealSecondary

@Composable
fun StatisticsScreen(
    vocabList: List<VocabularyWithProgress>,
    reviewHistory: List<ReviewHistoryEntity>,
    quizAttempts: List<QuizAttemptEntity>,
    onOpenVocabDetail: (String) -> Unit,
    onPracticeWeakWords: () -> Unit
) {
    val totalReviews = reviewHistory.size
    val correctReviews = reviewHistory.count { it.wasCorrect }
    val reviewAccuracy = if (totalReviews > 0) (correctReviews * 100) / totalReviews else 0

    val totalQuizQuestions = quizAttempts.sumOf { it.totalQuestions }
    val totalQuizCorrect = quizAttempts.sumOf { it.correctAnswers }
    val quizAccuracy = if (totalQuizQuestions > 0) (totalQuizCorrect * 100) / totalQuizQuestions else 0

    val weakWords = remember(vocabList) {
        vocabList
            .filter { it.progress.wrongCount > 0 || it.progress.learningState == LearningState.LEARNING.name }
            .sortedByDescending { it.progress.wrongCount }
    }

    val vocabMap = remember(vocabList) { vocabList.associateBy { it.entry.id } }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("statistics_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tổng quan hiệu suất & Độ chính xác",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatSummaryBox(
                                label = "Lượt ôn tập",
                                value = "$totalReviews lượt",
                                sub = "Chính xác: $reviewAccuracy%",
                                modifier = Modifier.weight(1f)
                            )
                            StatSummaryBox(
                                label = "Bài kiểm tra Quiz",
                                value = "${quizAttempts.size} bài",
                                sub = "Độ chính xác: $quizAccuracy%",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        val mastered = vocabList.count { it.progress.learningState == LearningState.MASTERED.name }
                        val review = vocabList.count { it.progress.learningState == LearningState.REVIEW.name }
                        val learning = vocabList.count { it.progress.learningState == LearningState.LEARNING.name }
                        val newWords = vocabList.count { it.progress.learningState == LearningState.NEW.name }

                        Text(
                            text = "Phân bổ trạng thái Spaced Repetition: Mới ($newWords) • Đang học ($learning) • Đang ôn ($review) • Thành thạo ($mastered)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Weak Words Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AmberTertiary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Từ vựng cần củng cố (${weakWords.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            if (weakWords.isNotEmpty()) {
                                FilledTonalButton(
                                    onClick = onPracticeWeakWords,
                                    modifier = Modifier.testTag("practice_weak_words_button")
                                ) {
                                    Text("Ôn ngay")
                                }
                            }
                        }

                        if (weakWords.isEmpty()) {
                            Text(
                                text = "Chưa có từ vựng nào bị trả lời sai nhiều lần. Hãy tiếp tục học và làm Quiz!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            weakWords.take(8).forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenVocabDetail(item.entry.id) }
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${item.entry.headword} (${item.entry.partOfSpeech})",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = item.entry.vietnameseMeaning,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Sai: ${item.progress.wrongCount} lần",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StatusDangerColor
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            if (quizAttempts.isNotEmpty()) {
                item {
                    Text(
                        text = "Lịch sử làm bài kiểm tra (${quizAttempts.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(quizAttempts.take(10), key = { it.id }) { attempt ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (attempt.quizMode == "MANDATORY_GATE") {
                                        "Kiểm tra Ôn tập bắt buộc"
                                    } else {
                                        "Bài kiểm tra Quiz từ vựng"
                                    },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Thời gian hoàn thành: ${attempt.durationSeconds} giây",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${attempt.correctAnswers}/${attempt.totalQuestions} đúng",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Nhật ký ôn tập gần đây (${reviewHistory.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (reviewHistory.isEmpty()) {
                item {
                    Text(
                        text = "Chưa có lịch sử ôn tập nào được ghi nhận.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(reviewHistory.take(15), key = { it.id }) { history ->
                    val word = vocabMap[history.vocabId]?.entry?.headword ?: history.vocabId
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$word • ${history.reviewType}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${history.previousState} → ${history.newState} (Chu kỳ mới: ${history.newIntervalDays} ngày)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (history.wasCorrect) "Đúng / Hiểu" else "Sai / Chưa chắc",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (history.wasCorrect) StatusMasteredColor else StatusDangerColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatSummaryBox(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(text = sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettingsEntity,
    llmStatus: LlmConfigStatus,
    llmCacheCount: Int,
    currentDateFormatted: String,
    importReport: SeedValidationReport?,
    onSaveAiConfig: (String, String) -> Unit,
    onUpdateDailyGoal: (Int) -> Unit,
    onUpdateReminder: (Boolean, Int, Int) -> Unit,
    onTriggerTestNotification: () -> Unit,
    onUpdateIntervals: (String, Int) -> Unit,
    onUpdateTheme: (String) -> Unit,
    onAdvanceSimulatedDays: (Int) -> Unit,
    onImportSeedData: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var hasNotifPermission by remember {
        mutableStateOf(StudyReminderScheduler.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    var apiKeyInput by remember(settings.customApiKey) { mutableStateOf(settings.customApiKey) }
    var showApiKeyPlainText by remember { mutableStateOf(false) }
    var selectedModel by remember(settings.preferredModel) {
        mutableStateOf(settings.preferredModel.ifBlank { LlmService.MODEL_GENERAL_FLASH })
    }

    var dailyGoalInput by remember(settings.dailyGoal) { mutableStateOf(settings.dailyGoal.toString()) }
    var reminderHourInput by remember(settings.reminderHour) { mutableStateOf(settings.reminderHour.toString()) }
    var reminderMinuteInput by remember(settings.reminderMinute) { mutableStateOf(settings.reminderMinute.toString()) }
    var intervalsInput by remember(settings.reviewIntervalsCsv) { mutableStateOf(settings.reviewIntervalsCsv) }
    var masteryThresholdInput by remember(settings.masteryThreshold) { mutableStateOf(settings.masteryThreshold.toString()) }

    var importRawInput by remember {
        mutableStateOf(
            "headword,part_of_speech,ipa,vietnamese_meaning,english_definition,example_sentence,vietnamese_example_translation,collocations,topic_ids,difficulty_level,source_name\n" +
                "paradigm,noun,/ˈpær.ə.daɪm/,mô hình kiểu mẫu,a typical example or pattern of something,Renewable energy has created a new paradigm in global power generation.,Năng lượng tái tạo đã tạo ra một mô hình mới trong sản xuất điện toàn cầu.,paradigm shift|new paradigm,topic_1_environment|topic_13_science,Band 6.5–7.0+,Academic Word List (VUW)"
        )
    }
    var importAsCsv by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("settings_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Direct GEMINI_API_KEY & Model Selection Card (3.8 Flash / 4 Argon)
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Điền GEMINI_API_KEY & Chọn Model AI",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Text(
                            text = "Nhập khóa GEMINI_API_KEY của bạn trực tiếp tại đây (lưu cục bộ trên máy) và chọn model Gemini 3.8 Flash hoặc Gemini 4 Argon:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("GEMINI_API_KEY") },
                            placeholder = { Text("Dán API Key của bạn vào đây...") },
                            singleLine = true,
                            visualTransformation = if (showApiKeyPlainText) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showApiKeyPlainText = !showApiKeyPlainText }) {
                                    Icon(
                                        imageVector = if (showApiKeyPlainText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showApiKeyPlainText) "Ẩn API Key" else "Hiện API Key"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_api_key_input")
                        )

                        Text(
                            text = "Lựa chọn Model Gemini:",
                            style = MaterialTheme.typography.labelLarge
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LlmService.AVAILABLE_MODELS.forEach { (modelId, modelLabel) ->
                                FilterChip(
                                    selected = selectedModel == modelId,
                                    onClick = { selectedModel = modelId },
                                    label = { Text(modelLabel) },
                                    modifier = Modifier.testTag("model_chip_$modelId")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSaveAiConfig(apiKeyInput, selectedModel) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_save_api_key_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lưu API Key & Model")
                            }
                            if (apiKeyInput.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        apiKeyInput = ""
                                        onSaveAiConfig("", selectedModel)
                                    }
                                ) {
                                    Text("Xóa Key")
                                }
                            }
                        }

                        HorizontalDivider()

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = TealSecondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Trạng thái: ${llmStatus.statusDescription}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "• Bộ nhớ đệm SQLite LLM Cache: $llmCacheCount phản hồi đã lưu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Daily Goal & Theme
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Mục tiêu học hàng ngày & Giao diện", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = dailyGoalInput,
                                onValueChange = { dailyGoalInput = it },
                                label = { Text("Số từ / ngày") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_daily_goal_input")
                            )
                            Button(
                                onClick = {
                                    dailyGoalInput.toIntOrNull()?.let(onUpdateDailyGoal)
                                },
                                modifier = Modifier.testTag("settings_save_goal_button")
                            ) {
                                Text("Lưu")
                            }
                        }

                        Text("Chế độ giao diện (Sáng / Tối):", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("SYSTEM" to "Theo máy", "LIGHT" to "Sáng", "DARK" to "Tối").forEach { (mode, label) ->
                                FilterChip(
                                    selected = settings.darkModePref == mode,
                                    onClick = { onUpdateTheme(mode) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Local Study Notifications
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Nhắc nhở học tập cục bộ", style = MaterialTheme.typography.titleMedium)
                            }
                            Switch(
                                checked = settings.reminderEnabled,
                                onCheckedChange = { enabled ->
                                    val h = reminderHourInput.toIntOrNull() ?: settings.reminderHour
                                    val m = reminderMinuteInput.toIntOrNull() ?: settings.reminderMinute
                                    onUpdateReminder(enabled, h, m)
                                },
                                modifier = Modifier.testTag("settings_reminder_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = reminderHourInput,
                                onValueChange = { reminderHourInput = it },
                                label = { Text("Giờ (0-23)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = reminderMinuteInput,
                                onValueChange = { reminderMinuteInput = it },
                                label = { Text("Phút (0-59)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val h = reminderHourInput.toIntOrNull() ?: 20
                                    val m = reminderMinuteInput.toIntOrNull() ?: 0
                                    onUpdateReminder(settings.reminderEnabled, h, m)
                                }
                            ) {
                                Text("Đặt giờ")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                                OutlinedButton(
                                    onClick = {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cấp quyền thông báo")
                                }
                            }
                            FilledTonalButton(
                                onClick = onTriggerTestNotification,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_test_notification_button")
                            ) {
                                Text("Gửi thông báo thử")
                            }
                        }

                        Text(
                            text = "Lưu ý: Thời điểm hiển thị thông báo có thể chênh lệch nhẹ tùy theo chế độ tối ưu hóa pin (Battery Optimization) của Android. Ứng dụng vẫn hoạt động đầy đủ nếu bạn từ chối quyền thông báo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 4. Spaced Repetition Intervals & Time Simulation for Mandatory Review Gate
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Chu kỳ Spaced Repetition & Kiểm thử ngày ôn", style = MaterialTheme.typography.titleMedium)
                        }

                        OutlinedTextField(
                            value = intervalsInput,
                            onValueChange = { intervalsInput = it },
                            label = { Text("Chu kỳ ôn tập (ngày, cách nhau dấu phẩy)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = masteryThresholdInput,
                                onValueChange = { masteryThresholdInput = it },
                                label = { Text("Số lần đúng liên tiếp để Thành thạo") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    onUpdateIntervals(
                                        intervalsInput,
                                        masteryThresholdInput.toIntOrNull() ?: 5
                                    )
                                }
                            ) {
                                Text("Lưu chu kỳ")
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = "Giả lập thời gian kiểm thử Ôn tập bắt buộc (Hiện tại: $currentDateFormatted, lệch +${settings.simulatedDayOffset} ngày):",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onAdvanceSimulatedDays(1) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("simulate_plus_1_day_button")
                            ) {
                                Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+1 Ngày")
                            }
                            FilledTonalButton(
                                onClick = { onAdvanceSimulatedDays(3) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("simulate_plus_3_days_button")
                            ) {
                                Text("+3 Ngày")
                            }
                            OutlinedButton(
                                onClick = { onAdvanceSimulatedDays(0) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Hôm nay")
                            }
                        }
                    }
                }
            }

            // 5. CSV / JSON Vocabulary Import Pipeline
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Nhập dữ liệu từ vựng (CSV / JSON Pipeline)", style = MaterialTheme.typography.titleMedium)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = importAsCsv,
                                onClick = { importAsCsv = true },
                                label = { Text("Định dạng CSV") }
                            )
                            FilterChip(
                                selected = !importAsCsv,
                                onClick = { importAsCsv = false },
                                label = { Text("Định dạng JSON") }
                            )
                        }

                        OutlinedTextField(
                            value = importRawInput,
                            onValueChange = { importRawInput = it },
                            label = { Text("Dữ liệu CSV hoặc JSON cần kiểm tra & nhập") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("seed_import_input")
                        )

                        Button(
                            onClick = { onImportSeedData(importRawInput, importAsCsv) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("seed_import_button")
                        ) {
                            Text("Kiểm tra hợp lệ & Nhập vào SQLite")
                        }

                        importReport?.let { rep ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Kết quả kiểm tra: Hợp lệ ${rep.validEntries.size}/${rep.totalInputCount} bản ghi • Lỗi/Trùng lặp: ${rep.errors.size}",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    rep.errors.take(3).forEach { err ->
                                        Text(
                                            text = "• Dòng ${err.recordIndex} (${err.headword}): ${err.reason}",
                                            style = MaterialTheme.typography.bodySmall
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
