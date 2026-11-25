package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.data.repository.MandatoryGateState
import com.example.domain.AnswerEvaluationResult
import com.example.domain.QuizQuestionItem
import com.example.domain.QuizQuestionType
import com.example.ui.FlashcardSessionUiState
import com.example.ui.QuizSessionUiState
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.StatusDangerColor
import com.example.ui.theme.StatusMasteredColor
import com.example.ui.theme.TealSecondary
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlashcardSessionScreen(
    state: FlashcardSessionUiState,
    onFlipCard: () -> Unit,
    onAnswerCard: (Boolean) -> Unit,
    onRequestQuickTip: () -> Unit,
    onExitSession: () -> Unit
) {
    BackHandler(onBack = onExitSession)

    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 130.dp.toPx() } }
    val maxDragPx = remember(density) { with(density) { 200.dp.toPx() } }

    val total = state.cards.size
    val currentCard = state.cards.getOrNull(state.currentIndex)

    // Keyed by both session ID and currentIndex so each card step has an isolated gesture lock
    var dragOffsetX by remember(state.session?.id, state.currentIndex) { mutableFloatStateOf(0f) }
    var gestureConsumedForCard by remember(state.session?.id, state.currentIndex) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (state.isLoadingSession) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(16.dp)
                .testTag("flashcard_session_screen"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Header with Exit & Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onExitSession,
                        modifier = Modifier.testTag("flashcard_exit_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tạm dừng và thoát")
                    }
                    Text(
                        text = if (state.isCompleted || currentCard == null) {
                            "Hoàn thành phiên Flashcard"
                        } else {
                            "Thẻ ${state.currentIndex + 1} / $total"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("flashcard_progress_text")
                    )
                }

                Text(
                    text = "Đã nắm: ${state.understoodCount}/${state.initialUniqueCardCount.coerceAtLeast(1)} • Ôn lại: ${state.notSureCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (total > 0 && !state.isCompleted) {
                LinearProgressIndicator(
                    progress = { (state.currentIndex + 1).toFloat() / total.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = TealSecondary
                )
            }

            state.lastActionHint?.let { hint ->
                if (!state.isCompleted) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (state.isCompleted && total > 0) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Đã hoàn thành phiên học Flashcard!",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Bạn đã nắm vững toàn bộ ${state.initialUniqueCardCount} từ vựng trong phiên này (Số lượt đánh dấu Chưa chắc để ôn lại trong phiên: ${state.notSureCount}).\nLịch ôn tập ngắt quãng (Spaced Repetition) đã được lưu tự động.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onExitSession,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("flashcard_finish_button")
                        ) {
                            Text("Quay về màn hình chính")
                        }
                    }
                }
            } else if (currentCard == null) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Chưa có từ vựng trong bộ lọc này",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Hãy quay lại và chọn chế độ Học tất cả hoặc chủ đề khác để bắt đầu học.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onExitSession,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("flashcard_finish_button")
                        ) {
                            Text("Quay về màn hình chính")
                        }
                    }
                }
            } else {
                // Interactive Flashcard with Tap-to-Flip and Density-Aware Single-Card Swipe Protection
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
                        .pointerInput(state.session?.id, state.currentIndex, state.isProcessingAnswer) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (!gestureConsumedForCard && !state.isProcessingAnswer) {
                                        if (dragOffsetX > swipeThresholdPx) {
                                            gestureConsumedForCard = true
                                            onAnswerCard(true)
                                        } else if (dragOffsetX < -swipeThresholdPx) {
                                            gestureConsumedForCard = true
                                            onAnswerCard(false)
                                        }
                                    }
                                    dragOffsetX = 0f
                                },
                                onDragCancel = { dragOffsetX = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    if (!gestureConsumedForCard && !state.isProcessingAnswer) {
                                        change.consume()
                                        dragOffsetX = (dragOffsetX + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                                    }
                                }
                            )
                        }
                        .clickable(enabled = !state.isProcessingAnswer) { onFlipCard() }
                        .semantics {
                            contentDescription = "Thẻ từ vựng ${currentCard.entry.headword}. Chạm để lật thẻ."
                        }
                        .testTag("flashcard_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (!state.isFlipped) Arrangement.Center else Arrangement.Top
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = currentCard.entry.difficultyLevel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                            if (state.currentIndex >= state.initialUniqueCardCount) {
                                Surface(
                                    color = AmberTertiary.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "Ôn lại thẻ Chưa chắc",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AmberTertiary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = currentCard.entry.headword,
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("flashcard_headword")
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "(${currentCard.entry.partOfSpeech}) ${currentCard.entry.ipa ?: ""}".trim(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!state.isFlipped) {
                            Spacer(modifier = Modifier.height(28.dp))
                            FilledTonalButton(
                                onClick = onFlipCard,
                                modifier = Modifier.testTag("flashcard_flip_button")
                            ) {
                                Icon(
                                    Icons.Default.Flip,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Chạm để lật xem nghĩa & ví dụ")
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Nghĩa tiếng Việt:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = currentCard.entry.vietnameseMeaning,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("flashcard_revealed_meaning")
                                )

                                Text(
                                    text = "Định nghĩa: ${currentCard.entry.englishDefinition}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            text = "Ví dụ câu IELTS:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "\"${currentCard.entry.exampleSentence}\"",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontStyle = FontStyle.Italic
                                        )
                                        Text(
                                            text = "→ ${currentCard.entry.vietnameseExampleTranslation}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (currentCard.collocations.isNotEmpty()) {
                                    Text(
                                        text = "Collocations trọng tâm:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        currentCard.collocations.forEach { col ->
                                            AssistChip(onClick = {}, label = { Text(col) })
                                        }
                                    }
                                }

                                if (currentCard.wordFamily.isNotEmpty()) {
                                    Text(
                                        text = "Word family: ${currentCard.wordFamily.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                TextButton(
                                    onClick = onRequestQuickTip,
                                    enabled = !state.isLoadingTip,
                                    modifier = Modifier.testTag("flashcard_quick_tip_button")
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (state.isLoadingTip) "Đang tạo mẹo nhớ nhanh..." else "Mẹo nhớ nhanh (Gemini AI)")
                                }

                                state.quickTipText?.let { tip ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = tip,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Nếu chọn \"Chưa chắc\", thẻ sẽ được đưa xuống cuối phiên để bạn học lại cho đến khi nắm rõ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Visible accessible buttons equivalent to Swipe Left ("Not sure") & Swipe Right ("Understand")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!gestureConsumedForCard && !state.isProcessingAnswer) {
                                gestureConsumedForCard = true
                                onAnswerCard(false)
                            }
                        },
                        enabled = !state.isProcessingAnswer,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .semantics { contentDescription = "Chưa chắc chắn, xếp lại cuối phiên để ôn tiếp" }
                            .testTag("flashcard_not_sure_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = StatusDangerColor)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chưa chắc (Học lại)")
                    }

                    Button(
                        onClick = {
                            if (!gestureConsumedForCard && !state.isProcessingAnswer) {
                                gestureConsumedForCard = true
                                onAnswerCard(true)
                            }
                        },
                        enabled = !state.isProcessingAnswer,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusMasteredColor),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .semantics { contentDescription = "Hiểu rõ từ này, chuyển sang thẻ tiếp theo" }
                            .testTag("flashcard_understand_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hiểu rõ (Qua thẻ)")
                    }
                }
            }
        }
    }
}

@Composable
fun QuizSessionScreen(
    state: QuizSessionUiState,
    onSubmitAnswer: (String) -> Unit,
    onNextQuestion: () -> Unit,
    onRegenerateWithAi: () -> Unit,
    onExitQuiz: () -> Unit
) {
    BackHandler(onBack = onExitQuiz)

    val total = state.questions.size
    val currentQuestion = state.questions.getOrNull(state.currentIndex)
    var userInput by remember(state.currentIndex, currentQuestion?.id) { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .testTag("quiz_session_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onExitQuiz,
                        modifier = Modifier.testTag("quiz_exit_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Thoát bài kiểm tra")
                    }
                    Text(
                        text = if (state.isCompleted || currentQuestion == null) {
                            "Kết quả bài kiểm tra"
                        } else {
                            "Câu ${state.currentIndex + 1} / $total"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Text(
                    text = "Đúng: ${state.correctCount}/$total",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (state.isCompleted || currentQuestion == null) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val accuracy = if (total > 0) (state.correctCount * 100) / total else 100
                        Text(
                            text = "Hoàn thành bài kiểm tra!",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Điểm số: ${state.correctCount} / $total câu đúng ($accuracy%)",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (state.mistakeVocabIds.isEmpty()) {
                                "Xuất sắc! Toàn bộ từ vựng trong bài kiểm tra đã được nâng bậc lịch ôn tập."
                            } else {
                                "${state.mistakeVocabIds.size} từ chưa chính xác đã được giảm khoảng cách ôn tập để bạn ôn lại sớm hơn."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onExitQuiz,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("quiz_finish_button")
                        ) {
                            Text("Hoàn tất")
                        }
                    }
                }
            } else {
                LinearProgressIndicator(
                    progress = { (state.currentIndex + 1).toFloat() / total.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                QuizQuestionContent(
                    question = currentQuestion,
                    userInput = userInput,
                    onUserInputChange = { userInput = it },
                    evaluation = state.currentEvaluation,
                    isGeneratingAi = state.isGeneratingAiQuestion,
                    onRegenerateWithAi = onRegenerateWithAi,
                    onSubmitAnswer = { onSubmitAnswer(userInput) },
                    onNextQuestion = onNextQuestion
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Critical Feature: Dedicated, Blocking Mandatory Review Gate Screen.
 * Enforces completion of all due vocabulary reviews before unlocking normal navigation.
 */
@Composable
fun MandatoryReviewGateScreen(
    gateState: MandatoryGateState,
    evaluation: AnswerEvaluationResult?,
    onEvaluateAnswer: (String) -> Unit,
    onAdvanceQuestion: () -> Unit
) {
    BackHandler(enabled = true) {
        // Intentionally blocks in-app back navigation until the mandatory review is completed
    }

    val total = gateState.questions.size
    val currentQuestion = gateState.questions.getOrNull(gateState.currentIndex)
    var userInput by remember(gateState.currentIndex, currentQuestion?.id) { mutableStateOf("") }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("mandatory_review_gate_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Cổng ôn tập bắt buộc",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ôn tập bắt buộc (${gateState.currentIndex + 1} / $total từ đến hạn)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Bạn có $total từ đến hạn ôn hôm nay. Hoàn thành bài kiểm tra này để mở khóa toàn bộ ứng dụng (không yêu cầu đúng 100%; từ sai sẽ được lên lịch ôn lại sớm).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { (gateState.currentIndex + 1).toFloat() / total.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = AmberTertiary
                    )
                }

                if (currentQuestion != null) {
                    QuizQuestionContent(
                        question = currentQuestion,
                        userInput = userInput,
                        onUserInputChange = { userInput = it },
                        evaluation = evaluation,
                        isGeneratingAi = false,
                        onRegenerateWithAi = null,
                        onSubmitAnswer = { onEvaluateAnswer(userInput) },
                        onNextQuestion = onAdvanceQuestion
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QuizQuestionContent(
    question: QuizQuestionItem,
    userInput: String,
    onUserInputChange: (String) -> Unit,
    evaluation: AnswerEvaluationResult?,
    isGeneratingAi: Boolean,
    onRegenerateWithAi: (() -> Unit)?,
    onSubmitAnswer: () -> Unit,
    onNextQuestion: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = question.questionType.vietnameseLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                if (onRegenerateWithAi != null && evaluation == null) {
                    TextButton(
                        onClick = onRegenerateWithAi,
                        enabled = !isGeneratingAi,
                        modifier = Modifier.testTag("quiz_ai_regenerate_button")
                    ) {
                        if (isGeneratingAi) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Biến thể AI")
                    }
                }
            }

            Text(
                text = question.prompt,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!question.subPrompt.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = question.subPrompt,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(14.dp)
                            .testTag("quiz_question_subprompt")
                    )
                }
            }

            if (question.questionType == QuizQuestionType.TYPED_ANSWER) {
                val bringIntoViewRequester = remember { BringIntoViewRequester() }
                val coroutineScope = rememberCoroutineScope()
                val density = LocalDensity.current
                val imeBottom = WindowInsets.ime.getBottom(density)
                var isInputFocused by remember(question.id) { mutableStateOf(false) }

                // Re-scroll both the input box and submit button into view once the soft keyboard opens or resizes
                LaunchedEffect(isInputFocused, imeBottom, question.id) {
                    if (isInputFocused) {
                        delay(80)
                        bringIntoViewRequester.bringIntoView()
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = onUserInputChange,
                        enabled = evaluation == null,
                        label = { Text("Nhập từ vựng tiếng Anh...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (userInput.isNotBlank() && evaluation == null) {
                                    onSubmitAnswer()
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                isInputFocused = focusState.isFocused
                                if (focusState.isFocused) {
                                    coroutineScope.launch {
                                        bringIntoViewRequester.bringIntoView()
                                    }
                                }
                            }
                            .testTag("quiz_typed_input")
                    )

                    if (evaluation == null) {
                        Button(
                            onClick = onSubmitAnswer,
                            enabled = userInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("quiz_submit_answer_button")
                        ) {
                            Text("Kiểm tra đáp án")
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    question.options.forEachIndexed { idx, option ->
                        val isSelected = userInput == option
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = evaluation == null) {
                                    onUserInputChange(option)
                                }
                                .testTag("quiz_option_$idx"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = if (evaluation == null) ({ onUserInputChange(option) }) else null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                if (evaluation == null) {
                    Button(
                        onClick = onSubmitAnswer,
                        enabled = userInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("quiz_submit_answer_button")
                    ) {
                        Text("Kiểm tra đáp án")
                    }
                }
            }

            if (evaluation != null) {
                AnimatedVisibility(visible = true) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (evaluation.isCorrect) {
                                StatusMasteredColor.copy(alpha = 0.14f)
                            } else {
                                StatusDangerColor.copy(alpha = 0.14f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quiz_feedback_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = evaluation.feedbackMessage,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (evaluation.isCorrect) StatusMasteredColor else StatusDangerColor
                            )
                            Text(
                                text = evaluation.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Button(
                    onClick = onNextQuestion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("quiz_next_question_button")
                ) {
                    Text("Tiếp tục")
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}
