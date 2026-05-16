package com.qsemyon.engwolf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.qsemyon.engwolf.data.WordRepository
import com.qsemyon.engwolf.data.local.AppDatabase
import com.qsemyon.engwolf.data.local.WordEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(applicationContext)
        val repo = WordRepository(db.wordDao(), applicationContext)
        setContent {
            MaterialTheme { AppContent(repo) }
        }
    }
}

private var lastClickTime = 0L
fun NavController.navigateSafe(route: String) {
    val now = System.currentTimeMillis()
    if (now - lastClickTime <= 500) return
    lastClickTime = now
    navigate(route)
}

@Composable
fun AppContent(repo: WordRepository) {
    val navController = rememberNavController()
    val args = listOf(navArgument("lvl") { type = NavType.StringType })

    NavHost(navController, "first", Modifier.fillMaxSize()) {
        composable("first") {
            FirstScreen { navController.navigateSafe("selection") }
        }
        composable("selection") {
            SelectionScreen(navController)
        }
        composable("quiz/{lvl}", arguments = args) { entry ->
            val lvl = entry.arguments?.getString("lvl") ?: "A1"
            QuizScreen(repo, lvl) { navController.popBackStack() }
        }
    }
}

@Composable
fun FirstScreen(onNavigate: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(
            onClick = onNavigate,
            modifier = Modifier.height(50.dp).fillMaxWidth(0.5f)
        ) {
            Text("Слова", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SelectionScreen(navController: NavController) {
    Scaffold(
        topBar = { TopBarBack { navController.popBackStack() } }
    ) { padding ->
        SelectionList(Modifier.padding(padding), navController)
    }
}

@Composable
fun SelectionList(modifier: Modifier, navController: NavController) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LevelButton("A1", navController)
        LevelButton("A2", navController)
        LevelButton("B1", navController)
    }
}

@Composable
fun LevelButton(level: String, navController: NavController) {
    Button(
        onClick = { navController.navigateSafe("quiz/$level") },
        modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 6.dp).height(50.dp)
    ) {
        Text(level, style = MaterialTheme.typography.titleMedium)
    }
}

data class QuizUiState(
    val word: WordEntity? = null,
    val text: String = "",
    val feedback: String = "",
    val showNextButton: Boolean = false,
    val isLimitReached: Boolean = false,
    val isLoading: Boolean = true
)

class QuizViewModel(
    private val repo: WordRepository,
    private val lvl: String
) : ViewModel() {

    var state by mutableStateOf(QuizUiState())
        private set

    private var isProcessing = false

    init {
        loadNextWord(initialLoad = true)
    }

    fun onTextChange(newText: String) {
        if (!state.showNextButton) state = state.copy(text = newText)
    }

    fun handleAction() {
        if (isProcessing) return
        if (state.showNextButton) {
            loadNextWord(initialLoad = false)
            return
        }
        val target = state.word?.translation?.trim().orEmpty()
        val isCorrect = state.text.trim().equals(target, ignoreCase = true)

        if (state.text.isBlank()) {
            state = state.copy(feedback = "Введи слово")
            return
        }
        processAnswer(isCorrect, target)
    }

    private fun processAnswer(isCorrect: Boolean, target: String): kotlinx.coroutines.Job = viewModelScope.launch {
        isProcessing = true
        state.word?.let { repo.updateWordProgress(it, isCorrect) }

        if (isCorrect) {
            state = state.copy(feedback = "Правильно", showNextButton = true)
            delay(1000)
            loadNextWord(initialLoad = false)
            isProcessing = false
            return@launch
        }

        state = state.copy(feedback = "Неверно, это: $target", showNextButton = true)
        isProcessing = false
    }

    private fun loadNextWord(initialLoad: Boolean): kotlinx.coroutines.Job = viewModelScope.launch {
        if (initialLoad) state = state.copy(isLoading = true)

        repo.checkAndPrepopulate(lvl)
        val limit = !repo.canLearnMore(lvl)
        val available = repo.getWordsToReview(lvl)
        val currentId = state.word?.id
        val next = available.filter { it.id != currentId }.randomOrNull() ?: available.firstOrNull()

        state = QuizUiState(word = next, isLimitReached = limit, isLoading = false)

        if (next == null) {
            delay(5000)
            loadNextWord(false)
        }
    }
}

@Composable
fun QuizScreen(repo: WordRepository, lvl: String, onBack: () -> Unit) {
    val viewModel = remember(lvl) { QuizViewModel(repo, lvl) }
    val state = viewModel.state

    Scaffold(topBar = { TopBarBack(onBack) }) { padding ->
        val mod = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)
        Box(modifier = mod) {
            QuizStateRouter(state, viewModel)
        }
    }
}

@Composable
fun BoxScope.QuizStateRouter(state: QuizUiState, vm: QuizViewModel) {
    if (state.isLoading) return
    if (state.word != null) {
        QuizActiveContent(state, vm::onTextChange, vm::handleAction)
        return
    }
    QuizEmptyState(isLimitReached = state.isLimitReached)
}

@Composable
fun BoxScope.QuizActiveContent(state: QuizUiState, onTextChange: (String) -> Unit, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(state.word?.word.orEmpty(), style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = state.text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            placeholder = { Text("Введи перевод") }
        )
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(50.dp)
        ) {
            Text(if (state.showNextButton) "Далее" else "Проверить", style = MaterialTheme.typography.titleMedium)
        }
        if (state.feedback.isNotEmpty()) {
            Text(state.feedback, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun QuizEmptyState(isLimitReached: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val msg = if (isLimitReached) {
            "Лимит 10 новых слов на сегодня исчерпан\nТекущие слова появятся автоматически по интервалам"
        } else {
            "Пока слов для изучения нет"
        }
        Text(msg, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator()
    }
}

@Composable
fun TopBarBack(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 12.dp, start = 8.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
            Text("← Назад", style = MaterialTheme.typography.titleLarge)
        }
    }
}