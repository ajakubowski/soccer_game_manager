package com.example.soccergamemanager.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.Configuration
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.soccergamemanager.R
import com.example.soccergamemanager.data.AssignmentEntity
import com.example.soccergamemanager.data.GameDetail
import com.example.soccergamemanager.data.GameEntity
import com.example.soccergamemanager.data.GoalEventEntity
import com.example.soccergamemanager.data.PlayerAvailabilityEntity
import com.example.soccergamemanager.data.PlayerEntity
import com.example.soccergamemanager.data.SeasonEntity
import com.example.soccergamemanager.data.isAvailableForHalf
import com.example.soccergamemanager.data.manualGroupLocks
import com.example.soccergamemanager.data.template
import com.example.soccergamemanager.domain.FieldPosition
import com.example.soccergamemanager.domain.GameStatus
import com.example.soccergamemanager.domain.GoalSide
import com.example.soccergamemanager.domain.PositionGroup
import com.example.soccergamemanager.ui.theme.BadgeBlack
import com.example.soccergamemanager.ui.theme.IceWhite
import com.example.soccergamemanager.ui.theme.McFarlandBlue
import com.example.soccergamemanager.ui.theme.McFarlandBlueDark
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class Destination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Setup("setup", "Setup", Icons.Outlined.Settings),
    Games("games", "Games", Icons.Outlined.Event),
    History("history", "History & Stats", Icons.Outlined.BarChart),
    Reports("reports", "Reports", Icons.Outlined.Print),
}

private enum class GameHubTab(val routeSegment: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    PLANNER("planner", "Planner"),
    LIVE("live", "Live"),
    REPORT("report", "Report"),
}

private enum class GameWorkflowStatus(val label: String) {
    NEEDS_LINEUP("Needs lineup"),
    READY("Ready"),
    LIVE("Live"),
    FINAL("Final"),
}

private object GameHubRoute {
    const val base = "game_hub"
    const val gameIdArg = "gameId"
    const val tabArg = "tab"
    const val routePattern = "$base/{$gameIdArg}/{$tabArg}"

    fun create(gameId: String, tab: GameHubTab): String = "$base/$gameId/${tab.routeSegment}"
}

private fun AppUiState.workflowStatus(game: GameEntity): GameWorkflowStatus = when (game.status) {
    GameStatus.LIVE -> GameWorkflowStatus.LIVE
    GameStatus.FINAL -> GameWorkflowStatus.FINAL
    else -> if ((assignmentCountsByGame[game.gameId] ?: 0) > 0) {
        GameWorkflowStatus.READY
    } else {
        GameWorkflowStatus.NEEDS_LINEUP
    }
}

private fun defaultHubTabFor(uiState: AppUiState, game: GameEntity): GameHubTab = when (uiState.workflowStatus(game)) {
    GameWorkflowStatus.NEEDS_LINEUP -> GameHubTab.PLANNER
    GameWorkflowStatus.READY -> GameHubTab.OVERVIEW
    GameWorkflowStatus.LIVE -> GameHubTab.LIVE
    GameWorkflowStatus.FINAL -> GameHubTab.REPORT
}

private fun String?.isGamesSectionRoute(): Boolean =
    this == Destination.Games.route || this == GameHubRoute.routePattern

private fun gameHubTabFromRouteSegment(routeSegment: String?): GameHubTab =
    GameHubTab.entries.firstOrNull { it.routeSegment == routeSegment } ?: GameHubTab.OVERVIEW

@Composable
fun SoccerManagerRoot(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Destination.Setup.route

    LaunchedEffect(uiState.transientMessage) {
        val message = uiState.transientMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
            ) {
                Destination.entries.forEach { destination ->
                    val selected = when (destination) {
                        Destination.Games -> currentRoute.isGamesSectionRoute()
                        else -> currentRoute == destination.route
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = Color.White.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.inverseOnSurface,
                            unselectedTextColor = MaterialTheme.colorScheme.inverseOnSurface,
                        ),
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
        ) {
            NavHost(
                navController = navController,
                startDestination = Destination.Setup.route,
            ) {
                composable(Destination.Setup.route) {
                    SetupScreen(
                        uiState = uiState,
                        onCreateSeason = viewModel::createSeason,
                        onSelectSeason = viewModel::selectSeason,
                        onUpdateSeason = viewModel::updateSeason,
                        onDeleteSeason = viewModel::deleteSeason,
                        onAddPlayer = viewModel::addPlayer,
                        onUpdateSeasonDefaults = viewModel::updateSeasonDefaults,
                        onUpdateOrientationLock = viewModel::updateOrientationLock,
                        onUpdatePlayer = viewModel::updatePlayerDetails,
                        onTogglePlayerActive = viewModel::togglePlayerActive,
                    )
                }
                composable(Destination.Games.route) {
                    GamesScreen(
                        uiState = uiState,
                        onCreateGame = viewModel::createGame,
                        onUpdateGame = viewModel::updateGameDetails,
                        onOpenGameHub = { gameId, initialTab ->
                            viewModel.selectGame(gameId)
                            navController.navigate(GameHubRoute.create(gameId, initialTab))
                        },
                        onOpenReports = { gameId ->
                            viewModel.selectGame(gameId)
                            navController.navigate(Destination.Reports.route)
                        },
                    )
                }
                composable(GameHubRoute.routePattern) { entry ->
                    val gameId = entry.arguments?.getString(GameHubRoute.gameIdArg)
                    val initialTab = gameHubTabFromRouteSegment(entry.arguments?.getString(GameHubRoute.tabArg))
                    if (gameId != null) {
                        LaunchedEffect(gameId) {
                            viewModel.selectGame(gameId)
                        }
                    }
                    GameHubScreen(
                        uiState = uiState,
                        initialTab = initialTab,
                        onBackToGames = { navController.popBackStack(Destination.Games.route, false) },
                        onOpenTopLevelReports = { navController.navigate(Destination.Reports.route) },
                        onSelectGame = viewModel::selectGame,
                        onRefreshReport = viewModel::refreshReport,
                        onGenerateAssignments = viewModel::generateAssignments,
                        onToggleAvailability = viewModel::toggleAvailability,
                        onUpdateManualGroupLock = viewModel::updateManualGroupLock,
                        onSetAssignmentPlayer = viewModel::setAssignmentPlayer,
                        onStartOrPause = viewModel::startOrPauseClock,
                        onAdvanceRound = viewModel::advanceRound,
                        onAdvanceHalf = viewModel::advanceHalf,
                        onRecordGoal = viewModel::recordGoal,
                        onApplyLiveSub = viewModel::applyLiveSub,
                        onApplyInjurySub = viewModel::applyInjurySub,
                        onClearPlayerInjury = viewModel::clearPlayerInjury,
                        onFinalize = viewModel::finalizeGame,
                    )
                }
                composable(Destination.History.route) {
                    HistoryScreen(
                        uiState = uiState,
                        onSelectGame = viewModel::selectGame,
                    )
                }
                composable(Destination.Reports.route) {
                    ReportsScreen(
                        uiState = uiState,
                        onSelectGame = viewModel::selectGame,
                        onRefresh = viewModel::refreshReport,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupScreen(
    uiState: AppUiState,
    onCreateSeason: (String, String) -> Unit,
    onSelectSeason: (String) -> Unit,
    onUpdateSeason: (SeasonEntity, String, String) -> Unit,
    onDeleteSeason: (SeasonEntity) -> Unit,
    onAddPlayer: (String, String, Boolean) -> Unit,
    onUpdateSeasonDefaults: (String, String) -> Unit,
    onUpdateOrientationLock: (OrientationLockMode) -> Unit,
    onUpdatePlayer: (PlayerEntity, String, String, Boolean, String) -> Unit,
    onTogglePlayerActive: (PlayerEntity) -> Unit,
) {
    val selectedTemplate = uiState.selectedSeason?.template()
    var teamProfileName by rememberSaveable(uiState.selectedSeasonId, uiState.selectedSeason?.name) {
        mutableStateOf(uiState.selectedSeason?.name ?: "McFarland SC")
    }
    var teamYear by rememberSaveable(uiState.selectedSeasonId, uiState.selectedSeason?.year) {
        mutableStateOf(
            uiState.selectedSeason?.year?.toString()
                ?: Date().toInstant().atZone(ZoneId.systemDefault()).year.toString(),
        )
    }
    var playerName by rememberSaveable { mutableStateOf("") }
    var jerseyNumber by rememberSaveable { mutableStateOf("") }
    var preferredKeeper by rememberSaveable { mutableStateOf(true) }
    var editingPlayerId by rememberSaveable(uiState.selectedSeasonId) { mutableStateOf<String?>(null) }
    var halfDurationMinutes by rememberSaveable(uiState.selectedSeasonId) {
        mutableStateOf(selectedTemplate?.halfDurationMinutes?.toString() ?: "25")
    }
    var substitutionWindowMinutes by rememberSaveable(uiState.selectedSeasonId) {
        mutableStateOf(selectedTemplate?.substitutionWindowMinutes?.toString() ?: "4")
    }
    val estimatedRoundsPerHalf = run {
        val halfMinutes = halfDurationMinutes.toIntOrNull()?.coerceAtLeast(1) ?: 25
        val roundMinutes = substitutionWindowMinutes.toIntOrNull()?.coerceAtLeast(1) ?: 4
        ((halfMinutes + roundMinutes - 1) / roundMinutes) + 1
    }
    val editingPlayer = uiState.players.firstOrNull { it.playerId == editingPlayerId }

    editingPlayer?.let { player ->
        var editName by rememberSaveable(player.playerId) { mutableStateOf(player.name) }
        var editJersey by rememberSaveable(player.playerId) { mutableStateOf(player.jerseyNumber) }
        var editNotes by rememberSaveable(player.playerId) { mutableStateOf(player.notes) }
        var editPreferredKeeper by rememberSaveable(player.playerId) { mutableStateOf(player.preferredKeeper) }

        AlertDialog(
            onDismissRequest = { editingPlayerId = null },
            title = { Text("Edit player") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Player name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editJersey,
                        onValueChange = { editJersey = it },
                        label = { Text("Jersey number") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilterChip(
                        selected = editPreferredKeeper,
                        onClick = { editPreferredKeeper = !editPreferredKeeper },
                        label = { Text("Keeper eligible") },
                    )
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdatePlayer(player, editName, editJersey, editPreferredKeeper, editNotes)
                        editingPlayerId = null
                    },
                ) {
                    Text("Save player")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPlayerId = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                title = "Team & Roster Setup",
                subtitle = "Manage the team, roster, and default game settings.",
            )
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Team profile", style = MaterialTheme.typography.titleLarge)
                    if (uiState.seasons.size > 1) {
                        Text("Active team")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.seasons.forEach { season ->
                                FilterChip(
                                    selected = season.seasonId == uiState.selectedSeasonId,
                                    onClick = { onSelectSeason(season.seasonId) },
                                    label = { Text("${season.name} ${season.year}") },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = teamProfileName,
                        onValueChange = { teamProfileName = it },
                        label = { Text("Team name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = teamYear,
                        onValueChange = { teamYear = it },
                        label = { Text("Year") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                uiState.selectedSeason?.let { team ->
                                    onUpdateSeason(team, teamProfileName, teamYear)
                                }
                            },
                            enabled = uiState.selectedSeason != null,
                        ) {
                            Text("Save team")
                        }
                        OutlinedButton(onClick = { onCreateSeason(teamProfileName, teamYear) }) {
                            Text("New team")
                        }
                        TextButton(
                            onClick = { uiState.selectedSeason?.let(onDeleteSeason) },
                            enabled = uiState.selectedSeason != null,
                        ) {
                            Text("Delete team")
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Game defaults", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = halfDurationMinutes,
                        onValueChange = { halfDurationMinutes = it },
                        label = { Text("Half length (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = substitutionWindowMinutes,
                        onValueChange = { substitutionWindowMinutes = it },
                        label = { Text("Substitution timer (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Estimated planned rounds per half: $estimatedRoundsPerHalf",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Button(
                        onClick = { onUpdateSeasonDefaults(halfDurationMinutes, substitutionWindowMinutes) },
                        enabled = uiState.selectedSeasonId != null,
                    ) {
                        Text("Save defaults")
                    }
                    Text("Screen orientation", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrientationLockMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.orientationLockMode == mode,
                                onClick = { onUpdateOrientationLock(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Add player", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = playerName,
                        onValueChange = { playerName = it },
                        label = { Text("Player name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = jerseyNumber,
                        onValueChange = { jerseyNumber = it },
                        label = { Text("Jersey number") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilterChip(
                        selected = preferredKeeper,
                        onClick = { preferredKeeper = !preferredKeeper },
                        label = { Text("Keeper eligible") },
                    )
                    Button(
                        onClick = {
                            onAddPlayer(playerName, jerseyNumber, preferredKeeper)
                            playerName = ""
                            jerseyNumber = ""
                            preferredKeeper = true
                        },
                        enabled = uiState.selectedSeasonId != null,
                    ) {
                        Text("Add player")
                    }
                }
            }
        }
        item {
            Text("Roster", style = MaterialTheme.typography.titleLarge)
        }
        items(uiState.players, key = { it.playerId }) { player ->
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(player.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Jersey ${player.jerseyNumber.ifBlank { "--" }} • ${if (player.preferredKeeper) "Keeper eligible" else "Not keeper eligible"}",
                        )
                        if (player.notes.isNotBlank()) {
                            Text(player.notes)
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(onClick = { editingPlayerId = player.playerId }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit player")
                        }
                        FilterChip(
                            selected = player.active,
                            onClick = { onTogglePlayerActive(player) },
                            label = { Text(if (player.active) "Active" else "Inactive") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GamesScreen(
    uiState: AppUiState,
    onCreateGame: (String, String, Long) -> Unit,
    onUpdateGame: (GameEntity, String, String, Long) -> Unit,
    onOpenGameHub: (String, GameHubTab) -> Unit,
    onOpenReports: (String) -> Unit,
) {
    val context = LocalContext.current
    var opponent by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var scheduledAtMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var editingGameId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingGame = uiState.games.firstOrNull { it.gameId == editingGameId }
    val now = remember(uiState.games) { ZonedDateTime.now() }
    val liveGame = uiState.games.firstOrNull { uiState.workflowStatus(it) == GameWorkflowStatus.LIVE }
    val focusGame = uiState.games
        .filterNot { it.gameId == liveGame?.gameId }
        .sortedBy { it.scheduledAt }
        .firstOrNull { game ->
            Instant.ofEpochMilli(game.scheduledAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate() >= now.toLocalDate()
        } ?: uiState.games.sortedByDescending { it.scheduledAt }.firstOrNull()

    editingGame?.let { game ->
        var editOpponent by rememberSaveable(game.gameId) { mutableStateOf(game.opponent) }
        var editLocation by rememberSaveable(game.gameId) { mutableStateOf(game.location) }
        var editScheduledAtMillis by rememberSaveable(game.gameId) { mutableStateOf(game.scheduledAt) }

        AlertDialog(
            onDismissRequest = { editingGameId = null },
            title = { Text("Edit game") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editOpponent,
                        onValueChange = { editOpponent = it },
                        label = { Text("Opponent") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editLocation,
                        onValueChange = { editLocation = it },
                        label = { Text("Location") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GameDateTimePicker(
                        scheduledAtMillis = editScheduledAtMillis,
                        onScheduledAtChange = { editScheduledAtMillis = it },
                        context = context,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateGame(game, editOpponent, editLocation, editScheduledAtMillis)
                        editingGameId = null
                    },
                ) {
                    Text("Save game")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingGameId = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            CompactScreenHeader(
                title = "Games",
                subtitle = "Choose a match and manage planning, live play, and reports from one place.",
            )
        }
        if (liveGame != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenGameHub(liveGame.gameId, GameHubTab.LIVE) },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Continue live game", style = MaterialTheme.typography.titleLarge)
                        Text("${liveGame.opponent} • ${formatDate(liveGame.scheduledAt)}")
                        Button(onClick = { onOpenGameHub(liveGame.gameId, GameHubTab.LIVE) }) {
                            Text("Resume live")
                        }
                    }
                }
            }
        }
        if (focusGame != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenGameHub(focusGame.gameId, defaultHubTabFor(uiState, focusGame)) },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Today / Next game", style = MaterialTheme.typography.titleLarge)
                        Text(focusGame.opponent, style = MaterialTheme.typography.headlineSmall)
                        Text("${formatDate(focusGame.scheduledAt)} • ${focusGame.location.ifBlank { "Location TBD" }}")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WorkflowStatusChip(uiState.workflowStatus(focusGame))
                            TextButton(onClick = { onOpenGameHub(focusGame.gameId, defaultHubTabFor(uiState, focusGame)) }) {
                                Text("Open game hub")
                            }
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("New game", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = opponent,
                        onValueChange = { opponent = it },
                        label = { Text("Opponent") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Location") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GameDateTimePicker(
                        scheduledAtMillis = scheduledAtMillis,
                        onScheduledAtChange = { scheduledAtMillis = it },
                        context = context,
                    )
                    Button(
                        onClick = {
                            onCreateGame(opponent, location, scheduledAtMillis)
                            opponent = ""
                            location = ""
                            scheduledAtMillis = System.currentTimeMillis()
                        },
                        enabled = uiState.selectedSeasonId != null,
                    ) {
                        Text("Create game")
                    }
                }
            }
        }
        items(uiState.games, key = { it.gameId }) { game ->
            val workflowStatus = uiState.workflowStatus(game)
            val primaryTab = when (workflowStatus) {
                GameWorkflowStatus.NEEDS_LINEUP -> GameHubTab.PLANNER
                GameWorkflowStatus.READY -> GameHubTab.OVERVIEW
                GameWorkflowStatus.LIVE -> GameHubTab.LIVE
                GameWorkflowStatus.FINAL -> GameHubTab.REPORT
            }
            val primaryLabel = when (workflowStatus) {
                GameWorkflowStatus.NEEDS_LINEUP -> "Plan lineup"
                GameWorkflowStatus.READY -> "Review lineup"
                GameWorkflowStatus.LIVE -> "Resume live"
                GameWorkflowStatus.FINAL -> "View report"
            }
            Card {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenGameHub(game.gameId, defaultHubTabFor(uiState, game)) }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(game.opponent, style = MaterialTheme.typography.titleLarge)
                            Text("${formatDate(game.scheduledAt)} • ${game.location.ifBlank { "Location TBD" }}")
                        }
                        WorkflowStatusChip(workflowStatus)
                    }
                    Button(
                        onClick = { onOpenGameHub(game.gameId, primaryTab) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(primaryLabel)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editingGameId = game.gameId }) {
                            Text("Edit game")
                        }
                        TextButton(onClick = { onOpenGameHub(game.gameId, defaultHubTabFor(uiState, game)) }) {
                            Text("Open hub")
                        }
                        if (workflowStatus == GameWorkflowStatus.FINAL) {
                            TextButton(onClick = { onOpenReports(game.gameId) }) {
                                Text("Report")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameHubScreen(
    uiState: AppUiState,
    initialTab: GameHubTab,
    onBackToGames: () -> Unit,
    onOpenTopLevelReports: () -> Unit,
    onSelectGame: (String) -> Unit,
    onRefreshReport: () -> Unit,
    onGenerateAssignments: () -> Unit,
    onToggleAvailability: (String, Int, Boolean) -> Unit,
    onUpdateManualGroupLock: (Int, PositionGroup, List<String>) -> Unit,
    onSetAssignmentPlayer: (String, String) -> Unit,
    onStartOrPause: () -> Unit,
    onAdvanceRound: () -> Unit,
    onAdvanceHalf: (Set<String>) -> Unit,
    onRecordGoal: (GoalSide, String?, String?) -> Unit,
    onApplyLiveSub: (String, String) -> Unit,
    onApplyInjurySub: (String, String) -> Unit,
    onClearPlayerInjury: (String, Boolean) -> Unit,
    onFinalize: () -> Unit,
) {
    val detail = uiState.selectedGameDetail ?: run {
        EmptyState("Open a game from the Games tab to manage it from the game hub.")
        return
    }
    var selectedTab by rememberSaveable(detail.game.gameId, initialTab.routeSegment) {
        mutableStateOf(initialTab)
    }
    var headerExpanded by rememberSaveable(detail.game.gameId) {
        mutableStateOf(initialTab == GameHubTab.OVERVIEW)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GameHubHeader(
            uiState = uiState,
            detail = detail,
            onBackToGames = onBackToGames,
            onOpenTopLevelReports = onOpenTopLevelReports,
            expanded = headerExpanded,
            onToggleExpanded = { headerExpanded = !headerExpanded },
        )
        GameHubTabButtons(
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
        ) {
            when (selectedTab) {
                GameHubTab.OVERVIEW -> OverviewTab(
                    uiState = uiState,
                    detail = detail,
                    onGenerateAssignments = onGenerateAssignments,
                    onOpenPlanner = { selectedTab = GameHubTab.PLANNER },
                    onOpenLive = { selectedTab = GameHubTab.LIVE },
                    onOpenReport = { selectedTab = GameHubTab.REPORT },
                )

                GameHubTab.PLANNER -> PlannerScreen(
                    uiState = uiState,
                    onToggleAvailability = onToggleAvailability,
                    onGenerateAssignments = onGenerateAssignments,
                    onUpdateManualGroupLock = onUpdateManualGroupLock,
                    onSetAssignmentPlayer = onSetAssignmentPlayer,
                    showHeader = false,
                    onGoToLive = { selectedTab = GameHubTab.LIVE },
                )

                GameHubTab.LIVE -> LiveScreen(
                    uiState = uiState,
                    onStartOrPause = onStartOrPause,
                    onAdvanceRound = onAdvanceRound,
                    onAdvanceHalf = onAdvanceHalf,
                    onRecordGoal = onRecordGoal,
                    onApplyLiveSub = onApplyLiveSub,
                    onApplyInjurySub = onApplyInjurySub,
                    onClearPlayerInjury = onClearPlayerInjury,
                    onFinalize = onFinalize,
                    showHeader = false,
                )

                GameHubTab.REPORT -> ReportsScreen(
                    uiState = uiState,
                    onSelectGame = onSelectGame,
                    onRefresh = onRefreshReport,
                    showHeader = false,
                    showGamePicker = false,
                )
            }
        }
    }
}

@Composable
private fun GameHubHeader(
    uiState: AppUiState,
    detail: GameDetail,
    onBackToGames: () -> Unit,
    onOpenTopLevelReports: () -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val teamName = uiState.selectedSeason?.name?.ifBlank { "Team" } ?: "Team"
    val workflowStatus = uiState.workflowStatus(detail.game)
    val teamGoals = detail.goals.count { it.scoredBy == GoalSide.TEAM }
    val opponentGoals = detail.goals.count { it.scoredBy == GoalSide.OPPONENT }
    val activeProgressIndex = when (workflowStatus) {
        GameWorkflowStatus.NEEDS_LINEUP -> 0
        GameWorkflowStatus.READY -> 1
        GameWorkflowStatus.LIVE -> 2
        GameWorkflowStatus.FINAL -> 3
    }

    Card(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onBackToGames) {
                            Text("Back to Games")
                        }
                        IconButton(onClick = onToggleExpanded) {
                            Icon(
                                imageVector = Icons.Outlined.ExpandLess,
                                contentDescription = "Minimize header",
                            )
                        }
                    }
                    TextButton(onClick = onOpenTopLevelReports) {
                        Text("Global Reports")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            teamName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "${teamName.ifBlank { "Team" }} vs ${detail.game.opponent}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "${formatDate(detail.game.scheduledAt)} • ${detail.game.location.ifBlank { "Location TBD" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WorkflowStatusChip(workflowStatus)
                        if (workflowStatus == GameWorkflowStatus.LIVE || workflowStatus == GameWorkflowStatus.FINAL) {
                            Text(
                                "$teamGoals - $opponentGoals",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        CompactGameProgressSummary(activeProgressIndex = activeProgressIndex)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Game Overview",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = "Expand header",
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(
    uiState: AppUiState,
    detail: GameDetail,
    onGenerateAssignments: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenLive: () -> Unit,
    onOpenReport: () -> Unit,
) {
    val workflowStatus = uiState.workflowStatus(detail.game)
    val playerLookup = detail.players.associateBy({ it.playerId }, { it.name })
    val availableCountByHalf = (1..detail.game.template().halfCount).associateWith { halfNumber ->
        detail.availability.count { it.isAvailableForHalf(halfNumber) && !it.isInjured }
    }
    val teamGoals = detail.goals.count { it.scoredBy == GoalSide.TEAM }
    val opponentGoals = detail.goals.count { it.scoredBy == GoalSide.OPPONENT }
    val goalieSummary = (1..detail.game.template().halfCount).associateWith { halfNumber ->
        detail.assignments.firstOrNull { it.halfNumber == halfNumber && it.position == FieldPosition.GOALIE }
            ?.playerId
            ?.let(playerLookup::get)
            ?: "Not assigned"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Match overview", style = MaterialTheme.typography.titleLarge)
                Text("Status: ${workflowStatus.label}")
                Text(
                    if (detail.assignments.isEmpty()) {
                        "Lineup readiness: lineup still needs to be generated."
                    } else {
                        "Lineup readiness: lineup is ready for review."
                    },
                )
                availableCountByHalf.forEach { (halfNumber, count) ->
                    Text("Half $halfNumber available: $count")
                }
                goalieSummary.forEach { (halfNumber, goalieName) ->
                    Text("Half $halfNumber goalie: $goalieName")
                }
                if (workflowStatus == GameWorkflowStatus.LIVE || workflowStatus == GameWorkflowStatus.FINAL) {
                    Text("Current score: $teamGoals-$opponentGoals")
                }
                if (workflowStatus == GameWorkflowStatus.LIVE) {
                    Text("Live state: Half ${detail.game.currentHalf} • Sub round ${detail.game.currentRound}")
                }
                if (detail.game.plannerNotes.isNotBlank()) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(detail.game.plannerNotes, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Next steps", style = MaterialTheme.typography.titleLarge)
                if (detail.assignments.isEmpty() && workflowStatus != GameWorkflowStatus.FINAL) {
                    Button(onClick = onGenerateAssignments, modifier = Modifier.fillMaxWidth()) {
                        Text("Generate lineup")
                    }
                }
                if (detail.assignments.isNotEmpty() && workflowStatus != GameWorkflowStatus.FINAL) {
                    OutlinedButton(onClick = onOpenPlanner, modifier = Modifier.fillMaxWidth()) {
                        Text("Review planner")
                    }
                }
                when (workflowStatus) {
                    GameWorkflowStatus.NEEDS_LINEUP -> {
                        OutlinedButton(onClick = onOpenPlanner, modifier = Modifier.fillMaxWidth()) {
                            Text("Open planner")
                        }
                    }

                    GameWorkflowStatus.READY -> {
                        Button(onClick = onOpenLive, modifier = Modifier.fillMaxWidth()) {
                            Text("Start live")
                        }
                    }

                    GameWorkflowStatus.LIVE -> {
                        Button(onClick = onOpenLive, modifier = Modifier.fillMaxWidth()) {
                            Text("Resume live")
                        }
                    }

                    GameWorkflowStatus.FINAL -> {
                        Button(onClick = onOpenReport, modifier = Modifier.fillMaxWidth()) {
                            Text("Open report")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlannerScreen(
    uiState: AppUiState,
    onToggleAvailability: (String, Int, Boolean) -> Unit,
    onGenerateAssignments: () -> Unit,
    onUpdateManualGroupLock: (Int, PositionGroup, List<String>) -> Unit,
    onSetAssignmentPlayer: (String, String) -> Unit,
    showHeader: Boolean = true,
    onGoToLive: (() -> Unit)? = null,
) {
    val detail = uiState.selectedGameDetail ?: run {
        EmptyState("Open a game from the Games tab to plan lineups.")
        return
    }
    val readOnly = detail.game.status == GameStatus.LIVE || detail.game.status == GameStatus.FINAL
    val availabilityMap = detail.availability.associateBy { it.playerId }
    val playerLookup = detail.players.associateBy({ it.playerId }, { it.name })
    val availablePlayers = detail.players
        .filter { it.active }
        .sortedBy { it.name }
    val manualLocksByHalfGroup = detail.game.manualGroupLocks()
        .associateBy({ it.halfNumber to it.positionGroup }, { it.playerIds })
    var editingLockKey by rememberSaveable(detail.game.gameId) {
        mutableStateOf<Pair<Int, PositionGroup>?>(null)
    }
    var editingAssignmentId by rememberSaveable(detail.game.gameId) {
        mutableStateOf<String?>(null)
    }

    editingLockKey?.let { (halfNumber, group) ->
        val existingSelection = manualLocksByHalfGroup[halfNumber to group].orEmpty()
        val lockedElsewhereIds = manualLocksByHalfGroup
            .filterKeys { (lockedHalfNumber, lockedGroup) ->
                lockedHalfNumber == halfNumber && lockedGroup != group
            }
            .values
            .flatten()
            .toSet()
        var selectedIds by remember(detail.game.gameId, halfNumber, group, existingSelection, manualLocksByHalfGroup) {
            mutableStateOf(existingSelection)
        }

        AlertDialog(
            onDismissRequest = { editingLockKey = null },
            title = { Text("Half $halfNumber ${group.label} lock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (group == PositionGroup.GOALIE) {
                            "Choose one player to lock into goalie for this half."
                        } else {
                            "Choose players to keep in ${group.label} for this half before autofill."
                        },
                    )
                    Text(
                        "Players already locked in another group for this half are shown but cannot be selected here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    availablePlayers
                        .filter { availabilityMap[it.playerId]?.isAvailableForHalf(halfNumber) != false }
                        .forEach { player ->
                        val lockedElsewhere = player.playerId in lockedElsewhereIds && player.playerId !in selectedIds
                        FilterChip(
                            selected = player.playerId in selectedIds,
                            onClick = {
                                if (lockedElsewhere) return@FilterChip
                                selectedIds = if (group == PositionGroup.GOALIE) {
                                    if (player.playerId in selectedIds) emptyList() else listOf(player.playerId)
                                } else if (player.playerId in selectedIds) {
                                    selectedIds - player.playerId
                                } else {
                                    selectedIds + player.playerId
                                }
                            },
                            label = {
                                Text(
                                    if (lockedElsewhere) {
                                        "${player.name} (locked elsewhere)"
                                    } else {
                                        player.name
                                    },
                                )
                            },
                            enabled = !lockedElsewhere,
                        )
                    }
                    if (selectedIds.isNotEmpty()) {
                        TextButton(onClick = { selectedIds = emptyList() }) {
                            Text("Clear lock")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateManualGroupLock(halfNumber, group, selectedIds)
                        editingLockKey = null
                    },
                ) {
                    Text("Save lock")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLockKey = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    editingAssignmentId?.let { assignmentId ->
        val assignment = detail.assignments.firstOrNull { it.assignmentId == assignmentId }
        if (assignment != null) {
            val sameRoundAssignments = detail.assignments.filter {
                it.halfNumber == assignment.halfNumber && it.roundIndex == assignment.roundIndex
            }
            AlertDialog(
                onDismissRequest = { editingAssignmentId = null },
                title = { Text("Set ${assignment.position.label}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pick a player for half ${assignment.halfNumber}, round ${assignment.roundIndex}.")
                        Text("If the player is already on the field this round, the app will swap the two positions.")
                        availablePlayers
                            .filter { availabilityMap[it.playerId]?.isAvailableForHalf(assignment.halfNumber) != false }
                            .forEach { player ->
                            val onFieldThisRound = sameRoundAssignments.any { it.playerId == player.playerId }
                            OutlinedButton(
                                onClick = {
                                    onSetAssignmentPlayer(assignment.assignmentId, player.playerId)
                                    editingAssignmentId = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                val currentLabel = if (player.playerId == assignment.playerId) {
                                    " (current)"
                                } else if (onFieldThisRound) {
                                    " (swap)"
                                } else {
                                    ""
                                }
                                Text("${player.name}$currentLabel")
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { editingAssignmentId = null }) {
                        Text("Close")
                    }
                },
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            if (showHeader) {
                ScreenHeader(
                    title = "Pregame Planner",
                    subtitle = "${detail.game.opponent} • ${formatDate(detail.game.scheduledAt)}",
                )
            }
        }
        item {
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onGenerateAssignments,
                        enabled = !readOnly,
                    ) {
                        Text(if (detail.assignments.isEmpty()) "Generate lineup" else "Regenerate")
                    }
                    if (onGoToLive != null) {
                        OutlinedButton(
                            onClick = onGoToLive,
                            enabled = detail.assignments.isNotEmpty(),
                        ) {
                            Text("Go to live")
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Availability", style = MaterialTheme.typography.titleLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detail.players.filter { it.active }.forEach { player ->
                            val availability = availabilityMap[player.playerId] ?: PlayerAvailabilityEntity(
                                gameId = detail.game.gameId,
                                playerId = player.playerId,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(player.name, modifier = Modifier.weight(1f))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    (1..detail.game.template().halfCount).forEach { halfNumber ->
                                        val available = availability.isAvailableForHalf(halfNumber)
                                        FilterChip(
                                            selected = available,
                                            onClick = {
                                                if (!readOnly) {
                                                    onToggleAvailability(player.playerId, halfNumber, !available)
                                                }
                                            },
                                            label = { Text("H$halfNumber") },
                                            enabled = !readOnly,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (detail.game.plannerNotes.isNotBlank()) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(detail.game.plannerNotes, modifier = Modifier.padding(12.dp))
                        }
                    }
                    if (readOnly) {
                        Text("Planner is read-only once a game is live or finalized.")
                    }
                }
            }
        }
        item {
            BoxWithConstraints {
                val halfNumbers = (1..detail.game.template().halfCount).toList()
                if (maxWidth > 920.dp && halfNumbers.size >= 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        halfNumbers.forEach { halfNumber ->
                            ManualLocksCard(
                                modifier = Modifier.weight(1f),
                                halfNumber = halfNumber,
                                playerLookup = playerLookup,
                                locksByGroup = manualLocksByHalfGroup,
                                onEditGroup = { group ->
                                    if (!readOnly) {
                                        editingLockKey = halfNumber to group
                                    }
                                },
                                editable = !readOnly,
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        halfNumbers.forEach { halfNumber ->
                            ManualLocksCard(
                                halfNumber = halfNumber,
                                playerLookup = playerLookup,
                                locksByGroup = manualLocksByHalfGroup,
                                onEditGroup = { group ->
                                    if (!readOnly) {
                                        editingLockKey = halfNumber to group
                                    }
                                },
                                editable = !readOnly,
                            )
                        }
                    }
                }
            }
        }
        if (detail.assignments.isEmpty()) {
            item { EmptyState("Generate the lineup to see half groups and round-by-round assignments.") }
        } else {
            item {
                PositionGroupComparisonSection(detail = detail)
            }
            item {
                BoxWithConstraints {
                    val halfNumbers = (1..detail.game.template().halfCount).toList()
                    if (maxWidth > 1080.dp && halfNumbers.size >= 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            halfNumbers.forEach { halfNumber ->
                                PlannerHalfRoundsSection(
                                    modifier = Modifier.weight(1f),
                                    detail = detail,
                                    halfNumber = halfNumber,
                                    onOpenAssignment = { editingAssignmentId = it.assignmentId },
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            halfNumbers.forEach { halfNumber ->
                                PlannerHalfRoundsSection(
                                    detail = detail,
                                    halfNumber = halfNumber,
                                    onOpenAssignment = { editingAssignmentId = it.assignmentId },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveScreen(
    uiState: AppUiState,
    onStartOrPause: () -> Unit,
    onAdvanceRound: () -> Unit,
    onAdvanceHalf: (Set<String>) -> Unit,
    onRecordGoal: (GoalSide, String?, String?) -> Unit,
    onApplyLiveSub: (String, String) -> Unit,
    onApplyInjurySub: (String, String) -> Unit,
    onClearPlayerInjury: (String, Boolean) -> Unit,
    onFinalize: () -> Unit,
    showHeader: Boolean = true,
) {
    val detail = uiState.selectedGameDetail ?: run {
        EmptyState("Open a game from the Games tab to manage it live.")
        return
    }
    val template = detail.game.template()
    val configuration = LocalConfiguration.current
    val teamName = uiState.selectedSeason?.name?.ifBlank { "Team" } ?: "Team"
    val playerLookup = detail.players.associateBy({ it.playerId }, { it.name })
    val availabilityByPlayer = detail.availability.associateBy { it.playerId }
    val currentAssignments = detail.assignments
        .filter { it.halfNumber == detail.game.currentHalf && it.roundIndex == detail.game.currentRound }
        .sortedBy { template.positions.indexOf(it.position) }
    val nextAssignments = detail.assignments
        .filter {
            detail.game.currentRound < template.roundsPerHalf &&
                it.halfNumber == detail.game.currentHalf &&
                it.roundIndex == detail.game.currentRound + 1
        }
        .sortedBy { template.positions.indexOf(it.position) }
    val nextAssignmentByPosition = nextAssignments.associateBy { it.position }
    val activeRoster = detail.players.filter { it.active }.sortedBy { it.name }
    val injuredPlayers = detail.availability.filter { it.isInjured }
    val halfPositionGroupByPlayerId = detail.assignments
        .filter { it.halfNumber == detail.game.currentHalf }
        .groupBy { it.playerId }
        .mapValues { (_, assignments) ->
            assignments
                .groupingBy { it.positionGroup }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
        }
    val onFieldPlayerIds = currentAssignments.map { it.playerId }.toSet()
    val benchCandidates = activeRoster.filter { player ->
        player.playerId !in onFieldPlayerIds &&
            availabilityByPlayer[player.playerId]?.isAvailableForHalf(detail.game.currentHalf) != false &&
            availabilityByPlayer[player.playerId]?.isInjured != true
    }
    val gameRemainingSeconds = (template.halfDurationMinutes * 60) - uiState.effectiveHalfElapsedSeconds
    val substitutionRemainingSeconds =
        (template.substitutionWindowMinutes * 60) - uiState.effectiveRoundElapsedSeconds
    val advanceHalfReady = gameRemainingSeconds <= 0
    var showScorerDialog by rememberSaveable(detail.game.gameId, detail.game.currentHalf, detail.game.currentRound) {
        mutableStateOf(false)
    }
    var showAssistDialog by rememberSaveable(detail.game.gameId, detail.game.currentHalf, detail.game.currentRound) {
        mutableStateOf(false)
    }
    var pendingScorerId by rememberSaveable(detail.game.gameId, detail.game.currentHalf, detail.game.currentRound) {
        mutableStateOf<String?>(null)
    }
    var scorerShowAll by rememberSaveable(detail.game.gameId, detail.game.currentHalf, detail.game.currentRound) {
        mutableStateOf(false)
    }
    var showSecondHalfDialog by rememberSaveable(detail.game.gameId, detail.game.currentHalf) {
        mutableStateOf(false)
    }
    var returningSecondHalfIds by rememberSaveable(detail.game.gameId, detail.game.currentHalf) {
        mutableStateOf(listOf<String>())
    }
    var selectedAssignmentId by rememberSaveable(detail.game.gameId, detail.game.currentHalf, detail.game.currentRound) {
        mutableStateOf<String?>(null)
    }
    var compareMode by rememberSaveable(detail.game.gameId, detail.game.currentHalf, detail.game.currentRound) {
        mutableStateOf(false)
    }
    var showPositionGroups by rememberSaveable(detail.game.gameId, detail.game.currentHalf) {
        mutableStateOf(true)
    }
    var alertPlayedForRound by rememberSaveable(detail.game.gameId, detail.game.currentHalf, detail.game.currentRound) {
        mutableStateOf(false)
    }

    LaunchedEffect(substitutionRemainingSeconds, detail.game.status) {
        if (detail.game.status == GameStatus.LIVE && substitutionRemainingSeconds <= 0 && !alertPlayedForRound) {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
                tone.release()
            }
            alertPlayedForRound = true
        }
    }

    if (showScorerDialog) {
        val filteredChoices = if (scorerShowAll) {
            activeRoster
        } else {
            currentAssignments
                .filter { it.position != FieldPosition.GOALIE }
                .mapNotNull { assignment -> activeRoster.firstOrNull { it.playerId == assignment.playerId } }
        }
        val scorerChoices = filteredChoices.ifEmpty { activeRoster }
        AlertDialog(
            onDismissRequest = { showScorerDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Who scored?")
                    IconButton(onClick = { scorerShowAll = !scorerShowAll }) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Show all players")
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (scorerShowAll) {
                            "Showing all active players"
                        } else {
                            "Showing active field players only"
                        },
                    )
                    scorerChoices.forEach { player ->
                        OutlinedButton(
                            onClick = {
                                pendingScorerId = player.playerId
                                showScorerDialog = false
                                showAssistDialog = true
                                scorerShowAll = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(player.name)
                        }
                    }
                    TextButton(
                        onClick = {
                            onRecordGoal(GoalSide.TEAM, null, null)
                            pendingScorerId = null
                            showAssistDialog = false
                            showScorerDialog = false
                            scorerShowAll = false
                        },
                    ) {
                        Text("Scorer unknown")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showScorerDialog = false
                    pendingScorerId = null
                    scorerShowAll = false
                }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showAssistDialog) {
        val assistChoices = currentAssignments
            .filter { it.playerId != pendingScorerId }
            .mapNotNull { assignment -> activeRoster.firstOrNull { it.playerId == assignment.playerId } }
        AlertDialog(
            onDismissRequest = {
                showAssistDialog = false
                pendingScorerId = null
            },
            title = { Text("Who assisted the goal?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose another player currently on the field, or mark the goal unassisted.")
                    assistChoices.forEach { player ->
                        OutlinedButton(
                            onClick = {
                                onRecordGoal(GoalSide.TEAM, pendingScorerId, player.playerId)
                                pendingScorerId = null
                                showAssistDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(player.name)
                        }
                    }
                    TextButton(
                        onClick = {
                            onRecordGoal(GoalSide.TEAM, pendingScorerId, null)
                            pendingScorerId = null
                            showAssistDialog = false
                        },
                    ) {
                        Text("Unassisted")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showAssistDialog = false
                    pendingScorerId = null
                }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showSecondHalfDialog) {
        AlertDialog(
            onDismissRequest = { showSecondHalfDialog = false },
            title = { Text("Second-half injury check") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose which injured players will be available for the second half.")
                    injuredPlayers.forEach { injured ->
                        val playerName = playerLookup[injured.playerId].orEmpty()
                        FilterChip(
                            selected = injured.playerId in returningSecondHalfIds,
                            onClick = {
                                returningSecondHalfIds = if (injured.playerId in returningSecondHalfIds) {
                                    returningSecondHalfIds - injured.playerId
                                } else {
                                    returningSecondHalfIds + injured.playerId
                                }
                            },
                            label = {
                                Text(
                                    if (injured.playerId in returningSecondHalfIds) {
                                        "$playerName plays second half"
                                    } else {
                                        "$playerName held out"
                                    },
                                )
                            },
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAdvanceHalf(returningSecondHalfIds.toSet())
                        showSecondHalfDialog = false
                        returningSecondHalfIds = emptyList()
                    },
                ) {
                    Text("Confirm half change")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecondHalfDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    selectedAssignmentId?.let { assignmentId ->
        val assignment = currentAssignments.firstOrNull { it.assignmentId == assignmentId }
        if (assignment != null) {
            val prioritizedBenchCandidates = benchCandidates.sortedWith(
                compareByDescending<PlayerEntity> {
                    halfPositionGroupByPlayerId[it.playerId] == assignment.positionGroup
                }.thenBy { it.name },
            )
            AlertDialog(
                onDismissRequest = { selectedAssignmentId = null },
                title = { Text("${assignment.position.label} actions") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Current player: ${playerLookup[assignment.playerId].orEmpty()}")
                        Text("Position group: ${assignment.positionGroup.label}")
                        if (benchCandidates.isEmpty()) {
                            Text("No bench players are available for a quick sub.")
                        } else {
                            Text("Quick sub")
                            prioritizedBenchCandidates.forEach { player ->
                                val sameGroup = halfPositionGroupByPlayerId[player.playerId] == assignment.positionGroup
                                OutlinedButton(
                                    onClick = {
                                        onApplyLiveSub(assignment.assignmentId, player.playerId)
                                        selectedAssignmentId = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    border = if (sameGroup) {
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        null
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(player.name)
                                        if (sameGroup) {
                                            Text(
                                                "Same group",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                            Text("Mark injured and sub")
                            prioritizedBenchCandidates.forEach { player ->
                                val sameGroup = halfPositionGroupByPlayerId[player.playerId] == assignment.positionGroup
                                Button(
                                    onClick = {
                                        onApplyInjurySub(assignment.assignmentId, player.playerId)
                                        selectedAssignmentId = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = if (sameGroup) {
                                        ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = IceWhite,
                                        )
                                    } else {
                                        ButtonDefaults.buttonColors()
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Injure out, ${player.name} in")
                                        if (sameGroup) {
                                            Text(
                                                "Same group",
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selectedAssignmentId = null }) {
                        Text("Close")
                    }
                },
            )
        }
    }

    val goalLog = detail.goals.sortedWith(
        compareBy(GoalEventEntity::halfNumber, GoalEventEntity::elapsedSecondsInHalf, GoalEventEntity::createdAt),
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletLandscape =
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && configuration.screenWidthDp >= 840

        if (isTabletLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (detail.game.status == GameStatus.PLANNED && detail.assignments.isNotEmpty()) {
                        LivePregameCard(detail = detail, currentAssignments = currentAssignments, nextAssignments = nextAssignments)
                    }
                    LiveControlPanel(
                        detail = detail,
                        uiState = uiState,
                        teamName = teamName,
                        gameRemainingSeconds = gameRemainingSeconds,
                        substitutionRemainingSeconds = substitutionRemainingSeconds,
                        advanceHalfReady = advanceHalfReady,
                        onStartOrPause = onStartOrPause,
                        onAdvanceRound = onAdvanceRound,
                        onAdvanceHalf = {
                            if (injuredPlayers.isNotEmpty() && detail.game.currentHalf < template.halfCount) {
                                showSecondHalfDialog = true
                            } else {
                                onAdvanceHalf(emptySet())
                            }
                        },
                        onFinalize = onFinalize,
                        onTeamGoal = { showScorerDialog = true },
                        onOpponentGoal = { onRecordGoal(GoalSide.OPPONENT, null, null) },
                        showHeader = showHeader,
                    )
                    PositionGroupsCard(
                        detail = detail,
                        halfNumber = detail.game.currentHalf,
                        showGroups = showPositionGroups,
                        onToggleGroups = { showPositionGroups = !showPositionGroups },
                    )
                    InjuredPlayersCard(
                        injuredPlayers = injuredPlayers,
                        playerLookup = playerLookup,
                        onClearPlayerInjury = onClearPlayerInjury,
                    )
                    GoalLogCard(
                        goals = goalLog,
                        playerLookup = playerLookup,
                        teamName = teamName,
                        opponentName = detail.game.opponent,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LineupCard(
                        title = "Current lineup",
                        detail = detail,
                        assignments = currentAssignments,
                        nextAssignmentByPosition = nextAssignmentByPosition,
                        compareMode = compareMode,
                        onToggleCompareMode = { compareMode = !compareMode },
                        onOpenActions = { selectedAssignmentId = it.assignmentId },
                    )
                    LineupCard(
                        title = "Next round lineup",
                        detail = detail,
                        assignments = nextAssignments,
                        nextAssignmentByPosition = nextAssignments.associateBy { it.position },
                        compareMode = false,
                        onToggleCompareMode = { compareMode = !compareMode },
                        onOpenActions = null,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (detail.game.status == GameStatus.PLANNED && detail.assignments.isNotEmpty()) {
                    LivePregameCard(detail = detail, currentAssignments = currentAssignments, nextAssignments = nextAssignments)
                }
                LiveControlPanel(
                    detail = detail,
                    uiState = uiState,
                    teamName = teamName,
                    gameRemainingSeconds = gameRemainingSeconds,
                    substitutionRemainingSeconds = substitutionRemainingSeconds,
                    advanceHalfReady = advanceHalfReady,
                    onStartOrPause = onStartOrPause,
                    onAdvanceRound = onAdvanceRound,
                    onAdvanceHalf = {
                        if (injuredPlayers.isNotEmpty() && detail.game.currentHalf < template.halfCount) {
                            showSecondHalfDialog = true
                        } else {
                            onAdvanceHalf(emptySet())
                        }
                    },
                    onFinalize = onFinalize,
                    onTeamGoal = { showScorerDialog = true },
                    onOpponentGoal = { onRecordGoal(GoalSide.OPPONENT, null, null) },
                    showHeader = showHeader,
                )
                PositionGroupsCard(
                    detail = detail,
                    halfNumber = detail.game.currentHalf,
                    showGroups = showPositionGroups,
                    onToggleGroups = { showPositionGroups = !showPositionGroups },
                )
                LineupCard(
                    title = "Current lineup",
                    detail = detail,
                    assignments = currentAssignments,
                    nextAssignmentByPosition = nextAssignmentByPosition,
                    compareMode = compareMode,
                    onToggleCompareMode = { compareMode = !compareMode },
                    onOpenActions = { selectedAssignmentId = it.assignmentId },
                )
                if (nextAssignments.isNotEmpty()) {
                    LineupCard(
                        title = "Next round lineup",
                        detail = detail,
                        assignments = nextAssignments,
                        nextAssignmentByPosition = nextAssignments.associateBy { it.position },
                        compareMode = false,
                        onToggleCompareMode = { compareMode = !compareMode },
                        onOpenActions = null,
                    )
                }
                InjuredPlayersCard(
                    injuredPlayers = injuredPlayers,
                    playerLookup = playerLookup,
                    onClearPlayerInjury = onClearPlayerInjury,
                )
                GoalLogCard(
                    goals = goalLog,
                    playerLookup = playerLookup,
                    teamName = teamName,
                    opponentName = detail.game.opponent,
                )
            }
        }
    }
}

@Composable
private fun LiveControlPanel(
    detail: GameDetail,
    uiState: AppUiState,
    teamName: String,
    gameRemainingSeconds: Int,
    substitutionRemainingSeconds: Int,
    advanceHalfReady: Boolean,
    onStartOrPause: () -> Unit,
    onAdvanceRound: () -> Unit,
    onAdvanceHalf: () -> Unit,
    onFinalize: () -> Unit,
    onTeamGoal: () -> Unit,
    onOpponentGoal: () -> Unit,
    showHeader: Boolean = true,
) {
    val template = detail.game.template()
    val totalTeamGoals = detail.goals.count { it.scoredBy == GoalSide.TEAM }
    val totalOpponentGoals = detail.goals.count { it.scoredBy == GoalSide.OPPONENT }
    val showFinalizeButton =
        detail.game.currentHalf == template.halfCount && gameRemainingSeconds <= 5 * 60
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showHeader) {
                ScreenHeader(
                    title = "Live Game",
                    subtitle = "${detail.game.opponent} • Half ${detail.game.currentHalf} • Sub round ${detail.game.currentRound}",
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ScoreColumn(
                            label = teamName,
                            score = totalTeamGoals,
                        )
                        Text(
                            text = "-",
                            style = MaterialTheme.typography.headlineLarge,
                            fontFamily = FontFamily.Monospace,
                        )
                        ScoreColumn(
                            label = detail.game.opponent.ifBlank { "Opponent" },
                            score = totalOpponentGoals,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onTeamGoal,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("$teamName goal")
                        }
                        Button(
                            onClick = onOpponentGoal,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IceWhite,
                                contentColor = BadgeBlack,
                            ),
                        ) {
                            Text("${detail.game.opponent.ifBlank { "Opponent" }} goal")
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ClockCard(
                    modifier = Modifier.weight(1f),
                    label = "Game clock",
                    value = formatClock(gameRemainingSeconds.coerceAtLeast(0)),
                    sublabel = "Counts down for the half",
                )
                ClockCard(
                    modifier = Modifier.weight(1f),
                    label = "Sub clock",
                    value = formatSignedClock(substitutionRemainingSeconds),
                    sublabel = "Resets on next sub round",
                    valueColor = if (substitutionRemainingSeconds < 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartOrPause) {
                    Text(
                        when {
                            uiState.clockRunning -> "Pause clock"
                            detail.game.status == GameStatus.PLANNED -> "Start match"
                            else -> "Start clock"
                        },
                    )
                }
                Button(
                    onClick = onAdvanceRound,
                    enabled = detail.game.status != GameStatus.FINAL && detail.game.currentRound < template.roundsPerHalf,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IceWhite,
                        contentColor = BadgeBlack,
                        disabledContainerColor = IceWhite.copy(alpha = 0.45f),
                        disabledContentColor = BadgeBlack.copy(alpha = 0.55f),
                    ),
                ) {
                    Text("Next sub round")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAdvanceHalf,
                    enabled = detail.game.status != GameStatus.FINAL && detail.game.currentHalf < template.halfCount,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (advanceHalfReady) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    ),
                ) {
                    Text("Advance half")
                }
                if (showFinalizeButton) {
                    OutlinedButton(onClick = onFinalize) {
                        Text("Finalize")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreColumn(
    label: String,
    score: Int,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun WorkflowStatusChip(
    status: GameWorkflowStatus,
    compact: Boolean = false,
) {
    val containerColor = when (status) {
        GameWorkflowStatus.NEEDS_LINEUP -> MaterialTheme.colorScheme.surfaceVariant
        GameWorkflowStatus.READY -> MaterialTheme.colorScheme.primaryContainer
        GameWorkflowStatus.LIVE -> MaterialTheme.colorScheme.primary
        GameWorkflowStatus.FINAL -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (status) {
        GameWorkflowStatus.LIVE -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            status.label,
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 4.dp else 6.dp,
            ),
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun GameHubTabButtons(
    selectedTab: GameHubTab,
    onSelectTab: (GameHubTab) -> Unit,
) {
    val rowBackground = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(rowBackground)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GameHubTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onSelectTab(tab) },
                color = if (selected) MaterialTheme.colorScheme.primary else IceWhite,
                contentColor = if (selected) IceWhite else MaterialTheme.colorScheme.primary,
                tonalElevation = if (selected) 0.dp else 3.dp,
                shadowElevation = if (selected) 0.dp else 1.dp,
                border = if (selected) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                },
                shape = MaterialTheme.shapes.large,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactGameProgressSummary(
    activeProgressIndex: Int,
) {
    val steps = listOf("Setup", "Ready", "Live", "Final")
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, label ->
            val completed = index <= activeProgressIndex
            val isCurrent = index == activeProgressIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Surface(
                    color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 9.dp else 7.dp),
                    )
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (completed) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(12.dp)
                        .padding(horizontal = 2.dp)
                        .background(
                            if (index < activeProgressIndex) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = MaterialTheme.shapes.small,
                        ),
                )
            }
        }
    }
}

@Composable
private fun LivePregameCard(
    detail: GameDetail,
    currentAssignments: List<AssignmentEntity>,
    nextAssignments: List<AssignmentEntity>,
) {
    val playerLookup = detail.players.associateBy({ it.playerId }, { it.name })
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pregame live check", style = MaterialTheme.typography.titleLarge)
            Text("Starting lineup is ready for kickoff.")
            Text(
                "On field: ${
                    currentAssignments.joinToString { playerLookup[it.playerId].orEmpty() }
                }",
            )
            if (nextAssignments.isNotEmpty()) {
                Text(
                    "Next sub round: ${
                        nextAssignments.joinToString { playerLookup[it.playerId].orEmpty() }
                    }",
                )
            }
            Text("Position groups and lineup details are below for the final pre-kickoff check.")
        }
    }
}

private enum class HistoryTableMetric(val label: String, val compactLabel: String) {
    HALVES("Halves", "H"),
    MINUTES("Minutes", "Min"),
    GOALS("Goals", "G"),
    ASSISTS("Assists", "A"),
    DIFFERENTIAL("Differential", "+/-"),
}

private enum class HistoryMetricPreset(val label: String, val metrics: List<HistoryTableMetric>) {
    USAGE("Usage", listOf(HistoryTableMetric.HALVES, HistoryTableMetric.MINUTES)),
    ATTACKING("Attacking", listOf(HistoryTableMetric.GOALS, HistoryTableMetric.ASSISTS)),
    IMPACT("Impact", listOf(HistoryTableMetric.MINUTES, HistoryTableMetric.DIFFERENTIAL)),
    ALL("All", HistoryTableMetric.entries),
}

private enum class HeatmapMode(val label: String) {
    TEAM_AGGREGATE("Team Aggregate"),
    INDIVIDUAL_PLAYER("Individual Player"),
}

private enum class HeatmapMetric(val label: String, val explanation: String) {
    MINUTES(
        "Minutes",
        "Minutes shows total time played at each position across finalized games. Darker cells mean more time spent in that position.",
    ),
    HALVES(
        "Halves",
        "Halves counts how many halves a player, or the full team total, has appeared in that position across finalized games.",
    ),
    GOALS(
        "Goals",
        "Goals counts team goals scored by players while they were assigned to that exact position.",
    ),
    ASSISTS(
        "Assists",
        "Assists counts recorded assists by players while they were assigned to that exact position.",
    ),
    DIFFERENTIAL(
        "Differential",
        "Differential shows goals for minus goals against while players were assigned to that position. Blue is positive, neutral is even, and red is negative.",
    ),
}

private data class PositionHeatmapCell(
    val rawValue: Double,
    val displayValue: String,
    val intensity: Float,
)

private data class PlayerPositionHeatmapRow(
    val label: String,
    val playerId: String? = null,
    val cells: Map<FieldPosition, PositionHeatmapCell>,
)

@Composable
private fun HistoryStatsTable(
    playerMetrics: List<com.example.soccergamemanager.domain.PlayerMetrics>,
    selectedMetrics: List<HistoryTableMetric>,
) {
    val horizontalState = rememberScrollState()
    val positions = com.example.soccergamemanager.domain.GameTemplateConfig.DEFAULT_POSITIONS
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row {
            StatsHeaderCell(label = "Player", width = 148.dp)
            Row(modifier = Modifier.horizontalScroll(horizontalState)) {
                positions.forEach { position ->
                    StatsHeaderCell(label = position.label, width = 156.dp)
                }
            }
        }
        playerMetrics.forEach { player ->
            Row {
                StatsPlayerCell(
                    playerName = player.playerName,
                    totalMinutes = player.totalMinutes,
                    totalGoals = player.totalGoalsScored,
                    totalAssists = player.totalAssists,
                    totalDifferential = player.scoreDifferentialWhileAssigned,
                    width = 148.dp,
                )
                Row(modifier = Modifier.horizontalScroll(horizontalState)) {
                    positions.forEach { position ->
                        StatsMetricCell(
                            metrics = player.positionStats[position]
                                ?: com.example.soccergamemanager.domain.PositionStatMetrics(),
                            visibleMetrics = selectedMetrics,
                            width = 156.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHeaderCell(label: String, width: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.width(width),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun StatsPlayerCell(
    playerName: String,
    totalMinutes: Double,
    totalGoals: Int,
    totalAssists: Int,
    totalDifferential: Int,
    width: androidx.compose.ui.unit.Dp,
) {
    Surface(
        modifier = Modifier.width(width),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(playerName, style = MaterialTheme.typography.titleLarge)
            Text("Min ${"%.1f".format(totalMinutes)}")
            Text("G $totalGoals")
            Text("A $totalAssists")
            Text("Diff ${formatDifferential(totalDifferential)}")
        }
    }
}

@Composable
private fun StatsMetricCell(
    metrics: com.example.soccergamemanager.domain.PositionStatMetrics,
    visibleMetrics: List<HistoryTableMetric>,
    width: androidx.compose.ui.unit.Dp,
) {
    val intensity = when {
        visibleMetrics.contains(HistoryTableMetric.MINUTES) -> (metrics.minutesPlayed / 30.0).coerceIn(0.0, 1.0)
        visibleMetrics.contains(HistoryTableMetric.GOALS) -> (metrics.goalsScored / 2.0).coerceIn(0.0, 1.0)
        visibleMetrics.contains(HistoryTableMetric.ASSISTS) -> (metrics.assists / 2.0).coerceIn(0.0, 1.0)
        visibleMetrics.contains(HistoryTableMetric.DIFFERENTIAL) -> (kotlin.math.abs(metrics.scoreDifferential) / 3.0).coerceIn(0.0, 1.0)
        else -> (metrics.halvesPlayed / 4.0).coerceIn(0.0, 1.0)
    }.toFloat()
    Surface(
        modifier = Modifier.width(width),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f + intensity * 0.30f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            visibleMetrics.forEach { metric ->
                Text(
                    when (metric) {
                        HistoryTableMetric.HALVES -> "${metric.compactLabel} ${metrics.halvesPlayed}"
                        HistoryTableMetric.MINUTES -> "${metric.compactLabel} ${"%.1f".format(metrics.minutesPlayed)}"
                        HistoryTableMetric.GOALS -> "${metric.compactLabel} ${metrics.goalsScored}"
                        HistoryTableMetric.ASSISTS -> "${metric.compactLabel} ${metrics.assists}"
                        HistoryTableMetric.DIFFERENTIAL -> "${metric.compactLabel} ${formatDifferential(metrics.scoreDifferential)}"
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PositionHeatmapCard(
    playerMetrics: List<com.example.soccergamemanager.domain.PlayerMetrics>,
    selectedPlayerId: String?,
    selectedMode: HeatmapMode,
    selectedMetric: HeatmapMetric,
    onSelectMode: (HeatmapMode) -> Unit,
    onSelectMetric: (HeatmapMetric) -> Unit,
    onSelectPlayer: (String) -> Unit,
) {
    val selectedPlayer = playerMetrics.firstOrNull { it.playerId == selectedPlayerId } ?: playerMetrics.firstOrNull()
    var showMetricInfo by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Position heatmap", style = MaterialTheme.typography.titleLarge)
                    Text("Quick visual scan of position usage and contribution across finalized games.")
                }
                IconButton(onClick = { showMetricInfo = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Explain ${selectedMetric.label}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeatmapMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == selectedMode,
                        onClick = { onSelectMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeatmapMetric.entries.forEach { metric ->
                    FilterChip(
                        selected = metric == selectedMetric,
                        onClick = { onSelectMetric(metric) },
                        label = { Text(metric.label) },
                    )
                }
            }
            if (selectedMode == HeatmapMode.INDIVIDUAL_PLAYER && selectedPlayer != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    playerMetrics.forEach { player ->
                        FilterChip(
                            selected = player.playerId == selectedPlayer.playerId,
                            onClick = { onSelectPlayer(player.playerId) },
                            label = { Text(player.playerName) },
                        )
                    }
                }
            }
            BoxWithConstraints {
                val wideLayout = maxWidth >= 960.dp
                when (selectedMode) {
                    HeatmapMode.TEAM_AGGREGATE -> {
                        val teamRow = buildTeamAggregateHeatmapRow(playerMetrics, selectedMetric)
                        val playerRow = selectedPlayer?.let { buildPlayerHeatmapRow(it, selectedMetric) }
                        if (wideLayout && playerRow != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                HeatmapSummaryCard(
                                    title = "Team aggregate",
                                    subtitle = "Total ${selectedMetric.label.lowercase()} by position across the team.",
                                    row = teamRow,
                                    modifier = Modifier.weight(1f),
                                )
                                HeatmapSummaryCard(
                                    title = selectedPlayer.playerName,
                                    subtitle = "Selected player preview by position.",
                                    row = playerRow,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            HeatmapSummaryCard(
                                title = "Team aggregate",
                                subtitle = "Total ${selectedMetric.label.lowercase()} by position across the team.",
                                row = teamRow,
                            )
                        }
                        AllPlayersHeatmapCard(
                            rows = buildAllPlayersHeatmapRows(playerMetrics, selectedMetric),
                            metric = selectedMetric,
                        )
                    }

                    HeatmapMode.INDIVIDUAL_PLAYER -> {
                        if (selectedPlayer != null) {
                            val playerRow = buildPlayerHeatmapRow(selectedPlayer, selectedMetric)
                            val teamRow = buildTeamAggregateHeatmapRow(playerMetrics, selectedMetric)
                            if (wideLayout) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    HeatmapSummaryCard(
                                        title = selectedPlayer.playerName,
                                        subtitle = "${selectedMetric.label} by position for the selected player.",
                                        row = playerRow,
                                        modifier = Modifier.weight(1f),
                                    )
                                    HeatmapSummaryCard(
                                        title = "Team aggregate",
                                        subtitle = "Overall team context for the same metric.",
                                        row = teamRow,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            } else {
                                HeatmapSummaryCard(
                                    title = selectedPlayer.playerName,
                                    subtitle = "${selectedMetric.label} by position for the selected player.",
                                    row = playerRow,
                                )
                            }
                        } else {
                            Text("Select a player to view the individual heatmap.")
                        }
                    }
                }
            }
        }
    }
    if (showMetricInfo) {
        AlertDialog(
            onDismissRequest = { showMetricInfo = false },
            confirmButton = {
                TextButton(onClick = { showMetricInfo = false }) {
                    Text("Close")
                }
            },
            title = { Text(selectedMetric.label) },
            text = { Text(selectedMetric.explanation) },
        )
    }
}

@Composable
private fun HeatmapSummaryCard(
    title: String,
    subtitle: String,
    row: PlayerPositionHeatmapRow,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleRowHeatmap(row = row)
        }
    }
}

@Composable
private fun SingleRowHeatmap(row: PlayerPositionHeatmapRow) {
    val positions = com.example.soccergamemanager.domain.GameTemplateConfig.DEFAULT_POSITIONS
    val scrollState = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(row.label, style = MaterialTheme.typography.labelLarge)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            Row(modifier = Modifier.horizontalScroll(scrollState)) {
                positions.forEach { position ->
                    Column(
                        modifier = Modifier.width(104.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            position.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        HeatmapCell(cell = row.cells[position] ?: PositionHeatmapCell(0.0, "0", 0f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AllPlayersHeatmapCard(
    rows: List<PlayerPositionHeatmapRow>,
    metric: HeatmapMetric,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("All players heatmap", style = MaterialTheme.typography.titleLarge)
            Text(
                "${metric.label} for every player across all positions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AllPlayersHeatmapTable(rows = rows)
        }
    }
}

@Composable
private fun AllPlayersHeatmapTable(rows: List<PlayerPositionHeatmapRow>) {
    val positions = com.example.soccergamemanager.domain.GameTemplateConfig.DEFAULT_POSITIONS
    val horizontalState = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatsHeaderCell(label = "Player", width = 132.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds(),
            ) {
                Row(modifier = Modifier.horizontalScroll(horizontalState)) {
                    positions.forEach { position ->
                        StatsHeaderCell(label = position.label, width = 104.dp)
                    }
                }
            }
        }
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.width(132.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Text(
                        text = row.label,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds(),
                ) {
                    Row(modifier = Modifier.horizontalScroll(horizontalState)) {
                        positions.forEach { position ->
                            HeatmapCell(
                                cell = row.cells[position] ?: PositionHeatmapCell(0.0, "0", 0f),
                                width = 104.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    cell: PositionHeatmapCell,
    width: androidx.compose.ui.unit.Dp = 104.dp,
) {
    val backgroundColor = when {
        cell.rawValue > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f + (cell.intensity * 0.55f))
        cell.rawValue < 0 -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f + (cell.intensity * 0.40f))
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = when {
        cell.intensity > 0.60f && cell.rawValue >= 0 -> IceWhite
        cell.intensity > 0.72f && cell.rawValue < 0 -> IceWhite
        cell.rawValue < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier.width(width),
        color = backgroundColor,
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cell.displayValue,
                color = contentColor,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun buildTeamAggregateHeatmapRow(
    playerMetrics: List<com.example.soccergamemanager.domain.PlayerMetrics>,
    metric: HeatmapMetric,
): PlayerPositionHeatmapRow {
    val positions = com.example.soccergamemanager.domain.GameTemplateConfig.DEFAULT_POSITIONS
    val totals = positions.associateWith { position ->
        playerMetrics.sumOf { player -> heatmapMetricValue(metric, player.positionStats[position]) }
    }
    val normalizer = heatmapNormalizer(metric, totals.values)
    return PlayerPositionHeatmapRow(
        label = "Whole team",
        cells = positions.associateWith { position ->
            val value = totals[position] ?: 0.0
            PositionHeatmapCell(
                rawValue = value,
                displayValue = formatHeatmapValue(metric, value),
                intensity = heatmapIntensity(metric, value, normalizer),
            )
        },
    )
}

private fun buildPlayerHeatmapRow(
    playerMetric: com.example.soccergamemanager.domain.PlayerMetrics,
    metric: HeatmapMetric,
): PlayerPositionHeatmapRow {
    val positions = com.example.soccergamemanager.domain.GameTemplateConfig.DEFAULT_POSITIONS
    val values = positions.associateWith { position ->
        heatmapMetricValue(metric, playerMetric.positionStats[position])
    }
    val normalizer = heatmapNormalizer(metric, values.values)
    return PlayerPositionHeatmapRow(
        label = playerMetric.playerName,
        playerId = playerMetric.playerId,
        cells = positions.associateWith { position ->
            val value = values[position] ?: 0.0
            PositionHeatmapCell(
                rawValue = value,
                displayValue = formatHeatmapValue(metric, value),
                intensity = heatmapIntensity(metric, value, normalizer),
            )
        },
    )
}

private fun buildAllPlayersHeatmapRows(
    playerMetrics: List<com.example.soccergamemanager.domain.PlayerMetrics>,
    metric: HeatmapMetric,
): List<PlayerPositionHeatmapRow> {
    val positions = com.example.soccergamemanager.domain.GameTemplateConfig.DEFAULT_POSITIONS
    val allValues = playerMetrics.flatMap { player ->
        positions.map { position -> heatmapMetricValue(metric, player.positionStats[position]) }
    }
    val normalizer = heatmapNormalizer(metric, allValues)
    return playerMetrics.map { player ->
        PlayerPositionHeatmapRow(
            label = player.playerName,
            playerId = player.playerId,
            cells = positions.associateWith { position ->
                val value = heatmapMetricValue(metric, player.positionStats[position])
                PositionHeatmapCell(
                    rawValue = value,
                    displayValue = formatHeatmapValue(metric, value),
                    intensity = heatmapIntensity(metric, value, normalizer),
                )
            },
        )
    }
}

private fun heatmapMetricValue(
    metric: HeatmapMetric,
    positionStats: com.example.soccergamemanager.domain.PositionStatMetrics?,
): Double {
    val stats = positionStats ?: com.example.soccergamemanager.domain.PositionStatMetrics()
    return when (metric) {
        HeatmapMetric.MINUTES -> stats.minutesPlayed
        HeatmapMetric.HALVES -> stats.halvesPlayed.toDouble()
        HeatmapMetric.GOALS -> stats.goalsScored.toDouble()
        HeatmapMetric.ASSISTS -> stats.assists.toDouble()
        HeatmapMetric.DIFFERENTIAL -> stats.scoreDifferential.toDouble()
    }
}

private fun formatHeatmapValue(metric: HeatmapMetric, value: Double): String =
    when (metric) {
        HeatmapMetric.MINUTES -> "%.1f".format(value)
        HeatmapMetric.HALVES, HeatmapMetric.GOALS, HeatmapMetric.ASSISTS -> value.toInt().toString()
        HeatmapMetric.DIFFERENTIAL -> formatDifferential(value.toInt())
    }

private fun heatmapNormalizer(metric: HeatmapMetric, values: Collection<Double>): Double {
    if (values.isEmpty()) return 1.0
    return when (metric) {
        HeatmapMetric.DIFFERENTIAL -> values.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(1.0) ?: 1.0
        else -> values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    }
}

private fun heatmapIntensity(metric: HeatmapMetric, value: Double, normalizer: Double): Float {
    val base = when (metric) {
        HeatmapMetric.DIFFERENTIAL -> kotlin.math.abs(value) / normalizer
        else -> value / normalizer
    }
    return base.coerceIn(0.0, 1.0).toFloat()
}

@Composable
private fun InjuredPlayersCard(
    injuredPlayers: List<PlayerAvailabilityEntity>,
    playerLookup: Map<String, String>,
    onClearPlayerInjury: (String, Boolean) -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Injured players", style = MaterialTheme.typography.titleLarge)
            if (injuredPlayers.isEmpty()) {
                Text("No players are currently marked injured.")
            } else {
                injuredPlayers.forEach { injured ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(playerLookup[injured.playerId].orEmpty())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onClearPlayerInjury(injured.playerId, false) }) {
                                Text("Return next sub")
                            }
                            TextButton(onClick = { onClearPlayerInjury(injured.playerId, true) }) {
                                Text("Return now")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionGroupsCard(
    detail: GameDetail,
    halfNumber: Int,
    showGroups: Boolean,
    onToggleGroups: () -> Unit,
) {
    val playerLookup = detail.players.associateBy({ it.playerId }, { it.name })
    val canMinimize = detail.game.status == GameStatus.LIVE || detail.game.status == GameStatus.FINAL
    val halfAssignments = detail.assignments.filter { it.halfNumber == halfNumber }
    val summary = PositionGroup.entries.associateWith { group ->
        halfAssignments
            .filter { it.positionGroup == group }
            .mapNotNull { playerLookup[it.playerId] }
            .distinct()
    }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Position groups", style = MaterialTheme.typography.titleLarge)
                    Text("Half $halfNumber briefing")
                }
                if (canMinimize) {
                    IconButton(onClick = onToggleGroups) {
                        Icon(
                            if (showGroups) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (showGroups) "Minimize position groups" else "Expand position groups",
                        )
                    }
                }
            }
            if (showGroups || !canMinimize) {
                summary.forEach { (group, players) ->
                    Text("${group.label}: ${players.joinToString().ifBlank { "None" }}")
                }
            }
        }
    }
}

@Composable
private fun GoalLogCard(
    goals: List<GoalEventEntity>,
    playerLookup: Map<String, String>,
    teamName: String,
    opponentName: String,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Goal log", style = MaterialTheme.typography.titleLarge)
            if (goals.isEmpty()) {
                Text("No goals recorded yet.")
            } else {
                goals.forEach { goal ->
                    val label = if (goal.scoredBy == GoalSide.TEAM) {
                        val scorer = playerLookup[goal.scorerPlayerId] ?: "${teamName.ifBlank { "Team" }} goal"
                        val assister = goal.assisterPlayerId?.let(playerLookup::get)
                        if (assister != null) "$scorer • Ast: $assister" else scorer
                    } else {
                        "${opponentName.ifBlank { "Opponent" }} goal"
                    }
                    Text("Half ${goal.halfNumber} • ${formatClock(goal.elapsedSecondsInHalf)} • $label")
                }
            }
        }
    }
}

@Composable
private fun LineupCard(
    title: String,
    detail: GameDetail,
    assignments: List<AssignmentEntity>,
    nextAssignmentByPosition: Map<FieldPosition, AssignmentEntity>,
    compareMode: Boolean,
    onToggleCompareMode: () -> Unit,
    onOpenActions: ((AssignmentEntity) -> Unit)?,
) {
    val playerLookup = detail.players.associateBy({ it.playerId }, { it.name })
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onToggleCompareMode) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Toggle next sub view")
                }
            }
            if (assignments.isEmpty()) {
                Text("No lineup available.")
            } else {
                assignments.forEach { assignment ->
                    val currentName = playerLookup[assignment.playerId].orEmpty()
                    val nextAssignment = nextAssignmentByPosition[assignment.position]
                    val nextName = nextAssignment?.playerId?.let(playerLookup::get) ?: currentName
                    val changingPlayer = nextAssignment != null && nextAssignment.playerId != assignment.playerId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(assignment.position.label)
                            if (compareMode) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        currentName,
                                        color = if (changingPlayer) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text("->")
                                    Text(
                                        nextName,
                                        color = if (changingPlayer) Color(0xFF1E8E3E) else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            } else {
                                Text(currentName)
                            }
                        }
                        if (onOpenActions != null) {
                            IconButton(onClick = { onOpenActions(assignment) }) {
                                Icon(
                                    Icons.Outlined.Report,
                                    contentDescription = "Open injury workflow",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryScreen(
    uiState: AppUiState,
    onSelectGame: (String) -> Unit,
) {
    val metrics = uiState.teamMetrics
    var selectedPreset by rememberSaveable { mutableStateOf(HistoryMetricPreset.ALL.name) }
    var selectedMetricNames by rememberSaveable {
        mutableStateOf(HistoryTableMetric.entries.map { it.name })
    }
    var selectedPlayerId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedHeatmapMode by rememberSaveable { mutableStateOf(HeatmapMode.TEAM_AGGREGATE.name) }
    var selectedHeatmapMetric by rememberSaveable { mutableStateOf(HeatmapMetric.MINUTES.name) }
    val selectedMetrics = HistoryTableMetric.entries.filter { it.name in selectedMetricNames }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                title = "History & Stats",
                subtitle = "Team results plus a per-position player stats table.",
            )
        }
        if (metrics == null || metrics.totalGames == 0) {
            item { EmptyState("Finalize a few games to unlock team metrics.") }
        } else {
            val selectedPlayer = metrics.playerDevelopmentSnapshots.firstOrNull { it.playerId == selectedPlayerId }
                ?: metrics.playerDevelopmentSnapshots.firstOrNull()
            item {
                SeasonSnapshotCard(metrics = metrics)
            }
            item {
                TrendCardsSection(metrics = metrics)
            }
            item {
                SeasonFormCard(metrics = metrics)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        HalfPerformanceCard(metrics = metrics)
                        FairnessDashboardCard(metrics = metrics)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        PositionGroupSeasonCard(metrics = metrics)
                        LightRankingsCard(metrics = metrics)
                    }
                }
            }
            item {
                PlayerDevelopmentSection(
                    snapshots = metrics.playerDevelopmentSnapshots,
                    selectedPlayerId = selectedPlayer?.playerId,
                    onSelectPlayer = { selectedPlayerId = it },
                )
            }
            if (selectedPlayer != null) {
                item {
                    PlayerTrendDetailCard(player = selectedPlayer)
                }
            }
            item {
                PositionHeatmapCard(
                    playerMetrics = metrics.playerMetrics,
                    selectedPlayerId = selectedPlayer?.playerId,
                    selectedMode = HeatmapMode.valueOf(selectedHeatmapMode),
                    selectedMetric = HeatmapMetric.valueOf(selectedHeatmapMetric),
                    onSelectMode = { selectedHeatmapMode = it.name },
                    onSelectMetric = { selectedHeatmapMetric = it.name },
                    onSelectPlayer = { selectedPlayerId = it },
                )
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Detailed position table", style = MaterialTheme.typography.titleLarge)
                        Text("Use presets or fine-tune multiple metrics when you need the detailed table view.")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HistoryMetricPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = preset.name == selectedPreset,
                                    onClick = {
                                        selectedPreset = preset.name
                                        selectedMetricNames = preset.metrics.map { it.name }
                                    },
                                    label = { Text(preset.label) },
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HistoryTableMetric.entries.forEach { metric ->
                                FilterChip(
                                    selected = metric.name in selectedMetricNames,
                                    onClick = {
                                        selectedMetricNames = if (metric.name in selectedMetricNames) {
                                            selectedMetricNames - metric.name
                                        } else {
                                            selectedMetricNames + metric.name
                                        }
                                        selectedPreset = ""
                                    },
                                    label = { Text(metric.label) },
                                )
                            }
                        }
                        if (selectedMetrics.isEmpty()) {
                            Text("Select at least one metric to show the table.")
                        } else {
                            HistoryStatsTable(
                                playerMetrics = metrics.playerMetrics,
                                selectedMetrics = selectedMetrics,
                            )
                        }
                    }
                }
            }
        }
            item {
                Text("Game archive", style = MaterialTheme.typography.titleLarge)
            }
        items(uiState.games.filter { it.status == GameStatus.FINAL }, key = { it.gameId }) { game ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = uiState.selectedGameId == game.gameId, onClick = { onSelectGame(game.gameId) }),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(game.opponent, style = MaterialTheme.typography.titleLarge)
                        Text(formatDate(game.scheduledAt))
                    }
                    Icon(Icons.Outlined.SportsSoccer, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun SeasonSnapshotCard(metrics: com.example.soccergamemanager.domain.TeamMetrics) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Season snapshot", style = MaterialTheme.typography.titleLarge)
            BoxWithConstraints {
                val compact = maxWidth < 840.dp
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SnapshotMetricRow("Record", "${metrics.wins}-${metrics.draws}-${metrics.losses}")
                        SnapshotMetricRow("Goals", "${metrics.teamGoals}-${metrics.opponentGoals}")
                        SnapshotMetricRow("Average diff", formatSignedDecimal(metrics.averageGoalDifferential))
                        SnapshotMetricRow("Strongest half", metrics.strongestHalf?.let { "Half $it" } ?: "None")
                        SnapshotMetricRow("Assists", metrics.totalAssists.toString())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SnapshotMetricCard("Record", "${metrics.wins}-${metrics.draws}-${metrics.losses}", Modifier.weight(1f))
                        SnapshotMetricCard("Goals", "${metrics.teamGoals}-${metrics.opponentGoals}", Modifier.weight(1f))
                        SnapshotMetricCard("Avg diff", formatSignedDecimal(metrics.averageGoalDifferential), Modifier.weight(1f))
                        SnapshotMetricCard("Strongest half", metrics.strongestHalf?.let { "Half $it" } ?: "None", Modifier.weight(1f))
                        SnapshotMetricCard("Assists", metrics.totalAssists.toString(), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendCardsSection(metrics: com.example.soccergamemanager.domain.TeamMetrics) {
    val diffTrend = metrics.gameTrendPoints.map { it.differential.toDouble() }
    val goalsForTrend = metrics.gameTrendPoints.map { it.teamGoals.toDouble() }
    val goalsAgainstTrend = metrics.gameTrendPoints.map { it.opponentGoals.toDouble() }
    val minutesBalanceTrend = metrics.gameTrendPoints.map { it.minutesBalanceScore.toDouble() }
    val keeperBalanceTrend = metrics.gameTrendPoints.map { it.keeperBalanceScore.toDouble() }
    val trendLabels = metrics.gameTrendPoints.map { it.dateLabel.substringBefore(" vs") }
    BoxWithConstraints {
        val compact = maxWidth < 900.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniTrendCard(
                    title = "Score differential trend",
                    values = diffTrend,
                    labels = trendLabels,
                    highlight = formatDifferential(diffTrend.lastOrNull()?.toInt() ?: 0),
                    explanation = "The value is your team's goals minus the opponent's goals for that game. Positive bars mean your team outscored the opponent.",
                    valueFormatter = { formatDifferential(it.toInt()) },
                )
                MiniTrendCard(
                    title = "Goals for",
                    values = goalsForTrend,
                    labels = trendLabels,
                    highlight = goalsForTrend.lastOrNull()?.toInt()?.toString() ?: "0",
                    explanation = "The value is the number of goals your team scored in that game.",
                    valueFormatter = { it.toInt().toString() },
                )
                MiniTrendCard(
                    title = "Goals against",
                    values = goalsAgainstTrend,
                    labels = trendLabels,
                    highlight = goalsAgainstTrend.lastOrNull()?.toInt()?.toString() ?: "0",
                    explanation = "The value is the number of goals allowed in that game.",
                    valueFormatter = { it.toInt().toString() },
                )
                MiniTrendCard(
                    title = "Playing-time balance",
                    values = minutesBalanceTrend,
                    labels = trendLabels,
                    highlight = "${metrics.fairnessSummary.minutesBalanceScore}",
                    explanation = "This 0-100 score tracks season-to-date playing-time fairness after each finalized game. It compares each player's cumulative minutes across the season so far. Higher scores mean total minutes have been shared more evenly across the roster.",
                    valueFormatter = { it.toInt().toString() },
                )
                MiniTrendCard(
                    title = "Keeper balance",
                    values = keeperBalanceTrend,
                    labels = trendLabels,
                    highlight = "${metrics.fairnessSummary.keeperBalanceScore}",
                    explanation = "This 0-100 score tracks season-to-date goalkeeper fairness after each finalized game. It compares cumulative keeper assignments across the season so far. Higher scores mean keeper duties have been shared more evenly across the roster.",
                    valueFormatter = { it.toInt().toString() },
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniTrendCard(
                        title = "Score differential trend",
                        values = diffTrend,
                        labels = trendLabels,
                        highlight = formatDifferential(diffTrend.lastOrNull()?.toInt() ?: 0),
                        explanation = "The value is your team's goals minus the opponent's goals for that game. Positive bars mean your team outscored the opponent.",
                        valueFormatter = { formatDifferential(it.toInt()) },
                        modifier = Modifier.weight(1f),
                    )
                    MiniTrendCard(
                        title = "Goals for",
                        values = goalsForTrend,
                        labels = trendLabels,
                        highlight = goalsForTrend.lastOrNull()?.toInt()?.toString() ?: "0",
                        explanation = "The value is the number of goals your team scored in that game.",
                        valueFormatter = { it.toInt().toString() },
                        modifier = Modifier.weight(1f),
                    )
                    MiniTrendCard(
                        title = "Goals against",
                        values = goalsAgainstTrend,
                        labels = trendLabels,
                        highlight = goalsAgainstTrend.lastOrNull()?.toInt()?.toString() ?: "0",
                        explanation = "The value is the number of goals allowed in that game.",
                        valueFormatter = { it.toInt().toString() },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniTrendCard(
                        title = "Playing-time balance",
                        values = minutesBalanceTrend,
                        labels = trendLabels,
                        highlight = "${metrics.fairnessSummary.minutesBalanceScore}",
                        explanation = "This 0-100 score tracks season-to-date playing-time fairness after each finalized game. It compares each player's cumulative minutes across the season so far. Higher scores mean total minutes have been shared more evenly across the roster.",
                        valueFormatter = { it.toInt().toString() },
                        modifier = Modifier.weight(1f),
                    )
                    MiniTrendCard(
                        title = "Keeper balance",
                        values = keeperBalanceTrend,
                        labels = trendLabels,
                        highlight = "${metrics.fairnessSummary.keeperBalanceScore}",
                        explanation = "This 0-100 score tracks season-to-date goalkeeper fairness after each finalized game. It compares cumulative keeper assignments across the season so far. Higher scores mean keeper duties have been shared more evenly across the roster.",
                        valueFormatter = { it.toInt().toString() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonFormCard(metrics: com.example.soccergamemanager.domain.TeamMetrics) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Season form", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                metrics.gameTrendPoints.forEach { point ->
                    Surface(
                        color = when {
                            point.differential > 0 -> MaterialTheme.colorScheme.primaryContainer
                            point.differential < 0 -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(point.resultLabel, style = MaterialTheme.typography.titleLarge)
                            Text(point.dateLabel, style = MaterialTheme.typography.labelMedium)
                            Text("${point.teamGoals}-${point.opponentGoals}")
                            Text(point.opponent, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HalfPerformanceCard(metrics: com.example.soccergamemanager.domain.TeamMetrics) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Half performance", style = MaterialTheme.typography.titleLarge)
            metrics.goalsByHalf.toSortedMap().forEach { (half, score) ->
                val total = (score.first + score.second).coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Half $half")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${score.first}-${score.second}", modifier = Modifier.width(72.dp))
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(score.first.toFloat().coerceAtLeast(0.5f))
                                    .height(10.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(score.second.toFloat().coerceAtLeast(0.5f))
                                    .height(10.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.65f)),
                            )
                        }
                    }
                    Text("Differential ${formatDifferential(score.first - score.second)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PositionGroupSeasonCard(metrics: com.example.soccergamemanager.domain.TeamMetrics) {
    val maxMinutes = metrics.positionGroupSummaries.maxOfOrNull { it.totalMinutes }?.coerceAtLeast(1.0) ?: 1.0
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Position group season view", style = MaterialTheme.typography.titleLarge)
            metrics.positionGroupSummaries.forEach { summary ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(summary.positionGroup.label)
                        Text("${"%.1f".format(summary.totalMinutes)} min")
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((summary.totalMinutes / maxMinutes).toFloat()),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                    Text(
                        "${summary.goalContributions} contributions • Diff ${formatDifferential(summary.totalDifferential)} • ${summary.uniquePlayers} players",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun FairnessDashboardCard(metrics: com.example.soccergamemanager.domain.TeamMetrics) {
    val playerLookup = metrics.playerDevelopmentSnapshots.associateBy({ it.playerId }, { it.playerName })
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Fairness dashboard", style = MaterialTheme.typography.titleLarge)
            SnapshotMetricRow("Minutes balance", "${metrics.fairnessSummary.minutesBalanceScore}/100")
            SnapshotMetricRow("Group exposure balance", "${metrics.fairnessSummary.groupExposureBalanceScore}/100")
            SnapshotMetricRow("Keeper balance", "${metrics.fairnessSummary.keeperBalanceScore}/100")
            if (metrics.fairnessSummary.overusedPlayerIds.isNotEmpty()) {
                Text(
                    "Higher-use players: ${
                        metrics.fairnessSummary.overusedPlayerIds.mapNotNull { playerLookup[it] }.joinToString()
                    }",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (metrics.fairnessSummary.underusedPlayerIds.isNotEmpty()) {
                Text(
                    "Lower-use players: ${
                        metrics.fairnessSummary.underusedPlayerIds.mapNotNull { playerLookup[it] }.joinToString()
                    }",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun LightRankingsCard(metrics: com.example.soccergamemanager.domain.TeamMetrics) {
    val topGoals = metrics.playerMetrics.sortedByDescending { it.totalGoalsScored }.take(3)
    val topAssists = metrics.playerMetrics.sortedByDescending { it.totalAssists }.take(3)
    val topMinutes = metrics.playerMetrics.sortedByDescending { it.totalMinutes }.take(3)
    val topDiff = metrics.playerMetrics.sortedByDescending { it.scoreDifferentialWhileAssigned }.take(3)
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Light rankings", style = MaterialTheme.typography.titleLarge)
            RankingBlock("Goals", topGoals.map { "${it.playerName} ${it.totalGoalsScored}" })
            RankingBlock("Assists", topAssists.map { "${it.playerName} ${it.totalAssists}" })
            RankingBlock("Minutes", topMinutes.map { "${it.playerName} ${"%.1f".format(it.totalMinutes)}" })
            RankingBlock("Differential", topDiff.map { "${it.playerName} ${formatDifferential(it.scoreDifferentialWhileAssigned)}" })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerDevelopmentSection(
    snapshots: List<com.example.soccergamemanager.domain.PlayerDevelopmentSnapshot>,
    selectedPlayerId: String?,
    onSelectPlayer: (String) -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Player development", style = MaterialTheme.typography.titleLarge)
            BoxWithConstraints {
                val useTwoRows = maxWidth >= 840.dp && snapshots.size > 2
                if (useTwoRows) {
                    val splitIndex = (snapshots.size + 1) / 2
                    val rows = listOf(
                        snapshots.take(splitIndex),
                        snapshots.drop(splitIndex),
                    ).filter { it.isNotEmpty() }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.forEach { rowSnapshots ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowSnapshots.forEach { snapshot ->
                                    PlayerDevelopmentCard(
                                        snapshot = snapshot,
                                        selected = snapshot.playerId == selectedPlayerId,
                                        onSelect = onSelectPlayer,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        snapshots.forEach { snapshot ->
                            PlayerDevelopmentCard(
                                snapshot = snapshot,
                                selected = snapshot.playerId == selectedPlayerId,
                                onSelect = onSelectPlayer,
                                modifier = Modifier.width(180.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerDevelopmentCard(
    snapshot: com.example.soccergamemanager.domain.PlayerDevelopmentSnapshot,
    selected: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable { onSelect(snapshot.playerId) },
        color = if (selected) MaterialTheme.colorScheme.primary else IceWhite,
        contentColor = if (selected) IceWhite else MaterialTheme.colorScheme.primary,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (selected) 0.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(snapshot.playerName, fontWeight = FontWeight.SemiBold)
            Text("${"%.1f".format(snapshot.totalMinutes)} min")
            Text("${snapshot.totalGoals}G • ${snapshot.totalAssists}A")
            Text("${snapshot.uniquePositions} positions • ${snapshot.uniqueGroups} groups", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlayerTrendDetailCard(player: com.example.soccergamemanager.domain.PlayerDevelopmentSnapshot) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${player.playerName} trends", style = MaterialTheme.typography.titleLarge)
            SnapshotMetricRow("Position variety", "${player.positionVarietyScore}/100")
            SnapshotMetricRow("Group variety", "${player.groupVarietyScore}/100")
            player.trendPoints.forEach { point ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(point.label, style = MaterialTheme.typography.labelLarge)
                        Text("${"%.1f".format(point.minutes)} min")
                    }
                    Text(
                        "${point.goals}G • ${point.assists}A • Diff ${formatDifferential(point.differential)} • ${point.uniquePositions} pos / ${point.uniqueGroups} groups",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchSummaryHeroCard(analytics: com.example.soccergamemanager.domain.MatchReportAnalytics) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Match report dashboard", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(analytics.opponent, style = MaterialTheme.typography.headlineSmall)
                    Text("${analytics.dateLabel} • ${analytics.location}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${analytics.teamGoals} - ${analytics.opponentGoals}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                analytics.halfScores.forEach { half ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("Half ${half.halfNumber}", style = MaterialTheme.typography.labelMedium)
                            Text("${half.teamGoals}-${half.opponentGoals}", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchTimelineCard(analytics: com.example.soccergamemanager.domain.MatchReportAnalytics) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Game flow timeline", style = MaterialTheme.typography.titleLarge)
            analytics.halfScores.forEach { half ->
                val halfEvents = analytics.timelineEvents.filter { it.halfNumber == half.halfNumber }
                MatchHalfTimeline(
                    halfNumber = half.halfNumber,
                    events = halfEvents,
                )
            }
        }
    }
}

@Composable
private fun MatchHalfTimeline(
    halfNumber: Int,
    events: List<com.example.soccergamemanager.domain.MatchTimelineEvent>,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val timelineWidth = maxWidth
        val halfDuration = (events.maxOfOrNull { it.elapsedSecondsInHalf } ?: 1).coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Half $halfNumber", style = MaterialTheme.typography.labelLarge)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                events.forEach { event ->
                    val fraction = (event.elapsedSecondsInHalf.toFloat() / halfDuration.toFloat()).coerceIn(0f, 1f)
                    val markerColor = when (event.kind) {
                        com.example.soccergamemanager.domain.MatchTimelineKind.TEAM_GOAL -> MaterialTheme.colorScheme.primary
                        com.example.soccergamemanager.domain.MatchTimelineKind.OPPONENT_GOAL -> MaterialTheme.colorScheme.error
                        com.example.soccergamemanager.domain.MatchTimelineKind.SUB_ROUND -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = (timelineWidth * fraction) - 6.dp, y = 2.dp)
                            .size(if (event.kind == com.example.soccergamemanager.domain.MatchTimelineKind.SUB_ROUND) 8.dp else 12.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(markerColor),
                    )
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                events.filter {
                    it.kind == com.example.soccergamemanager.domain.MatchTimelineKind.TEAM_GOAL ||
                        it.kind == com.example.soccergamemanager.domain.MatchTimelineKind.OPPONENT_GOAL
                }.forEach { event ->
                    Surface(
                        color = if (event.kind == com.example.soccergamemanager.domain.MatchTimelineKind.TEAM_GOAL) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            "${formatClock(event.elapsedSecondsInHalf)} ${event.label}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundImpactCard(analytics: com.example.soccergamemanager.domain.MatchReportAnalytics) {
    val strongestRound = analytics.roundImpactSummaries.maxByOrNull { it.differential * 100 + it.goalsFor }
    val toughestRound = analytics.roundImpactSummaries.minByOrNull { it.differential * 100 - it.goalsAgainst }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Lineup impact summary", style = MaterialTheme.typography.titleLarge)
            strongestRound?.let {
                Text("Best stretch: Half ${it.halfNumber}, round ${it.roundIndex} (${formatDifferential(it.differential)})")
            }
            toughestRound?.let {
                Text("Most difficult stretch: Half ${it.halfNumber}, round ${it.roundIndex} (${formatDifferential(it.differential)})")
            }
            analytics.roundImpactSummaries.forEach { summary ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("H${summary.halfNumber} R${summary.roundIndex}", modifier = Modifier.width(70.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                when {
                                    summary.differential > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    summary.differential < 0 -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                    )
                    Text(
                        "${summary.goalsFor}-${summary.goalsAgainst}",
                        modifier = Modifier.width(56.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionGroupMatchCard(analytics: com.example.soccergamemanager.domain.MatchReportAnalytics) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Position group performance", style = MaterialTheme.typography.titleLarge)
            analytics.positionGroupSummaries.forEach { summary ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(summary.positionGroup.label, style = MaterialTheme.typography.titleMedium)
                        Text("${"%.1f".format(summary.totalMinutes)} min • ${summary.goalContributions} contributions")
                        Text("${summary.goalsFor}-${summary.goalsAgainst} • Diff ${formatDifferential(summary.differential)}")
                        Text(summary.playersUsed.joinToString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerUsageCard(analytics: com.example.soccergamemanager.domain.MatchReportAnalytics) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Player usage summary", style = MaterialTheme.typography.titleLarge)
            analytics.playerUsage.forEach { usage ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(usage.playerName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${usage.goals}G • ${usage.assists}A • ${usage.positions.joinToString { it.label }}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${"%.1f".format(usage.minutes)} min")
                            Text(
                                "${if (usage.fairnessDeltaMinutes > 0) "+" else ""}${"%.1f".format(usage.fairnessDeltaMinutes)} vs norm",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (usage.fairnessDeltaMinutes >= 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchTakeawaysCard(analytics: com.example.soccergamemanager.domain.MatchReportAnalytics) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Coach takeaways", style = MaterialTheme.typography.titleLarge)
            if (analytics.takeaways.isEmpty()) {
                Text("No standout takeaways yet.")
            } else {
                analytics.takeaways.forEach { takeaway ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(takeaway.title, style = MaterialTheme.typography.titleMedium)
                            Text(takeaway.body)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SnapshotMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MiniTrendCard(
    title: String,
    values: List<Double>,
    labels: List<String>,
    highlight: String,
    explanation: String,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    var showInfo by remember { mutableStateOf(false) }
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { showInfo = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Explain $title",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(highlight, style = MaterialTheme.typography.headlineSmall)
            MiniBarStrip(values = values, labels = labels, valueFormatter = valueFormatter)
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text("Close")
                }
            },
            title = { Text(title) },
            text = {
                Text(
                    "$explanation\n\nChart layout: each bar represents one finalized game, ordered from oldest to newest."
                )
            },
        )
    }
}

@Composable
private fun MiniBarStrip(
    values: List<Double>,
    labels: List<String>,
    valueFormatter: (Double) -> String,
) {
    if (values.isEmpty()) {
        Text("No data yet.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val recentValues = values.takeLast(8)
    val recentLabels = labels.takeLast(recentValues.size).map {
        it.replace("Sept", "Sep")
            .replace("April", "Apr")
            .replace("March", "Mar")
            .replace("February", "Feb")
            .replace("January", "Jan")
            .replace("August", "Aug")
            .replace("October", "Oct")
            .replace("November", "Nov")
            .replace("December", "Dec")
    }
    val maxMagnitude = recentValues.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(1.0) ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            recentValues.forEach { value ->
                val fraction = (kotlin.math.abs(value) / maxMagnitude).toFloat().coerceIn(0.12f, 1f)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = valueFormatter(value),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .height(44.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp * fraction)
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    when {
                                        value > 0 -> MaterialTheme.colorScheme.primary
                                        value < 0 -> MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            recentLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RankingBlock(title: String, rows: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        rows.forEach { row ->
            Text(row, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReportsScreen(
    uiState: AppUiState,
    onSelectGame: (String) -> Unit,
    onRefresh: () -> Unit,
    showHeader: Boolean = true,
    showGamePicker: Boolean = true,
) {
    val context = LocalContext.current
    val report = uiState.report
    val analytics = report?.analytics
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHeader) {
            ScreenHeader(
                title = "Report & Print",
                subtitle = "Single-page lineup sheet with halves, rounds, and score boxes.",
            )
        }
        if (showGamePicker) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Game reports", style = MaterialTheme.typography.titleLarge)
                    if (uiState.games.isEmpty()) {
                        Text("Create a game to unlock reports.")
                    } else {
                        uiState.games.forEach { game ->
                            OutlinedButton(
                                onClick = { onSelectGame(game.gameId) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(game.opponent)
                                    Text(formatDate(game.scheduledAt))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (uiState.selectedGameDetail == null) {
            EmptyState("Select a game to preview and print its report.")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh) { Text("Refresh") }
                OutlinedButton(onClick = { report?.let { printReport(context, it) } }, enabled = report != null) {
                    Text("Print / Save PDF")
                }
            }
            if (report == null || analytics == null) {
                EmptyState("No report available yet.")
            } else {
                MatchSummaryHeroCard(analytics = analytics)
                MatchTimelineCard(analytics = analytics)
                BoxWithConstraints {
                    if (maxWidth > 880.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                RoundImpactCard(analytics = analytics)
                                MatchTakeawaysCard(analytics = analytics)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                PositionGroupMatchCard(analytics = analytics)
                                PlayerUsageCard(analytics = analytics)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            RoundImpactCard(analytics = analytics)
                            PositionGroupMatchCard(analytics = analytics)
                            PlayerUsageCard(analytics = analytics)
                            MatchTakeawaysCard(analytics = analytics)
                        }
                    }
                }
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Printable summary preview", style = MaterialTheme.typography.titleLarge)
                        Text(
                            report.plainText,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Card {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(BadgeBlack, McFarlandBlueDark, McFarlandBlue),
                    ),
                )
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(IceWhite.copy(alpha = 0.92f)),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        color = IceWhite,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.mcfarland_badge),
                            contentDescription = "McFarland Soccer badge",
                            modifier = Modifier
                                .size(84.dp)
                                .padding(8.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "McFarland SC",
                            style = MaterialTheme.typography.labelLarge,
                            color = IceWhite.copy(alpha = 0.88f),
                        )
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = IceWhite,
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = IceWhite.copy(alpha = 0.86f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactScreenHeader(title: String, subtitle: String) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun PositionGroupComparisonSection(detail: GameDetail) {
    val halfNumbers = (1..detail.game.template().halfCount).toList()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Position group comparison", style = MaterialTheme.typography.titleLarge)
        Text(
            "Compare the planned groups for both halves before you dive into the round-by-round rotation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints {
            if (maxWidth > 920.dp && halfNumbers.size >= 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    halfNumbers.forEach { halfNumber ->
                        HalfSummaryCard(
                            detail = detail,
                            halfNumber = halfNumber,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    halfNumbers.forEach { halfNumber ->
                        HalfSummaryCard(detail = detail, halfNumber = halfNumber)
                    }
                }
            }
        }
    }
}

@Composable
private fun HalfSummaryCard(
    detail: GameDetail,
    halfNumber: Int,
    modifier: Modifier = Modifier,
) {
    val playerLookup = detail.players.associateBy({ it.playerId }, { it.name })
    val halfAssignments = detail.assignments.filter { it.halfNumber == halfNumber }
    val summary = listOf(
        PositionGroup.DEFENSE,
        PositionGroup.LR_MID,
        PositionGroup.CM_STRIKER,
        PositionGroup.GOALIE,
    ).associateWith { group ->
        halfAssignments
            .filter { it.positionGroup == group }
            .mapNotNull { playerLookup[it.playerId] }
            .distinct()
    }
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Half $halfNumber groups", style = MaterialTheme.typography.titleMedium)
            summary.forEach { (group, players) ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            group.label,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.width(92.dp),
                        )
                        Text(
                            players.joinToString().ifBlank { "None" },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannerHalfRoundsSection(
    detail: GameDetail,
    halfNumber: Int,
    onOpenAssignment: (AssignmentEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Half $halfNumber rounds", style = MaterialTheme.typography.titleLarge)
        (1..detail.game.template().roundsPerHalf).forEach { roundNumber ->
            RoundCard(
                detail = detail,
                halfNumber = halfNumber,
                roundNumber = roundNumber,
                onOpenAssignment = onOpenAssignment,
            )
        }
    }
}

@Composable
private fun RoundCard(
    detail: GameDetail,
    halfNumber: Int,
    roundNumber: Int,
    onOpenAssignment: (AssignmentEntity) -> Unit,
) {
    val template = detail.game.template()
    val playerLookup = detail.players.associateBy({ it.playerId }, { it.name })
    val assignments = detail.assignments
        .filter { it.halfNumber == halfNumber && it.roundIndex == roundNumber }
        .sortedBy { template.positions.indexOf(it.position) }
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Round $roundNumber", style = MaterialTheme.typography.titleMedium)
            assignments.forEach { assignment ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = detail.game.status != GameStatus.LIVE && detail.game.status != GameStatus.FINAL,
                        ) { onOpenAssignment(assignment) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            assignment.position.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            playerLookup[assignment.playerId].orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameDateTimePicker(
    scheduledAtMillis: Long,
    onScheduledAtChange: (Long) -> Unit,
    context: android.content.Context,
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = scheduledAtMillis }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Game date and time", style = MaterialTheme.typography.titleLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val updated = Calendar.getInstance().apply {
                                timeInMillis = scheduledAtMillis
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            }
                            onScheduledAtChange(updated.timeInMillis)
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH),
                    ).show()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(formatPickerDate(scheduledAtMillis))
            }
            OutlinedButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            val updated = Calendar.getInstance().apply {
                                timeInMillis = scheduledAtMillis
                                set(Calendar.HOUR_OF_DAY, hourOfDay)
                                set(Calendar.MINUTE, minute)
                            }
                            onScheduledAtChange(updated.timeInMillis)
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        false,
                    ).show()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(formatPickerTime(scheduledAtMillis))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualLocksCard(
    modifier: Modifier = Modifier,
    halfNumber: Int,
    playerLookup: Map<String, String>,
    locksByGroup: Map<Pair<Int, PositionGroup>, List<String>>,
    onEditGroup: (PositionGroup) -> Unit,
    editable: Boolean,
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Half $halfNumber manual locks", style = MaterialTheme.typography.titleMedium)
            Text("Lock key players into a position group before generating the lineup.")
            listOf(
                PositionGroup.GOALIE,
                PositionGroup.DEFENSE,
                PositionGroup.LR_MID,
                PositionGroup.CM_STRIKER,
            ).forEach { group ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(group.label, style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { onEditGroup(group) }, enabled = editable) {
                                Text("Edit")
                            }
                        }
                        val names = locksByGroup[halfNumber to group]
                            .orEmpty()
                            .mapNotNull { playerLookup[it] }
                        if (names.isEmpty()) {
                            Text("No players locked.")
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                names.forEach { name ->
                                    ElevatedAssistChip(
                                        onClick = { if (editable) onEditGroup(group) },
                                        label = { Text(name) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    sublabel: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge)
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                color = valueColor,
            )
            Text(sublabel)
        }
    }
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(Date(epochMillis))

private fun formatPickerDate(epochMillis: Long): String =
    SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(Date(epochMillis))

private fun formatPickerTime(epochMillis: Long): String =
    SimpleDateFormat("h:mm a", Locale.US).format(Date(epochMillis))

private fun formatClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatDifferential(value: Int): String = if (value > 0) "+$value" else value.toString()

private fun formatSignedDecimal(value: Double): String {
    val rounded = "%.1f".format(value)
    return if (value > 0) "+$rounded" else rounded
}

private fun formatSignedClock(totalSeconds: Int): String {
    return if (totalSeconds >= 0) {
        formatClock(totalSeconds)
    } else {
        "-${formatClock(-totalSeconds)}"
    }
}
