package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IeltsDao {

    // --- Topics ---
    @Query("SELECT * FROM topics ORDER BY order_index ASC")
    fun observeTopics(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics ORDER BY order_index ASC")
    suspend fun getAllTopics(): List<TopicEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTopics(topics: List<TopicEntity>)

    @Update
    suspend fun updateTopics(topics: List<TopicEntity>)

    // --- Vocabulary Entries ---
    @Query("SELECT COUNT(*) FROM vocabulary_entries")
    suspend fun getVocabularyCount(): Int

    @Query("SELECT COUNT(*) FROM vocabulary_entries")
    fun observeVocabularyCount(): Flow<Int>

    @Query("SELECT * FROM vocabulary_entries ORDER BY headword ASC")
    fun observeAllVocabulary(): Flow<List<VocabularyEntity>>

    @Query("SELECT * FROM vocabulary_entries ORDER BY headword ASC")
    suspend fun getAllVocabulary(): List<VocabularyEntity>

    @Query("SELECT * FROM vocabulary_entries WHERE id = :vocabId LIMIT 1")
    suspend fun getVocabularyById(vocabId: String): VocabularyEntity?

    @Query("SELECT * FROM vocabulary_entries WHERE id = :vocabId LIMIT 1")
    fun observeVocabularyById(vocabId: String): Flow<VocabularyEntity?>

    @Query("SELECT * FROM vocabulary_entries WHERE id IN (:vocabIds)")
    suspend fun getVocabularyByIds(vocabIds: List<String>): List<VocabularyEntity>

    @Query("SELECT * FROM vocabulary_entries WHERE normalized_headword = :normalized LIMIT 1")
    suspend fun getVocabularyByNormalizedHeadword(normalized: String): VocabularyEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVocabularyIgnore(entry: VocabularyEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVocabularyBatchIgnore(entries: List<VocabularyEntity>): List<Long>

    @Update
    suspend fun updateVocabulary(entry: VocabularyEntity)

    @Update
    suspend fun updateVocabularyBatch(entries: List<VocabularyEntity>)

    /**
     * Safe upsert that NEVER uses SQLite REPLACE (which would delete the parent row and
     * trigger ON DELETE CASCADE on vocabulary_topic_cross_ref and user_vocabulary_progress).
     */
    @Transaction
    suspend fun upsertVocabulary(entry: VocabularyEntity) {
        val rowId = insertVocabularyIgnore(entry)
        if (rowId == -1L) {
            updateVocabulary(entry)
        }
    }

    @Transaction
    suspend fun upsertVocabularyBatch(entries: List<VocabularyEntity>) {
        val rowIds = insertVocabularyBatchIgnore(entries)
        val existingToUpdate = entries.filterIndexed { index, _ -> rowIds.getOrNull(index) == -1L }
        if (existingToUpdate.isNotEmpty()) {
            updateVocabularyBatch(existingToUpdate)
        }
    }

    // --- Cross References ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<VocabularyTopicCrossRef>)

    @Query("SELECT COUNT(*) FROM vocabulary_topic_cross_ref")
    suspend fun getCrossRefCount(): Int

    @Query("SELECT * FROM vocabulary_topic_cross_ref")
    fun observeAllCrossRefs(): Flow<List<VocabularyTopicCrossRef>>

    @Query("SELECT * FROM vocabulary_topic_cross_ref")
    suspend fun getAllCrossRefs(): List<VocabularyTopicCrossRef>

    @Query("SELECT topic_id FROM vocabulary_topic_cross_ref WHERE vocab_id = :vocabId")
    suspend fun getTopicIdsForVocab(vocabId: String): List<String>

    @Query(
        """
        SELECT v.* FROM vocabulary_entries v
        INNER JOIN vocabulary_topic_cross_ref ref ON v.id = ref.vocab_id
        WHERE ref.topic_id = :topicId
        ORDER BY v.headword ASC
        """
    )
    suspend fun getVocabularyForTopic(topicId: String): List<VocabularyEntity>

    @Query(
        """
        SELECT v.* FROM vocabulary_entries v
        INNER JOIN vocabulary_topic_cross_ref ref ON v.id = ref.vocab_id
        WHERE ref.topic_id = :topicId
        ORDER BY v.headword ASC
        """
    )
    fun observeVocabularyForTopic(topicId: String): Flow<List<VocabularyEntity>>

    // --- User Vocabulary Progress ---
    @Query("SELECT * FROM user_vocabulary_progress")
    fun observeAllProgress(): Flow<List<UserVocabularyProgressEntity>>

    @Query("SELECT * FROM user_vocabulary_progress")
    suspend fun getAllProgress(): List<UserVocabularyProgressEntity>

    @Query("SELECT * FROM user_vocabulary_progress WHERE vocab_id = :vocabId LIMIT 1")
    suspend fun getProgressForVocab(vocabId: String): UserVocabularyProgressEntity?

    @Query("SELECT * FROM user_vocabulary_progress WHERE vocab_id = :vocabId LIMIT 1")
    fun observeProgressForVocab(vocabId: String): Flow<UserVocabularyProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: UserVocabularyProgressEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInitialProgressBatch(progressList: List<UserVocabularyProgressEntity>)

    @Query(
        """
        SELECT * FROM user_vocabulary_progress
        WHERE learning_state != 'NEW'
          AND next_due_epoch_day IS NOT NULL
          AND next_due_epoch_day <= :currentEpochDay
        ORDER BY next_due_epoch_day ASC
        """
    )
    suspend fun getDueProgressList(currentEpochDay: Long): List<UserVocabularyProgressEntity>

    @Query(
        """
        SELECT * FROM user_vocabulary_progress
        WHERE learning_state != 'NEW'
          AND next_due_epoch_day IS NOT NULL
          AND next_due_epoch_day <= :currentEpochDay
        ORDER BY next_due_epoch_day ASC
        """
    )
    fun observeDueProgressList(currentEpochDay: Long): Flow<List<UserVocabularyProgressEntity>>

    // --- Review History ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewHistory(history: ReviewHistoryEntity)

    @Query("SELECT * FROM review_history ORDER BY reviewed_at DESC LIMIT :limit")
    fun observeRecentReviewHistory(limit: Int = 100): Flow<List<ReviewHistoryEntity>>

    @Query("SELECT * FROM review_history WHERE vocab_id = :vocabId ORDER BY reviewed_at DESC")
    fun observeReviewHistoryForVocab(vocabId: String): Flow<List<ReviewHistoryEntity>>

    @Query("SELECT * FROM review_history ORDER BY reviewed_at DESC")
    suspend fun getAllReviewHistory(): List<ReviewHistoryEntity>

    // --- Quiz Questions & Attempts ---
    @Query("SELECT * FROM quiz_questions WHERE vocab_id = :vocabId AND question_type = :type LIMIT 1")
    suspend fun getCachedQuizQuestion(vocabId: String, type: String): QuizQuestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizQuestion(question: QuizQuestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizAttempt(attempt: QuizAttemptEntity)

    @Query("SELECT * FROM quiz_attempts ORDER BY completed_at DESC")
    fun observeQuizAttempts(): Flow<List<QuizAttemptEntity>>

    // --- Review Sessions (Mandatory Gate & Resumable Flashcard Sessions) ---
    @Query("SELECT * FROM review_sessions WHERE is_mandatory = 1 AND status = 'IN_PROGRESS' ORDER BY started_at DESC LIMIT 1")
    suspend fun getActiveMandatorySession(): ReviewSessionEntity?

    @Query("SELECT * FROM review_sessions WHERE is_mandatory = 1 AND status = 'IN_PROGRESS' ORDER BY started_at DESC LIMIT 1")
    fun observeActiveMandatorySession(): Flow<ReviewSessionEntity?>

    @Query("SELECT * FROM review_sessions WHERE is_mandatory = 0 AND status = 'IN_PROGRESS' ORDER BY started_at DESC LIMIT 1")
    suspend fun getActiveFlashcardSession(): ReviewSessionEntity?

    @Query("SELECT * FROM review_sessions WHERE is_mandatory = 0 AND status = 'IN_PROGRESS' ORDER BY started_at DESC LIMIT 1")
    fun observeActiveFlashcardSession(): Flow<ReviewSessionEntity?>

    @Query("UPDATE review_sessions SET status = 'COMPLETED', completed_at = :completedAt WHERE is_mandatory = 0 AND status = 'IN_PROGRESS'")
    suspend fun closeAllActiveFlashcardSessions(completedAt: Long)

    @Query("UPDATE review_sessions SET status = 'COMPLETED', completed_at = :completedAt WHERE is_mandatory = 0 AND status = 'IN_PROGRESS' AND (vocab_ids_json = '[]' OR vocab_ids_json = '')")
    suspend fun cleanupEmptyFlashcardSessions(completedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReviewSession(session: ReviewSessionEntity)

    // --- User Settings ---
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun observeUserSettings(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun getUserSettings(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserSettings(settings: UserSettingsEntity)

    // --- LLM Cache ---
    @Query("SELECT * FROM llm_response_cache WHERE cache_key = :key LIMIT 1")
    suspend fun getLlmCache(key: String): LlmCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putLlmCache(entry: LlmCacheEntity)

    @Query("SELECT COUNT(*) FROM llm_response_cache")
    fun observeLlmCacheCount(): Flow<Int>
}
