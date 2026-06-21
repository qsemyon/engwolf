package com.qsemyon.engwolf

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.InputStream
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.qsemyon.engwolf.data.WordRepository
import com.qsemyon.engwolf.data.local.AppDatabase
import com.qsemyon.engwolf.data.local.WordEntity
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(applicationContext)
        val repo = WordRepository(db.wordDao())

        setContent {
            com.qsemyon.engwolf.ui.theme.EngwolfTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AppContent(repo) }
            }
        }
    }
}

private var isNavigating = false

fun NavController.navigateSafe(route: String) {
    if (isNavigating || currentDestination?.route == route) return
    isNavigating = true
    navigate(route) { launchSingleTop = true }
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ isNavigating = false }, 300)
}

fun NavController.popBackStackSafe() {
    if (isNavigating || previousBackStackEntry == null) return
    isNavigating = true
    popBackStack()
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ isNavigating = false }, 300)
}

@Composable
fun AppContent(repo: WordRepository) {
    val navController = rememberNavController()
    val args = listOf(navArgument("lvl") { type = NavType.StringType })

    NavHost(navController, "first", Modifier.fillMaxSize()) {
        composable("first") {
            FirstScreen(
                onWordsClick = { navController.navigateSafe("selection") },
                onGrammarClick = { navController.navigateSafe("grammar") },
                onStatsClick = { navController.navigateSafe("stat") },
                onDictionariesClick = { navController.navigateSafe("dictionaries") }
            )
        }
        composable("dictionaries") {
            DictionariesSelectionScreen(navController)
        }
        composable("dictionary_view/{lvl}") { entry ->
            val lvl = entry.arguments?.getString("lvl") ?: "A1"
            DictionaryViewScreen(
                lvl = lvl,
                repo = repo,
                onAddWord = { navController.navigateSafe("add_word/$lvl") },
                onBack = { navController.popBackStackSafe() }
            )
        }
        composable("add_word/{lvl}") { entry ->
            val lvl = entry.arguments?.getString("lvl") ?: "A1"
            AddWordScreen(
                lvl = lvl,
                repo = repo,
                onBack = { navController.popBackStackSafe() }
            )
        }
        composable("selection") {
            SelectionScreen(navController)
        }
        composable("quiz/{lvl}", arguments = args) { entry ->
            val lvl = entry.arguments?.getString("lvl") ?: "A1"
            QuizScreen(repo, lvl) { navController.popBackStackSafe() }
        }
        composable("grammar") {
            GrammarScreen(
                onSectionClick = { sectionId -> navController.navigateSafe("grammar_info/$sectionId") },
                onBack = { navController.popBackStackSafe() }
            )
        }
        composable("grammar_info/{sectionId}") { entry ->
            val sectionId = entry.arguments?.getString("sectionId") ?: "1"
            GrammarInfoScreen(
                sectionId = sectionId,
                onStartQuiz = { navController.navigateSafe("grammar_quiz/$sectionId") },
                onBack = { navController.popBackStackSafe() }
            )
        }
        composable("grammar_quiz/{sectionId}") { entry ->
            val sectionId = entry.arguments?.getString("sectionId") ?: "1"
            GrammarQuizScreen(sectionId = sectionId, repo = repo, onBack = { navController.popBackStackSafe() })
        }
        composable("stat") {
            StatisticsScreen(repo = repo, onBack = { navController.popBackStackSafe() })
        }
    }
}

@Composable
fun FirstScreen(
    onWordsClick: () -> Unit,
    onGrammarClick: () -> Unit,
    onStatsClick: () -> Unit,
    onDictionariesClick: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onWordsClick,
                modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 6.dp).height(50.dp)
            ) {
                Text("Слова", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = onDictionariesClick,
                modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 6.dp).height(50.dp)
            ) {
                Text("Словари", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = onGrammarClick,
                modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 6.dp).height(50.dp)
            ) {
                Text("Грамматика", style = MaterialTheme.typography.titleMedium)
            }
        }

        Button(
            onClick = onStatsClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(0.6f)
                .height(50.dp)
        ) {
            Text("Статистика", style = MaterialTheme.typography.titleMedium)
        }
    }
}

fun getArticleFromAssets(context: Context, sectionId: String): String {
    return try {
        val inputStream = context.assets.open("articles.json")
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("sectionId") == sectionId) {
                return obj.getString("text")
            }
        }
        "Статья не найдена"
    } catch (_: Exception) {
        "Ошибка загрузки"
    }
}

@Composable
fun GrammarInfoScreen(sectionId: String, onStartQuiz: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val articleText = remember(sectionId) { getArticleFromAssets(context, sectionId) }

    Scaffold(topBar = { TopBarBack(onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = articleText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            Button(
                onClick = onStartQuiz,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
            ) {
                Text("Тест", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun SelectionScreen(navController: NavController) {
    Scaffold(
        topBar = { TopBarBack { navController.popBackStackSafe() } }
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
    private val lvl: String,
    private val jsonStreamProvider: () -> InputStream
) : ViewModel() {

    var state by mutableStateOf(QuizUiState())
        private set

    private var isProcessing = false
    private var showNewWordNext = true
    private var lastWordId: Int? = null

    private var tickerJob: kotlinx.coroutines.Job? = null

    init {
        loadNextWord(initialLoad = true)

        tickerJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                if (state.isLimitReached && state.word == null) {
                    loadNextWord(initialLoad = false)
                }
            }
        }
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

        val userInput = state.text.trim()
        if (userInput.isBlank()) {
            state = state.copy(feedback = "Введи слово")
            return
        }

        val targetRaw = state.word?.translation.orEmpty()
        val targets = targetRaw
            .replace("[", "")
            .replace("]", "")
            .replace("\"", "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val isCorrect = targets.any { it.equals(userInput, ignoreCase = true) }
        val displayTarget = targets.joinToString(", ")

        processAnswer(isCorrect, displayTarget)
    }

    private fun processAnswer(isCorrect: Boolean, target: String): kotlinx.coroutines.Job = viewModelScope.launch {
        isProcessing = true
        state.word?.let { repo.updateWordProgress(it, isCorrect) }

        if (isCorrect) {
            state = state.copy(feedback = "Правильно", showNextButton = true)
            delay(1000)
            loadNextWord(false)
            isProcessing = false
            return@launch
        }

        state = state.copy(feedback = "Неверно, это: $target", showNextButton = true)
        isProcessing = false
    }

    private fun loadNextWord(initialLoad: Boolean): kotlinx.coroutines.Job = viewModelScope.launch {
        if (initialLoad) state = state.copy(isLoading = true)

        repo.checkAndPrepopulate(lvl, jsonStreamProvider)
        val limitReached = !repo.canLearnMore(lvl)

        val newWords = if (!limitReached) repo.getNewWords(lvl) else emptyList()
        val studyingWords = repo.getStudyingWords(lvl)

        if (limitReached && studyingWords.isEmpty()) {
            state = QuizUiState(word = null, isLimitReached = true, isLoading = false)
            return@launch
        }

        var next: WordEntity? = null

        if (showNewWordNext) {
            val pool = newWords.filter { it.id != lastWordId }
            if (pool.isNotEmpty()) {
                next = pool.randomOrNull()
                if (studyingWords.isNotEmpty()) {
                    showNewWordNext = false
                }
            }
        } else {
            val pool = studyingWords.filter { it.id != lastWordId }
            if (pool.isNotEmpty()) {
                next = pool.randomOrNull()
                showNewWordNext = true
            }
        }

        if (next == null) {
            val altPool = if (showNewWordNext) {
                studyingWords.filter { it.id != lastWordId }
            } else {
                newWords.filter { it.id != lastWordId }
            }

            if (altPool.isNotEmpty()) {
                next = altPool.randomOrNull()
            }
        }

        if (next == null) {
            next = studyingWords.firstOrNull() ?: newWords.firstOrNull()
        }

        if (next == null) {
            state = QuizUiState(word = null, isLimitReached = true, isLoading = false)
            return@launch
        }

        lastWordId = next.id
        state = QuizUiState(word = next, isLimitReached = limitReached, isLoading = false)
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }
}

@Composable
fun QuizScreen(repo: WordRepository, lvl: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val viewModel = remember(lvl) {
        QuizViewModel(repo, lvl) { app.assets.open("$lvl.json") }
    }
    val state = viewModel.state

    LaunchedEffect(lvl) {
        viewModel.onTextChange("")
    }

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
            placeholder = { Text("Введи перевод") },
            singleLine = true,
            enabled = !state.showNextButton
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
    }
}

@Composable
fun TopBarBack(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 12.dp, start = 8.dp)) {
        TextButton(onClick = onBack) {
            Text("← Назад", style = MaterialTheme.typography.titleLarge)
        }
    }
}

data class GrammarQuestion(
    val sectionId: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int
)

fun getGrammarQuestionsFromAssets(context: Context, sectionId: String): List<GrammarQuestion> {
    val result = mutableListOf<GrammarQuestion>()

    val inputStream: InputStream = context.assets.open("grammar.json")

    val jsonString = inputStream.bufferedReader().use { reader ->
        reader.readText()
    }

    val jsonArray = JSONArray(jsonString)

    for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)

        if (obj.getString("sectionId") == sectionId) {
            val optionsArray = obj.getJSONArray("options")
            val optionsList = mutableListOf<String>()
            for (j in 0 until optionsArray.length()) {
                optionsList.add(optionsArray.getString(j))
            }

            result.add(
                GrammarQuestion(
                    sectionId = obj.getString("sectionId"),
                    question = obj.getString("question"),
                    options = optionsList,
                    correctIndex = obj.getInt("correctIndex")
                )
            )
        }
    }
    return result
}

@Composable
fun GrammarScreen(onSectionClick: (String) -> Unit, onBack: () -> Unit) {
    var pads: PaddingValues? = null
    Scaffold(topBar = { TopBarBack(onBack) }) { pads = it }

    Column(
        modifier = Modifier.fillMaxSize().padding(pads ?: PaddingValues()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Выбери раздел грамматики", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 24.dp))
        val sections = listOf(
            "1" to "Verb 'To Be'",
            "2" to "Plural Nouns",
            "3" to "Present Simple vs Continuous",
            "4" to "Past Simple"
        )
        sections.forEach { (id, name) ->
            Button(
                onClick = { onSectionClick(id) },
                modifier = Modifier.fillMaxWidth().height(55.dp).padding(vertical = 6.dp)
            ) { Text(name, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

private fun calculateAnswerResult(
    selectedOption: Int?,
    activeQuestion: GrammarQuestion?,
    resultText: String?,
    onCorrect: () -> Unit,
    onIncorrect: (String) -> Unit
) {
    if (selectedOption == null || activeQuestion == null || resultText != null) return

    val correctIdx = activeQuestion.correctIndex
    val isCorrect = selectedOption == correctIdx + 1

    if (isCorrect) {
        onCorrect()
    } else {
        val correctText = activeQuestion.options.getOrNull(correctIdx) ?: ""
        onIncorrect("Неверно, это $correctText")
    }
}

@Composable
fun GrammarQuizScreen(sectionId: String, repo: WordRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentQuestion by remember { mutableIntStateOf(1) }
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var correctAnswersCount by remember { mutableIntStateOf(0) }
    var isInitialized by remember { mutableStateOf(false) }

    val questions = remember(sectionId) { getGrammarQuestionsFromAssets(context, sectionId) }

    LaunchedEffect(sectionId) {
        val (savedQ, savedScore) = repo.getGrammarProgress(sectionId)
        currentQuestion = savedQ
        correctAnswersCount = savedScore
        isInitialized = true
    }

    if (currentQuestion > 10) {
        LaunchedEffect(Unit) {
            repo.saveGrammarResult(sectionId, correctAnswersCount)
            repo.saveCurrentGrammarStep(sectionId, 1, 0)
        }
    }

    if (!isInitialized) return
    val activeQuestion = questions.getOrNull(currentQuestion - 1)

    val handleNext: () -> Unit = {
        selectedOption = null
        resultText = null
        currentQuestion++
        coroutineScope.launch {
            repo.saveCurrentGrammarStep(sectionId, currentQuestion, correctAnswersCount)
        }
    }

    val handleCheck: () -> Unit = {
        calculateAnswerResult(
            selectedOption = selectedOption,
            activeQuestion = activeQuestion,
            resultText = resultText,
            onCorrect = {
                resultText = "Правильно"
                correctAnswersCount++
                coroutineScope.launch {
                    delay(1000L)
                    handleNext()
                }
            },
            onIncorrect = { errorMsg ->
                resultText = errorMsg
            }
        )
    }

    GrammarQuizUi(
        sectionId = sectionId,
        currentQuestion = currentQuestion,
        activeQuestion = activeQuestion,
        selectedOption = selectedOption,
        resultText = resultText,
        correctAnswersCount = correctAnswersCount,
        onOptionSelect = { if (resultText == null) selectedOption = it },
        onCheck = handleCheck,
        onNext = handleNext,
        onBack = onBack
    )
}

@Composable
private fun GrammarQuizUi(
    sectionId: String, currentQuestion: Int, activeQuestion: GrammarQuestion?,
    selectedOption: Int?, resultText: String?, correctAnswersCount: Int,
    onOptionSelect: (Int) -> Unit, onCheck: () -> Unit, onNext: () -> Unit, onBack: () -> Unit
) {
    Scaffold(topBar = { TopBarBack(onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            if (currentQuestion > 10) {
                Text("Раздел $sectionId завершен", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Правильных ответов: $correctAnswersCount из 10", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Вернуться") }
            } else {
                QuizQuestionContent(currentQuestion, activeQuestion, selectedOption, resultText, onOptionSelect, onCheck, onNext)
            }
        }
    }
}

@Composable
fun ColumnScope.QuizQuestionContent(
    currentQuestion: Int,
    activeQuestion: GrammarQuestion?,
    selectedOption: Int?,
    resultText: String?,
    onOptionSelect: (Int) -> Unit,
    onCheck: () -> Unit,
    onNext: () -> Unit
) {
    Text(text = "Вопрос $currentQuestion из 10", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
    Spacer(modifier = Modifier.height(24.dp))
    Text(text = activeQuestion?.question ?: "Загрузка вопроса...", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))

    val options = activeQuestion?.options ?: emptyList()
    for ((index, optionText) in options.withIndex()) {
        val optionId = index + 1
        val isSelected = selectedOption == optionId
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            RadioButton(
                selected = isSelected,
                onClick = { onOptionSelect(optionId) },
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary, unselectedColor = MaterialTheme.colorScheme.onSurface)
            )
            Text(text = optionText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onCheck,
        enabled = resultText == null && selectedOption != null,
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        Text("Проверить", style = MaterialTheme.typography.titleMedium)
    }

    if (resultText != null) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = resultText, style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterHorizontally))

        if (resultText != "Правильно") {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onNext, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Следующий вопрос")
            }
        }
    }
}

@Composable
fun StatisticsScreen(repo: WordRepository, onBack: () -> Unit) {
    var statsA1 by remember { mutableStateOf<List<WordEntity>>(emptyList()) }
    var statsA2 by remember { mutableStateOf<List<WordEntity>>(emptyList()) }
    var statsB1 by remember { mutableStateOf<List<WordEntity>>(emptyList()) }
    var grammarStats by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(Unit) {
        statsA1 = repo.getWordsByDictionary("A1")
        statsA2 = repo.getWordsByDictionary("A2")
        statsB1 = repo.getWordsByDictionary("B1")
        grammarStats = repo.getGrammarStats()
    }

    Scaffold(topBar = { TopBarBack(onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val levelsData = listOf("A1" to statsA1, "A2" to statsA2, "B1" to statsB1)
            VocabularyStatsSection(levelsData)

            Text(text = "Статистика тестов:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            GrammarStatsSection(grammarStats)
        }
    }
}

@Composable
private fun VocabularyStatsSection(levelsData: List<Pair<String, List<WordEntity>>>) {
    levelsData.forEach { (levelName, words) ->
        val newWords = words.count { it.intervalStep == 0 && it.nextReviewTime == 0L }
        val knownWords = words.count { it.intervalStep == 999 }
        val studyingWords = words.count { it.intervalStep == 0 && it.nextReviewTime > 0L }

        Text(text = "$levelName:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(text = "Осталось новых слов: $newWords")
        Text(text = "Известные: $knownWords")
        Text(text = "Изучаемые: $studyingWords")

        val maxStep = words.filter { it.intervalStep < 999 }.maxOfOrNull { it.intervalStep } ?: 0
        for (step in 1..maxStep) {
            val count = words.count { it.intervalStep == step && it.nextReviewTime > 0L && it.nextReviewTime != Long.MAX_VALUE }
            if (count > 0) {
                val timesString = if (step % 10 == 1 && step % 100 != 11) "раз" else "раза"
                Text(text = "  • Повторено $step $timesString: $count")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun GrammarStatsSection(grammarStats: Map<String, Int>) {
    val sections = listOf(
        "1" to "Verb 'To Be",
        "2" to "Plural Nouns",
        "3" to "Present Simple vs Continuous",
        "4" to "Past Simple"
    )
    sections.forEach { (id, label) ->
        val correct = grammarStats[id] ?: 0
        Text(text = "$label: правильно $correct из 10")
    }
}

@Composable
fun DictionariesSelectionScreen(navController: NavController) {
    Scaffold(
        topBar = { TopBarBack { navController.popBackStackSafe() } }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            listOf("A1", "A2", "B1").forEach { level ->
                Button(
                    onClick = { navController.navigateSafe("dictionary_view/$level") },
                    modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 6.dp).height(50.dp)
                ) {
                    Text("Словарь $level", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun DictionaryViewScreen(
    lvl: String,
    repo: WordRepository,
    onAddWord: () -> Unit,
    onBack: () -> Unit
) {
    val state = rememberDictionaryState(lvl, repo)

    state.wordToEdit?.let { word ->
        EditWordDialog(
            word = word,
            onDismiss = { state.clearEditing() },
            onSave = { newWord, newTranslation ->
                state.updateWord(word, newWord, newTranslation)
            }
        )
    }

    Scaffold(topBar = { TopBarBack(onBack) }) { padding ->
        DictionaryContent(
            lvl = lvl,
            state = state,
            onAddWord = onAddWord,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun DictionaryContent(
    lvl: String,
    state: DictionaryState,
    onAddWord: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        WordList(
            lvl = lvl,
            state = state,
            modifier = Modifier.weight(1f)
        )
        AddWordButton(onClick = onAddWord)
    }
}

@Composable
private fun WordList(
    lvl: String,
    state: DictionaryState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Словарь $lvl",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { state.searchQuery = it },
            label = { Text("Поиск слова") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        if (!state.isLoaded) return@Column

        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (state.words.isEmpty()) {
                EmptyListMessage()
                return@Column
            }
            state.words.forEach { word ->
                WordRow(
                    word = word,
                    onEdit = { state.startEditing(word) },
                    onDelete = { state.deleteWord(word) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun WordRow(
    word: WordEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cleanTranslation = remember(word.translation) {
        word.translation
            .replace("[", "")
            .replace("]", "")
            .replace("\"", "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(word.word, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(cleanTranslation, style = MaterialTheme.typography.bodyMedium)
        }
        Row {
            TextButton(onClick = onEdit) { Text("✎") }
            TextButton(onClick = onDelete) {
                Text("✕", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun EmptyListMessage() {
    Text(
        text = "Ничего не найдено",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}

@Composable
private fun AddWordButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(55.dp)
    ) {
        Text("Добавить слово", style = MaterialTheme.typography.titleMedium)
    }
}

@Stable
class DictionaryState(
    private val lvl: String,
    private val repo: WordRepository,
    private val scope: CoroutineScope
) {
    var words by mutableStateOf<List<WordEntity>>(emptyList())
        private set
    var isLoaded by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
    var wordToEdit by mutableStateOf<WordEntity?>(null)
        private set

    suspend fun load(openAsset: () -> InputStream) {
        delay(600)
        repo.checkAndPrepopulate(lvl, openAsset)
        words = sorted(repo.getWordsByDictionary(lvl))
        isLoaded = true
    }

    fun search() {
        if (!isLoaded) return
        scope.launch { words = sorted(repo.searchDictionary(lvl, searchQuery)) }
    }

    fun startEditing(word: WordEntity) { wordToEdit = word }
    fun clearEditing() { wordToEdit = null }

    fun updateWord(old: WordEntity, newWord: String, newTranslation: String) {
        scope.launch {
            repo.updateWordLocally(old.copy(word = newWord, translation = newTranslation))
            wordToEdit = null
            reload()
        }
    }

    fun deleteWord(word: WordEntity) {
        scope.launch {
            repo.deleteWordLocally(word)
            reload()
        }
    }

    private suspend fun reload() {
        words = sorted(repo.searchDictionary(lvl, searchQuery))
    }

    private fun sorted(list: List<WordEntity>) = list.sortedBy { it.word.lowercase() }
}

@Composable
fun rememberDictionaryState(lvl: String, repo: WordRepository): DictionaryState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(lvl) { DictionaryState(lvl, repo, scope) }

    LaunchedEffect(lvl) {
        state.load { context.assets.open("$lvl.json") }
    }
    LaunchedEffect(state.searchQuery) {
        state.search()
    }
    return state
}

@Composable
fun EditWordDialog(
    word: WordEntity,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val cleanTranslation = remember(word.translation) {
        word.translation
            .replace("[", "")
            .replace("]", "")
            .replace("\"", "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }

    var newWord by remember { mutableStateOf(word.word) }
    var newTranslation by remember { mutableStateOf(cleanTranslation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать слово") },
        text = {
            Column {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it },
                    label = { Text("Слово") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = newTranslation,
                    onValueChange = { newTranslation = it },
                    label = { Text("Перевод") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(newWord.trim(), newTranslation.trim()) },
                enabled = newWord.isNotBlank() && newTranslation.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun AddWordScreen(lvl: String, repo: WordRepository, onBack: () -> Unit) {
    var wordText by remember { mutableStateOf("") }
    var translationText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(topBar = { TopBarBack(onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Добавить слово в $lvl", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))

            OutlinedTextField(
                value = wordText,
                onValueChange = {
                    wordText = it
                    errorMessage = null
                },
                label = { Text("Слово на английском") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = translationText,
                onValueChange = {
                    translationText = it
                    errorMessage = null
                },
                label = { Text("Перевод (можно несколько через запятую)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            )

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Button(
                onClick = {
                    if (wordText.isNotBlank() && translationText.isNotBlank()) {
                        coroutineScope.launch {
                            val success = repo.addNewWordToDictionary(lvl, wordText.trim(), translationText.trim())
                            if (success) {
                                onBack()
                            } else {
                                errorMessage = "Такое слово уже есть в словаре"
                            }
                        }
                    }
                },
                enabled = wordText.isNotBlank() && translationText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Сохранить", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}