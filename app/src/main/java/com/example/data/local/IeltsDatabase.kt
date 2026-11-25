package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VocabularyEntity::class,
        TopicEntity::class,
        VocabularyTopicCrossRef::class,
        UserVocabularyProgressEntity::class,
        ReviewHistoryEntity::class,
        QuizQuestionEntity::class,
        QuizAttemptEntity::class,
        ReviewSessionEntity::class,
        UserSettingsEntity::class,
        LlmCacheEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class IeltsDatabase : RoomDatabase() {
    abstract fun ieltsDao(): IeltsDao

    companion object {
        @Volatile
        private var INSTANCE: IeltsDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vocabulary_entries_difficulty_level` ON `vocabulary_entries` (`difficulty_level`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `user_settings` ADD COLUMN `custom_api_key` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `user_settings` ADD COLUMN `preferred_model` TEXT NOT NULL DEFAULT 'gemini-3.8-flash'"
                )
            }
        }

        fun getInstance(context: Context): IeltsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IeltsDatabase::class.java,
                    "ielts_vocab_academic.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
