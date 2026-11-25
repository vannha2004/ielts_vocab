package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.IeltsDatabase
import com.example.data.local.LearningState
import com.example.data.local.QuizAttemptEntity
import com.example.data.local.ReviewHistoryEntity
import com.example.data.local.ReviewSessionEntity
import com.example.data.local.UserSettingsEntity
import com.example.data.remote.GroundedTopicInsight
import com.example.data.remote.LlmConfigStatus
import com.example.data.remote.LlmService
import com.example.data.remote.VocabularyEnrichmentPayload
import com.example.data.repository.IeltsRepository
import com.example.data.repository.MandatoryGateState
import com.example.data.repository.TopicStudyMode
import com.example.data.repository.TopicWithStats
import com.example.data.repository.VocabularyWithProgress
import com.example.data.seed.SeedDataImporter
import com.example.data.seed.SeedValidationReport
import com.example.domain.AnswerEvaluationResult
import com.example.domain.QuizEngine
import com.example.domain.QuizQuestionItem
import com.example.domain.QuizQuestionType
import com.example.domain.SpacedRepetitionEngine
import com.example.domain.SystemAppClock
import com.example.notifications.StudyReminderReceiver
import com.example.notifications.StudyReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class FlashcardSessionUiState(
    val isActive: Boolean = false,
    val isLoadingSession: Boolean = false,
    val isProcessingAnswer: Boolean = false,
    val session: ReviewSessionEntity? = null,
    val cards: List<VocabularyWithProgress> = emptyList(),
    val initialUniqueCardCount: Int = 0,
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val quickTipText: String? = null,
    val isLoadingTip: Boolean = false,
    val understoodCount: Int = 0,
    val notSureCount: Int = 0,
    val lastActionHint: String? = null,
    val isCompleted: Boolean = false
)

data class QuizSessionUiState(
    val isActive: Boolean = false,
    val sessionId: String = "",
    val topicId: String? = null,
    val quizMode: String = "TOPIC_QUIZ",
    val questions: List<QuizQuestionItem> = emptyList(),
    val currentIndex: Int = 0,
    val selectedOption: String = "",
    val typedInput: String = "",
    val currentEvaluation: AnswerEvaluationResult? = null,
    val correctCount: Int = 0,
    val mistakeVocabIds: List<String> = emptyList(),
    val startedAtMillis: Long = 0L,
    val isCompleted: Boolean = false,
    val isGeneratingAiQuestion: Boolean = false
)

data class VocabDetailAiUiState(
    val vocabId: String? = null,
    val isLoadingEnrichment: Boolean = false,
    val enrichment: VocabularyEnrichmentPayload? = null,
    val isLoadingComparison: Boolean = false,
    val comparisonResult: String? = null,
    val isLoadingGroundedUsage: Boolean = false,
    val groundedUsage: GroundedTopicInsight? = null,
    val errorMessage: String? = null
)

class IeltsViewModel(
    private val appContext: Context,
    private val repository: IeltsRepository,
    private val importer: SeedDataImporter,
    private val clock: SystemAppClock
) : ViewModel() {

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val _mandatoryGateState = MutableStateFlow(MandatoryGateState(isLocked = false))
    val mandatoryGateState: StateFlow<MandatoryGateState> = _mandatoryGateState.asStateFlow()

    private val _gateEvaluation = MutableStateFlow<AnswerEvaluationResult?>(null)
    val gateEvaluation: StateFlow<AnswerEvaluationResult?> = _gateEvaluation.asStateFlow()

    private val _lastGateUnlockSummary = MutableStateFlow<String?>(null)
    val lastGateUnlockSummary: StateFlow<String?> = _lastGateUnlockSummary.asStateFlow()

    // Library Filter State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTopicFilter = MutableStateFlow<String?>(null)
    val selectedTopicFilter: StateFlow<String?> = _selectedTopicFilter.asStateFlow()

    private val _selectedDifficultyFilter = MutableStateFlow<String?>(null)
    val selectedDifficultyFilter: StateFlow<String?> = _selectedDifficultyFilter.asStateFlow()

    private val _selectedStateFilter = MutableStateFlow<String?>(null)
    val selectedStateFilter: StateFlow<String?> = _selectedStateFilter.asStateFlow()

    // Session UI States
    private val _flashcardState = MutableStateFlow(FlashcardSessionUiState())
    val flashcardState: StateFlow<FlashcardSessionUiState> = _flashcardState.asStateFlow()

    private val _quizState = MutableStateFlow(QuizSessionUiState())
    val quizState: StateFlow<QuizSessionUiState> = _quizState.asStateFlow()

    private val _detailAiState = MutableStateFlow(VocabDetailAiUiState())
    val detailAiState: StateFlow<VocabDetailAiUiState> = _detailAiState.asStateFlow()

    private val _importReport = MutableStateFlow<SeedValidationReport?>(null)
    val importReport: StateFlow<SeedValidationReport?> = _importReport.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    // Reactive Flows from Repository
    val topicsWithStats: StateFlow<List<TopicWithStats>> = repository.observeTopicsWithStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVocabularyWithProgress: StateFlow<List<VocabularyWithProgress>> = repository.observeVocabularyWithProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredVocabulary: StateFlow<List<VocabularyWithProgress>> = combine(
        allVocabularyWithProgress,
        _searchQuery,
        _selectedTopicFilter,
        _selectedDifficultyFilter,
        _selectedStateFilter
    ) { allItems, query, topicId, difficulty, stateFilter ->
        val trimmedQuery = query.trim().lowercase()
        allItems.filter { item ->
            val matchesQuery = trimmedQuery.isEmpty() ||
                item.entry.headword.lowercase().contains(trimmedQuery) ||
                item.entry.vietnameseMeaning.lowercase().contains(trimmedQuery) ||
                item.entry.englishDefinition.lowercase().contains(trimmedQuery)
            val matchesTopic = topicId == null || topicId in item.topicIds
            val matchesDifficulty = difficulty == null || item.entry.difficultyLevel == difficulty
            val matchesState = stateFilter == null ||
                (stateFilter == "DUE" && item.isDue) ||
                item.progress.learningState == stateFilter
            matchesQuery && matchesTopic && matchesDifficulty && matchesState
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentReviewHistory: StateFlow<List<ReviewHistoryEntity>> = repository.observeRecentReviewHistory(120)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quizAttempts: StateFlow<List<QuizAttemptEntity>> = repository.observeQuizAttempts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userSettings: StateFlow<UserSettingsEntity> = combine(
        repository.observeUserSettings(),
        _isInitialLoading
    ) { settings, _ ->
        val resolved = settings ?: UserSettingsEntity(id = 1)
        clock.setDayOffset(resolved.simulatedDayOffset)
        resolved
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettingsEntity(id = 1))

    val activeFlashcardSession: StateFlow<ReviewSessionEntity?> = repository.observeActiveFlashcardSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val llmCacheCount: StateFlow<Int> = repository.observeLlmCacheCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            importer.ensureSeeded(appContext)
            val settings = userSettings.value
            clock.setDayOffset(settings.simulatedDayOffset)
            StudyReminderScheduler.scheduleDailyReminder(
                context = appContext,
                enabled = settings.reminderEnabled,
                hour = settings.reminderHour,
                minute = settings.reminderMinute
            )
            refreshMandatoryGateOnForeground()
            _isInitialLoading.value = false
        }
    }

    fun refreshMandatoryGateOnForeground() {
        viewModelScope.launch {
            val gate = repository.checkAndPrepareMandatoryGate()
            _mandatoryGateState.value = gate
            if (!gate.isLocked) {
                _gateEvaluation.value = null
            }
        }
    }

    fun evaluateMandatoryGateAnswer(userAnswer: String) {
        val gate = _mandatoryGateState.value
        val currentQuestion = gate.questions.getOrNull(gate.currentIndex) ?: return
        val evaluation = QuizEngine.evaluateAnswer(currentQuestion, userAnswer)
        _gateEvaluation.value = evaluation
    }

    fun advanceMandatoryGateQuestion() {
        val gate = _mandatoryGateState.value
        val session = gate.activeSession ?: return
        val currentQuestion = gate.questions.getOrNull(gate.currentIndex) ?: return
        val evaluation = _gateEvaluation.value ?: return

        viewModelScope.launch {
            val nextIdx = gate.currentIndex + 1
            val total = gate.questions.size
            val updatedGate = repository.submitMandatoryGateAnswer(
                session = session,
                vocabId = currentQuestion.vocabId,
                wasCorrect = evaluation.isCorrect,
                nextIndex = nextIdx,
                totalQuestions = total
            )
            _gateEvaluation.value = null
            _mandatoryGateState.value = updatedGate

            if (!updatedGate.isLocked) {
                val wrongCount = updatedGate.incorrectVocabIds.size
                val correctCount = (total - wrongCount).coerceAtLeast(0)
                _lastGateUnlockSummary.value =
                    "Đã hoàn thành bài kiểm tra ôn tập bắt buộc ($correctCount/$total câu đúng). " +
                        if (wrongCount > 0) "$wrongCount từ trả lời chưa đúng đã được ưu tiên lên lịch ôn sớm vào ngày mai."
                        else "Tất cả các từ đã được nâng bậc khoảng cách ôn tập!"
            }
        }
    }

    fun dismissGateUnlockSummary() {
        _lastGateUnlockSummary.value = null
    }

    // --- Library Filter Actions ---
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTopicFilter(topicId: String?) {
        _selectedTopicFilter.value = topicId
    }

    fun setDifficultyFilter(difficulty: String?) {
        _selectedDifficultyFilter.value = difficulty
    }

    fun setStateFilter(state: String?) {
        _selectedStateFilter.value = state
    }

    // --- Flashcard Session Actions ---
    fun startFlashcardSession(
        topicId: String?,
        studyMode: TopicStudyMode,
        forceNew: Boolean = false
    ) {
        // Immediately reset state to loading so stale isCompleted = true never flashes on screen
        _flashcardState.value = FlashcardSessionUiState(
            isActive = true,
            isLoadingSession = true,
            isCompleted = false
        )

        viewModelScope.launch {
            importer.ensureSeeded(appContext)
            val (session, cards) = repository.startOrResumeFlashcardSession(topicId, studyMode, forceNew)
            val startIndex = if (cards.isEmpty()) 0 else session.currentIndex.coerceIn(0, cards.size - 1)
            val completedIds = repository.decodeStringList(session.completedVocabIdsJson)
            val incorrectIds = repository.decodeStringList(session.incorrectVocabIdsJson)
            val uniqueInitialCount = cards.map { it.entry.id }.distinct().size

            _flashcardState.value = FlashcardSessionUiState(
                isActive = true,
                isLoadingSession = false,
                isProcessingAnswer = false,
                session = session,
                cards = cards,
                initialUniqueCardCount = uniqueInitialCount,
                currentIndex = startIndex,
                isFlipped = false,
                quickTipText = null,
                understoodCount = completedIds.size,
                notSureCount = incorrectIds.size,
                lastActionHint = null,
                isCompleted = cards.isNotEmpty() && session.currentIndex >= cards.size
            )
        }
    }

    fun toggleFlashcardFlip() {
        _flashcardState.update {
            if (it.isLoadingSession || it.isProcessingAnswer || it.isCompleted) it
            else it.copy(isFlipped = !it.isFlipped)
        }
    }

    fun requestFlashcardQuickTip() {
        val currentCard = _flashcardState.value.cards.getOrNull(_flashcardState.value.currentIndex) ?: return
        viewModelScope.launch {
            _flashcardState.update { it.copy(isLoadingTip = true) }
            val tip = repository.generateQuickMemoryTip(currentCard.entry.id)
            _flashcardState.update { it.copy(isLoadingTip = false, quickTipText = tip) }
        }
    }

    /**
     * Records the user's Flashcard self-assessment:
     * - Prevents concurrent/double-tap race conditions via [isProcessingAnswer].
     * - When the user selects "Chưa chắc" (understood = false), the card is re-queued to the end
     *   of the current session deck so the session never falsely claims all words are finished
     *   while the user still has unlearned ("Chưa chắc") cards.
     */
    fun answerCurrentFlashcard(understood: Boolean) {
        val snapshot = _flashcardState.value
        if (snapshot.isLoadingSession || snapshot.isProcessingAnswer || snapshot.isCompleted) return
        val session = snapshot.session ?: return
        val currentCard = snapshot.cards.getOrNull(snapshot.currentIndex) ?: return

        // Synchronously lock processing so double-taps or simultaneous swipe+tap cannot skip cards
        _flashcardState.update { it.copy(isProcessingAnswer = true) }

        viewModelScope.launch {
            val nextIdx = snapshot.currentIndex + 1
            // If user clicked "Chưa chắc" (Not sure), re-append this card to the end of the session deck
            // so they can review it again before the session finishes (capped at 50 cards per session).
            val updatedCards = if (!understood && snapshot.cards.size < 50) {
                snapshot.cards + currentCard
            } else {
                snapshot.cards
            }
            val updatedVocabIds = updatedCards.map { it.entry.id }

            val updatedSession = repository.recordFlashcardResponse(
                session = session,
                vocabId = currentCard.entry.id,
                understood = understood,
                nextIndex = nextIdx,
                totalCards = updatedCards.size,
                updatedVocabIds = updatedVocabIds
            )

            val completed = nextIdx >= updatedCards.size
            val hint = if (!understood && !completed) {
                "Đã xếp \"${currentCard.entry.headword}\" xuống cuối phiên học để bạn ôn lại cho đến khi nắm vững."
            } else {
                null
            }

            _flashcardState.update {
                it.copy(
                    isProcessingAnswer = false,
                    session = updatedSession,
                    cards = updatedCards,
                    currentIndex = nextIdx,
                    isFlipped = false,
                    quickTipText = null,
                    understoodCount = if (understood) it.understoodCount + 1 else it.understoodCount,
                    notSureCount = if (!understood) it.notSureCount + 1 else it.notSureCount,
                    lastActionHint = hint,
                    isCompleted = completed
                )
            }
        }
    }

    fun exitFlashcardSession() {
        _flashcardState.update { it.copy(isActive = false, isProcessingAnswer = false) }
    }

    // --- Quiz Session Actions ---
    fun startQuizSession(
        topicId: String?,
        studyMode: TopicStudyMode,
        preferredType: QuizQuestionType? = null
    ) {
        viewModelScope.launch {
            importer.ensureSeeded(appContext)
            val targetWithProgress = repository.getVocabularyForStudyMode(topicId, studyMode).take(15)
            val allPool = repository.getVocabularyForStudyMode(null, TopicStudyMode.STUDY_ALL).map { it.entry }
            val questions = QuizEngine.generateQuestionsForVocabulary(
                targetVocabList = targetWithProgress.map { it.entry },
                distractorPool = allPool.ifEmpty { targetWithProgress.map { it.entry } },
                preferredType = preferredType
            )
            _quizState.value = QuizSessionUiState(
                isActive = true,
                sessionId = "quiz_session_${UUID.randomUUID()}",
                topicId = topicId,
                quizMode = if (topicId != null) "TOPIC_QUIZ" else "REVIEW_QUIZ",
                questions = questions,
                currentIndex = 0,
                selectedOption = "",
                typedInput = "",
                currentEvaluation = null,
                correctCount = 0,
                mistakeVocabIds = emptyList(),
                startedAtMillis = clock.nowMillis(),
                isCompleted = false
            )
        }
    }

    fun regenerateCurrentQuestionWithAi() {
        val state = _quizState.value
        val currentQ = state.questions.getOrNull(state.currentIndex) ?: return
        val targetEntry = allVocabularyWithProgress.value.find { it.entry.id == currentQ.vocabId }?.entry ?: return
        val pool = allVocabularyWithProgress.value.map { it.entry }

        viewModelScope.launch {
            _quizState.update { it.copy(isGeneratingAiQuestion = true) }
            val aiQuestion = repository.generateLlmValidatedQuiz(targetEntry, pool, currentQ.questionType)
            val updatedList = state.questions.toMutableList().apply {
                set(state.currentIndex, aiQuestion)
            }
            _quizState.update {
                it.copy(
                    questions = updatedList,
                    isGeneratingAiQuestion = false,
                    selectedOption = "",
                    typedInput = "",
                    currentEvaluation = null
                )
            }
        }
    }

    fun submitQuizAnswer(answer: String) {
        val state = _quizState.value
        val currentQ = state.questions.getOrNull(state.currentIndex) ?: return
        if (state.currentEvaluation != null) return

        val evaluation = QuizEngine.evaluateAnswer(currentQ, answer)
        viewModelScope.launch {
            repository.recordQuizQuestionResult(
                vocabId = currentQ.vocabId,
                wasCorrect = evaluation.isCorrect,
                questionType = currentQ.questionType,
                sessionId = state.sessionId
            )
            _quizState.update {
                it.copy(
                    currentEvaluation = evaluation,
                    correctCount = it.correctCount + if (evaluation.isCorrect) 1 else 0,
                    mistakeVocabIds = if (evaluation.isCorrect) it.mistakeVocabIds else (it.mistakeVocabIds + currentQ.vocabId).distinct()
                )
            }
        }
    }

    fun nextQuizQuestion() {
        val state = _quizState.value
        val nextIdx = state.currentIndex + 1
        val isDone = nextIdx >= state.questions.size
        if (isDone) {
            viewModelScope.launch {
                repository.saveCompletedQuizAttempt(
                    topicId = state.topicId,
                    quizMode = state.quizMode,
                    totalQuestions = state.questions.size,
                    correctAnswers = state.correctCount,
                    mistakeVocabIds = state.mistakeVocabIds,
                    startedAt = state.startedAtMillis
                )
            }
        }
        _quizState.update {
            it.copy(
                currentIndex = nextIdx,
                selectedOption = "",
                typedInput = "",
                currentEvaluation = null,
                isCompleted = isDone
            )
        }
    }

    fun exitQuizSession() {
        _quizState.update { it.copy(isActive = false) }
    }

    // --- Vocabulary Detail AI Actions ---
    fun resetDetailAiState(vocabId: String) {
        if (_detailAiState.value.vocabId != vocabId) {
            _detailAiState.value = VocabDetailAiUiState(vocabId = vocabId)
        }
    }

    fun requestAiEnrichment(vocabId: String) {
        viewModelScope.launch {
            _detailAiState.update { it.copy(vocabId = vocabId, isLoadingEnrichment = true, errorMessage = null) }
            val result = repository.enrichVocabularyWithLlm(vocabId, applyToDatabaseIfUnverified = true)
            _detailAiState.update {
                it.copy(
                    isLoadingEnrichment = false,
                    enrichment = result.getOrNull(),
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun requestWordComparisonHighThinking(vocabId: String, comparisonQuery: String) {
        viewModelScope.launch {
            _detailAiState.update { it.copy(vocabId = vocabId, isLoadingComparison = true, errorMessage = null) }
            val text = repository.compareWordsHighThinking(vocabId, comparisonQuery)
            _detailAiState.update {
                it.copy(
                    isLoadingComparison = false,
                    comparisonResult = text
                )
            }
        }
    }

    fun requestSearchGroundedUsage(vocabId: String) {
        viewModelScope.launch {
            _detailAiState.update { it.copy(vocabId = vocabId, isLoadingGroundedUsage = true, errorMessage = null) }
            val grounded = repository.fetchSearchGroundedUsage(vocabId)
            _detailAiState.update {
                it.copy(
                    isLoadingGroundedUsage = false,
                    groundedUsage = grounded
                )
            }
        }
    }

    // --- Settings, Notifications, Time Simulation & Seed Import ---
    fun updateDailyGoal(goal: Int) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(dailyGoal = goal.coerceIn(5, 100)) }
            _statusBannerMessage.value = "Đã cập nhật mục tiêu học hàng ngày: ${goal.coerceIn(5, 100)} từ."
        }
    }

    fun updateReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            val updated = repository.updateSettings {
                it.copy(
                    reminderEnabled = enabled,
                    reminderHour = hour.coerceIn(0, 23),
                    reminderMinute = minute.coerceIn(0, 59)
                )
            }
            StudyReminderScheduler.scheduleDailyReminder(
                context = appContext,
                enabled = updated.reminderEnabled,
                hour = updated.reminderHour,
                minute = updated.reminderMinute
            )
            _statusBannerMessage.value = if (enabled) {
                "Đã đặt lịch nhắc nhở học lúc %02d:%02d hàng ngày.".format(updated.reminderHour, updated.reminderMinute)
            } else {
                "Đã tắt thông báo nhắc nhở học hàng ngày."
            }
        }
    }

    fun triggerTestNotificationNow() {
        val dueCount = allVocabularyWithProgress.value.count { it.isDue }
        val sent = StudyReminderReceiver.showReminderNotification(
            context = appContext,
            dueCount = dueCount,
            dailyGoal = userSettings.value.dailyGoal
        )
        _statusBannerMessage.value = if (sent) {
            "Đã gửi thông báo nhắc học thử nghiệm ($dueCount từ đến hạn)."
        } else {
            "Chưa cấp quyền thông báo (POST_NOTIFICATIONS). Ứng dụng vẫn hoạt động bình thường."
        }
    }

    fun updateSpacedRepetitionSettings(intervalsCsv: String, masteryThreshold: Int) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(
                    reviewIntervalsCsv = intervalsCsv.ifBlank { "1,3,7,14,30" },
                    masteryThreshold = masteryThreshold.coerceIn(2, 10)
                )
            }
            _statusBannerMessage.value = "Đã lưu cấu hình khoảng cách Spaced Repetition ($intervalsCsv ngày)."
        }
    }

    fun updateThemePreference(pref: String) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(darkModePref = pref) }
        }
    }

    fun advanceSimulatedClockDays(deltaDays: Int) {
        viewModelScope.launch {
            val updated = repository.updateSettings {
                val nextOffset = if (deltaDays == 0) 0 else (it.simulatedDayOffset + deltaDays).coerceAtLeast(0)
                it.copy(simulatedDayOffset = nextOffset)
            }
            clock.setDayOffset(updated.simulatedDayOffset)
            refreshMandatoryGateOnForeground()
            _statusBannerMessage.value = if (updated.simulatedDayOffset == 0) {
                "Đã đưa đồng hồ học tập về ngày thực tế hôm nay (${clock.formatLocalDate(clock.nowMillis())})."
            } else {
                "Đã tua nhanh đồng hồ +${updated.simulatedDayOffset} ngày (${clock.formatLocalDate(clock.nowMillis())}) để kiểm tra Ôn tập bắt buộc."
            }
        }
    }

    fun importVocabularyData(rawText: String, isCsv: Boolean) {
        viewModelScope.launch {
            val report = if (isCsv) {
                importer.importFromCsv(rawText)
            } else {
                importer.importFromJson(rawText)
            }
            _importReport.value = report
            _statusBannerMessage.value =
                "Đã nhập thành công ${report.validEntries.size}/${report.totalInputCount} từ vựng mới (${report.errors.size} lỗi/trùng lặp bị từ chối)."
        }
    }

    fun saveAiConfiguration(customApiKey: String, preferredModel: String) {
        viewModelScope.launch {
            val cleanKey = customApiKey.trim()
            val cleanModel = preferredModel.trim().ifBlank { LlmService.MODEL_GENERAL_FLASH }
            repository.updateSettings {
                it.copy(
                    customApiKey = cleanKey,
                    preferredModel = cleanModel
                )
            }
            _statusBannerMessage.value = if (cleanKey.isNotEmpty()) {
                "Đã lưu GEMINI_API_KEY và chọn model: $cleanModel."
            } else {
                "Đã xóa khóa API trong ứng dụng. Model mặc định: $cleanModel."
            }
        }
    }

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    fun getLlmStatus(): LlmConfigStatus = repository.getLlmConfigStatus(userSettings.value)
    fun getCurrentLocalDateFormatted(): String = clock.formatLocalDate(clock.nowMillis())
}

class IeltsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appContext = context.applicationContext
        val db = IeltsDatabase.getInstance(appContext)
        val dao = db.ieltsDao()
        val clock = SystemAppClock()
        val srEngine = SpacedRepetitionEngine(clock)
        val llmService = LlmService(dao, clock)
        val repository = IeltsRepository(dao, srEngine, llmService, clock)
        val importer = SeedDataImporter(dao, clock)
        @Suppress("UNCHECKED_CAST")
        return IeltsViewModel(appContext, repository, importer, clock) as T
    }
}
