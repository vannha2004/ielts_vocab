package com.example.data.seed

import android.content.Context
import com.example.data.local.IeltsDao
import com.example.data.local.LearningState
import com.example.data.local.UserSettingsEntity
import com.example.data.local.UserVocabularyProgressEntity
import com.example.domain.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SeedDataImporter(
    private val dao: IeltsDao,
    private val clock: AppClock
) {
    private val seedMutex = Mutex()

    /**
     * Ensures official 15 IELTS topics, default user settings, and starter vocabulary dataset +
     * topic cross-references are populated into local SQLite storage without calling any LLM API.
     * Protected by [seedMutex] so concurrent callers always wait until seeding/repair is complete.
     */
    suspend fun ensureSeeded(context: Context): SeedValidationReport? = withContext(Dispatchers.IO) {
        seedMutex.withLock {
            // 1. Ensure the 15 official topics exist (INSERT OR IGNORE so ON DELETE CASCADE never fires)
            dao.insertTopics(SeedDataValidator.OFFICIAL_TOPICS)

            // 2. Ensure default user settings exist
            if (dao.getUserSettings() == null) {
                dao.upsertUserSettings(UserSettingsEntity(id = 1))
            }

            // 3. Clean up any empty IN_PROGRESS flashcard sessions
            dao.cleanupEmptyFlashcardSessions(clock.nowMillis())

            // 4. Seed or auto-repair starter vocabulary and topic cross-references if either is missing/incomplete
            val existingVocabCount = dao.getVocabularyCount()
            val existingCrossRefCount = dao.getCrossRefCount()
            if (existingVocabCount >= 30 && existingCrossRefCount >= 45) {
                return@withLock null
            }

            val rawJson = context.assets.open("seed_vocabulary.json").bufferedReader().use { it.readText() }
            val report = SeedDataValidator.validateJsonSeedData(
                rawJson = rawJson,
                timestampMillis = clock.nowMillis(),
                existingNormalizedHeadwords = emptySet()
            )
            persistValidReport(report)
            report
        }
    }

    /**
     * Validates and imports vocabulary records from a JSON string, deduplicating against
     * already stored normalized headwords so master entries are never duplicated.
     */
    suspend fun importFromJson(rawJson: String): SeedValidationReport = withContext(Dispatchers.IO) {
        val existingNormalized = dao.getAllVocabulary().map { it.normalizedHeadword }.toSet()
        val report = SeedDataValidator.validateJsonSeedData(
            rawJson = rawJson,
            timestampMillis = clock.nowMillis(),
            existingNormalizedHeadwords = existingNormalized
        )
        persistValidReport(report)
        report
    }

    /**
     * Validates and imports vocabulary records from a CSV string.
     */
    suspend fun importFromCsv(rawCsv: String): SeedValidationReport = withContext(Dispatchers.IO) {
        val existingNormalized = dao.getAllVocabulary().map { it.normalizedHeadword }.toSet()
        val report = SeedDataValidator.validateCsvSeedData(
            rawCsv = rawCsv,
            timestampMillis = clock.nowMillis(),
            existingNormalizedHeadwords = existingNormalized
        )
        persistValidReport(report)
        report
    }

    private suspend fun persistValidReport(report: SeedValidationReport) {
        if (report.validEntries.isNotEmpty()) {
            dao.upsertVocabularyBatch(report.validEntries)
            dao.insertCrossRefs(report.crossRefs)
            val initialProgress = report.validEntries.map { entry ->
                UserVocabularyProgressEntity(
                    vocabId = entry.id,
                    learningState = LearningState.NEW.name
                )
            }
            dao.insertInitialProgressBatch(initialProgress)
        }
    }
}
