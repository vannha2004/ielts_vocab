package com.example.domain

import com.example.data.local.VocabularyEntity
import kotlinx.serialization.Serializable

enum class QuizQuestionType(val vietnameseLabel: String) {
    MULTIPLE_CHOICE_EN_TO_VI("Trắc nghiệm: Anh → Việt"),
    MULTIPLE_CHOICE_VI_TO_EN("Trắc nghiệm: Việt → Anh"),
    FILL_IN_THE_BLANK("Điền từ vào câu IELTS"),
    TYPED_ANSWER("Gõ từ tiếng Anh")
}

@Serializable
data class QuizQuestionItem(
    val id: String,
    val vocabId: String,
    val headword: String,
    val ipa: String?,
    val partOfSpeech: String,
    val questionType: QuizQuestionType,
    val prompt: String,
    val subPrompt: String? = null,
    val options: List<String> = emptyList(),
    val correctAnswer: String,
    val explanation: String,
    val source: String = "LOCAL"
)

data class AnswerEvaluationResult(
    val isCorrect: Boolean,
    val normalizedUserAnswer: String,
    val normalizedExpectedAnswer: String,
    val feedbackMessage: String,
    val explanation: String
)

/**
 * Generates deterministic local quizzes and validates both user answers and LLM-generated quiz questions.
 */
object QuizEngine {

    /**
     * Normalizes whitespace and letter case without accepting arbitrary misspellings.
     */
    fun normalizeAnswer(input: String): String {
        return input
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    /**
     * Evaluates a user's quiz answer strictly (normalized case and whitespace, no arbitrary typos).
     */
    fun evaluateAnswer(
        question: QuizQuestionItem,
        userAnswer: String
    ): AnswerEvaluationResult {
        val normUser = normalizeAnswer(userAnswer)
        val normExpected = normalizeAnswer(question.correctAnswer)
        val normHeadword = normalizeAnswer(question.headword)

        val isCorrect = when (question.questionType) {
            QuizQuestionType.MULTIPLE_CHOICE_EN_TO_VI,
            QuizQuestionType.MULTIPLE_CHOICE_VI_TO_EN -> normUser == normExpected
            QuizQuestionType.FILL_IN_THE_BLANK,
            QuizQuestionType.TYPED_ANSWER -> normUser == normExpected || normUser == normHeadword
        }

        val feedback = if (isCorrect) {
            "Chính xác! Đáp án đúng là \"${question.correctAnswer}\"."
        } else {
            "Chưa chính xác. Đáp án đúng là \"${question.correctAnswer}\"."
        }

        return AnswerEvaluationResult(
            isCorrect = isCorrect,
            normalizedUserAnswer = normUser,
            normalizedExpectedAnswer = normExpected,
            feedbackMessage = feedback,
            explanation = question.explanation
        )
    }

    /**
     * Generates a diverse set of quiz questions from target vocabulary and the full vocabulary pool for distractors.
     * Never produces duplicate options within a question.
     */
    fun generateQuestionsForVocabulary(
        targetVocabList: List<VocabularyEntity>,
        distractorPool: List<VocabularyEntity>,
        preferredType: QuizQuestionType? = null
    ): List<QuizQuestionItem> {
        if (targetVocabList.isEmpty()) return emptyList()
        val combinedPool = (distractorPool + targetVocabList).distinctBy { it.id }

        return targetVocabList.mapIndexed { index, target ->
            val type = preferredType ?: when (index % 4) {
                0 -> QuizQuestionType.MULTIPLE_CHOICE_EN_TO_VI
                1 -> QuizQuestionType.FILL_IN_THE_BLANK
                2 -> QuizQuestionType.TYPED_ANSWER
                else -> QuizQuestionType.MULTIPLE_CHOICE_VI_TO_EN
            }
            buildQuestionForEntry(target, combinedPool, type, index)
        }
    }

    fun buildQuestionForEntry(
        target: VocabularyEntity,
        pool: List<VocabularyEntity>,
        type: QuizQuestionType,
        seedIndex: Int = 0
    ): QuizQuestionItem {
        val baseExplanation = buildString {
            append("${target.headword} (${target.partOfSpeech}) ")
            if (!target.ipa.isNullOrBlank()) append("${target.ipa} ")
            append(": ${target.vietnameseMeaning}. ")
            append("Định nghĩa: ${target.englishDefinition}. ")
            append("Ví dụ: \"${target.exampleSentence}\" (${target.vietnameseExampleTranslation})")
        }

        return when (type) {
            QuizQuestionType.MULTIPLE_CHOICE_EN_TO_VI -> {
                val distractors = selectPlausibleDistractors(target, pool, count = 3, seed = seedIndex)
                    .map { it.vietnameseMeaning }
                    .filter { normalizeAnswer(it) != normalizeAnswer(target.vietnameseMeaning) }
                    .distinctBy { normalizeAnswer(it) }
                    .take(3)

                val fallbackDistractors = listOf(
                    "sự thay đổi tạm thời trong ngắn hạn",
                    "phương pháp giải trí truyền thống",
                    "hệ thống giao thông nội đô"
                ).filter { normalizeAnswer(it) != normalizeAnswer(target.vietnameseMeaning) }

                val finalDistractors = (distractors + fallbackDistractors)
                    .distinctBy { normalizeAnswer(it) }
                    .take(3)

                val options = (finalDistractors + target.vietnameseMeaning)
                    .distinctBy { normalizeAnswer(it) }
                    .sortedBy { (it.hashCode() xor (seedIndex + 17)) }

                QuizQuestionItem(
                    id = "q_mc_en_vi_${target.id}",
                    vocabId = target.id,
                    headword = target.headword,
                    ipa = target.ipa,
                    partOfSpeech = target.partOfSpeech,
                    questionType = QuizQuestionType.MULTIPLE_CHOICE_EN_TO_VI,
                    prompt = "Chọn nghĩa tiếng Việt chính xác nhất cho từ học thuật sau:",
                    subPrompt = "${target.headword} (${target.partOfSpeech}) ${target.ipa ?: ""}".trim(),
                    options = options,
                    correctAnswer = target.vietnameseMeaning,
                    explanation = baseExplanation
                )
            }

            QuizQuestionType.MULTIPLE_CHOICE_VI_TO_EN -> {
                val distractors = selectPlausibleDistractors(target, pool, count = 3, seed = seedIndex + 7)
                    .map { it.headword }
                    .filter { normalizeAnswer(it) != normalizeAnswer(target.headword) }
                    .distinctBy { normalizeAnswer(it) }
                    .take(3)

                val fallbackDistractors = listOf("subsequent", "conventional", "inevitable", "sustainable")
                    .filter { normalizeAnswer(it) != normalizeAnswer(target.headword) }

                val finalDistractors = (distractors + fallbackDistractors)
                    .distinctBy { normalizeAnswer(it) }
                    .take(3)

                val options = (finalDistractors + target.headword)
                    .distinctBy { normalizeAnswer(it) }
                    .sortedBy { (it.hashCode() xor (seedIndex + 31)) }

                QuizQuestionItem(
                    id = "q_mc_vi_en_${target.id}",
                    vocabId = target.id,
                    headword = target.headword,
                    ipa = target.ipa,
                    partOfSpeech = target.partOfSpeech,
                    questionType = QuizQuestionType.MULTIPLE_CHOICE_VI_TO_EN,
                    prompt = "Từ tiếng Anh nào tương ứng với nghĩa tiếng Việt và định nghĩa sau?",
                    subPrompt = "${target.vietnameseMeaning}\n(${target.englishDefinition})",
                    options = options,
                    correctAnswer = target.headword,
                    explanation = baseExplanation
                )
            }

            QuizQuestionType.FILL_IN_THE_BLANK -> {
                val blankedSentence = maskTargetWordInSentence(target.exampleSentence, target.headword)
                val distractors = selectPlausibleDistractors(target, pool, count = 3, seed = seedIndex + 13)
                    .map { it.headword }
                    .filter { normalizeAnswer(it) != normalizeAnswer(target.headword) }
                    .distinctBy { normalizeAnswer(it) }
                    .take(3)

                val fallbackDistractors = listOf("mitigate", "allocate", "fluctuate", "implement")
                    .filter { normalizeAnswer(it) != normalizeAnswer(target.headword) }

                val options = ((distractors + fallbackDistractors).distinctBy { normalizeAnswer(it) }.take(3) + target.headword)
                    .distinctBy { normalizeAnswer(it) }
                    .sortedBy { (it.hashCode() xor (seedIndex + 53)) }

                QuizQuestionItem(
                    id = "q_blank_${target.id}",
                    vocabId = target.id,
                    headword = target.headword,
                    ipa = target.ipa,
                    partOfSpeech = target.partOfSpeech,
                    questionType = QuizQuestionType.FILL_IN_THE_BLANK,
                    prompt = "Điền từ học thuật phù hợp vào chỗ trống trong câu IELTS sau:",
                    subPrompt = "$blankedSentence\n\nGợi ý nghĩa: ${target.vietnameseMeaning} (${target.partOfSpeech})",
                    options = options,
                    correctAnswer = target.headword,
                    explanation = baseExplanation
                )
            }

            QuizQuestionType.TYPED_ANSWER -> {
                QuizQuestionItem(
                    id = "q_typed_${target.id}",
                    vocabId = target.id,
                    headword = target.headword,
                    ipa = target.ipa,
                    partOfSpeech = target.partOfSpeech,
                    questionType = QuizQuestionType.TYPED_ANSWER,
                    prompt = "Nhập từ tiếng Anh chính xác tương ứng với nghĩa và ngữ cảnh sau:",
                    subPrompt = "Nghĩa tiếng Việt: ${target.vietnameseMeaning}\nTừ loại: ${target.partOfSpeech} | Định nghĩa: ${target.englishDefinition}",
                    options = emptyList(),
                    correctAnswer = target.headword,
                    explanation = baseExplanation
                )
            }
        }
    }

    fun maskTargetWordInSentence(sentence: String, headword: String): String {
        val escaped = Regex.escape(headword.trim())
        // Match exact headword or common inflections (s, es, ed, ing, d)
        val regex = Regex("\\b$escaped(s|es|ed|ing|d)?\\b", RegexOption.IGNORE_CASE)
        return if (regex.containsMatchIn(sentence)) {
            regex.replaceFirst(sentence, "________")
        } else {
            // If headword stem changed slightly, mask prefix or append blank prompt
            val stem = headword.trim().take((headword.length - 2).coerceAtLeast(4))
            val stemRegex = Regex("\\b${Regex.escape(stem)}[a-zA-Z]*\\b", RegexOption.IGNORE_CASE)
            if (stemRegex.containsMatchIn(sentence)) {
                stemRegex.replaceFirst(sentence, "________")
            } else {
                "$sentence (Từ cần điền: ________)"
            }
        }
    }

    private fun selectPlausibleDistractors(
        target: VocabularyEntity,
        pool: List<VocabularyEntity>,
        count: Int,
        seed: Int
    ): List<VocabularyEntity> {
        val candidates = pool.filter {
            it.id != target.id &&
                normalizeAnswer(it.headword) != normalizeAnswer(target.headword) &&
                normalizeAnswer(it.vietnameseMeaning) != normalizeAnswer(target.vietnameseMeaning)
        }
        // Prefer same part of speech and difficulty level for plausible academic distractors
        val samePos = candidates.filter { it.partOfSpeech.equals(target.partOfSpeech, ignoreCase = true) }
        val ordered = (samePos + candidates).distinctBy { it.id }
        if (ordered.size <= count) return ordered
        val rotated = ordered.indices.map { i -> ordered[(i + seed) % ordered.size] }
        return rotated.take(count)
    }

    /**
     * Validates an LLM-generated quiz question before caching or presenting it to the user.
     */
    fun validateLlmQuestion(question: QuizQuestionItem, targetVocab: VocabularyEntity): Boolean {
        if (question.prompt.isBlank() || question.correctAnswer.isBlank() || question.explanation.isBlank()) {
            return false
        }
        if (question.vocabId != targetVocab.id) {
            return false
        }
        if (question.questionType != QuizQuestionType.TYPED_ANSWER) {
            if (question.options.size < 4) return false
            val distinctNormalized = question.options.map { normalizeAnswer(it) }.distinct()
            if (distinctNormalized.size != question.options.size) return false // Reject duplicate options
            if (distinctNormalized.none { it == normalizeAnswer(question.correctAnswer) }) return false
        }
        return true
    }
}
