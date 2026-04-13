package com.example.engwolf.data

import android.content.Context
import com.example.engwolf.data.local.WordDao
import com.example.engwolf.data.local.WordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class WordRepository(private val wordDao: WordDao, private val context: Context) {

    private val intervals = listOf(
        0L,
        60_000L,
        3_600_000L,
        43_200_000L,
        86_400_000L,
        604_800_000L,
        2_592_000_000L
    )

    suspend fun checkAndPrepopulate(dictName: String) {
        withContext(Dispatchers.IO) {
            try {
                val currentCount = wordDao.getCount(dictName)
                if (currentCount == 0) {
                    val jsonString = context.assets.open("dictionary.json").bufferedReader().use { it.readText() }
                    val jsonArray = org.json.JSONArray(jsonString)
                    val words = mutableListOf<WordEntity>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        words.add(WordEntity(
                            word = obj.getString("word"),
                            translation = obj.getString("translation"),
                            dictionaryName = dictName,
                            nextReviewTime = 0,
                            intervalStep = 0
                        ))
                    }
                    wordDao.insertWords(words)
                    android.util.Log.d("Engwolf", "Успешно загружено слов из dictionary.json: ${words.size}")
                }
            } catch (e: Exception) {
                android.util.Log.e("Engwolf", "Ошибка загрузки: ${e.message}")
            }
        }
    }

    suspend fun updateWordProgress(word: WordEntity, isCorrect: Boolean) {
        withContext(Dispatchers.IO) {
            if (isCorrect) {
                word.intervalStep = (word.intervalStep + 1).coerceAtMost(intervals.size - 1)
            } else {
                word.intervalStep = 0
            }

            word.nextReviewTime = System.currentTimeMillis() + intervals[word.intervalStep]

            wordDao.updateWord(word)
            android.util.Log.d("Engwolf", "Слово '${word.word}' обновлено. Шаг: ${word.intervalStep}. Следующий показ через: ${intervals[word.intervalStep] / 1000} секунд")
        }
    }

    suspend fun getWordsToReview(dictName: String): List<WordEntity> {
        return wordDao.getWordsToReview(dictName, System.currentTimeMillis())
    }

    suspend fun resetAllProgress() {
        withContext(Dispatchers.IO) {
            try {
                wordDao.clearAllWords()

                val fileName = "dictionary.json"
                val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
                val jsonArray = org.json.JSONArray(jsonString)
                val words = mutableListOf<WordEntity>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    words.add(WordEntity(
                        word = obj.getString("word"),
                        translation = obj.getString("translation"),
                        dictionaryName = "A1",
                        nextReviewTime = 0,
                        intervalStep = 0
                    ))
                }
                wordDao.insertWords(words)
            } catch (e: Exception) {
                android.util.Log.e("Engwolf", "Crash during reset: ${e.message}")
            }
        }
    }
}