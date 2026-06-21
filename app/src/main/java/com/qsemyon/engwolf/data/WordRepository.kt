package com.qsemyon.engwolf.data

import com.qsemyon.engwolf.data.local.WordDao
import com.qsemyon.engwolf.data.local.WordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.Calendar

class WordRepository(private val wordDao: WordDao) {

    private val intervals = listOf(
        0L,
        60_000L,
        600_000L,
        3_600_000L,
        43_200_000L,
        86_400_000L,
        86_400_000L * 7,
        86_400_000L * 30
    )

    suspend fun checkAndPrepopulate(dict: String, jsonStreamProvider: () -> InputStream) = withContext(Dispatchers.IO) {
        if (wordDao.getCount(dict) > 0) return@withContext
        val words = loadWordsFromStream(dict, jsonStreamProvider)
        if (words.isNotEmpty()) wordDao.insertWords(words)
    }

    suspend fun canLearnMore(dict: String): Boolean = withContext(Dispatchers.IO) {
        wordDao.getUniqueWordsStudiedToday(dict, getStartOfDay()) < 10
    }

    private fun getStartOfDay(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    suspend fun updateWordProgress(word: WordEntity, isCorrect: Boolean) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()

        if (!isCorrect) {
            word.intervalStep = 0
            word.nextReviewTime = currentTime
        } else {
            if (word.intervalStep == 0 && word.nextReviewTime == 0L) {
                word.intervalStep = 999
                word.nextReviewTime = Long.MAX_VALUE
            } else {
                val nextStep = if (word.intervalStep == 0) 1 else word.intervalStep + 1
                if (nextStep >= intervals.size) {
                    word.intervalStep = 999
                    word.nextReviewTime = Long.MAX_VALUE
                } else {
                    word.intervalStep = nextStep
                    word.nextReviewTime = currentTime + intervals[word.intervalStep]
                }
            }
        }
        word.isLearned = (word.intervalStep == 999)
        wordDao.updateWord(word)
    }

    suspend fun getNewWords(dict: String): List<WordEntity> = withContext(Dispatchers.IO) {
        wordDao.getNewWords(dict)
    }

    suspend fun getStudyingWords(dict: String): List<WordEntity> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        wordDao.getStudyingWords(dict).filter { it.nextReviewTime <= currentTime }
    }

    private fun loadWordsFromStream(dict: String, jsonStreamProvider: () -> InputStream): List<WordEntity> = try {
        val json = jsonStreamProvider().bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        (0 until array.length()).map { i -> array.getJSONObject(i).toEntity(dict) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun JSONObject.toEntity(dict: String): WordEntity {
        val translationArray = getJSONArray("translation")
        val translations = mutableListOf<String>()
        for (i in 0 until translationArray.length()) {
            translations.add(translationArray.getString(i))
        }
        return WordEntity(
            word = getString("word"),
            translation = translations.joinToString(", "),
            dictionaryName = dict,
            nextReviewTime = 0,
            intervalStep = 0,
            isLearned = false
        )
    }

    suspend fun getWordsByDictionary(dictName: String): List<WordEntity> = withContext(Dispatchers.IO) {
        wordDao.getWordsByDictionary(dictName)
    }

    suspend fun addNewWordToDictionary(dictName: String, word: String, translation: String): Boolean = withContext(Dispatchers.IO) {
        val existingWords = wordDao.getWordsByDictionary(dictName)
        val alreadyExists = existingWords.any { it.word.equals(word.trim(), ignoreCase = true) }
        if (alreadyExists) {
            return@withContext false
        }

        val normalizedTranslation = translation
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")

        val entity = WordEntity(
            word = word.trim(),
            translation = normalizedTranslation,
            dictionaryName = dictName,
            nextReviewTime = 0L,
            intervalStep = 0,
            isLearned = false
        )
        wordDao.insertWords(listOf(entity))
        true
    }

    suspend fun getGrammarStats(): Map<String, Int> = withContext(Dispatchers.IO) {
        val records = wordDao.getWordsByDictionary("grammar_internal_stats")
        val sectionIds = listOf("1", "2", "3", "4")

        sectionIds.associateWith { id ->
            val cleanId = id.trim()
            val finalRecord = records.find { it.word == cleanId }
            val finalScore = finalRecord?.translation?.toIntOrNull()

            if (finalScore != null) {
                finalScore
            } else {
                val currentScoreRecord = records.find { it.word == "${cleanId}_score" }
                currentScoreRecord?.translation?.toIntOrNull() ?: 0
            }
        }
    }

    suspend fun getGrammarProgress(sectionId: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val records = wordDao.getWordsByDictionary("grammar_internal_stats")
        val cleanId = sectionId.trim()

        val qRecord = records.find { it.word == "${cleanId}_current_q" }
        val scoreRecord = records.find { it.word == "${cleanId}_score" }

        val currentQ = qRecord?.translation?.toIntOrNull() ?: 1
        val score = scoreRecord?.translation?.toIntOrNull() ?: 0

        Pair(currentQ, score)
    }

    suspend fun saveGrammarResult(sectionId: String, correctCount: Int) = withContext(Dispatchers.IO) {
        val cleanId = sectionId.trim()
        val allRecords = wordDao.getWordsByDictionary("grammar_internal_stats")
        val existing = allRecords.find { it.word == cleanId }

        if (existing != null) {
            val currentMax = existing.translation.toIntOrNull() ?: 0
            if (correctCount > currentMax) {
                val updated = existing.copy(translation = correctCount.toString())
                wordDao.updateWord(updated)
            }
        } else {
            val newStat = WordEntity(
                word = cleanId,
                translation = correctCount.toString(),
                dictionaryName = "grammar_internal_stats",
                nextReviewTime = 0L,
                intervalStep = 0,
                isLearned = false
            )
            wordDao.insertWords(listOf(newStat))
        }
    }

    suspend fun saveCurrentGrammarStep(sectionId: String, currentQuestion: Int, correctCount: Int) = withContext(Dispatchers.IO) {
        val cleanId = sectionId.trim()
        val allRecords = wordDao.getWordsByDictionary("grammar_internal_stats")

        val qKey = "${cleanId}_current_q"
        val scoreKey = "${cleanId}_score"

        val existingQ = allRecords.find { it.word == qKey }
        val existingScore = allRecords.find { it.word == scoreKey }

        if (existingQ != null) {
            wordDao.updateWord(existingQ.copy(translation = currentQuestion.toString()))
        } else {
            wordDao.insertWords(listOf(WordEntity(word = qKey, translation = currentQuestion.toString(), dictionaryName = "grammar_internal_stats", nextReviewTime = 0L, intervalStep = 0, isLearned = false)))
        }

        if (existingScore != null) {
            wordDao.updateWord(existingScore.copy(translation = correctCount.toString()))
        } else {
            wordDao.insertWords(listOf(WordEntity(word = scoreKey, translation = correctCount.toString(), dictionaryName = "grammar_internal_stats", nextReviewTime = 0L, intervalStep = 0, isLearned = false)))
        }
    }

    suspend fun updateWordLocally(word: WordEntity) {
        wordDao.updateWord(word)
    }

    suspend fun deleteWordLocally(word: WordEntity) {
        wordDao.deleteWord(word)
    }

    suspend fun searchDictionary(dictName: String, query: String): List<WordEntity> {
        return if (query.isBlank()) {
            wordDao.getWordsByDictionary(dictName)
        } else {
            wordDao.searchWordsInDictionary(dictName, query)
        }
    }
}