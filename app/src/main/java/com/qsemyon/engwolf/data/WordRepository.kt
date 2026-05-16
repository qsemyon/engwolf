package com.qsemyon.engwolf.data

import android.content.Context
import android.util.Log
import com.qsemyon.engwolf.data.local.WordDao
import com.qsemyon.engwolf.data.local.WordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class WordRepository(private val wordDao: WordDao, private val context: Context) {

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

    suspend fun checkAndPrepopulate(dict: String) = withContext(Dispatchers.IO) {
        if (wordDao.getCount(dict) > 0) return@withContext
        val words = loadWordsFromJson(dict, "$dict.json")
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
        if (!isCorrect) {
            word.intervalStep = 0
            word.nextReviewTime = getStartOfDay() + 1000
        } else {
            val nextStep = if (word.nextReviewTime == 0L || word.nextReviewTime <= getStartOfDay() + 1000) 1 else word.intervalStep + 1
            word.intervalStep = nextStep.coerceAtMost(intervals.size - 1)
            word.nextReviewTime = System.currentTimeMillis() + intervals[word.intervalStep]
            word.isLearned = false
        }
        wordDao.updateWord(word)
    }

    suspend fun getWordsToReview(dict: String): List<WordEntity> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        val allReady = wordDao.getWordsToReview(dict, currentTime)

        if (canLearnMore(dict)) {
            return@withContext allReady
        } else {
            return@withContext allReady.filter { it.nextReviewTime > 0 }
        }
    }

    private fun loadWordsFromJson(dict: String, fileName: String): List<WordEntity> = try {
        val json = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        (0 until array.length()).map { i -> array.getJSONObject(i).toEntity(dict) }
    } catch (e: Exception) {
        Log.e("Engwolf", "JSON error in $fileName: ${e.localizedMessage}")
        emptyList()
    }

    private fun JSONObject.toEntity(dict: String) = WordEntity(
        word = getString("word"),
        translation = getString("translation"),
        dictionaryName = dict,
        nextReviewTime = 0,
        intervalStep = 0,
        isLearned = false
    )
}