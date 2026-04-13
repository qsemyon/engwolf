package com.example.engwolf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.engwolf.data.WordRepository
import com.example.engwolf.data.local.AppDatabase
import com.example.engwolf.data.local.WordEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(applicationContext)
        val repository = WordRepository(db.wordDao(), applicationContext)

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    repository.checkAndPrepopulate("A1")
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(navController = navController, startDestination = "first") {
                        composable("first") {
                            FirstScreen(repository = repository, onNavigate = { navController.navigate("second") })
                        }
                        composable("second") {
                            SecondScreen(
                                repository = repository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FirstScreen(repository: WordRepository, onNavigate: () -> Unit) {
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Button(onClick = onNavigate) {
            Text("Начать обучение")
        }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = {
            scope.launch {
                repository.resetAllProgress()
            }
        }) {
            Text("Сбросить весь прогресс")
        }
    }
}

@Composable
fun SecondScreen(repository: WordRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var currentWord by remember { mutableStateOf<WordEntity?>(null) }
    var textState by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    var showNextButton by remember { mutableStateOf(false) }

    fun loadNext(excludeId: Int? = null) {
        scope.launch {
            val availableWords = repository.getWordsToReview("A1")

            val filteredWords = if (excludeId != null) {
                availableWords.filter { it.id != excludeId }
            } else {
                availableWords
            }

            currentWord = if (filteredWords.isNotEmpty()) {
                filteredWords.randomOrNull()
            } else {
                availableWords.firstOrNull()
            }

            textState = ""
            feedback = ""
            showNextButton = false
        }
    }

    LaunchedEffect(Unit) {
        loadNext()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("Назад")
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            currentWord?.let { word ->
                Text(
                    text = "Как переводится: ${word.word}?",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = textState,
                    onValueChange = { if (!showNextButton && feedback != "Да") textState = it },
                    label = { Text("Введи перевод") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (feedback.isEmpty()) {
                    Button(
                        onClick = {
                            val isCorrect = textState.lowercase().trim() == word.translation.lowercase().trim()
                            scope.launch {
                                if (isCorrect) {
                                    feedback = "Да"
                                    repository.updateWordProgress(word, true)
                                    kotlinx.coroutines.delay(1000)
                                    loadNext(excludeId = word.id)
                                } else {
                                    feedback = "Нет, это слово ${word.translation}"
                                    repository.updateWordProgress(word, false)
                                    showNextButton = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Проверить")
                    }
                }

                if (feedback.isNotEmpty()) {
                    Text(
                        text = feedback,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (feedback.contains("Да")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                if (showNextButton) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { loadNext(excludeId = word.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Следующее слово")
                    }
                }
            } ?: Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "На сегодня всё выучено\nВозвращайся позже",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { loadNext() }) {
                    Text("Проверить снова")
                }
            }
        }
    }
}