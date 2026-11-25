package com.example.data.repository

import com.example.data.local.IeltsDao
import com.example.data.local.LearningState
import com.example.data.local.QuizAttemptEntity
import com.example.data.local.ReviewHistoryEntity
import com.example.data.local.ReviewSessionEntity
import com.example.data.local.TopicEntity
import com.example.data.local.UserSettingsEntity
import com.example.data.local.UserVocabularyProgressEntity
import com.example.data.local.VocabularyEntity
import com.example.data.local.VocabularyTopicCrossRef
import com.example.data.remote.GroundedTopicInsight
import com.example.data.remote.LlmConfigStatus
import com.example.data.remote.LlmService
import com.example.data.remote.VocabularyEnrichmentPayload
import com.example.domain.AppClock
import com.example.domain.QuizEngine
import com.example.domain.QuizQuestionItem
import com.example.domain.QuizQuestionType
import com.example.domain.SpacedRepetitionConfig
import com.example.domain.SpacedRepetitionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

enum class TopicStudyMode(val vietnameseTitle: String, val subtitle: String) {
    STUDY_ALL("Học tất cả (Study All)", "Học toàn bộ từ vựng trong chủ đề này"),
    CONTINUE_LEARNING("Tiếp tục học (Continue Learning)", "Học các từ mới và các từ chưa thành thạo"),
    REVIEW_DUE_TODAY("Ôn từ đến hạn (Review Due Today)", "Tập trung vào các từ đã đến hạn ôn tập hôm nay"),
    REVIEW_WEAK_WORDS("Ôn từ hay sai (Review Weak Words)", "Tập trung vào các từ bạn từng trả lời sai hoặc chưa chắc chắn")
}

data class VocabularyWithProgress(
    val entry: VocabularyEntity,
    val progress: UserVocabularyProgressEntity,
    val topicIds: List<String>,
    val isDue: Boolean,
    val collocations: List<String>,
    val wordFamily: List<String>,
    val synonyms: List<String>,
    val antonyms: List<String>
)

data class TopicWithStats(
    val topic: TopicEntity,
    val totalCount: Int,
    val newCount: Int,
    val learningCount: Int,
    val reviewCount: Int,
    val masteredCount: Int,
    val dueCount: Int,
    val weakCount: Int
)

data class MandatoryGateState(
    val isLocked: Boolean,
    val activeSession: ReviewSessionEntity? = null,
    val questions: List<QuizQuestionItem> = emptyList(),
    val currentIndex: Int = 0,
    val completedVocabIds: List<String> = emptyList(),
    val incorrectVocabIds: List<String> = emptyList()
)

class IeltsRepository(
    private val dao: IeltsDao,
    private val srEngine: SpacedRepetitionEngine,
    private val llmService: LlmService,
    private val clock: AppClock
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeTopics(): Flow<List<TopicEntity>> = dao.observeTopics()
    fun observeAllVocabulary(): Flow<List<VocabularyEntity>> = dao.observeAllVocabulary()
    fun observeAllProgress(): Flow<List<UserVocabularyProgressEntity>> = dao.observeAllProgress()
    fun observeAllCrossRefs(): Flow<List<VocabularyTopicCrossRef>> = dao.observeAllCrossRefs()
    fun observeRecentReviewHistory(limit: Int = 100): Flow<List<ReviewHistoryEntity>> = dao.observeRecentReviewHistory(limit)
    fun observeQuizAttempts(): Flow<List<QuizAttemptEntity>> = dao.observeQuizAttempts()
    fun observeUserSettings(): Flow<UserSettingsEntity?> = dao.observeUserSettings()
    fun observeActiveMandatorySession(): Flow<ReviewSessionEntity?> = dao.observeActiveMandatorySession()
    fun observeActiveFlashcardSession(): Flow<ReviewSessionEntity?> =
        dao.observeActiveFlashcardSession().combine(dao.observeAllVocabulary()) { session, allVocab ->
            if (session == null || allVocab.isEmpty()) {
                null
            } else {
                val ids = decodeStringList(session.vocabIdsJson)
                if (ids.isEmpty() || session.currentIndex !in 0 until ids.size) {
                    null
                } else {
                    session
                }
            }
        }
    fun observeLlmCacheCount(): Flow<Int> = dao.observeLlmCacheCount()

    fun observeVocabularyWithProgress(): Flow<List<VocabularyWithProgress>> {
        return combine(
            dao.observeAllVocabulary(),
            dao.observeAllProgress(),
            dao.observeAllCrossRefs()
        ) { vocabList, progressList, crossRefs ->
            val progressMap = progressList.associateBy { it.vocabId }
            val topicsByVocab = crossRefs.groupBy({ it.vocabId }, { it.topicId })
            vocabList.map { entry ->
                val prog = progressMap[entry.id] ?: UserVocabularyProgressEntity(vocabId = entry.id)
                mapToVocabularyWithProgress(entry, prog, topicsByVocab[entry.id].orEmpty())
            }
        }
    }

    fun observeTopicsWithStats(): Flow<List<TopicWithStats>> {
        return combine(
            dao.observeTopics(),
            observeVocabularyWithProgress()
        ) { topics, vocabWithProgress ->
            topics.map { topic ->
                val entriesForTopic = vocabWithProgress.filter { topic.id in it.topicIds }
                val total = entriesForTopic.size
                // Count words as "New / Unlearned" if still NEW or if only marked "Not sure" (consecutiveSuccesses == 0)
                val newCount = entriesForTopic.count {
                    it.progress.learningState == LearningState.NEW.name ||
                        (it.progress.learningState == LearningState.LEARNING.name && it.progress.consecutiveSuccesses == 0)
                }
                // Only count words as actively learned/in-progress when the user has at least 1 successful understanding/review
                val learningCount = entriesForTopic.count {
                    (it.progress.learningState == LearningState.LEARNING.name && it.progress.consecutiveSuccesses > 0) ||
                        it.progress.learningState == LearningState.REVIEW.name
                }
                val reviewCount = entriesForTopic.count { it.progress.learningState == LearningState.REVIEW.name }
                val masteredCount = entriesForTopic.count { it.progress.learningState == LearningState.MASTERED.name }
                val dueCount = entriesForTopic.count { it.isDue }
                val weakCount = entriesForTopic.count {
                    it.progress.wrongCount > 0 ||
                        (it.progress.learningState == LearningState.LEARNING.name && it.progress.consecutiveSuccesses == 0)
                }
                TopicWithStats(
                    topic = topic,
                    totalCount = total,
                    newCount = newCount,
                    learningCount = learningCount,
                    reviewCount = reviewCount,
                    masteredCount = masteredCount,
                    dueCount = dueCount,
                    weakCount = weakCount
                )
            }
        }
    }

    suspend fun getVocabularyWithProgressById(vocabId: String): VocabularyWithProgress? {
        val entry = dao.getVocabularyById(vocabId) ?: return null
        val prog = dao.getProgressForVocab(vocabId) ?: UserVocabularyProgressEntity(vocabId = vocabId)
        val topicIds = dao.getTopicIdsForVocab(vocabId)
        return mapToVocabularyWithProgress(entry, prog, topicIds)
    }

    private fun mapToVocabularyWithProgress(
        entry: VocabularyEntity,
        progress: UserVocabularyProgressEntity,
        topicIds: List<String>
    ): VocabularyWithProgress {
        return VocabularyWithProgress(
            entry = entry,
            progress = progress,
            topicIds = topicIds,
            isDue = srEngine.isDue(progress),
            collocations = decodeStringList(entry.collocationsJson),
            wordFamily = decodeStringList(entry.wordFamilyJson),
            synonyms = decodeStringList(entry.synonymsJson),
            antonyms = decodeStringList(entry.antonymsJson)
        )
    }

    fun decodeStringList(rawJson: String?): List<String> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(rawJson) }.getOrDefault(emptyList())
    }

    /**
     * Selects vocabulary for a chosen TopicStudyMode without duplicating records.
     * In CONTINUE_LEARNING mode, prioritizes NEW and unlearned/weak words first, followed by LEARNING/REVIEW words.
     */
    suspend fun getVocabularyForStudyMode(
        topicId: String?,
        mode: TopicStudyMode
    ): List<VocabularyWithProgress> {
        val allVocab = dao.getAllVocabulary()
        val progressMap = dao.getAllProgress().associateBy { it.vocabId }
        val crossRefs = dao.getAllCrossRefs().groupBy({ it.vocabId }, { it.topicId })

        val allItems = allVocab.map { entry ->
            val prog = progressMap[entry.id] ?: UserVocabularyProgressEntity(vocabId = entry.id)
            mapToVocabularyWithProgress(entry, prog, crossRefs[entry.id].orEmpty())
        }

        val scoped = allItems.filter { item ->
            topicId == null || topicId in item.topicIds
        }.ifEmpty { allItems }

        val filtered = when (mode) {
            TopicStudyMode.STUDY_ALL -> scoped
            TopicStudyMode.REVIEW_DUE_TODAY -> scoped.filter { it.isDue }
            TopicStudyMode.REVIEW_WEAK_WORDS -> scoped.filter {
                it.progress.wrongCount > 0 ||
                    (it.progress.learningState == LearningState.LEARNING.name && it.progress.consecutiveSuccesses == 0)
            }.sortedByDescending { it.progress.wrongCount }
            TopicStudyMode.CONTINUE_LEARNING -> {
                // Prioritize words not yet understood (NEW or consecutiveSuccesses == 0), then words in LEARNING/REVIEW
                val notYetUnderstood = scoped.filter {
                    it.progress.learningState == LearningState.NEW.name || it.progress.consecutiveSuccesses == 0
                }
                val inProgress = scoped.filter {
                    it.progress.learningState != LearningState.NEW.name &&
                        it.progress.consecutiveSuccesses > 0 &&
                        it.progress.learningState != LearningState.MASTERED.name
                }
                (notYetUnderstood + inProgress).distinctBy { it.entry.id }
            }
        }

        return filtered.ifEmpty { scoped }
    }

    // --- Flashcard Session Persistence & Resumption ---

    suspend fun startOrResumeFlashcardSession(
        topicId: String?,
        studyMode: TopicStudyMode,
        forceNew: Boolean = false
    ): Pair<ReviewSessionEntity, List<VocabularyWithProgress>> {
        if (!forceNew) {
            val existing = dao.getActiveFlashcardSession()
            if (existing != null && existing.topicId == topicId && existing.studyMode == studyMode.name) {
                val ids = decodeStringList(existing.vocabIdsJson)
                val items = ids.mapNotNull { getVocabularyWithProgressById(it) }
                // Only resume if the session still has remaining cards to study
                if (items.isNotEmpty() && existing.currentIndex in 0 until items.size) {
                    return existing to items
                } else {
                    dao.upsertReviewSession(
                        existing.copy(status = "COMPLETED", completedAt = clock.nowMillis())
                    )
                }
            }
        }

        // Close any previous lingering IN_PROGRESS flashcard sessions before starting a new one
        dao.closeAllActiveFlashcardSessions(clock.nowMillis())

        val items = getVocabularyForStudyMode(topicId, studyMode)
        val session = ReviewSessionEntity(
            id = "fc_${UUID.randomUUID()}",
            sessionType = "FLASHCARD_SESSION",
            topicId = topicId,
            studyMode = studyMode.name,
            isMandatory = false,
            status = if (items.isNotEmpty()) "IN_PROGRESS" else "COMPLETED",
            vocabIdsJson = json.encodeToString(items.map { it.entry.id }),
            completedVocabIdsJson = "[]",
            incorrectVocabIdsJson = "[]",
            currentIndex = 0,
            startedAt = clock.nowMillis(),
            completedAt = if (items.isEmpty()) clock.nowMillis() else null,
            startedEpochDay = clock.currentLocalEpochDay()
        )
        if (items.isNotEmpty()) {
            dao.upsertReviewSession(session)
        }
        return session to items
    }

    suspend fun recordFlashcardResponse(
        session: ReviewSessionEntity,
        vocabId: String,
        understood: Boolean,
        nextIndex: Int,
        totalCards: Int,
        updatedVocabIds: List<String>? = null
    ): ReviewSessionEntity {
        val settings = dao.getUserSettings() ?: UserSettingsEntity()
        val config = SpacedRepetitionConfig.fromCsv(settings.reviewIntervalsCsv, settings.masteryThreshold)
        val currentProgress = dao.getProgressForVocab(vocabId)

        val updateResult = srEngine.recordReview(
            current = currentProgress,
            vocabId = vocabId,
            wasCorrect = understood,
            reviewType = "FLASHCARD",
            sessionId = session.id,
            config = config
        )

        dao.upsertProgress(updateResult.updatedProgress)
        dao.insertReviewHistory(updateResult.historyRecord)

        val currentCompleted = decodeStringList(session.completedVocabIdsJson)
        val currentIncorrect = decodeStringList(session.incorrectVocabIdsJson)

        val completed = if (understood) {
            (currentCompleted + vocabId).distinct()
        } else {
            currentCompleted
        }
        val incorrect = if (!understood) {
            (currentIncorrect + vocabId).distinct()
        } else {
            currentIncorrect - vocabId
        }

        val finalVocabIdsJson = updatedVocabIds?.let { json.encodeToString(it) } ?: session.vocabIdsJson
        val effectiveTotalCards = updatedVocabIds?.size ?: totalCards
        val isFinished = nextIndex >= effectiveTotalCards

        val updatedSession = session.copy(
            vocabIdsJson = finalVocabIdsJson,
            completedVocabIdsJson = json.encodeToString(completed),
            incorrectVocabIdsJson = json.encodeToString(incorrect),
            currentIndex = nextIndex,
            status = if (isFinished) "COMPLETED" else "IN_PROGRESS",
            completedAt = if (isFinished) clock.nowMillis() else null
        )
        dao.upsertReviewSession(updatedSession)
        return updatedSession
    }

    // --- Mandatory Review Gate (Blocking Foreground/Launch Check) ---

    suspend fun checkAndPrepareMandatoryGate(): MandatoryGateState {
        val allVocab = dao.getAllVocabulary()
        val activeSession = dao.getActiveMandatorySession()

        if (activeSession != null) {
            val lockedIds = decodeStringList(activeSession.vocabIdsJson)
            val completedIds = decodeStringList(activeSession.completedVocabIdsJson)
            val incorrectIds = decodeStringList(activeSession.incorrectVocabIdsJson)
            val targetEntries = lockedIds.mapNotNull { id -> allVocab.find { it.id == id } }

            if (targetEntries.isEmpty() || activeSession.currentIndex >= targetEntries.size) {
                val finished = activeSession.copy(
                    status = "COMPLETED",
                    completedAt = clock.nowMillis()
                )
                dao.upsertReviewSession(finished)
            } else {
                val questions = QuizEngine.generateQuestionsForVocabulary(targetEntries, allVocab)
                return MandatoryGateState(
                    isLocked = true,
                    activeSession = activeSession,
                    questions = questions,
                    currentIndex = activeSession.currentIndex.coerceIn(0, (questions.size - 1).coerceAtLeast(0)),
                    completedVocabIds = completedIds,
                    incorrectVocabIds = incorrectIds
                )
            }
        }

        val todayEpochDay = clock.currentLocalEpochDay()
        val dueProgress = dao.getDueProgressList(todayEpochDay)

        if (dueProgress.isEmpty()) {
            return MandatoryGateState(isLocked = false)
        }

        val dueIds = dueProgress.map { it.vocabId }.distinct()
        val dueEntries = dueIds.mapNotNull { id -> allVocab.find { it.id == id } }
        if (dueEntries.isEmpty()) {
            return MandatoryGateState(isLocked = false)
        }

        val newMandatorySession = ReviewSessionEntity(
            id = "gate_${todayEpochDay}_${UUID.randomUUID().toString().take(8)}",
            sessionType = "MANDATORY_GATE",
            isMandatory = true,
            status = "IN_PROGRESS",
            vocabIdsJson = json.encodeToString(dueEntries.map { it.id }),
            completedVocabIdsJson = "[]",
            incorrectVocabIdsJson = "[]",
            currentIndex = 0,
            startedAt = clock.nowMillis(),
            startedEpochDay = todayEpochDay
        )
        dao.upsertReviewSession(newMandatorySession)

        val questions = QuizEngine.generateQuestionsForVocabulary(dueEntries, allVocab)
        return MandatoryGateState(
            isLocked = true,
            activeSession = newMandatorySession,
            questions = questions,
            currentIndex = 0,
            completedVocabIds = emptyList(),
            incorrectVocabIds = incorrectIdsFromSession(newMandatorySession)
        )
    }

    private fun incorrectIdsFromSession(session: ReviewSessionEntity): List<String> =
        decodeStringList(session.incorrectVocabIdsJson)

    suspend fun submitMandatoryGateAnswer(
        session: ReviewSessionEntity,
        vocabId: String,
        wasCorrect: Boolean,
        nextIndex: Int,
        totalQuestions: Int
    ): MandatoryGateState {
        val settings = dao.getUserSettings() ?: UserSettingsEntity()
        val config = SpacedRepetitionConfig.fromCsv(settings.reviewIntervalsCsv, settings.masteryThreshold)
        val currentProgress = dao.getProgressForVocab(vocabId)

        val updateResult = srEngine.recordReview(
            current = currentProgress,
            vocabId = vocabId,
            wasCorrect = wasCorrect,
            reviewType = "MANDATORY_GATE",
            sessionId = session.id,
            config = config
        )
        dao.upsertProgress(updateResult.updatedProgress)
        dao.insertReviewHistory(updateResult.historyRecord)

        val completedIds = (decodeStringList(session.completedVocabIdsJson) + vocabId).distinct()
        val incorrectIds = if (!wasCorrect) {
            (decodeStringList(session.incorrectVocabIdsJson) + vocabId).distinct()
        } else {
            decodeStringList(session.incorrectVocabIdsJson)
        }

        val isSessionComplete = nextIndex >= totalQuestions
        val nowMillis = clock.nowMillis()
        val updatedSession = session.copy(
            completedVocabIdsJson = json.encodeToString(completedIds),
            incorrectVocabIdsJson = json.encodeToString(incorrectIds),
            currentIndex = nextIndex,
            status = if (isSessionComplete) "COMPLETED" else "IN_PROGRESS",
            completedAt = if (isSessionComplete) nowMillis else null
        )
        dao.upsertReviewSession(updatedSession)

        if (isSessionComplete) {
            val durationSec = ((nowMillis - session.startedAt) / 1000L).toInt().coerceAtLeast(1)
            val correctCount = (totalQuestions - incorrectIds.size).coerceAtLeast(0)
            dao.insertQuizAttempt(
                QuizAttemptEntity(
                    id = "attempt_${session.id}",
                    topicId = null,
                    quizMode = "MANDATORY_GATE",
                    totalQuestions = totalQuestions,
                    correctAnswers = correctCount,
                    mistakesJson = json.encodeToString(incorrectIds),
                    durationSeconds = durationSec,
                    startedAt = session.startedAt,
                    completedAt = nowMillis
                )
            )
            return MandatoryGateState(
                isLocked = false,
                activeSession = updatedSession,
                questions = emptyList(),
                currentIndex = nextIndex,
                completedVocabIds = completedIds,
                incorrectVocabIds = incorrectIds
            )
        }

        val allVocab = dao.getAllVocabulary()
        val lockedIds = decodeStringList(updatedSession.vocabIdsJson)
        val targetEntries = lockedIds.mapNotNull { id -> allVocab.find { it.id == id } }
        val questions = QuizEngine.generateQuestionsForVocabulary(targetEntries, allVocab)

        return MandatoryGateState(
            isLocked = true,
            activeSession = updatedSession,
            questions = questions,
            currentIndex = nextIndex,
            completedVocabIds = completedIds,
            incorrectVocabIds = incorrectIds
        )
    }

    // --- Standard Quiz Recording ---

    suspend fun recordQuizQuestionResult(
        vocabId: String,
        wasCorrect: Boolean,
        questionType: QuizQuestionType,
        sessionId: String
    ) {
        val settings = dao.getUserSettings() ?: UserSettingsEntity()
        val config = SpacedRepetitionConfig.fromCsv(settings.reviewIntervalsCsv, settings.masteryThreshold)
        val currentProgress = dao.getProgressForVocab(vocabId)

        val reviewType = when (questionType) {
            QuizQuestionType.MULTIPLE_CHOICE_EN_TO_VI,
            QuizQuestionType.MULTIPLE_CHOICE_VI_TO_EN -> "QUIZ_MC"
            QuizQuestionType.FILL_IN_THE_BLANK -> "QUIZ_BLANK"
            QuizQuestionType.TYPED_ANSWER -> "QUIZ_TYPED"
        }

        val result = srEngine.recordReview(
            current = currentProgress,
            vocabId = vocabId,
            wasCorrect = wasCorrect,
            reviewType = reviewType,
            sessionId = sessionId,
            config = config
        )
        dao.upsertProgress(result.updatedProgress)
        dao.insertReviewHistory(result.historyRecord)
    }

    suspend fun saveCompletedQuizAttempt(
        topicId: String?,
        quizMode: String,
        totalQuestions: Int,
        correctAnswers: Int,
        mistakeVocabIds: List<String>,
        startedAt: Long
    ) {
        val now = clock.nowMillis()
        val durationSec = ((now - startedAt) / 1000L).toInt().coerceAtLeast(1)
        dao.insertQuizAttempt(
            QuizAttemptEntity(
                id = "quiz_${UUID.randomUUID()}",
                topicId = topicId,
                quizMode = quizMode,
                totalQuestions = totalQuestions,
                correctAnswers = correctAnswers,
                mistakesJson = json.encodeToString(mistakeVocabIds.distinct()),
                durationSeconds = durationSec,
                startedAt = startedAt,
                completedAt = now
            )
        )
    }

    // --- Settings & LLM Delegation ---

    suspend fun updateSettings(transform: (UserSettingsEntity) -> UserSettingsEntity): UserSettingsEntity {
        val current = dao.getUserSettings() ?: UserSettingsEntity(id = 1)
        val updated = transform(current)
        dao.upsertUserSettings(updated)
        return updated
    }

    fun getLlmConfigStatus(settings: UserSettingsEntity? = null): LlmConfigStatus =
        llmService.getConfigurationStatus(settings)

    suspend fun enrichVocabularyWithLlm(
        vocabId: String,
        applyToDatabaseIfUnverified: Boolean = false
    ): Result<VocabularyEnrichmentPayload> {
        val entry = dao.getVocabularyById(vocabId)
            ?: return Result.failure(IllegalArgumentException("Vocabulary entry not found"))
        val result = llmService.enrichVocabulary(entry)
        if (applyToDatabaseIfUnverified && result.isSuccess && entry.verificationStatus != "AWL_VERIFIED") {
            val payload = result.getOrThrow()
            val updatedEntry = entry.copy(
                commonMistakes = entry.commonMistakes ?: payload.commonMistakes,
                wordFamilyJson = entry.wordFamilyJson ?: json.encodeToString(payload.wordFamily),
                updatedAt = clock.nowMillis()
            )
            dao.upsertVocabulary(updatedEntry)
        }
        return result
    }

    suspend fun compareWordsHighThinking(vocabId: String, comparisonQuery: String): String {
        val entry = dao.getVocabularyById(vocabId) ?: return "Không tìm thấy từ vựng."
        return llmService.compareWordsHighThinking(entry, comparisonQuery)
    }

    suspend fun generateQuickMemoryTip(vocabId: String): String {
        val entry = dao.getVocabularyById(vocabId) ?: return ""
        return llmService.generateQuickMemoryTip(entry)
    }

    suspend fun fetchSearchGroundedUsage(vocabId: String): GroundedTopicInsight {
        val entry = dao.getVocabularyById(vocabId)
            ?: return GroundedTopicInsight("Không tìm thấy từ vựng.", emptyList(), isFallback = true)
        return llmService.fetchSearchGroundedUsage(entry)
    }

    suspend fun generateLlmValidatedQuiz(
        target: VocabularyEntity,
        pool: List<VocabularyEntity>,
        type: QuizQuestionType
    ): QuizQuestionItem {
        return llmService.generateValidatedQuizQuestion(target, pool, type)
    }
}
