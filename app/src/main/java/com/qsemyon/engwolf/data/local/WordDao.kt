package com.qsemyon.engwolf.data.local

import androidx.room.*

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE dictionaryName = :dictName AND isLearned = 0 AND nextReviewTime <= :currentTime")
    suspend fun getWordsToReview(dictName: String, currentTime: Long): List<WordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWords(words: List<WordEntity>)

    @Update
    suspend fun updateWord(word: WordEntity)

    @Query("SELECT COUNT(*) FROM words WHERE dictionaryName = :dictName")
    suspend fun getCount(dictName: String): Int

    @Query("""
        SELECT COUNT(DISTINCT id) FROM words 
        WHERE dictionaryName = :dict 
        AND nextReviewTime > :start
    """)
    suspend fun getUniqueWordsStudiedToday(dict: String, start: Long): Int
}