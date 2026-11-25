package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.TopicStudyMode
import com.example.ui.IeltsViewModel
import com.example.ui.IeltsViewModelFactory
import com.example.ui.screens.FlashcardSessionScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.MandatoryReviewGateScreen
import com.example.ui.screens.QuizSessionScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.screens.TopicLibraryScreen
import com.example.ui.screens.VocabularyDetailScreen
import com.example.ui.screens.VocabularyLibraryScreen
import com.example.ui.theme.MyApplicationTheme

enum class MainTab(
    val route: String,
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    TOPICS("topics", R.string.nav_topics, Icons.Filled.Category, Icons.Outlined.Category),
    LIBRARY("library", R.string.nav_library, Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    STATS("stats", R.string.nav_stats, Icons.Filled.BarChart, Icons.Outlined.BarChart),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val viewModel: IeltsViewModel = viewModel(factory = IeltsViewModelFactory(context))
            val settings by viewModel.userSettings.collectAsStateWithLifecycle()

            val useDarkTheme = when (settings.darkModePref) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = useDarkTheme) {
                IeltsAppRoot(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IeltsAppRoot(viewModel: IeltsViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Enforce Mandatory Review Gate check whenever the app enters or returns to the foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshMandatoryGateOnForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isInitialLoading by viewModel.isInitialLoading.collectAsStateWithLifecycle()
    val mandatoryGateState by viewModel.mandatoryGateState.collectAsStateWithLifecycle()
    val gateEvaluation by viewModel.gateEvaluation.collectAsStateWithLifecycle()
    val gateUnlockSummary by viewModel.lastGateUnlockSummary.collectAsStateWithLifecycle()

    val allVocab by viewModel.allVocabularyWithProgress.collectAsStateWithLifecycle()
    val filteredVocab by viewModel.filteredVocabulary.collectAsStateWithLifecycle()
    val topicsWithStats by viewModel.topicsWithStats.collectAsStateWithLifecycle()
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val activeFlashcardSession by viewModel.activeFlashcardSession.collectAsStateWithLifecycle()
    val flashcardState by viewModel.flashcardState.collectAsStateWithLifecycle()
    val quizState by viewModel.quizState.collectAsStateWithLifecycle()
    val detailAiState by viewModel.detailAiState.collectAsStateWithLifecycle()
    val reviewHistory by viewModel.recentReviewHistory.collectAsStateWithLifecycle()
    val quizAttempts by viewModel.quizAttempts.collectAsStateWithLifecycle()
    val importReport by viewModel.importReport.collectAsStateWithLifecycle()
    val llmCacheCount by viewModel.llmCacheCount.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedTopicFilter by viewModel.selectedTopicFilter.collectAsStateWithLifecycle()
    val selectedDifficultyFilter by viewModel.selectedDifficultyFilter.collectAsStateWithLifecycle()
    val selectedStateFilter by viewModel.selectedStateFilter.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var selectedDetailVocabId by rememberSaveable { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    val dueCount = remember(allVocab) { allVocab.count { it.isDue } }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (!isInitialLoading && !mandatoryGateState.isLocked && !flashcardState.isActive && !quizState.isActive && selectedDetailVocabId == null) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.app_avatar_icon),
                                contentDescription = "Avatar ứng dụng IELTS Vocab 7.0",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .testTag("top_bar_app_avatar")
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "IELTS Vocab 7.0",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "Từ vựng Học thuật (Band 5.5 → 7.0+)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            // Only show bottom navigation bar on compact/medium width when Mandatory Review Gate and active sessions are not blocking
            BoxWithConstraints {
                if (maxWidth < 640.dp && !isInitialLoading && !mandatoryGateState.isLocked && !flashcardState.isActive && !quizState.isActive && selectedDetailVocabId == null) {
                    NavigationBar(modifier = Modifier.testTag("main_bottom_nav")) {
                        MainTab.entries.forEach { tab ->
                            val isSelected = currentTab == tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { currentTab = tab },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (tab == MainTab.HOME && dueCount > 0) {
                                                Badge { Text(dueCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.titleRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(tab.titleRes)) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            val isWideScreen = maxWidth >= 640.dp

            when {
                isInitialLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                // 1. CRITICAL REQUIREMENT: Mandatory Review Gate blocks all other learning screens until completed
                mandatoryGateState.isLocked -> {
                    MandatoryReviewGateScreen(
                        gateState = mandatoryGateState,
                        evaluation = gateEvaluation,
                        onEvaluateAnswer = viewModel::evaluateMandatoryGateAnswer,
                        onAdvanceQuestion = viewModel::advanceMandatoryGateQuestion
                    )
                }

                // 2. Active Flashcard Session
                flashcardState.isActive -> {
                    FlashcardSessionScreen(
                        state = flashcardState,
                        onFlipCard = viewModel::toggleFlashcardFlip,
                        onAnswerCard = viewModel::answerCurrentFlashcard,
                        onRequestQuickTip = viewModel::requestFlashcardQuickTip,
                        onExitSession = viewModel::exitFlashcardSession
                    )
                }

                // 3. Active Quiz Session
                quizState.isActive -> {
                    QuizSessionScreen(
                        state = quizState,
                        onSubmitAnswer = viewModel::submitQuizAnswer,
                        onNextQuestion = viewModel::nextQuizQuestion,
                        onRegenerateWithAi = viewModel::regenerateCurrentQuestionWithAi,
                        onExitQuiz = viewModel::exitQuizSession
                    )
                }

                // 4. Vocabulary Detail Screen
                selectedDetailVocabId != null -> {
                    val detailItem = allVocab.find { it.entry.id == selectedDetailVocabId }
                    if (detailItem == null) {
                        selectedDetailVocabId = null
                    } else {
                        val topicNames = detailItem.topicIds.mapNotNull { tid ->
                            topicsWithStats.find { it.topic.id == tid }?.topic?.vietnameseName
                        }
                        VocabularyDetailScreen(
                            item = detailItem,
                            topicNames = topicNames,
                            aiState = detailAiState,
                            onBack = { selectedDetailVocabId = null },
                            onRequestEnrichment = { viewModel.requestAiEnrichment(detailItem.entry.id) },
                            onRequestComparison = { q -> viewModel.requestWordComparisonHighThinking(detailItem.entry.id, q) },
                            onRequestSearchGrounding = { viewModel.requestSearchGroundedUsage(detailItem.entry.id) }
                        )
                    }
                }

                // 5. Main Tab Screens (with NavigationRail on wide screens)
                else -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (isWideScreen) {
                            NavigationRail {
                                MainTab.entries.forEach { tab ->
                                    val isSelected = currentTab == tab
                                    NavigationRailItem(
                                        selected = isSelected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = stringResource(tab.titleRes)
                                            )
                                        },
                                        label = { Text(stringResource(tab.titleRes)) }
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            when (currentTab) {
                                MainTab.HOME -> HomeDashboardScreen(
                                    vocabList = allVocab,
                                    topicsWithStats = topicsWithStats,
                                    settings = settings,
                                    activeFlashcardSession = activeFlashcardSession,
                                    currentDateFormatted = viewModel.getCurrentLocalDateFormatted(),
                                    gateUnlockSummary = gateUnlockSummary,
                                    onDismissGateSummary = viewModel::dismissGateUnlockSummary,
                                    onStartFlashcards = { topicId, mode, forceNew ->
                                        viewModel.startFlashcardSession(topicId, mode, forceNew)
                                    },
                                    onStartQuiz = { topicId, mode, qType ->
                                        viewModel.startQuizSession(topicId, mode, qType)
                                    },
                                    onOpenTopicLibrary = { currentTab = MainTab.TOPICS },
                                    onOpenVocabDetail = { vocabId ->
                                        viewModel.resetDetailAiState(vocabId)
                                        selectedDetailVocabId = vocabId
                                    }
                                )

                                MainTab.TOPICS -> TopicLibraryScreen(
                                    topicsWithStats = topicsWithStats,
                                    onStartFlashcards = { topicId, mode, forceNew ->
                                        viewModel.startFlashcardSession(topicId, mode, forceNew)
                                    },
                                    onStartQuiz = { topicId, mode, qType ->
                                        viewModel.startQuizSession(topicId, mode, qType)
                                    }
                                )

                                MainTab.LIBRARY -> VocabularyLibraryScreen(
                                    filteredVocab = filteredVocab,
                                    totalMasterCount = allVocab.size,
                                    topicsWithStats = topicsWithStats,
                                    searchQuery = searchQuery,
                                    selectedTopicId = selectedTopicFilter,
                                    selectedDifficulty = selectedDifficultyFilter,
                                    selectedState = selectedStateFilter,
                                    onSearchQueryChange = viewModel::setSearchQuery,
                                    onSelectTopic = viewModel::setTopicFilter,
                                    onSelectDifficulty = viewModel::setDifficultyFilter,
                                    onSelectState = viewModel::setStateFilter,
                                    onOpenVocabDetail = { vocabId ->
                                        viewModel.resetDetailAiState(vocabId)
                                        selectedDetailVocabId = vocabId
                                    }
                                )

                                MainTab.STATS -> StatisticsScreen(
                                    vocabList = allVocab,
                                    reviewHistory = reviewHistory,
                                    quizAttempts = quizAttempts,
                                    onOpenVocabDetail = { vocabId ->
                                        viewModel.resetDetailAiState(vocabId)
                                        selectedDetailVocabId = vocabId
                                    },
                                    onPracticeWeakWords = {
                                        viewModel.startQuizSession(null, TopicStudyMode.REVIEW_WEAK_WORDS, null)
                                    }
                                )

                                MainTab.SETTINGS -> SettingsScreen(
                                    settings = settings,
                                    llmStatus = viewModel.getLlmStatus(),
                                    llmCacheCount = llmCacheCount,
                                    currentDateFormatted = viewModel.getCurrentLocalDateFormatted(),
                                    importReport = importReport,
                                    onSaveAiConfig = viewModel::saveAiConfiguration,
                                    onUpdateDailyGoal = viewModel::updateDailyGoal,
                                    onUpdateReminder = viewModel::updateReminderSettings,
                                    onTriggerTestNotification = viewModel::triggerTestNotificationNow,
                                    onUpdateIntervals = viewModel::updateSpacedRepetitionSettings,
                                    onUpdateTheme = viewModel::updateThemePreference,
                                    onAdvanceSimulatedDays = viewModel::advanceSimulatedClockDays,
                                    onImportSeedData = viewModel::importVocabularyData
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
