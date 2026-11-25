package com.example

import com.example.data.local.LearningState
import com.example.data.local.VocabularyEntity
import com.example.data.remote.VocabularyEnrichmentPayload
import com.example.data.seed.SeedDataValidator
import com.example.domain.ControllableAppClock
import com.example.domain.QuizEngine
import com.example.domain.QuizQuestionItem
import com.example.domain.QuizQuestionType
import com.example.domain.SpacedRepetitionConfig
import com.example.domain.SpacedRepetitionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ExampleUnitTest {

    @Test
    fun `spaced repetition advances intervals 1-3-7-14-30 and transitions to MASTERED`() {
        val clock = ControllableAppClock()
        val engine = SpacedRepetitionEngine(clock)
        val config = SpacedRepetitionConfig(intervalsDays = listOf(1, 3, 7, 14, 30), masteryThreshold = 5)

        // 1. First Flashcard "Understand" on NEW word -> moves to LEARNING (1 day), NOT MASTERED
        val r1 = engine.recordReview(
            current = null,
            vocabId = "vocab_mitigate",
            wasCorrect = true,
            reviewType = "FLASHCARD",
            sessionId = "s1",
            config = config
        )
        assertEquals(LearningState.LEARNING.name, r1.updatedProgress.learningState)
        assertEquals(1, r1.updatedProgress.currentIntervalDays)
        assertFalse(engine.isDue(r1.updatedProgress))

        // Advance 1 day -> now due
        clock.advanceDays(1)
        assertTrue(engine.isDue(r1.updatedProgress))

        // 2. Second successful review -> interval 3 days, state REVIEW
        val r2 = engine.recordReview(
            current = r1.updatedProgress,
            vocabId = "vocab_mitigate",
            wasCorrect = true,
            reviewType = "QUIZ_MC",
            sessionId = "s2",
            config = config
        )
        assertEquals(LearningState.REVIEW.name, r2.updatedProgress.learningState)
        assertEquals(3, r2.updatedProgress.currentIntervalDays)
        assertEquals(2, r2.updatedProgress.consecutiveSuccesses)

        // 3. Third successful review after 3 days -> interval 7 days
        clock.advanceDays(3)
        val r3 = engine.recordReview(
            current = r2.updatedProgress,
            vocabId = "vocab_mitigate",
            wasCorrect = true,
            reviewType = "QUIZ_BLANK",
            sessionId = "s3",
            config = config
        )
        assertEquals(7, r3.updatedProgress.currentIntervalDays)
        assertEquals(3, r3.updatedProgress.consecutiveSuccesses)

        // 4. Fourth successful review after 7 days -> interval 14 days
        clock.advanceDays(7)
        val r4 = engine.recordReview(
            current = r3.updatedProgress,
            vocabId = "vocab_mitigate",
            wasCorrect = true,
            reviewType = "QUIZ_TYPED",
            sessionId = "s4",
            config = config
        )
        assertEquals(14, r4.updatedProgress.currentIntervalDays)
        assertEquals(4, r4.updatedProgress.consecutiveSuccesses)
        assertEquals(LearningState.REVIEW.name, r4.updatedProgress.learningState)

        // 5. Fifth successful review after 14 days -> interval 30 days, state MASTERED
        clock.advanceDays(14)
        val r5 = engine.recordReview(
            current = r4.updatedProgress,
            vocabId = "vocab_mitigate",
            wasCorrect = true,
            reviewType = "MANDATORY_GATE",
            sessionId = "s5",
            config = config
        )
        assertEquals(30, r5.updatedProgress.currentIntervalDays)
        assertEquals(5, r5.updatedProgress.consecutiveSuccesses)
        assertEquals(LearningState.MASTERED.name, r5.updatedProgress.learningState)
    }

    @Test
    fun `incorrect answer resets interval to 1 day and prevents double scheduling in same session`() {
        val clock = ControllableAppClock()
        val engine = SpacedRepetitionEngine(clock)

        val r1 = engine.recordReview(null, "vocab_1", true, "QUIZ_MC", sessionId = "session_A")
        assertEquals(1, r1.updatedProgress.currentIntervalDays)

        // Duplicate event in the exact same session on the same day is skipped
        val duplicate = engine.recordReview(r1.updatedProgress, "vocab_1", true, "QUIZ_MC", sessionId = "session_A")
        assertTrue(duplicate.wasDuplicateEventSkipped)
        assertEquals(1, duplicate.updatedProgress.currentIntervalDays)

        // Advance 1 day and answer correctly -> 3 days
        clock.advanceDays(1)
        val r2 = engine.recordReview(r1.updatedProgress, "vocab_1", true, "QUIZ_MC", sessionId = "session_B")
        assertEquals(3, r2.updatedProgress.currentIntervalDays)

        // Advance 3 days and answer INCORRECTLY -> resets to 1 day and state LEARNING
        clock.advanceDays(3)
        val r3 = engine.recordReview(r2.updatedProgress, "vocab_1", false, "MANDATORY_GATE", sessionId = "session_C")
        assertEquals(1, r3.updatedProgress.currentIntervalDays)
        assertEquals(0, r3.updatedProgress.consecutiveSuccesses)
        assertEquals(LearningState.LEARNING.name, r3.updatedProgress.learningState)
        assertEquals(1, r3.updatedProgress.wrongCount)
    }

    @Test
    fun `timezone shift preserves local calendar epoch day scheduling`() {
        val clock = ControllableAppClock(
            currentMillis = 1_761_380_000_000L,
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        )
        val engine = SpacedRepetitionEngine(clock)
        val res = engine.recordReview(null, "vocab_tz", true, "FLASHCARD", sessionId = "s_tz")
        assertFalse(engine.isDue(res.updatedProgress))

        clock.setTimeZone(TimeZone.getTimeZone("Europe/London"))
        clock.advanceDays(1)
        assertTrue(engine.isDue(res.updatedProgress))
    }

    @Test
    fun `quiz answer evaluation normalizes case and whitespace and rejects arbitrary typos`() {
        val dummyVocab = VocabularyEntity(
            id = "vocab_mitigate",
            headword = "mitigate",
            normalizedHeadword = "mitigate",
            partOfSpeech = "verb",
            ipa = "/ˈmɪt.ɪ.ɡeɪt/",
            vietnameseMeaning = "giảm nhẹ",
            englishDefinition = "to make something less harmful",
            exampleSentence = "We must mitigate climate change.",
            vietnameseExampleTranslation = "Chúng ta phải giảm nhẹ biến đổi khí hậu.",
            difficultyLevel = "Band 6.5–7.0+",
            sourceName = "Test",
            verificationStatus = "CURATED_STARTER",
            createdAt = 0L,
            updatedAt = 0L
        )
        val question = QuizEngine.buildQuestionForEntry(dummyVocab, listOf(dummyVocab), QuizQuestionType.TYPED_ANSWER)

        // Case and whitespace normalization
        val evalCorrect = QuizEngine.evaluateAnswer(question, "  MiTiGaTe  ")
        assertTrue(evalCorrect.isCorrect)

        // Arbitrary misspelling rejected
        val evalTypo = QuizEngine.evaluateAnswer(question, "mitigatte")
        assertFalse(evalTypo.isCorrect)
    }

    @Test
    fun `quiz generator never produces duplicate options and validates LLM questions`() {
        val pool = (1..6).map { i ->
            VocabularyEntity(
                id = "v_$i",
                headword = "word$i",
                normalizedHeadword = "word$i",
                partOfSpeech = "noun",
                vietnameseMeaning = "nghĩa thứ $i",
                englishDefinition = "definition $i",
                exampleSentence = "This is word$i in a sentence.",
                vietnameseExampleTranslation = "Câu dịch $i",
                difficultyLevel = "Band 6.0–6.5",
                sourceName = "Test",
                verificationStatus = "CURATED_STARTER",
                createdAt = 0L,
                updatedAt = 0L
            )
        }
        val questions = QuizEngine.generateQuestionsForVocabulary(pool, pool, QuizQuestionType.MULTIPLE_CHOICE_EN_TO_VI)
        questions.forEach { q ->
            assertEquals(4, q.options.size)
            assertEquals(4, q.options.map { it.lowercase() }.distinct().size)
            assertTrue(QuizEngine.validateLlmQuestion(q, pool.first { it.id == q.vocabId }))
        }

        // Duplicate option in LLM question must be rejected
        val invalidLlmQuestion = QuizQuestionItem(
            id = "bad_q",
            vocabId = "v_1",
            headword = "word1",
            ipa = null,
            partOfSpeech = "noun",
            questionType = QuizQuestionType.MULTIPLE_CHOICE_EN_TO_VI,
            prompt = "Chọn nghĩa đúng",
            options = listOf("nghĩa thứ 1", "nghĩa thứ 1", "nghĩa thứ 2", "nghĩa thứ 3"),
            correctAnswer = "nghĩa thứ 1",
            explanation = "Giải thích"
        )
        assertFalse(QuizEngine.validateLlmQuestion(invalidLlmQuestion, pool[0]))
    }

    @Test
    fun `seed validator rejects duplicates, invalid topics, and malformed data`() {
        val rawJson = """
            [
              {
                "id": "v1",
                "headword": "Sustainable",
                "part_of_speech": "adjective",
                "vietnamese_meaning": "bền vững",
                "english_definition": "able to continue",
                "example_sentence": "Sustainable development is key.",
                "vietnamese_example_translation": "Phát triển bền vững là chìa khóa.",
                "topic_ids": ["topic_1_environment"],
                "source_name": "AWL",
                "verification_status": "AWL_VERIFIED"
              },
              {
                "id": "v2",
                "headword": "  sustainable ",
                "part_of_speech": "adjective",
                "vietnamese_meaning": "bền vững lặp lại",
                "english_definition": "duplicate entry",
                "example_sentence": "Duplicate example.",
                "vietnamese_example_translation": "Dịch lặp lại.",
                "topic_ids": ["topic_1_environment"],
                "source_name": "AWL",
                "verification_status": "AWL_VERIFIED"
              },
              {
                "id": "v3",
                "headword": "invalidtopicword",
                "part_of_speech": "noun",
                "vietnamese_meaning": "từ sai chủ đề",
                "english_definition": "wrong topic",
                "example_sentence": "Example.",
                "vietnamese_example_translation": "Ví dụ.",
                "topic_ids": ["topic_999_nonexistent"],
                "source_name": "Test",
                "verification_status": "CURATED_STARTER"
              }
            ]
        """.trimIndent()

        val report = SeedDataValidator.validateJsonSeedData(rawJson)
        assertEquals(3, report.totalInputCount)
        assertEquals(1, report.validEntries.size)
        assertEquals(2, report.errors.size)
        assertEquals(1, report.duplicateHeadwordsRejected.size)
    }

    @Test
    fun `same-day multiple sessions do not inflate interval or falsely mark words MASTERED`() {
        val clock = ControllableAppClock()
        val engine = SpacedRepetitionEngine(clock)
        val config = SpacedRepetitionConfig(intervalsDays = listOf(1, 3, 7, 14, 30), masteryThreshold = 5)

        // Session 1 on Day 0: user marks Understand in Flashcard -> interval 1 day, LEARNING
        var progress = engine.recordReview(
            current = null,
            vocabId = "vocab_same_day",
            wasCorrect = true,
            reviewType = "FLASHCARD",
            sessionId = "session_1",
            config = config
        ).updatedProgress
        assertEquals(1, progress.currentIntervalDays)
        assertEquals(1, progress.consecutiveSuccesses)
        assertEquals(LearningState.LEARNING.name, progress.learningState)

        // Sessions 2..8 on the exact same Day 0: interval and consecutiveSuccesses must NOT inflate
        for (i in 2..8) {
            progress = engine.recordReview(
                current = progress,
                vocabId = "vocab_same_day",
                wasCorrect = true,
                reviewType = "FLASHCARD",
                sessionId = "session_$i",
                config = config
            ).updatedProgress
        }
        assertEquals(1, progress.currentIntervalDays)
        assertEquals(1, progress.consecutiveSuccesses)
        assertNotEquals(LearningState.MASTERED.name, progress.learningState)

        // Even if the user clicks "Chưa chắc" (wasCorrect = false) on the same day, it immediately resets to 0 consecutive successes
        progress = engine.recordReview(
            current = progress,
            vocabId = "vocab_same_day",
            wasCorrect = false,
            reviewType = "FLASHCARD",
            sessionId = "session_9",
            config = config
        ).updatedProgress
        assertEquals(0, progress.consecutiveSuccesses)
        assertEquals(LearningState.LEARNING.name, progress.learningState)
    }
}

