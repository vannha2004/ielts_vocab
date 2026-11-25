package com.example.domain

import com.example.data.local.LearningState
import com.example.data.local.ReviewHistoryEntity
import com.example.data.local.UserVocabularyProgressEntity

data class SpacedRepetitionConfig(
    val intervalsDays: List<Int> = listOf(1, 3, 7, 14, 30),
    val masteryThreshold: Int = 5
) {
    companion object {
        fun fromCsv(csv: String, masteryThreshold: Int = 5): SpacedRepetitionConfig {
            val parsed = csv.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it > 0 }
            val validIntervals = if (parsed.isNotEmpty()) parsed else listOf(1, 3, 7, 14, 30)
            return SpacedRepetitionConfig(
                intervalsDays = validIntervals,
                masteryThreshold = masteryThreshold.coerceAtLeast(2)
            )
        }
    }
}

data class ReviewUpdateResult(
    val updatedProgress: UserVocabularyProgressEntity,
    val historyRecord: ReviewHistoryEntity,
    val wasDuplicateEventSkipped: Boolean = false
)

/**
 * Deterministic Spaced Repetition Engine using local calendar dates via [AppClock].
 * Never relies on an LLM for scheduling or mastery calculation.
 */
class SpacedRepetitionEngine(
    private val clock: AppClock
) {

    /**
     * Checks whether a vocabulary progress entry is currently due for review.
     */
    fun isDue(progress: UserVocabularyProgressEntity): Boolean {
        if (progress.learningState == LearningState.NEW.name) return false
        val dueEpochDay = progress.nextDueEpochDay ?: return false
        return dueEpochDay <= clock.currentLocalEpochDay()
    }

    /**
     * Records a flashcard self-assessment ("Understand" = true, "Not sure" = false)
     * or quiz evaluation result.
     *
     * Rules enforced:
     * 1. A newly learned word is never immediately marked MASTERED.
     * 2. Flashcard "Understand" is self-assessment: it moves a NEW word to LEARNING (interval = 1 day)
     *    and never promotes a word to MASTERED by itself.
     * 3. Same-day guard: once a word has already been scheduled on the current local calendar day
     *    (even across multiple sessions on the same day), additional correct responses on the SAME day
     *    do not advance the multi-day interval step [1, 3, 7, 14, 30] or inflate consecutiveSuccesses.
     * 4. Correct quiz/review on a subsequent day advances interval along [1, 3, 7, 14, 30].
     * 5. Reaching [config.masteryThreshold] consecutive successes across separate days marks MASTERED.
     * 6. Incorrect or "Not sure" reduces interval to step 0 (1 day), resets consecutiveSuccesses to 0,
     *    and sets state to LEARNING.
     */
    fun recordReview(
        current: UserVocabularyProgressEntity?,
        vocabId: String,
        wasCorrect: Boolean,
        reviewType: String,
        sessionId: String? = null,
        config: SpacedRepetitionConfig = SpacedRepetitionConfig()
    ): ReviewUpdateResult {
        val nowMillis = clock.nowMillis()
        val todayEpochDay = clock.currentLocalEpochDay()
        val intervals = config.intervalsDays.ifEmpty { listOf(1, 3, 7, 14, 30) }

        val existing = current ?: UserVocabularyProgressEntity(
            vocabId = vocabId,
            learningState = LearningState.NEW.name
        )

        // Guard against advancing a word's interval multiple times on the same calendar day
        // (unless it was previously wrong today with consecutiveSuccesses == 0 and is now understood for the first time)
        val alreadyScheduledToday = existing.learningState != LearningState.NEW.name &&
            existing.lastReviewedEpochDay == todayEpochDay &&
            existing.consecutiveSuccesses > 0

        if (alreadyScheduledToday && wasCorrect) {
            val isSameSession = sessionId != null && existing.lastSessionId == sessionId
            val updatedSameDay = existing.copy(
                totalReviews = if (isSameSession) existing.totalReviews else existing.totalReviews + 1,
                correctReviews = if (isSameSession) existing.correctReviews else existing.correctReviews + 1,
                lastReviewedAt = nowMillis,
                lastSessionId = sessionId ?: existing.lastSessionId
            )
            val noOpHistory = ReviewHistoryEntity(
                vocabId = vocabId,
                reviewType = reviewType,
                wasCorrect = true,
                previousState = existing.learningState,
                newState = existing.learningState,
                newIntervalDays = existing.currentIntervalDays,
                nextDueEpochDay = existing.nextDueEpochDay ?: (todayEpochDay + 1),
                reviewedAt = nowMillis,
                sessionId = sessionId
            )
            return ReviewUpdateResult(
                updatedProgress = updatedSameDay,
                historyRecord = noOpHistory,
                wasDuplicateEventSkipped = true
            )
        }

        val previousState = existing.learningState
        val newConsecutive: Int
        val newStep: Int
        val newIntervalDays: Int
        val newState: LearningState

        if (wasCorrect) {
            val isFirstSuccess = existing.learningState == LearningState.NEW.name || existing.consecutiveSuccesses == 0
            newConsecutive = existing.consecutiveSuccesses + 1

            if (isFirstSuccess) {
                newStep = 0
                newIntervalDays = intervals[0]
                newState = LearningState.LEARNING
            } else {
                val nextStepCandidate = existing.intervalStep + 1
                newStep = nextStepCandidate.coerceAtMost(intervals.lastIndex)
                newIntervalDays = intervals[newStep]

                // Flashcard "Understand" is self-assessment, not proof of mastery:
                // Only Quiz/Mandatory Review evaluations (or already-mastered words) can reach MASTERED
                val canBecomeMastered = newConsecutive >= config.masteryThreshold &&
                    (reviewType != "FLASHCARD" || existing.learningState == LearningState.MASTERED.name)

                newState = when {
                    canBecomeMastered -> LearningState.MASTERED
                    newStep >= 1 -> LearningState.REVIEW
                    else -> LearningState.LEARNING
                }
            }
        } else {
            // Incorrect or "Not sure" -> reset consecutive successes, reduce interval to 1 day for earlier review
            newConsecutive = 0
            newStep = 0
            newIntervalDays = intervals.first()
            newState = LearningState.LEARNING
        }

        val nextDueMillis = clock.addLocalDays(nowMillis, newIntervalDays)
        val nextDueEpochDay = todayEpochDay + newIntervalDays

        val updated = existing.copy(
            learningState = newState.name,
            intervalStep = newStep,
            currentIntervalDays = newIntervalDays,
            consecutiveSuccesses = newConsecutive,
            totalReviews = existing.totalReviews + 1,
            correctReviews = existing.correctReviews + if (wasCorrect) 1 else 0,
            wrongCount = existing.wrongCount + if (wasCorrect) 0 else 1,
            lastReviewedAt = nowMillis,
            lastReviewedEpochDay = todayEpochDay,
            nextDueTimestamp = nextDueMillis,
            nextDueEpochDay = nextDueEpochDay,
            lastSessionId = sessionId
        )

        val history = ReviewHistoryEntity(
            vocabId = vocabId,
            reviewType = reviewType,
            wasCorrect = wasCorrect,
            previousState = previousState,
            newState = newState.name,
            newIntervalDays = newIntervalDays,
            nextDueEpochDay = nextDueEpochDay,
            reviewedAt = nowMillis,
            sessionId = sessionId
        )

        return ReviewUpdateResult(
            updatedProgress = updated,
            historyRecord = history,
            wasDuplicateEventSkipped = false
        )
    }
}
