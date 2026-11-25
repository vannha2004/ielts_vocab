package com.example.data.seed

import com.example.data.local.TopicEntity
import com.example.data.local.VocabularyEntity
import com.example.data.local.VocabularyTopicCrossRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SeedVocabularyRecord(
    @SerialName("id") val id: String = "",
    @SerialName("headword") val headword: String = "",
    @SerialName("normalized_headword") val normalizedHeadword: String? = null,
    @SerialName("part_of_speech") val partOfSpeech: String = "",
    @SerialName("ipa") val ipa: String? = null,
    @SerialName("vietnamese_meaning") val vietnameseMeaning: String = "",
    @SerialName("english_definition") val englishDefinition: String = "",
    @SerialName("example_sentence") val exampleSentence: String = "",
    @SerialName("vietnamese_example_translation") val vietnameseExampleTranslation: String = "",
    @SerialName("collocations") val collocations: List<String> = emptyList(),
    @SerialName("word_family") val wordFamily: List<String>? = null,
    @SerialName("synonyms") val synonyms: List<String> = emptyList(),
    @SerialName("antonyms") val antonyms: List<String>? = null,
    @SerialName("common_mistakes") val commonMistakes: String? = null,
    @SerialName("topic_ids") val topicIds: List<String> = emptyList(),
    @SerialName("difficulty_level") val difficultyLevel: String = "Band 6.0–6.5",
    @SerialName("cefr_level") val cefrLevel: String? = null,
    @SerialName("source_name") val sourceName: String = "",
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("verification_status") val verificationStatus: String = "CURATED_STARTER"
)

@Serializable
data class SeedDatasetPayload(
    @SerialName("dataset_version") val datasetVersion: Int = 1,
    @SerialName("target_total_entries") val targetTotalEntries: Int = 1200,
    @SerialName("entries") val entries: List<SeedVocabularyRecord> = emptyList()
)

data class ValidationErrorItem(
    val recordIndex: Int,
    val headword: String,
    val reason: String
)

data class SeedValidationReport(
    val totalInputCount: Int,
    val validEntries: List<VocabularyEntity>,
    val crossRefs: List<VocabularyTopicCrossRef>,
    val errors: List<ValidationErrorItem>,
    val duplicateHeadwordsRejected: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty() && validEntries.isNotEmpty()
}

object SeedDataValidator {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    val OFFICIAL_TOPICS: List<TopicEntity> = listOf(
        TopicEntity(
            id = "topic_1_environment",
            orderIndex = 1,
            englishName = "Environment & Climate Change",
            vietnameseName = "Môi trường & Biến đổi khí hậu",
            description = "Từ vựng học thuật về sinh thái, phát thải carbon, năng lượng tái tạo và bảo tồn tự nhiên.",
            targetCount = 80
        ),
        TopicEntity(
            id = "topic_2_education",
            orderIndex = 2,
            englishName = "Education",
            vietnameseName = "Giáo dục & Học thuật",
            description = "Chương trình giảng dạy, phương pháp sư phạm, đánh giá năng lực và giáo dục đại học.",
            targetCount = 80
        ),
        TopicEntity(
            id = "topic_3_technology",
            orderIndex = 3,
            englishName = "Technology & Artificial Intelligence",
            vietnameseName = "Công nghệ & Trí tuệ nhân tạo",
            description = "Tự động hóa, thuật toán, đạo đức AI, chuyển đổi số và đổi mới sáng tạo.",
            targetCount = 80
        ),
        TopicEntity(
            id = "topic_4_health",
            orderIndex = 4,
            englishName = "Health & Lifestyle",
            vietnameseName = "Sức khỏe & Lối sống",
            description = "Y tế dự phòng, sức khỏe tinh thần, dịch tễ học, dinh dưỡng và cân bằng cuộc sống.",
            targetCount = 80
        ),
        TopicEntity(
            id = "topic_5_society",
            orderIndex = 5,
            englishName = "Society & Social Issues",
            vietnameseName = "Xã hội & Các vấn đề xã hội",
            description = "Bình đẳng xã hội, nhân khẩu học, già hóa dân số, phân hóa giàu nghèo và phúc lợi.",
            targetCount = 80
        ),
        TopicEntity(
            id = "topic_6_work",
            orderIndex = 6,
            englishName = "Work & Employment",
            vietnameseName = "Công việc & Việc làm",
            description = "Thị trường lao động, năng suất, làm việc từ xa, thăng tiến sự nghiệp và kỹ năng nghề nghiệp.",
            targetCount = 80
        ),
        TopicEntity(
            id = "topic_7_economy",
            orderIndex = 7,
            englishName = "Economy & Business",
            vietnameseName = "Kinh tế & Kinh doanh",
            description = "Lạm phát, tăng trưởng bền vững, thương mại, đầu tư tài chính và quản trị doanh nghiệp.",
            targetCount = 80
        ),
        TopicEntity(
            id = "topic_8_government",
            orderIndex = 8,
            englishName = "Government & Public Policy",
            vietnameseName = "Chính phủ & Chính sách công",
            description = "Quy định pháp lý, phân bổ ngân sách, thuế, trợ cấp và quản lý nhà nước.",
            targetCount = 80
        ),
        TopicEntity(
            id = "topic_9_urbanisation",
            orderIndex = 9,
            englishName = "Urbanisation & Housing",
            vietnameseName = "Đô thị hóa & Nhà ở",
            description = "Quy hoạch đô thị, mật độ dân cư, di cư nông thôn - thành thị và nhà ở giá phải chăng.",
            targetCount = 80
        ),
        TopicEntity(
            id = "topic_10_transport",
            orderIndex = 10,
            englishName = "Transport & Infrastructure",
            vietnameseName = "Giao thông & Cơ sở hạ tầng",
            description = "Giao thông công cộng, ùn tắc, mạng lưới logistics và hạ tầng thông minh.",
            targetCount = 80
        ),
        TopicEntity(
            id = "topic_11_globalisation",
            orderIndex = 11,
            englishName = "Globalisation & Culture",
            vietnameseName = "Toàn cầu hóa & Văn hóa",
            description = "Giao thoa văn hóa, bản sắc dân tộc, hội nhập quốc tế và bảo tồn di sản.",
            targetCount = 80
        ),
        TopicEntity(
            id = "topic_12_media",
            orderIndex = 12,
            englishName = "Media & Communication",
            vietnameseName = "Truyền thông & Giao tiếp",
            description = "Báo chí, mạng xã hội, tin giả, quyền riêng tư thông tin và truyền thông đại chúng.",
            targetCount = 80
        ),
        TopicEntity(
            id = "topic_13_science",
            orderIndex = 13,
            englishName = "Science & Research",
            vietnameseName = "Khoa học & Nghiên cứu",
            description = "Phương pháp thực nghiệm, giả thuyết khoa học, kiểm chứng dữ liệu và đột phá y sinh.",
            targetCount = 80
        ),
        TopicEntity(
            id = "topic_14_crime",
            orderIndex = 14,
            englishName = "Crime & Law",
            vietnameseName = "Tội phạm & Pháp luật",
            description = "Hệ thống tư pháp, răn đe tội phạm, tái hòa nhập cộng đồng và tội phạm mạng.",
            targetCount = 80
        ),
        TopicEntity(
            id = "topic_15_food",
            orderIndex = 15,
            englishName = "Food, Agriculture & Natural Resources",
            vietnameseName = "Thực phẩm, Nông nghiệp & Tài nguyên",
            description = "An ninh lương thực, nông nghiệp bền vững, khai thác tài nguyên và chuỗi cung ứng.",
            targetCount = 80
        )
    )

    val VALID_TOPIC_IDS: Set<String> = OFFICIAL_TOPICS.map { it.id }.toSet()

    fun normalizeHeadword(raw: String): String {
        return raw.trim().replace(Regex("\\s+"), " ").lowercase()
    }

    /**
     * Parses and validates a JSON seed dataset string.
     */
    fun validateJsonSeedData(
        rawJson: String,
        timestampMillis: Long = System.currentTimeMillis(),
        existingNormalizedHeadwords: Set<String> = emptySet()
    ): SeedValidationReport {
        val records: List<SeedVocabularyRecord> = try {
            val trimmed = rawJson.trim()
            if (trimmed.startsWith("[")) {
                json.decodeFromString<List<SeedVocabularyRecord>>(trimmed)
            } else {
                json.decodeFromString<SeedDatasetPayload>(trimmed).entries
            }
        } catch (e: Exception) {
            return SeedValidationReport(
                totalInputCount = 0,
                validEntries = emptyList(),
                crossRefs = emptyList(),
                errors = listOf(ValidationErrorItem(-1, "", "Invalid JSON format: ${e.message}")),
                duplicateHeadwordsRejected = emptyList()
            )
        }

        return validateRecords(records, timestampMillis, existingNormalizedHeadwords)
    }

    /**
     * Parses and validates a CSV string with header:
     * id,headword,part_of_speech,ipa,vietnamese_meaning,english_definition,example_sentence,vietnamese_example_translation,collocations,topic_ids,difficulty_level,cefr_level,source_name,verification_status
     */
    fun validateCsvSeedData(
        rawCsv: String,
        timestampMillis: Long = System.currentTimeMillis(),
        existingNormalizedHeadwords: Set<String> = emptySet()
    ): SeedValidationReport {
        val lines = rawCsv.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) {
            return SeedValidationReport(
                totalInputCount = 0,
                validEntries = emptyList(),
                crossRefs = emptyList(),
                errors = listOf(ValidationErrorItem(-1, "", "CSV is empty or missing data rows")),
                duplicateHeadwordsRejected = emptyList()
            )
        }

        val header = parseCsvLine(lines.first()).map { it.trim().lowercase() }
        val requiredCols = listOf("headword", "part_of_speech", "vietnamese_meaning", "english_definition", "example_sentence", "topic_ids")
        val missingCols = requiredCols.filter { it !in header }
        if (missingCols.isNotEmpty()) {
            return SeedValidationReport(
                totalInputCount = 0,
                validEntries = emptyList(),
                crossRefs = emptyList(),
                errors = listOf(ValidationErrorItem(-1, "", "Missing required CSV columns: ${missingCols.joinToString(", ")}")),
                duplicateHeadwordsRejected = emptyList()
            )
        }

        val records = mutableListOf<SeedVocabularyRecord>()
        val csvParseErrors = mutableListOf<ValidationErrorItem>()

        for (i in 1 until lines.size) {
            val cols = parseCsvLine(lines[i])
            if (cols.size != header.size) {
                csvParseErrors.add(
                    ValidationErrorItem(i, "", "Malformed CSV row $i: expected ${header.size} columns, got ${cols.size}")
                )
                continue
            }
            val map = header.zip(cols).toMap()
            val headword = map["headword"].orEmpty()
            val id = map["id"]?.takeIf { it.isNotBlank() }
                ?: "vocab_${normalizeHeadword(headword).replace(" ", "_")}"
            val topicIds = map["topic_ids"].orEmpty().split("|", ";").map { it.trim() }.filter { it.isNotEmpty() }
            val collocations = map["collocations"].orEmpty().split("|", ";").map { it.trim() }.filter { it.isNotEmpty() }
            val wordFamily = map["word_family"]?.split("|", ";")?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }
            val synonyms = map["synonyms"].orEmpty().split("|", ";").map { it.trim() }.filter { it.isNotEmpty() }

            records.add(
                SeedVocabularyRecord(
                    id = id,
                    headword = headword,
                    partOfSpeech = map["part_of_speech"].orEmpty(),
                    ipa = map["ipa"]?.takeIf { it.isNotBlank() },
                    vietnameseMeaning = map["vietnamese_meaning"].orEmpty(),
                    englishDefinition = map["english_definition"].orEmpty(),
                    exampleSentence = map["example_sentence"].orEmpty(),
                    vietnameseExampleTranslation = map["vietnamese_example_translation"].orEmpty().ifBlank {
                        map["vietnamese_meaning"].orEmpty()
                    },
                    collocations = collocations,
                    wordFamily = wordFamily,
                    synonyms = synonyms,
                    topicIds = topicIds,
                    difficultyLevel = map["difficulty_level"]?.takeIf { it.isNotBlank() } ?: "Band 6.0–6.5",
                    cefrLevel = map["cefr_level"]?.takeIf { it.isNotBlank() },
                    sourceName = map["source_name"]?.takeIf { it.isNotBlank() } ?: "CSV User Import",
                    sourceUrl = map["source_url"]?.takeIf { it.isNotBlank() },
                    verificationStatus = map["verification_status"]?.takeIf { it.isNotBlank() } ?: "USER_IMPORTED"
                )
            )
        }

        val baseReport = validateRecords(records, timestampMillis, existingNormalizedHeadwords)
        return baseReport.copy(
            totalInputCount = lines.size - 1,
            errors = csvParseErrors + baseReport.errors
        )
    }

    fun validateRecords(
        records: List<SeedVocabularyRecord>,
        timestampMillis: Long = System.currentTimeMillis(),
        existingNormalizedHeadwords: Set<String> = emptySet()
    ): SeedValidationReport {
        val validEntries = mutableListOf<VocabularyEntity>()
        val crossRefs = mutableListOf<VocabularyTopicCrossRef>()
        val errors = mutableListOf<ValidationErrorItem>()
        val duplicates = mutableListOf<String>()
        val seenNormalized = existingNormalizedHeadwords.toMutableSet()
        val seenIds = mutableSetOf<String>()

        records.forEachIndexed { idx, record ->
            val headword = record.headword.trim()
            val normalized = normalizeHeadword(record.normalizedHeadword?.takeIf { it.isNotBlank() } ?: headword)

            if (headword.isBlank()) {
                errors.add(ValidationErrorItem(idx, headword, "Missing required field: headword"))
                return@forEachIndexed
            }
            if (record.partOfSpeech.isBlank()) {
                errors.add(ValidationErrorItem(idx, headword, "Missing required field: part_of_speech"))
                return@forEachIndexed
            }
            if (record.vietnameseMeaning.isBlank()) {
                errors.add(ValidationErrorItem(idx, headword, "Missing required field: vietnamese_meaning"))
                return@forEachIndexed
            }
            if (record.englishDefinition.isBlank()) {
                errors.add(ValidationErrorItem(idx, headword, "Missing required field: english_definition"))
                return@forEachIndexed
            }
            if (record.exampleSentence.isBlank()) {
                errors.add(ValidationErrorItem(idx, headword, "Missing required field: example_sentence"))
                return@forEachIndexed
            }
            if (record.vietnameseExampleTranslation.isBlank()) {
                errors.add(ValidationErrorItem(idx, headword, "Missing required field: vietnamese_example_translation"))
                return@forEachIndexed
            }
            if (record.sourceName.isBlank()) {
                errors.add(ValidationErrorItem(idx, headword, "Missing required field: source_name"))
                return@forEachIndexed
            }
            if (record.topicIds.isEmpty()) {
                errors.add(ValidationErrorItem(idx, headword, "Missing topic_ids for '$headword'"))
                return@forEachIndexed
            }

            val invalidTopics = record.topicIds.filter { it !in VALID_TOPIC_IDS }
            if (invalidTopics.isNotEmpty()) {
                errors.add(
                    ValidationErrorItem(
                        idx,
                        headword,
                        "Invalid topic reference(s): ${invalidTopics.joinToString(", ")}"
                    )
                )
                return@forEachIndexed
            }

            if (normalized in seenNormalized) {
                duplicates.add(headword)
                errors.add(
                    ValidationErrorItem(
                        idx,
                        headword,
                        "Duplicate normalized headword '$normalized' rejected"
                    )
                )
                return@forEachIndexed
            }

            val entryId = record.id.trim().ifBlank { "vocab_${normalized.replace(" ", "_")}" }
            if (entryId in seenIds) {
                duplicates.add(headword)
                errors.add(ValidationErrorItem(idx, headword, "Duplicate vocabulary id '$entryId' rejected"))
                return@forEachIndexed
            }

            seenNormalized.add(normalized)
            seenIds.add(entryId)

            val entity = VocabularyEntity(
                id = entryId,
                headword = headword,
                normalizedHeadword = normalized,
                partOfSpeech = record.partOfSpeech.trim(),
                ipa = record.ipa?.trim()?.takeIf { it.isNotEmpty() },
                vietnameseMeaning = record.vietnameseMeaning.trim(),
                englishDefinition = record.englishDefinition.trim(),
                exampleSentence = record.exampleSentence.trim(),
                vietnameseExampleTranslation = record.vietnameseExampleTranslation.trim(),
                collocationsJson = json.encodeToString(record.collocations),
                wordFamilyJson = record.wordFamily?.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
                synonymsJson = json.encodeToString(record.synonyms),
                antonymsJson = record.antonyms?.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
                commonMistakes = record.commonMistakes?.trim()?.takeIf { it.isNotEmpty() },
                difficultyLevel = record.difficultyLevel.trim().ifBlank { "Band 6.0–6.5" },
                cefrLevel = record.cefrLevel?.trim()?.takeIf { it.isNotEmpty() },
                sourceName = record.sourceName.trim(),
                sourceUrl = record.sourceUrl?.trim()?.takeIf { it.isNotEmpty() },
                verificationStatus = record.verificationStatus.trim().ifBlank { "CURATED_STARTER" },
                createdAt = timestampMillis,
                updatedAt = timestampMillis
            )

            validEntries.add(entity)
            record.topicIds.distinct().forEach { topicId ->
                crossRefs.add(VocabularyTopicCrossRef(vocabId = entryId, topicId = topicId))
            }
        }

        return SeedValidationReport(
            totalInputCount = records.size,
            validEntries = validEntries,
            crossRefs = crossRefs,
            errors = errors,
            duplicateHeadwordsRejected = duplicates
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }
}
