package com.qsemyon.engwolf.data.local

import androidx.room.*

@Dao
interface WordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<WordEntity>)

    @Update
    suspend fun updateWord(word: WordEntity)

    @Query("SELECT COUNT(*) FROM words WHERE dictionaryName = :dictName")
    suspend fun getCount(dictName: String): Int

    @Query("""
        SELECT COUNT(DISTINCT id) FROM words 
        WHERE dictionaryName = :dict 
        AND nextReviewTime > :start
        AND intervalStep != 999
    """)
    suspend fun getUniqueWordsStudiedToday(dict: String, start: Long): Int

    @Query("SELECT * FROM words WHERE dictionaryName = :dictName")
    suspend fun getWordsByDictionary(dictName: String): List<WordEntity>

    @Query("SELECT * FROM words WHERE dictionaryName = :dictName AND intervalStep = 0 AND nextReviewTime = 0")
    suspend fun getNewWords(dictName: String): List<WordEntity>

    @Query("SELECT * FROM words WHERE dictionaryName = :dictName AND nextReviewTime > 0 AND intervalStep != 999")
    suspend fun getStudyingWords(dictName: String): List<WordEntity>

    @Delete
    suspend fun deleteWord(word: WordEntity)

    @Query("SELECT * FROM words WHERE dictionaryName = :dictName AND (word LIKE '%' || :searchQuery || '%' OR translation LIKE '%' || :searchQuery || '%')")
    suspend fun searchWordsInDictionary(dictName: String, searchQuery: String): List<WordEntity>
}