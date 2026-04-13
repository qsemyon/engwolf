package com.example.engwolf.data.local

import androidx.room.*

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE dictionaryName = :dictName AND nextReviewTime <= :currentTime")
    suspend fun getWordsToReview(dictName: String, currentTime: Long): List<WordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWords(words: List<WordEntity>)

    @Update
    suspend fun updateWord(word: WordEntity)

    @Query("SELECT COUNT(*) FROM words WHERE dictionaryName = :dictName")
    suspend fun getCount(dictName: String): Int

    @Query("DELETE FROM words")
    suspend fun clearAllWords()
}