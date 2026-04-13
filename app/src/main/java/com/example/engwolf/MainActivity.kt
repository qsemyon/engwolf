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
import org.json.JSONObject
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "first") {
                    composable("first") { FirstScreen { navController.navigate("second") } }
                    composable("second") { SecondScreen() }
                }
            }
        }
    }
}

@Composable
fun FirstScreen(onNavigate: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onNavigate) { Text("Начать") }
    }
}

fun loadDictionary(context: android.content.Context): Map<String, String> {
    return try {
        val jsonString = context.assets.open("dictionary.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        val map = mutableMapOf<String, String>()

        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = jsonObject.getString(key)
        }
        map
    } catch (e: Exception) {
        e.printStackTrace()
        mapOf("error" to "Файл не найден или кривой JSON")
    }
}

@Composable
fun SecondScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dictionary = remember { loadDictionary(context) }

    val learnedWords = remember { mutableStateListOf<String>() }
    var resetTime by remember { mutableLongStateOf(0L) }

    val getAvailableWords = { dictionary.keys.filter { it !in learnedWords } }

    var targetWord by remember { mutableStateOf(getAvailableWords().randomOrNull() ?: "") }
    var textState by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    var showNextButton by remember { mutableStateOf(false) }

    val moveToNext = {
        textState = ""
        feedback = ""
        showNextButton = false
        targetWord = getAvailableWords().randomOrNull() ?: ""
    }

    LaunchedEffect(learnedWords.size) {
        if (learnedWords.size >= 3) {
            resetTime = System.currentTimeMillis() + 60000
            delay(60000)
            learnedWords.clear()
            moveToNext()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (learnedWords.size >= 3) {
            Text("Всё выучено", style = MaterialTheme.typography.headlineLarge)
            val timeLeft = ((resetTime - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            Text("Слова обновятся через: $timeLeft сек")
        } else if (targetWord.isNotEmpty()) {
            Text(text = "Translate: $targetWord", style = MaterialTheme.typography.headlineMedium)

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = textState,
                onValueChange = { if (!showNextButton && feedback != "Да") textState = it },
                label = { Text("Перевод") },
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            if (feedback.isEmpty()) {
                Button(onClick = {
                    val correct = dictionary[targetWord]
                    if (textState.lowercase().trim() == correct) {
                        feedback = "Да"
                    } else {
                        feedback = "Нет, это слово $correct"
                        showNextButton = true
                    }
                }) { Text("Проверить") }
            }

            if (feedback == "Да") {
                Text(text = feedback, color = androidx.compose.ui.graphics.Color.Green)
                LaunchedEffect(targetWord) {
                    delay(1000)
                    learnedWords.add(targetWord)
                    moveToNext()
                }
            } else if (feedback.isNotEmpty()) {
                Text(text = feedback, color = androidx.compose.ui.graphics.Color.Red)
            }

            if (showNextButton) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = { moveToNext() }) {
                    Text("Следующее слово")
                }
            }
        }
    }
}