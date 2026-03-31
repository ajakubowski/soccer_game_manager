package com.example.soccergamemanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.soccergamemanager.data.GameDetail
import com.example.soccergamemanager.data.GameEntity
import com.example.soccergamemanager.data.PlayerEntity
import com.example.soccergamemanager.data.SeasonEntity
import com.example.soccergamemanager.data.SettingsStore
import com.example.soccergamemanager.data.SoccerRepository
import com.example.soccergamemanager.domain.GameStatus
import com.example.soccergamemanager.domain.GoalSide
import com.example.soccergamemanager.domain.PrintableReport
import com.example.soccergamemanager.domain.TeamMetrics
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

data class AppUiState(
    val seasons: List<SeasonEntity> = emptyList(),
    val selectedSeasonId: String? = null,
    val players: List<PlayerEntity> = emptyList(),
    val games: List<GameEntity> = emptyList(),
    val selectedGameId: String? = null,
    val selectedGameDetail: GameDetail? = null,
    val teamMetrics: TeamMetrics? = null,
    val report: PrintableReport? = null,
    val transientMessage: String? = null,
    val clockRunning: Boolean = false,
    val effectiveHalfElapsedSeconds: Int = 0,
    val effectiveRoundElapsedSeconds: Int = 0,
) {
    val selectedSeason: SeasonEntity?
        get() = seasons.firstOrNull { it.seasonId == selectedSeasonId }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: SoccerRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val selectedGameId = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val metrics = MutableStateFlow<TeamMetrics?>(null)
    private val report = MutableStateFlow<PrintableReport?>(null)
    private val clockRunning = MutableStateFlow(false)
    private val halfElapsedOverride = MutableStateFlow<Int?>(null)
    private val roundElapsedOverride = MutableStateFlow<Int?>(null)
    private var clockJob: Job? = null

    private val selectedSeasonFlow = settingsStore.selectedSeasonId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val seasonsFlow = repository.observeSeasons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val playersFlow = selectedSeasonFlow
        .flatMapLatest { repository.observePlayersBySeason(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val gamesFlow = selectedSeasonFlow
        .flatMapLatest { repository.observeGamesBySeason(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val gameDetailFlow = selectedGameId
        .flatMapLatest { repository.observeGameDetail(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val seasonUiFlow = combine(
        seasonsFlow,
        selectedSeasonFlow,
        playersFlow,
    ) { seasons, selectedSeasonId, players ->
        SeasonUiSnapshot(
            seasons = seasons,
            selectedSeasonId = selectedSeasonId,
            players = players,
        )
    }
    private val gameUiFlow = combine(
        gamesFlow,
        selectedGameId,
        gameDetailFlow,
    ) { games, activeGameId, detail ->
        GameUiSnapshot(
            games = games,
            selectedGameId = activeGameId,
            selectedGameDetail = detail,
        )
    }
    private val baseUiFlow = combine(seasonUiFlow, gameUiFlow) { seasonUi, gameUi ->
        BaseUiSnapshot(
            seasons = seasonUi.seasons,
            selectedSeasonId = seasonUi.selectedSeasonId,
            players = seasonUi.players,
            games = gameUi.games,
            selectedGameId = gameUi.selectedGameId,
            selectedGameDetail = gameUi.selectedGameDetail,
        )
    }
    private val clockUiFlow = combine(
        clockRunning,
        halfElapsedOverride,
        roundElapsedOverride,
    ) { running, halfElapsed, roundElapsed ->
        ClockUiSnapshot(
            clockRunning = running,
            halfElapsedOverride = halfElapsed,
            roundElapsedOverride = roundElapsed,
        )
    }
    private val runtimeUiFlow = combine(
        metrics,
        report,
        message,
        clockUiFlow,
    ) { teamMetrics, reportData, toast, clock ->
        RuntimeUiSnapshot(
            teamMetrics = teamMetrics,
            report = reportData,
            transientMessage = toast,
            clockRunning = clock.clockRunning,
            halfElapsedOverride = clock.halfElapsedOverride,
            roundElapsedOverride = clock.roundElapsedOverride,
        )
    }

    val uiState: StateFlow<AppUiState> = combine(baseUiFlow, runtimeUiFlow) { base, runtime ->
        AppUiState(
            seasons = base.seasons,
            selectedSeasonId = base.selectedSeasonId,
            players = base.players,
            games = base.games,
            selectedGameId = base.selectedGameId,
            selectedGameDetail = base.selectedGameDetail,
            teamMetrics = runtime.teamMetrics,
            report = runtime.report,
            transientMessage = runtime.transientMessage,
            clockRunning = runtime.clockRunning,
            effectiveHalfElapsedSeconds = runtime.halfElapsedOverride
                ?: base.selectedGameDetail?.game?.elapsedSecondsInHalf
                ?: 0,
            effectiveRoundElapsedSeconds = runtime.roundElapsedOverride
                ?: base.selectedGameDetail?.game?.elapsedSecondsInRound
                ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    init {
        viewModelScope.launch {
            repository.seedDemoSeasonIfEmpty()
        }
        viewModelScope.launch {
            seasonsFlow.collect { seasons ->
                if (selectedSeasonFlow.value == null && seasons.isNotEmpty()) {
                    settingsStore.setSelectedSeasonId(seasons.first().seasonId)
                }
            }
        }
        viewModelScope.launch {
            selectedSeasonFlow.collect { seasonId ->
                selectedGameId.value = null
                halfElapsedOverride.value = null
                roundElapsedOverride.value = null
                clockRunning.value = false
                clockJob?.cancel()
                report.value = null
                if (seasonId != null) {
                    refreshMetrics()
                }
            }
        }
        viewModelScope.launch {
            selectedGameId.collect { gameId ->
                halfElapsedOverride.value = null
                roundElapsedOverride.value = null
                clockRunning.value = false
                clockJob?.cancel()
                if (gameId != null) {
                    refreshReport()
                } else {
                    report.value = null
                }
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun selectSeason(seasonId: String) {
        viewModelScope.launch {
            settingsStore.setSelectedSeasonId(seasonId)
        }
    }

    fun createSeason(name: String, yearText: String) {
        val year = yearText.toIntOrNull() ?: ZonedDateTime.now().year
        if (name.isBlank()) {
            message.value = "Season name is required."
            return
        }
        launchTask {
            val seasonId = repository.createSeason(name.trim(), year)
            settingsStore.setSelectedSeasonId(seasonId)
            message.value = "Season created."
        }
    }

    fun addPlayer(name: String, jerseyNumber: String, preferredKeeper: Boolean) {
        val seasonId = selectedSeasonFlow.value ?: return
        if (name.isBlank()) {
            message.value = "Player name is required."
            return
        }
        launchTask {
            repository.addPlayer(seasonId, name, jerseyNumber, preferredKeeper)
            message.value = "Player added."
        }
    }

    fun updateSeasonDefaults(teamName: String, halfDurationText: String, substitutionWindowText: String) {
        val seasonId = selectedSeasonFlow.value ?: return
        val trimmedTeamName = teamName.trim()
        if (trimmedTeamName.isBlank()) {
            message.value = "Team name is required."
            return
        }
        val halfDuration = halfDurationText.toIntOrNull()?.coerceAtLeast(1) ?: 25
        val substitutionWindow = substitutionWindowText.toIntOrNull()?.coerceAtLeast(1) ?: 4
        launchTask {
            repository.updateSeasonDefaults(seasonId, trimmedTeamName, halfDuration, substitutionWindow)
            message.value = "Season defaults updated."
        }
    }

    fun togglePlayerActive(player: PlayerEntity) {
        launchTask {
            repository.togglePlayerActive(player)
        }
    }

    fun updatePlayerDetails(
        player: PlayerEntity,
        name: String,
        jerseyNumber: String,
        preferredKeeper: Boolean,
        notes: String,
    ) {
        if (name.isBlank()) {
            message.value = "Player name is required."
            return
        }
        launchTask {
            repository.updatePlayer(player, name, jerseyNumber, preferredKeeper, notes)
            message.value = "Player updated."
        }
    }

    fun createGame(opponent: String, location: String) {
        val seasonId = selectedSeasonFlow.value ?: return
        launchTask {
            val gameId = repository.createGame(
                seasonId = seasonId,
                opponent = opponent,
                location = location,
            )
            selectedGameId.value = gameId
            message.value = "Game created."
        }
    }

    fun selectGame(gameId: String) {
        selectedGameId.value = gameId
    }

    fun toggleAvailability(playerId: String, available: Boolean) {
        val gameId = selectedGameId.value ?: return
        launchTask {
            repository.setPlayerAvailability(gameId, playerId, available)
        }
    }

    fun generateAssignments() {
        val gameId = selectedGameId.value ?: return
        launchTask {
            val result = repository.generateAssignments(gameId)
            refreshReport()
            refreshMetrics()
            message.value = if (result.warnings.isEmpty()) {
                "Lineup generated."
            } else {
                result.warnings.joinToString("\n")
            }
        }
    }

    fun cycleAssignmentPlayer(assignmentId: String) {
        launchTask {
            repository.cycleAssignmentPlayer(assignmentId)
            refreshReport()
        }
    }

    fun applyLiveSub(assignmentId: String, replacementPlayerId: String) {
        launchTask {
            repository.applyLiveSub(assignmentId, replacementPlayerId)
            refreshReport()
        }
    }

    fun applyInjurySub(assignmentId: String, replacementPlayerId: String) {
        launchTask {
            repository.applyInjurySub(assignmentId, replacementPlayerId)
            refreshReport()
        }
    }

    fun clearPlayerInjury(playerId: String) {
        val gameId = selectedGameId.value ?: return
        launchTask {
            repository.clearPlayerInjury(gameId, playerId)
            refreshReport()
        }
    }

    fun startOrPauseClock() {
        val detail = uiState.value.selectedGameDetail ?: return
        val gameId = detail.game.gameId
        if (clockRunning.value) {
            launchTask {
                stopClockAndPersist()
            }
            return
        }
        if (detail.assignmentsMissing()) {
            message.value = "Generate a lineup before starting the game."
            return
        }
        launchTask {
            repository.startGame(gameId)
            clockRunning.value = true
            halfElapsedOverride.value = halfElapsedOverride.value ?: detail.game.elapsedSecondsInHalf
            roundElapsedOverride.value = roundElapsedOverride.value ?: detail.game.elapsedSecondsInRound
            clockJob?.cancel()
            clockJob = viewModelScope.launch {
                while (true) {
                    delay(1_000)
                    val nextHalf = (halfElapsedOverride.value ?: detail.game.elapsedSecondsInHalf) + 1
                    val nextRound = (roundElapsedOverride.value ?: detail.game.elapsedSecondsInRound) + 1
                    halfElapsedOverride.value = nextHalf
                    roundElapsedOverride.value = nextRound
                    if (nextHalf % 15 == 0) {
                        repository.updateLiveState(
                            gameId = gameId,
                            currentHalf = uiState.value.selectedGameDetail?.game?.currentHalf ?: 1,
                            currentRound = uiState.value.selectedGameDetail?.game?.currentRound ?: 1,
                            elapsedSecondsInHalf = nextHalf,
                            elapsedSecondsInRound = nextRound,
                        )
                    }
                }
            }
        }
    }

    fun advanceRound() {
        val detail = uiState.value.selectedGameDetail ?: return
        launchTask {
            repository.updateLiveState(
                gameId = detail.game.gameId,
                currentHalf = detail.game.currentHalf,
                currentRound = detail.game.currentRound,
                elapsedSecondsInHalf = uiState.value.effectiveHalfElapsedSeconds,
                elapsedSecondsInRound = uiState.value.effectiveRoundElapsedSeconds,
            )
            repository.advanceRound(detail.game.gameId)
            roundElapsedOverride.value = 0
            refreshReport()
        }
    }

    fun advanceHalf(returningPlayerIds: Set<String> = emptySet()) {
        val detail = uiState.value.selectedGameDetail ?: return
        launchTask {
            stopClockAndPersist()
            repository.prepareSecondHalfAvailability(detail.game.gameId, returningPlayerIds)
            repository.advanceHalf(detail.game.gameId)
            halfElapsedOverride.value = 0
            roundElapsedOverride.value = 0
            refreshReport()
        }
    }

    fun recordGoal(side: GoalSide, scorerPlayerId: String? = null) {
        val detail = uiState.value.selectedGameDetail ?: return
        launchTask {
            repository.recordGoal(
                gameId = detail.game.gameId,
                side = side,
                scorerPlayerId = scorerPlayerId,
                currentHalf = detail.game.currentHalf,
                currentRound = detail.game.currentRound,
                elapsedSeconds = uiState.value.effectiveHalfElapsedSeconds,
            )
            refreshMetrics()
            refreshReport()
        }
    }

    fun finalizeGame() {
        val detail = uiState.value.selectedGameDetail ?: return
        launchTask {
            stopClockAndPersist()
            repository.finalizeGame(detail.game.gameId)
            refreshMetrics()
            refreshReport()
            message.value = "Game finalized. Lineups are now locked."
        }
    }

    fun refreshReport() {
        val gameId = selectedGameId.value ?: return
        launchTask {
            report.value = repository.buildReport(gameId)
        }
    }

    private suspend fun refreshMetrics() {
        val seasonId = selectedSeasonFlow.value ?: return
        metrics.value = repository.calculateMetrics(seasonId)
    }

    private suspend fun stopClockAndPersist() {
        val detail = uiState.value.selectedGameDetail ?: return
        clockRunning.value = false
        clockJob?.cancel()
        clockJob = null
        repository.updateLiveState(
            gameId = detail.game.gameId,
            currentHalf = detail.game.currentHalf,
            currentRound = detail.game.currentRound,
            elapsedSecondsInHalf = uiState.value.effectiveHalfElapsedSeconds,
            elapsedSecondsInRound = uiState.value.effectiveRoundElapsedSeconds,
        )
    }

    private fun launchTask(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { throwable ->
                    message.value = throwable.message ?: "Something went wrong."
                }
        }
    }

    private fun GameDetail.assignmentsMissing(): Boolean = assignments.isEmpty()
}

private data class BaseUiSnapshot(
    val seasons: List<SeasonEntity>,
    val selectedSeasonId: String?,
    val players: List<PlayerEntity>,
    val games: List<GameEntity>,
    val selectedGameId: String?,
    val selectedGameDetail: GameDetail?,
)

private data class SeasonUiSnapshot(
    val seasons: List<SeasonEntity>,
    val selectedSeasonId: String?,
    val players: List<PlayerEntity>,
)

private data class GameUiSnapshot(
    val games: List<GameEntity>,
    val selectedGameId: String?,
    val selectedGameDetail: GameDetail?,
)

private data class RuntimeUiSnapshot(
    val teamMetrics: TeamMetrics?,
    val report: PrintableReport?,
    val transientMessage: String?,
    val clockRunning: Boolean,
    val halfElapsedOverride: Int?,
    val roundElapsedOverride: Int?,
)

private data class ClockUiSnapshot(
    val clockRunning: Boolean,
    val halfElapsedOverride: Int?,
    val roundElapsedOverride: Int?,
)

class MainViewModelFactory(
    private val repository: SoccerRepository,
    private val settingsStore: SettingsStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(repository, settingsStore) as T
    }
}
