package com.qsemyon.engwolf.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val word: String,
    val translation: String,
    val dictionaryName: String,
    var intervalStep: Int = 0,
    var nextReviewTime: Long = 0,
    var isLearned: Boolean = false
)