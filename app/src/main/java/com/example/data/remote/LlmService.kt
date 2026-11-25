package com.example.data.remote

import com.example.BuildConfig
import com.example.data.local.IeltsDao
import com.example.data.local.LlmCacheEntity
import com.example.data.local.UserSettingsEntity
import com.example.data.local.VocabularyEntity
import com.example.domain.AppClock
import com.example.domain.QuizEngine
import com.example.domain.QuizQuestionItem
import com.example.domain.QuizQuestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class ThinkingLevel(val apiValue: String) {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH")
}

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<JsonObject>? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null
)

@Serializable
data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
    val temperature: Float? = null,
    val thinkingConfig: ThinkingConfig? = null
    // Note: maxOutputTokens intentionally omitted per Gemini high-thinking guidelines
)

@Serializable
data class ThinkingConfig(
    val thinkingLevel: String
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList()
)

@Serializable
data class Candidate(
    val content: Content? = null,
    val groundingMetadata: GroundingMetadata? = null
)

@Serializable
data class GroundingMetadata(
    val webSearchQueries: List<String>? = null,
    val groundingChunks: List<GroundingChunk>? = null
)

@Serializable
data class GroundingChunk(
    val web: WebSource? = null
)

@Serializable
data class WebSource(
    val uri: String? = null,
    val title: String? = null
)

@Serializable
data class VocabularyEnrichmentPayload(
    @SerialName("vietnamese_explanation") val vietnameseExplanation: String,
    @SerialName("example_sentence") val exampleSentence: String,
    @SerialName("vietnamese_example_translation") val vietnameseExampleTranslation: String,
    @SerialName("collocations") val collocations: List<String>,
    @SerialName("word_family") val wordFamily: List<String>,
    @SerialName("common_mistakes") val commonMistakes: String
)

@Serializable
data class LlmGeneratedQuizDto(
    @SerialName("prompt") val prompt: String = "",
    @SerialName("sub_prompt") val subPrompt: String = "",
    @SerialName("options") val options: List<String> = emptyList(),
    @SerialName("correct_answer") val correctAnswer: String = "",
    @SerialName("explanation") val explanation: String = ""
)

@Serializable
data class GroundedTopicInsight(
    val text: String,
    val sources: List<String> = emptyList(),
    val fromCache: Boolean = false,
    val isFallback: Boolean = false
)

data class LlmConfigStatus(
    val providerName: String,
    val isConfigured: Boolean,
    val hasInAppKey: Boolean,
    val selectedModel: String,
    val complexModel: String,
    val generalModel: String,
    val fastModel: String,
    val statusDescription: String
)

/**
 * Provider-agnostic LLM integration supporting user-selectable Gemini models:
 * - gemini-4-argon (with ThinkingLevel.HIGH for complex queries)
 * - gemini-3.8-flash (general tasks, structured JSON, and Google Search grounding)
 * - gemini-3.6-flash / gemini-3.5-flash-lite / gemini-3.1-flash-lite
 * Supports both in-app user-supplied GEMINI_API_KEY (saved in local SQLite UserSettingsEntity)
 * and BuildConfig environment variables, with SQLite caching and deterministic offline fallbacks.
 */
class LlmService(
    private val dao: IeltsDao,
    private val clock: AppClock
) {
    companion object {
        const val MODEL_COMPLEX_ARGON = "gemini-4-argon"
        const val MODEL_GENERAL_FLASH = "gemini-3.8-flash"
        const val MODEL_FLASH_3_6 = "gemini-3.6-flash"
        const val MODEL_FLASH_3_5_LITE = "gemini-3.5-flash-lite"
        const val MODEL_FAST_LITE = "gemini-3.1-flash-lite"

        val AVAILABLE_MODELS = listOf(
            MODEL_GENERAL_FLASH to "Gemini 3.8 Flash",
            MODEL_COMPLEX_ARGON to "Gemini 4 Argon (Paid • High Thinking)",
            MODEL_FLASH_3_6 to "Gemini 3.6 Flash",
            MODEL_FLASH_3_5_LITE to "Gemini 3.5 Flash Lite"
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getConfigurationStatus(settings: UserSettingsEntity? = null): LlmConfigStatus {
        val inAppKey = settings?.customApiKey?.trim().orEmpty()
        val chosenModel = settings?.preferredModel?.ifBlank { MODEL_GENERAL_FLASH } ?: MODEL_GENERAL_FLASH
        val geminiKey = BuildConfig.GEMINI_API_KEY
        val llmKey = BuildConfig.LLM_API_KEY
        val provider = BuildConfig.LLM_PROVIDER.ifBlank { "gemini" }.lowercase()

        val hasInAppKey = inAppKey.isNotBlank()
        val hasBuildConfigGeminiKey = geminiKey.isNotBlank() &&
            geminiKey != "MY_GEMINI_API_KEY" &&
            !geminiKey.startsWith("YOUR_")
        val hasBuildConfigCustomKey = llmKey.isNotBlank() &&
            llmKey != "MY_LLM_API_KEY" &&
            !llmKey.startsWith("YOUR_")

        val isConfigured = hasInAppKey || hasBuildConfigGeminiKey || hasBuildConfigCustomKey
        val keySourceLabel = when {
            hasInAppKey -> "Đã nhập khóa trực tiếp trong ứng dụng (••••${inAppKey.takeLast(4)})"
            hasBuildConfigGeminiKey || hasBuildConfigCustomKey -> "Đã nạp khóa từ biến môi trường BuildConfig"
            else -> "Chưa nhập khóa API"
        }

        val desc = if (isConfigured) {
            "$keySourceLabel • Model đang chọn: $chosenModel (kết hợp $MODEL_COMPLEX_ARGON High Thinking cho phân tích chuyên sâu & $MODEL_GENERAL_FLASH cho Google Search Grounding)."
        } else {
            "Chưa nhập GEMINI_API_KEY. Hãy dán khóa API của bạn vào ô bên trên và chọn Gemini 3.8 Flash hoặc Gemini 4 Argon để bật AI trực tuyến (chế độ Ngoại tuyến vẫn hoạt động 100%)."
        }

        return LlmConfigStatus(
            providerName = provider.uppercase(),
            isConfigured = isConfigured,
            hasInAppKey = hasInAppKey,
            selectedModel = chosenModel,
            complexModel = MODEL_COMPLEX_ARGON,
            generalModel = MODEL_GENERAL_FLASH,
            fastModel = MODEL_FAST_LITE,
            statusDescription = desc
        )
    }

    private suspend fun resolveApiKeyAndModel(): Pair<String?, String> {
        val settings = dao.getUserSettings()
        val inAppKey = settings?.customApiKey?.trim().orEmpty()
        val preferredModel = settings?.preferredModel?.trim()?.ifBlank { MODEL_GENERAL_FLASH } ?: MODEL_GENERAL_FLASH

        if (inAppKey.isNotBlank()) {
            return inAppKey to preferredModel
        }
        val geminiKey = BuildConfig.GEMINI_API_KEY
        if (geminiKey.isNotBlank() && geminiKey != "MY_GEMINI_API_KEY") {
            return geminiKey to preferredModel
        }
        val llmKey = BuildConfig.LLM_API_KEY
        if (llmKey.isNotBlank() && llmKey != "MY_LLM_API_KEY") {
            return llmKey to preferredModel
        }
        return null to preferredModel
    }

    /**
     * A. Vocabulary Enrichment using the user's selected model (gemini-3.8-flash or gemini-4-argon)
     * with strict JSON schema validation.
     */
    suspend fun enrichVocabulary(entry: VocabularyEntity): Result<VocabularyEnrichmentPayload> = withContext(Dispatchers.IO) {
        val (apiKey, activeModel) = resolveApiKeyAndModel()
        val cacheKey = sha256("ENRICH_V2_${activeModel}_${entry.id}_${entry.headword}")
        dao.getLlmCache(cacheKey)?.let { cached ->
            runCatching {
                return@withContext Result.success(json.decodeFromString<VocabularyEnrichmentPayload>(cached.responseJson))
            }
        }

        if (apiKey == null) {
            return@withContext Result.success(buildFallbackEnrichment(entry))
        }

        val prompt = """
            Bạn là giảng viên IELTS Academic 8.5+ dạy học viên Việt Nam (mục tiêu Band 5.5 lên 7.0+).
            Hãy bổ sung thông tin học thuật cho từ vựng tiếng Anh sau (không làm sai lệch định nghĩa gốc):
            - Từ vựng: ${entry.headword} (${entry.partOfSpeech})
            - Nghĩa hiện tại: ${entry.vietnameseMeaning}
            - Định nghĩa: ${entry.englishDefinition}
            Trả về đúng định dạng JSON được yêu cầu.
        """.trimIndent()

        val schema = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("vietnamese_explanation") { put("type", "STRING") }
                putJsonObject("example_sentence") { put("type", "STRING") }
                putJsonObject("vietnamese_example_translation") { put("type", "STRING") }
                putJsonObject("collocations") {
                    put("type", "ARRAY")
                    putJsonObject("items") { put("type", "STRING") }
                }
                putJsonObject("word_family") {
                    put("type", "ARRAY")
                    putJsonObject("items") { put("type", "STRING") }
                }
                putJsonObject("common_mistakes") { put("type", "STRING") }
            }
            putJsonArray("required") {
                add("vietnamese_explanation")
                add("example_sentence")
                add("vietnamese_example_translation")
                add("collocations")
                add("word_family")
                add("common_mistakes")
            }
        }

        val thinkingConfig = if (activeModel == MODEL_COMPLEX_ARGON) {
            ThinkingConfig(thinkingLevel = ThinkingLevel.HIGH.apiValue)
        } else null

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = schema,
                temperature = 0.3f,
                thinkingConfig = thinkingConfig
            )
        )

        val rawResponse = executeGeminiCallWithRetry(activeModel, apiKey, request)
        if (rawResponse != null) {
            val text = rawResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                val parsed = runCatching { json.decodeFromString<VocabularyEnrichmentPayload>(text) }.getOrNull()
                if (parsed != null && validateEnrichmentPayload(parsed)) {
                    dao.putLlmCache(
                        LlmCacheEntity(
                            cacheKey = cacheKey,
                            featureType = "ENRICHMENT",
                            modelUsed = activeModel,
                            responseJson = text,
                            createdAt = clock.nowMillis()
                        )
                    )
                    return@withContext Result.success(parsed)
                }
            }
        }

        Result.success(buildFallbackEnrichment(entry))
    }

    fun validateEnrichmentPayload(payload: VocabularyEnrichmentPayload): Boolean {
        return payload.vietnameseExplanation.isNotBlank() &&
            payload.exampleSentence.isNotBlank() &&
            payload.vietnameseExampleTranslation.isNotBlank() &&
            payload.collocations.isNotEmpty() &&
            payload.commonMistakes.isNotBlank()
    }

    /**
     * B. Structured Quiz Question Generation using user's selected model, validated by QuizEngine before use.
     */
    suspend fun generateValidatedQuizQuestion(
        target: VocabularyEntity,
        distractorPool: List<VocabularyEntity>,
        questionType: QuizQuestionType = QuizQuestionType.MULTIPLE_CHOICE_EN_TO_VI
    ): QuizQuestionItem = withContext(Dispatchers.IO) {
        val (apiKey, activeModel) = resolveApiKeyAndModel()
        val cacheKey = sha256("QUIZ_GEN_V2_${activeModel}_${target.id}_${questionType.name}")
        dao.getLlmCache(cacheKey)?.let { cached ->
            val cachedItem = runCatching { json.decodeFromString<QuizQuestionItem>(cached.responseJson) }.getOrNull()
            if (cachedItem != null && QuizEngine.validateLlmQuestion(cachedItem, target)) {
                return@withContext cachedItem
            }
        }

        if (apiKey != null) {
            val prompt = """
                Tạo 1 câu hỏi trắc nghiệm IELTS Academic (4 lựa chọn không trùng nhau) cho từ '${target.headword}' (${target.partOfSpeech}).
                Nghĩa xác thực: ${target.vietnameseMeaning}. Định nghĩa: ${target.englishDefinition}.
                Loại câu hỏi: ${questionType.vietnameseLabel}.
            """.trimIndent()

            val schema = buildJsonObject {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("prompt") { put("type", "STRING") }
                    putJsonObject("sub_prompt") { put("type", "STRING") }
                    putJsonObject("options") {
                        put("type", "ARRAY")
                        putJsonObject("items") { put("type", "STRING") }
                    }
                    putJsonObject("correct_answer") { put("type", "STRING") }
                    putJsonObject("explanation") { put("type", "STRING") }
                }
                putJsonArray("required") {
                    add("prompt")
                    add("sub_prompt")
                    add("options")
                    add("correct_answer")
                    add("explanation")
                }
            }

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    responseSchema = schema,
                    temperature = 0.2f
                )
            )

            val rawResponse = executeGeminiCallWithRetry(activeModel, apiKey, request)
            val text = rawResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                val dto = runCatching { json.decodeFromString<LlmGeneratedQuizDto>(text) }.getOrNull()
                if (dto != null) {
                    val candidateItem = QuizQuestionItem(
                        id = "llm_q_${target.id}_${questionType.name}",
                        vocabId = target.id,
                        headword = target.headword,
                        ipa = target.ipa,
                        partOfSpeech = target.partOfSpeech,
                        questionType = questionType,
                        prompt = dto.prompt,
                        subPrompt = dto.subPrompt,
                        options = dto.options.distinct(),
                        correctAnswer = dto.correctAnswer,
                        explanation = dto.explanation,
                        source = "LLM_VALIDATED"
                    )
                    if (QuizEngine.validateLlmQuestion(candidateItem, target)) {
                        dao.putLlmCache(
                            LlmCacheEntity(
                                cacheKey = cacheKey,
                                featureType = "QUIZ_GEN",
                                modelUsed = activeModel,
                                responseJson = json.encodeToString(candidateItem),
                                createdAt = clock.nowMillis()
                            )
                        )
                        return@withContext candidateItem
                    }
                }
            }
        }

        QuizEngine.buildQuestionForEntry(target, distractorPool, questionType)
    }

    /**
     * C. Complex Vocabulary Nuance Comparison & Deep Academic Analysis using gemini-4-argon
     * with ThinkingLevel.HIGH (and no maxOutputTokens).
     */
    suspend fun compareWordsHighThinking(
        wordA: VocabularyEntity,
        wordBQuery: String
    ): String = withContext(Dispatchers.IO) {
        val cleanQuery = wordBQuery.trim().ifBlank {
            wordA.synonymsJson.removeSurrounding("[", "]").split(",").firstOrNull()?.replace("\"", "")?.trim()
                ?: "similar academic words"
        }
        val cacheKey = sha256("COMPARE_HIGH_THINKING_${wordA.headword.lowercase()}_${cleanQuery.lowercase()}")
        dao.getLlmCache(cacheKey)?.let { return@withContext it.responseJson }

        val (apiKey, _) = resolveApiKeyAndModel()
        if (apiKey != null) {
            val prompt = """
                Phân tích chuyên sâu sự khác biệt về sắc thái nghĩa (nuance), ngữ cảnh học thuật (register), collocations và cách dùng trong IELTS Academic Writing/Speaking (Band 7.0+) giữa:
                1. "${wordA.headword}" (${wordA.partOfSpeech} - ${wordA.vietnameseMeaning})
                2. "$cleanQuery"
                Trình bày rõ ràng bằng tiếng Việt kèm ví dụ câu tiếng Anh chuẩn học thuật.
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(
                    thinkingConfig = ThinkingConfig(thinkingLevel = ThinkingLevel.HIGH.apiValue)
                )
            )

            val response = executeGeminiCallWithRetry(MODEL_COMPLEX_ARGON, apiKey, request)
            val text = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                dao.putLlmCache(
                    LlmCacheEntity(
                        cacheKey = cacheKey,
                        featureType = "WORD_COMPARISON",
                        modelUsed = MODEL_COMPLEX_ARGON,
                        responseJson = text,
                        createdAt = clock.nowMillis()
                    )
                )
                return@withContext text
            }
        }

        buildString {
            appendLine("Phân tích so sánh ngoại tuyến: \"${wordA.headword}\" và \"$cleanQuery\"")
            appendLine("• ${wordA.headword} (${wordA.partOfSpeech}): ${wordA.vietnameseMeaning} — ${wordA.englishDefinition}.")
            appendLine("• Ngữ cảnh IELTS Academic: \"${wordA.exampleSentence}\"")
            if (!wordA.commonMistakes.isNullOrBlank()) {
                appendLine("• Lưu ý cách dùng: ${wordA.commonMistakes}")
            }
            appendLine("• Gợi ý nâng band 7.0+: Hãy ưu tiên dùng '${wordA.headword}' đúng với các cụm collocation học thuật đã được xác thực thay vì thay thế máy móc bằng từ đồng nghĩa '$cleanQuery'.")
        }
    }

    /**
     * D. Fast Memory Tip / Mnemonic using gemini-3.1-flash-lite for instant flashcard assistance.
     */
    suspend fun generateQuickMemoryTip(entry: VocabularyEntity): String = withContext(Dispatchers.IO) {
        val cacheKey = sha256("FAST_TIP_${entry.id}")
        dao.getLlmCache(cacheKey)?.let { return@withContext it.responseJson }

        val (apiKey, _) = resolveApiKeyAndModel()
        if (apiKey != null) {
            val prompt = """
                Tạo một mẹo ghi nhớ nhanh (mnemonic) ngắn gọn trong 2-3 dòng bằng tiếng Việt để nhớ từ IELTS "${entry.headword}" (${entry.vietnameseMeaning}) và 1 cụm collocation tiêu biểu.
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(temperature = 0.4f)
            )

            val response = executeGeminiCallWithRetry(MODEL_FAST_LITE, apiKey, request)
            val text = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                dao.putLlmCache(
                    LlmCacheEntity(
                        cacheKey = cacheKey,
                        featureType = "FAST_TIP",
                        modelUsed = MODEL_FAST_LITE,
                        responseJson = text,
                        createdAt = clock.nowMillis()
                    )
                )
                return@withContext text
            }
        }

        "Mẹo ghi nhớ nhanh (${entry.headword}): Gắn từ '${entry.headword}' (${entry.partOfSpeech}) với nghĩa '${entry.vietnameseMeaning}' qua câu mẫu: \"${entry.exampleSentence}\"."
    }

    /**
     * E. Up-to-date IELTS Academic Usage & Topic Context using gemini-3.8-flash with Google Search Grounding.
     */
    suspend fun fetchSearchGroundedUsage(entry: VocabularyEntity): GroundedTopicInsight = withContext(Dispatchers.IO) {
        val cacheKey = sha256("SEARCH_GROUNDED_${entry.id}")
        dao.getLlmCache(cacheKey)?.let { cached ->
            runCatching {
                return@withContext json.decodeFromString<GroundedTopicInsight>(cached.responseJson).copy(fromCache = true)
            }
        }

        val (apiKey, _) = resolveApiKeyAndModel()
        if (apiKey != null) {
            val prompt = """
                Tìm kiếm thông tin thực tế mới nhất và nêu 2 luận điểm IELTS Writing Task 2 / Speaking Part 3 hiện đại có sử dụng từ học thuật "${entry.headword}" (${entry.vietnameseMeaning}). Giải thích ngắn gọn bằng tiếng Việt.
            """.trimIndent()

            val googleSearchTool = buildJsonObject {
                putJsonObject("googleSearch") {}
            }

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                tools = listOf(googleSearchTool)
            )

            val response = executeGeminiCallWithRetry(MODEL_GENERAL_FLASH, apiKey, request)
            val candidate = response?.candidates?.firstOrNull()
            val text = candidate?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                val urls = candidate.groundingMetadata?.groundingChunks
                    ?.mapNotNull { chunk -> chunk.web?.let { "${it.title ?: "Nguồn tham khảo"}: ${it.uri}" } }
                    ?.distinct()
                    .orEmpty()
                val insight = GroundedTopicInsight(
                    text = text,
                    sources = urls,
                    fromCache = false,
                    isFallback = false
                )
                dao.putLlmCache(
                    LlmCacheEntity(
                        cacheKey = cacheKey,
                        featureType = "SEARCH_GROUNDING",
                        modelUsed = MODEL_GENERAL_FLASH,
                        responseJson = json.encodeToString(insight),
                        createdAt = clock.nowMillis()
                    )
                )
                return@withContext insight
            }
        }

        GroundedTopicInsight(
            text = "Ứng dụng trong IELTS Writing Task 2: Từ \"${entry.headword}\" (${entry.vietnameseMeaning}) thường được dùng để lập luận học thuật chặt chẽ. Ví dụ: \"${entry.exampleSentence}\" (${entry.vietnameseExampleTranslation}).",
            sources = listOfNotNull(entry.sourceName, entry.sourceUrl),
            fromCache = false,
            isFallback = true
        )
    }

    private suspend fun executeGeminiCallWithRetry(
        model: String,
        apiKey: String,
        requestPayload: GenerateContentRequest,
        maxRetries: Int = 2
    ): GenerateContentResponse? {
        val baseUrl = BuildConfig.LLM_BASE_URL.ifBlank { "https://generativelanguage.googleapis.com/" }
            .trimEnd('/')
        val url = "$baseUrl/v1beta/models/$model:generateContent?key=$apiKey"
        val bodyString = json.encodeToString(requestPayload)

        repeat(maxRetries) { attempt ->
            try {
                val httpRequest = Request.Builder()
                    .url(url)
                    .post(bodyString.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                okHttpClient.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val rawBody = response.body?.string()
                        if (!rawBody.isNullOrBlank()) {
                            return json.decodeFromString<GenerateContentResponse>(rawBody)
                        }
                    } else if (response.code == 429 || response.code >= 500) {
                        delay(800L * (attempt + 1))
                    } else {
                        return null
                    }
                }
            } catch (_: Exception) {
                if (attempt < maxRetries - 1) {
                    delay(600L * (attempt + 1))
                }
            }
        }
        return null
    }

    private fun buildFallbackEnrichment(entry: VocabularyEntity): VocabularyEnrichmentPayload {
        val collocations = runCatching {
            json.decodeFromString<List<String>>(entry.collocationsJson)
        }.getOrDefault(emptyList()).ifEmpty { listOf("${entry.headword} effectively") }

        val wordFamily = entry.wordFamilyJson?.let {
            runCatching { json.decodeFromString<List<String>>(it) }.getOrNull()
        } ?: listOf("${entry.headword} (${entry.partOfSpeech})")

        return VocabularyEnrichmentPayload(
            vietnameseExplanation = "${entry.vietnameseMeaning} — dùng trong văn phong học thuật IELTS để diễn đạt: ${entry.englishDefinition}.",
            exampleSentence = entry.exampleSentence,
            vietnameseExampleTranslation = entry.vietnameseExampleTranslation,
            collocations = collocations,
            wordFamily = wordFamily,
            commonMistakes = entry.commonMistakes
                ?: "Lưu ý sử dụng đúng từ loại (${entry.partOfSpeech}) và kết hợp chuẩn với các collocations học thuật trong IELTS Writing Task 2."
        )
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
