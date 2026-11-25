package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.IeltsDatabase
import com.example.data.local.LearningState
import com.example.data.remote.LlmService
import com.example.data.repository.IeltsRepository
import com.example.data.repository.TopicStudyMode
import com.example.data.seed.SeedDataImporter
import com.example.domain.ControllableAppClock
import com.example.domain.SpacedRepetitionEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: IeltsDatabase
    private lateinit var clock: ControllableAppClock
    private lateinit var repository: IeltsRepository
    private lateinit var importer: SeedDataImporter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IeltsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = ControllableAppClock()
        val dao = db.ieltsDao()
        val srEngine = SpacedRepetitionEngine(clock)
        val llmService = LlmService(dao, clock)
        repository = IeltsRepository(dao, srEngine, llmService, clock)
        importer = SeedDataImporter(dao, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `full CUJ - seed import, learning flashcards, 1 day advance, mandatory review gate lock, partial mistakes still unlock, and kill-resume persistence`() = runBlocking {
        // Scenario 1: New user opens app -> seed dataset imports 30 unique entries across 15 topics, no review due
        val seedReport = importer.ensureSeeded(context)
        assertNotNull(seedReport)
        assertTrue(seedReport!!.validEntries.size >= 30)

        val initialGate = repository.checkAndPrepareMandatoryGate()
        assertFalse("New user should not be locked by mandatory review gate", initialGate.isLocked)

        // Scenario 2: User learns 2 vocabulary entries via Flashcards
        val (fcSession, fcCards) = repository.startOrResumeFlashcardSession(
            topicId = "topic_1_environment",
            studyMode = TopicStudyMode.CONTINUE_LEARNING,
            forceNew = true
        )
        assertTrue(fcCards.size >= 2)
        val word1 = fcCards[0].entry.id
        val word2 = fcCards[1].entry.id

        val afterCard1 = repository.recordFlashcardResponse(fcSession, word1, understood = true, nextIndex = 1, totalCards = fcCards.size)
        repository.recordFlashcardResponse(afterCard1, word2, understood = true, nextIndex = 2, totalCards = fcCards.size)

        // Still same day -> no review due yet
        assertFalse(repository.checkAndPrepareMandatoryGate().isLocked)

        // Scenario 3 & 4: Advance clock by 1 day -> first review becomes due and Mandatory Review Gate locks
        clock.advanceDays(1)
        val gateDay1 = repository.checkAndPrepareMandatoryGate()
        assertTrue("Mandatory review gate must lock when words are due", gateDay1.isLocked)
        assertEquals(2, gateDay1.questions.size)
        assertEquals(0, gateDay1.currentIndex)

        // Scenario 5 & 7: User answers first question CORRECTLY, then app is killed and reopened
        val sessionDay1 = gateDay1.activeSession!!
        val q1 = gateDay1.questions[0]
        val afterQ1Gate = repository.submitMandatoryGateAnswer(
            session = sessionDay1,
            vocabId = q1.vocabId,
            wasCorrect = true,
            nextIndex = 1,
            totalQuestions = 2
        )
        assertTrue("Gate remains locked after 1 of 2 questions", afterQ1Gate.isLocked)
        assertEquals(1, afterQ1Gate.currentIndex)

        // Simulate app kill and reopen -> checkAndPrepareMandatoryGate resumes at index 1
        val resumedGate = repository.checkAndPrepareMandatoryGate()
        assertTrue("Gate must remain locked after app restart when incomplete", resumedGate.isLocked)
        assertEquals(sessionDay1.id, resumedGate.activeSession?.id)
        assertEquals(1, resumedGate.currentIndex)

        // Scenario 5 & 6: User answers second question INCORRECTLY -> gate still unlocks upon completion,
        // and the incorrect word receives an earlier review date (1 day) while the correct word gets 3 days!
        val q2 = resumedGate.questions[1]
        val afterQ2Gate = repository.submitMandatoryGateAnswer(
            session = resumedGate.activeSession!!,
            vocabId = q2.vocabId,
            wasCorrect = false,
            nextIndex = 2,
            totalQuestions = 2
        )
        assertFalse("Gate must unlock upon completing all due items even with incorrect answers", afterQ2Gate.isLocked)

        val prog1 = db.ieltsDao().getProgressForVocab(q1.vocabId)!!
        val prog2 = db.ieltsDao().getProgressForVocab(q2.vocabId)!!
        assertEquals("Correctly reviewed word advances to 3-day interval", 3, prog1.currentIntervalDays)
        assertEquals("Incorrectly reviewed word is rescheduled to 1-day interval", 1, prog2.currentIntervalDays)
    }

    @Test
    fun `offline LLM fallback works when backend or API key is unavailable`() = runBlocking {
        importer.ensureSeeded(context)
        val enrichResult = repository.enrichVocabularyWithLlm("vocab_mitigate")
        assertTrue(enrichResult.isSuccess)
        assertTrue(enrichResult.getOrThrow().collocations.isNotEmpty())

        val comparisonText = repository.compareWordsHighThinking("vocab_mitigate", "alleviate")
        assertTrue(comparisonText.contains("mitigate"))
    }

    @Test
    fun `multiple app launches and ensureSeeded calls preserve topic cross-refs and never produce 0-card flashcard sessions`() = runBlocking {
        // Launch 1
        importer.ensureSeeded(context)
        val initialCrossRefs = db.ieltsDao().getCrossRefCount()
        assertTrue("Initial cross-refs must be populated", initialCrossRefs >= 25)

        // Launch 2 & 3 (previously wiped cross-refs due to OnConflictStrategy.REPLACE cascade on topics)
        importer.ensureSeeded(context)
        importer.ensureSeeded(context)
        val crossRefsAfterRelaunch = db.ieltsDao().getCrossRefCount()
        assertEquals("Cross-refs must not be wiped on subsequent app launches", initialCrossRefs, crossRefsAfterRelaunch)

        // Verify all 15 topics return non-empty flashcard decks in CONTINUE_LEARNING mode
        for (topic in com.example.data.seed.SeedDataValidator.OFFICIAL_TOPICS) {
            val (session, cards) = repository.startOrResumeFlashcardSession(
                topicId = topic.id,
                studyMode = TopicStudyMode.CONTINUE_LEARNING,
                forceNew = true
            )
            assertTrue("Topic ${topic.id} must have flashcards to study", cards.isNotEmpty())
            assertEquals("IN_PROGRESS", session.status)
        }
    }
}

