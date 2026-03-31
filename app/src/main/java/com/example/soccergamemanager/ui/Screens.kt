package com.example.soccergamemanager.ui

import android.content.res.Configuration
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.example.soccergamemanager.data.GoalEventEntity
import com.example.soccergamemanager.data.PlayerAvailabilityEntity
import com.example.soccergamemanager.data.PlayerEntity
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
import java.time.ZoneId
import java.util.Date
import java.util.Locale

private enum class Destination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Setup("setup", "Setup", Icons.Outlined.Settings),
    Games("games", "Games", Icons.Outlined.Event),
    Planner("planner", "Planner", Icons.Outlined.Tune),
    Live("live", "Live", Icons.Outlined.Timer),
    History("history", "History", Icons.Outlined.BarChart),
    Reports("reports", "Reports", Icons.Outlined.Print),
}

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
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
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
                    onAddPlayer = viewModel::addPlayer,
                    onUpdateSeasonDefaults = viewModel::updateSeasonDefaults,
                    onUpdatePlayer = viewModel::updatePlayerDetails,
                    onTogglePlayerActive = viewModel::togglePlayerActive,
                )
            }
                composable(Destination.Games.route) {
                    GamesScreen(
                        uiState = uiState,
                        onCreateGame = viewModel::createGame,
                        onSelectGame = { gameId, destination ->
                            viewModel.selectGame(gameId)
                            navController.navigate(destination.route)
                        },
                    )
                }
                composable(Destination.Planner.route) {
                    PlannerScreen(
                        uiState = uiState,
                        onSelectGame = viewModel::selectGame,
                        onToggleAvailability = viewModel::toggleAvailability,
                        onGenerateAssignments = viewModel::generateAssignments,
                        onCycleAssignment = viewModel::cycleAssignmentPlayer,
                    )
                }
                composable(Destination.Live.route) {
                    LiveScreen(
                        uiState = uiState,
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
    onAddPlayer: (String, String, Boolean) -> Unit,
    onUpdateSeasonDefaults: (String, String, String) -> Unit,
    onUpdatePlayer: (PlayerEntity, String, String, Boolean, String) -> Unit,
    onTogglePlayerActive: (PlayerEntity) -> Unit,
) {
    val selectedTemplate = uiState.selectedSeason?.template()
    var seasonName by rememberSaveable { mutableStateOf("Spring Team") }
    var seasonYear by rememberSaveable {
        mutableStateOf(Date().toInstant().atZone(ZoneId.systemDefault()).year.toString())
    }
    var playerName by rememberSaveable { mutableStateOf("") }
    var jerseyNumber by rememberSaveable { mutableStateOf("") }
    var preferredKeeper by rememberSaveable { mutableStateOf(false) }
    var editingPlayerId by rememberSaveable(uiState.selectedSeasonId) { mutableStateOf<String?>(null) }
    var teamName by rememberSaveable(uiState.selectedSeasonId) {
        mutableStateOf(uiState.selectedSeason?.name ?: "McFarland SC")
    }
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
                        label = { Text("Preferred keeper") },
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
                title = "Season & Roster Setup",
                subtitle = "Create the season, keep the roster current, and set your default clocks.",
            )
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Seasons", style = MaterialTheme.typography.titleLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.seasons.forEach { season ->
                            FilterChip(
                                selected = season.seasonId == uiState.selectedSeasonId,
                                onClick = { onSelectSeason(season.seasonId) },
                                label = { Text("${season.name} ${season.year}") },
                            )
                        }
                    }
                    HorizontalDivider()
                    OutlinedTextField(
                        value = seasonName,
                        onValueChange = { seasonName = it },
                        label = { Text("Season name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = seasonYear,
                        onValueChange = { seasonYear = it },
                        label = { Text("Year") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = { onCreateSeason(seasonName, seasonYear) }) {
                        Text("Create season")
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Game defaults", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = teamName,
                        onValueChange = { teamName = it },
                        label = { Text("Team name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                        onClick = { onUpdateSeasonDefaults(teamName, halfDurationMinutes, substitutionWindowMinutes) },
                        enabled = uiState.selectedSeasonId != null,
                    ) {
                        Text("Save defaults")
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
                        label = { Text("Preferred keeper") },
                    )
                    Button(
                        onClick = {
                            onAddPlayer(playerName, jerseyNumber, preferredKeeper)
                            playerName = ""
                            jerseyNumber = ""
                            preferredKeeper = false
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
                            "Jersey ${player.jerseyNumber.ifBlank { "--" }} • ${if (player.preferredKeeper) "Keeper eligible" else "Field player"}",
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
    onCreateGame: (String, String) -> Unit,
    onSelectGame: (String, Destination) -> Unit,
) {
    var opponent by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                title = "Games",
                subtitle = "Create games, jump into planning, and move directly into live management.",
            )
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
                    Button(
                        onClick = {
                            onCreateGame(opponent, location)
                            opponent = ""
                            location = ""
                        },
                        enabled = uiState.selectedSeasonId != null,
                    ) {
                        Text("Create game")
                    }
                }
            }
        }
        items(uiState.games, key = { it.gameId }) { game ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(game.opponent, style = MaterialTheme.typography.titleLarge)
                    Text("${formatDate(game.scheduledAt)} • ${game.location.ifBlank { "Location TBD" }}")
                    Text("Status: ${game.status.name}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ElevatedAssistChip(
                            onClick = { onSelectGame(game.gameId, Destination.Planner) },
                            label = { Text("Planner") },
                            leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                        )
                        ElevatedAssistChip(
                            onClick = { onSelectGame(game.gameId, Destination.Live) },
                            label = { Text("Live") },
                            leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                        )
                        ElevatedAssistChip(
                            onClick = { onSelectGame(game.gameId, Destination.Reports) },
                            label = { Text("Report") },
                            leadingIcon = { Icon(Icons.Outlined.Print, contentDescription = null) },
                        )
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
    onSelectGame: (String) -> Unit,
    onToggleAvailability: (String, Boolean) -> Unit,
    onGenerateAssignments: () -> Unit,
    onCycleAssignment: (String) -> Unit,
) {
    val detail = uiState.selectedGameDetail ?: run {
        EmptyState("Select a game from the Games tab to plan lineups.")
        return
    }
    val availabilityMap = detail.availability.associateBy({ it.playerId }, { it.isAvailable })

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                title = "Pregame Planner",
                subtitle = "${detail.game.opponent} • ${formatDate(detail.game.scheduledAt)}",
            )
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Availability", style = MaterialTheme.typography.titleLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detail.players.filter { it.active }.forEach { player ->
                            val available = availabilityMap[player.playerId] != false
                            FilterChip(
                                selected = available,
                                onClick = { onToggleAvailability(player.playerId, !available) },
                                label = { Text(player.name) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onGenerateAssignments) {
                            Text(if (detail.assignments.isEmpty()) "Generate lineup" else "Regenerate")
                        }
                        TextButton(onClick = { onSelectGame(detail.game.gameId) }) {
                            Text("Refresh game")
                        }
                    }
                    if (detail.game.plannerNotes.isNotBlank()) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(detail.game.plannerNotes, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
        if (detail.assignments.isEmpty()) {
            item { EmptyState("Generate the lineup to see half groups and round-by-round assignments.") }
        } else {
            (1..detail.game.template().halfCount).forEach { halfNumber ->
                item { HalfSummaryCard(detail, halfNumber) }
                item { Text("Half $halfNumber rounds", style = MaterialTheme.typography.titleLarge) }
                items((1..detail.game.template().roundsPerHalf).toList(), key = { "$halfNumber-$it" }) { roundNumber ->
                    RoundCard(detail, halfNumber, roundNumber, onCycleAssignment)
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
    onRecordGoal: (GoalSide, String?) -> Unit,
    onApplyLiveSub: (String, String) -> Unit,
    onApplyInjurySub: (String, String) -> Unit,
    onClearPlayerInjury: (String) -> Unit,
    onFinalize: () -> Unit,
) {
    val detail = uiState.selectedGameDetail ?: run {
        EmptyState("Select a planned game to manage it live.")
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
    val onFieldPlayerIds = currentAssignments.map { it.playerId }.toSet()
    val benchCandidates = activeRoster.filter { player ->
        player.playerId !in onFieldPlayerIds &&
            availabilityByPlayer[player.playerId]?.isAvailable != false &&
            availabilityByPlayer[player.playerId]?.isInjured != true
    }
    val gameRemainingSeconds = (template.halfDurationMinutes * 60) - uiState.effectiveHalfElapsedSeconds
    val substitutionRemainingSeconds =
        (template.substitutionWindowMinutes * 60) - uiState.effectiveRoundElapsedSeconds
    val advanceHalfReady = gameRemainingSeconds <= 0
    var showScorerDialog by rememberSaveable(detail.game.gameId, detail.game.currentHalf, detail.game.currentRound) {
        mutableStateOf(false)
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
                                onRecordGoal(GoalSide.TEAM, player.playerId)
                                showScorerDialog = false
                                scorerShowAll = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(player.name)
                        }
                    }
                    TextButton(
                        onClick = {
                            onRecordGoal(GoalSide.TEAM, null)
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
                TextButton(onClick = { showScorerDialog = false }) {
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
            AlertDialog(
                onDismissRequest = { selectedAssignmentId = null },
                title = { Text("${assignment.position.label} actions") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Current player: ${playerLookup[assignment.playerId].orEmpty()}")
                        if (benchCandidates.isEmpty()) {
                            Text("No bench players are available for a quick sub.")
                        } else {
                            Text("Quick sub")
                            benchCandidates.forEach { player ->
                                OutlinedButton(
                                    onClick = {
                                        onApplyLiveSub(assignment.assignmentId, player.playerId)
                                        selectedAssignmentId = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(player.name)
                                }
                            }
                            HorizontalDivider()
                            Text("Mark injured and sub")
                            benchCandidates.forEach { player ->
                                Button(
                                    onClick = {
                                        onApplyInjurySub(assignment.assignmentId, player.playerId)
                                        selectedAssignmentId = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Injure out, ${player.name} in")
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
                        onOpponentGoal = { onRecordGoal(GoalSide.OPPONENT, null) },
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
                    onOpponentGoal = { onRecordGoal(GoalSide.OPPONENT, null) },
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
) {
    val template = detail.game.template()
    val totalTeamGoals = detail.goals.count { it.scoredBy == GoalSide.TEAM }
    val totalOpponentGoals = detail.goals.count { it.scoredBy == GoalSide.OPPONENT }
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(
                title = "Live Game",
                subtitle = "${detail.game.opponent} • Half ${detail.game.currentHalf} • Sub round ${detail.game.currentRound}",
            )
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
                        OutlinedButton(
                            onClick = onOpponentGoal,
                            modifier = Modifier.weight(1f),
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
                    Text(if (uiState.clockRunning) "Pause clock" else "Start clock")
                }
                OutlinedButton(
                    onClick = onAdvanceRound,
                    enabled = detail.game.status != GameStatus.FINAL && detail.game.currentRound < template.roundsPerHalf,
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
                OutlinedButton(onClick = onFinalize) {
                    Text("Finalize")
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
private fun InjuredPlayersCard(
    injuredPlayers: List<PlayerAvailabilityEntity>,
    playerLookup: Map<String, String>,
    onClearPlayerInjury: (String) -> Unit,
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
                        TextButton(onClick = { onClearPlayerInjury(injured.playerId) }) {
                            Text("Clear injury")
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
                        playerLookup[goal.scorerPlayerId] ?: "${teamName.ifBlank { "Team" }} goal"
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

@Composable
private fun HistoryScreen(
    uiState: AppUiState,
    onSelectGame: (String) -> Unit,
) {
    val metrics = uiState.teamMetrics
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                title = "History & Analytics",
                subtitle = "Fairness, position distribution, and descriptive score correlations.",
            )
        }
        if (metrics == null || metrics.totalGames == 0) {
            item { EmptyState("Finalize a few games to unlock season metrics.") }
        } else {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Team snapshot", style = MaterialTheme.typography.titleLarge)
                        Text("Finalized games: ${metrics.totalGames}")
                        Text("Goals for/against: ${metrics.teamGoals}-${metrics.opponentGoals}")
                        metrics.goalsByHalf.toSortedMap().forEach { (half, score) ->
                            Text("Half $half: ${score.first}-${score.second}")
                        }
                    }
                }
            }
            items(metrics.playerMetrics, key = { it.playerId }) { player ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(player.playerName, style = MaterialTheme.typography.titleLarge)
                        Text("Minutes: ${"%.1f".format(player.totalMinutes)}")
                        Text("Keeper starts: ${player.keeperCount}")
                        Text("Score differential while assigned: ${player.scoreDifferentialWhileAssigned}")
                        Text("Groups: ${player.groupCounts.entries.joinToString { "${it.key.label} ${it.value}" }}")
                        Text("Positions: ${player.positionCounts.entries.joinToString { "${it.key.label} ${it.value}" }}")
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
private fun ReportsScreen(
    uiState: AppUiState,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val report = uiState.report
    if (uiState.selectedGameDetail == null) {
        EmptyState("Select a game from the Games tab to view and print its one-page report.")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "Report & Print",
            subtitle = "Single-page lineup sheet with halves, rounds, and score boxes.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefresh) { Text("Refresh") }
            OutlinedButton(onClick = { report?.let { printReport(context, it) } }, enabled = report != null) {
                Text("Print / Save PDF")
            }
        }
        if (report == null) {
            EmptyState("No report available yet.")
        } else {
            Card {
                Text(
                    report.plainText,
                    modifier = Modifier.padding(16.dp),
                    fontFamily = FontFamily.Monospace,
                )
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
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun HalfSummaryCard(detail: GameDetail, halfNumber: Int) {
    val playerLookup = detail.players.associateBy({ it.playerId }, { it.name })
    val halfAssignments = detail.assignments.filter { it.halfNumber == halfNumber }
    val summary = PositionGroup.entries.associateWith { group ->
        halfAssignments
            .filter { it.positionGroup == group }
            .mapNotNull { playerLookup[it.playerId] }
            .distinct()
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Half $halfNumber groups", style = MaterialTheme.typography.titleLarge)
            summary.forEach { (group, players) ->
                Text("${group.label}: ${players.joinToString().ifBlank { "None" }}")
            }
        }
    }
}

@Composable
private fun RoundCard(
    detail: GameDetail,
    halfNumber: Int,
    roundNumber: Int,
    onCycleAssignment: (String) -> Unit,
) {
    val template = detail.game.template()
    val playerLookup = detail.players.associateBy({ it.playerId }, { it.name })
    val assignments = detail.assignments
        .filter { it.halfNumber == halfNumber && it.roundIndex == roundNumber }
        .sortedBy { template.positions.indexOf(it.position) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Round $roundNumber", style = MaterialTheme.typography.titleLarge)
            assignments.forEach { assignment ->
                OutlinedButton(
                    onClick = { onCycleAssignment(assignment.assignmentId) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = detail.game.status != GameStatus.LIVE && detail.game.status != GameStatus.FINAL,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(assignment.position.label)
                        Text(playerLookup[assignment.playerId].orEmpty())
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

private fun formatClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatSignedClock(totalSeconds: Int): String {
    return if (totalSeconds >= 0) {
        formatClock(totalSeconds)
    } else {
        "-${formatClock(-totalSeconds)}"
    }
}
