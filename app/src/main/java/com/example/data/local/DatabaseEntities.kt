package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Learning mastery states for each vocabulary entry.
 */
enum class LearningState {
    NEW,
    LEARNING,
    REVIEW,
    MASTERED
}

@Serializable
@Entity(
    tableName = "vocabulary_entries",
    indices = [
        Index(value = ["normalized_headword"], unique = true),
        Index(value = ["headword"]),
        Index(value = ["difficulty_level"])
    ]
)
data class VocabularyEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "headword")
    val headword: String,

    @ColumnInfo(name = "normalized_headword")
    val normalizedHeadword: String,

    @ColumnInfo(name = "part_of_speech")
    val partOfSpeech: String,

    @ColumnInfo(name = "ipa")
    val ipa: String? = null,

    @ColumnInfo(name = "vietnamese_meaning")
    val vietnameseMeaning: String,

    @ColumnInfo(name = "english_definition")
    val englishDefinition: String,

    @ColumnInfo(name = "example_sentence")
    val exampleSentence: String,

    @ColumnInfo(name = "vietnamese_example_translation")
    val vietnameseExampleTranslation: String,

    @ColumnInfo(name = "collocations_json")
    val collocationsJson: String = "[]",

    @ColumnInfo(name = "word_family_json")
    val wordFamilyJson: String? = null,

    @ColumnInfo(name = "synonyms_json")
    val synonymsJson: String = "[]",

    @ColumnInfo(name = "antonyms_json")
    val antonymsJson: String? = null,

    @ColumnInfo(name = "common_mistakes")
    val commonMistakes: String? = null,

    @ColumnInfo(name = "difficulty_level")
    val difficultyLevel: String, // e.g., "Band 5.5–6.0", "Band 6.0–6.5", "Band 6.5–7.0+"

    @ColumnInfo(name = "cefr_level")
    val cefrLevel: String? = null, // Nullable when unverified; not equated to IELTS band

    @ColumnInfo(name = "source_name")
    val sourceName: String,

    @ColumnInfo(name = "source_url")
    val sourceUrl: String? = null,

    @ColumnInfo(name = "verification_status")
    val verificationStatus: String, // e.g., "AWL_VERIFIED", "CURATED_STARTER", "USER_IMPORTED", "LLM_ENRICHED"

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Serializable
@Entity(
    tableName = "topics",
    indices = [Index(value = ["order_index"], unique = true)]
)
data class TopicEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int,

    @ColumnInfo(name = "english_name")
    val englishName: String,

    @ColumnInfo(name = "vietnamese_name")
    val vietnameseName: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "target_count")
    val targetCount: Int = 80
)

@Entity(
    tableName = "vocabulary_topic_cross_ref",
    primaryKeys = ["vocab_id", "topic_id"],
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["vocab_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topic_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["vocab_id"]),
        Index(value = ["topic_id"])
    ]
)
data class VocabularyTopicCrossRef(
    @ColumnInfo(name = "vocab_id")
    val vocabId: String,

    @ColumnInfo(name = "topic_id")
    val topicId: String
)

@Entity(
    tableName = "user_vocabulary_progress",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["vocab_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["learning_state"]),
        Index(value = ["next_due_epoch_day"]),
        Index(value = ["next_due_timestamp"])
    ]
)
data class UserVocabularyProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "vocab_id")
    val vocabId: String,

    @ColumnInfo(name = "learning_state")
    val learningState: String = LearningState.NEW.name,

    @ColumnInfo(name = "interval_step")
    val intervalStep: Int = 0, // Index into intervals [1, 3, 7, 14, 30]

    @ColumnInfo(name = "current_interval_days")
    val currentIntervalDays: Int = 0,

    @ColumnInfo(name = "consecutive_successes")
    val consecutiveSuccesses: Int = 0,

    @ColumnInfo(name = "total_reviews")
    val totalReviews: Int = 0,

    @ColumnInfo(name = "correct_reviews")
    val correctReviews: Int = 0,

    @ColumnInfo(name = "wrong_count")
    val wrongCount: Int = 0,

    @ColumnInfo(name = "last_reviewed_at")
    val lastReviewedAt: Long? = null,

    @ColumnInfo(name = "last_reviewed_epoch_day")
    val lastReviewedEpochDay: Long? = null,

    @ColumnInfo(name = "next_due_timestamp")
    val nextDueTimestamp: Long? = null,

    @ColumnInfo(name = "next_due_epoch_day")
    val nextDueEpochDay: Long? = null,

    @ColumnInfo(name = "last_session_id")
    val lastSessionId: String? = null
)

@Entity(
    tableName = "review_history",
    indices = [
        Index(value = ["vocab_id"]),
        Index(value = ["reviewed_at"])
    ]
)
data class ReviewHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "vocab_id")
    val vocabId: String,

    @ColumnInfo(name = "review_type")
    val reviewType: String, // "FLASHCARD", "QUIZ_MC", "QUIZ_BLANK", "QUIZ_TYPED", "MANDATORY_GATE"

    @ColumnInfo(name = "was_correct")
    val wasCorrect: Boolean,

    @ColumnInfo(name = "previous_state")
    val previousState: String,

    @ColumnInfo(name = "new_state")
    val newState: String,

    @ColumnInfo(name = "new_interval_days")
    val newIntervalDays: Int,

    @ColumnInfo(name = "next_due_epoch_day")
    val nextDueEpochDay: Long,

    @ColumnInfo(name = "reviewed_at")
    val reviewedAt: Long,

    @ColumnInfo(name = "session_id")
    val sessionId: String? = null
)

@Entity(
    tableName = "quiz_questions",
    indices = [
        Index(value = ["vocab_id"]),
        Index(value = ["question_type"])
    ]
)
data class QuizQuestionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "vocab_id")
    val vocabId: String,

    @ColumnInfo(name = "question_type")
    val questionType: String, // "MULTIPLE_CHOICE", "FILL_BLANK", "TYPED"

    @ColumnInfo(name = "prompt")
    val prompt: String,

    @ColumnInfo(name = "options_json")
    val optionsJson: String, // JSON array of options for MC

    @ColumnInfo(name = "correct_answer")
    val correctAnswer: String,

    @ColumnInfo(name = "explanation")
    val explanation: String,

    @ColumnInfo(name = "source")
    val source: String, // "LOCAL" or "LLM_VALIDATED"

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "quiz_attempts",
    indices = [Index(value = ["completed_at"])]
)
data class QuizAttemptEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "topic_id")
    val topicId: String?, // null if review or mandatory quiz

    @ColumnInfo(name = "quiz_mode")
    val quizMode: String, // "TOPIC_QUIZ", "REVIEW_QUIZ", "MANDATORY_GATE"

    @ColumnInfo(name = "total_questions")
    val totalQuestions: Int,

    @ColumnInfo(name = "correct_answers")
    val correctAnswers: Int,

    @ColumnInfo(name = "mistakes_json")
    val mistakesJson: String, // JSON array of vocabIds answered wrong

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long
)

@Entity(
    tableName = "review_sessions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["is_mandatory"])
    ]
)
data class ReviewSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_type")
    val sessionType: String, // "MANDATORY_GATE" or "FLASHCARD_SESSION"

    @ColumnInfo(name = "topic_id")
    val topicId: String? = null,

    @ColumnInfo(name = "study_mode")
    val studyMode: String? = null,

    @ColumnInfo(name = "is_mandatory")
    val isMandatory: Boolean,

    @ColumnInfo(name = "status")
    val status: String, // "IN_PROGRESS", "COMPLETED"

    @ColumnInfo(name = "vocab_ids_json")
    val vocabIdsJson: String, // Snapshot of vocabulary IDs locked when the session started

    @ColumnInfo(name = "completed_vocab_ids_json")
    val completedVocabIdsJson: String, // Vocabulary IDs already answered in this session

    @ColumnInfo(name = "incorrect_vocab_ids_json")
    val incorrectVocabIdsJson: String, // Vocabulary IDs answered wrong in this session

    @ColumnInfo(name = "current_index")
    val currentIndex: Int = 0,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    @ColumnInfo(name = "started_epoch_day")
    val startedEpochDay: Long
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,

    @ColumnInfo(name = "daily_goal")
    val dailyGoal: Int = 15,

    @ColumnInfo(name = "reminder_enabled")
    val reminderEnabled: Boolean = true,

    @ColumnInfo(name = "reminder_hour")
    val reminderHour: Int = 20,

    @ColumnInfo(name = "reminder_minute")
    val reminderMinute: Int = 0,

    @ColumnInfo(name = "review_intervals_csv")
    val reviewIntervalsCsv: String = "1,3,7,14,30",

    @ColumnInfo(name = "mastery_threshold")
    val masteryThreshold: Int = 5,

    @ColumnInfo(name = "dark_mode_pref")
    val darkModePref: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"

    @ColumnInfo(name = "simulated_day_offset")
    val simulatedDayOffset: Int = 0,

    @ColumnInfo(name = "custom_api_key")
    val customApiKey: String = "",

    @ColumnInfo(name = "preferred_model")
    val preferredModel: String = "gemini-3.8-flash"
)

@Entity(
    tableName = "llm_response_cache",
    indices = [Index(value = ["cache_key"], unique = true)]
)
data class LlmCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "cache_key")
    val cacheKey: String,

    @ColumnInfo(name = "feature_type")
    val featureType: String, // "ENRICHMENT", "QUIZ_GEN", "WORD_COMPARISON", "SEARCH_GROUNDING"

    @ColumnInfo(name = "model_used")
    val modelUsed: String,

    @ColumnInfo(name = "response_json")
    val responseJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
