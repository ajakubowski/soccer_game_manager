package com.example.soccergamemanager.data

import com.example.soccergamemanager.domain.GameStatus
import com.example.soccergamemanager.domain.GameTemplateConfig
import com.example.soccergamemanager.domain.GoalSide
import com.example.soccergamemanager.domain.FieldPosition
import com.example.soccergamemanager.domain.LineupGenerationResult
import com.example.soccergamemanager.domain.LineupGenerator
import com.example.soccergamemanager.domain.LineupPlayer
import com.example.soccergamemanager.domain.MetricsCalculator
import com.example.soccergamemanager.domain.PlayerSeasonHistory
import com.example.soccergamemanager.domain.PositionGroup
import com.example.soccergamemanager.domain.ReportFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class SoccerRepository(
    private val seasonDao: SeasonDao,
    private val playerDao: PlayerDao,
    private val gameDao: GameDao,
    private val availabilityDao: AvailabilityDao,
    private val assignmentDao: AssignmentDao,
    private val goalDao: GoalDao,
    private val lineupGenerator: LineupGenerator = LineupGenerator(),
    private val metricsCalculator: MetricsCalculator = MetricsCalculator(),
    private val reportFormatter: ReportFormatter = ReportFormatter(),
) {
    fun observeSeasons(): Flow<List<SeasonEntity>> = seasonDao.observeSeasons()

    fun observePlayersBySeason(seasonId: String?): Flow<List<PlayerEntity>> =
        seasonId?.let(playerDao::observePlayersBySeason) ?: flowOf(emptyList())

    fun observeGamesBySeason(seasonId: String?): Flow<List<GameEntity>> =
        seasonId?.let(gameDao::observeGamesBySeason) ?: flowOf(emptyList())

    fun observeAssignmentCountsBySeason(seasonId: String?): Flow<Map<String, Int>> =
        seasonId?.let { id ->
            assignmentDao.observeAssignmentCountsBySeason(id).map { counts ->
                counts.associate { it.gameId to it.assignmentCount }
            }
        } ?: flowOf(emptyMap())

    fun observeGameDetail(gameId: String?): Flow<GameDetail?> {
        if (gameId == null) return flowOf(null)
        return gameDao.observeGame(gameId).flatMapLatest { game ->
            if (game == null) {
                flowOf(null)
            } else {
                playerDao.observePlayersBySeason(game.seasonId).flatMapLatest { players ->
                    availabilityDao.observeByGame(gameId).flatMapLatest { availability ->
                        assignmentDao.observeByGame(gameId).flatMapLatest { assignments ->
                            goalDao.observeByGame(gameId).flatMapLatest { goals ->
                                flowOf(GameDetail(game, players, availability, assignments, goals))
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun seedDemoSeasonIfEmpty() {
        if (seasonDao.countSeasons() > 0) return
        val seasonId = UUID.randomUUID().toString()
        val template = GameTemplateConfig.defaultU9()
        seasonDao.insertSeason(
            SeasonEntity(
                seasonId = seasonId,
                name = "Youth Team",
                year = ZonedDateTime.now(ZoneId.systemDefault()).year,
                defaultTemplateJson = template.toJson(),
            ),
        )
        listOf(
            "Charlie",
            "Wesley",
            "Isaac",
            "Miles",
            "Easton",
            "Josh",
            "Dylan",
            "Caleb",
            "Harrison",
            "Roman",
            "Parker",
        ).forEachIndexed { index, name ->
            playerDao.insertPlayer(
                PlayerEntity(
                    playerId = UUID.randomUUID().toString(),
                    seasonId = seasonId,
                    name = name,
                    jerseyNumber = (index + 1).toString(),
                    preferredKeeper = true,
                ),
            )
        }
    }

    suspend fun createSeason(name: String, year: Int): String {
        val seasonId = UUID.randomUUID().toString()
        seasonDao.insertSeason(
            SeasonEntity(
                seasonId = seasonId,
                name = name,
                year = year,
                defaultTemplateJson = GameTemplateConfig.defaultU9().toJson(),
            ),
        )
        return seasonId
    }

    suspend fun addPlayer(
        seasonId: String,
        name: String,
        jerseyNumber: String,
        preferredKeeper: Boolean,
        notes: String = "",
    ) {
        playerDao.insertPlayer(
            PlayerEntity(
                playerId = UUID.randomUUID().toString(),
                seasonId = seasonId,
                name = name.trim(),
                jerseyNumber = jerseyNumber.trim(),
                preferredKeeper = preferredKeeper,
                notes = notes.trim(),
            ),
        )
    }

    suspend fun updateSeasonDefaults(
        seasonId: String,
        halfDurationMinutes: Int,
        substitutionWindowMinutes: Int,
    ) {
        val season = seasonDao.getSeason(seasonId) ?: return
        val updatedTemplate = season.template().copy(
            halfDurationMinutes = halfDurationMinutes,
            substitutionWindowMinutes = substitutionWindowMinutes,
        )
        seasonDao.updateSeason(
            season.copy(
                defaultTemplateJson = updatedTemplate.toJson(),
            ),
        )
    }

    suspend fun updateSeason(season: SeasonEntity, name: String, year: Int) {
        seasonDao.updateSeason(
            season.copy(
                name = name.trim().ifBlank { season.name },
                year = year,
            ),
        )
    }

    suspend fun deleteSeason(season: SeasonEntity) {
        seasonDao.deleteSeason(season)
    }

    suspend fun togglePlayerActive(player: PlayerEntity) {
        playerDao.updatePlayer(player.copy(active = !player.active))
    }

    suspend fun updatePlayer(
        player: PlayerEntity,
        name: String,
        jerseyNumber: String,
        preferredKeeper: Boolean,
        notes: String,
    ) {
        playerDao.updatePlayer(
            player.copy(
                name = name.trim(),
                jerseyNumber = jerseyNumber.trim(),
                preferredKeeper = preferredKeeper,
                notes = notes.trim(),
            ),
        )
    }

    suspend fun createGame(
        seasonId: String,
        opponent: String,
        location: String,
        scheduledAt: Long = Instant.now().toEpochMilli(),
        templateOverride: GameTemplateConfig? = null,
    ): String {
        val season = seasonDao.getSeason(seasonId) ?: error("Missing season $seasonId")
        val gameId = UUID.randomUUID().toString()
        val template = templateOverride ?: season.template()
        gameDao.insertGame(
            GameEntity(
                gameId = gameId,
                seasonId = seasonId,
                opponent = opponent.ifBlank { "Opponent TBD" },
                location = location,
                scheduledAt = scheduledAt,
                templateJson = template.toJson(),
            ),
        )
        val players = playerDao.getPlayersBySeason(seasonId).filter { it.active }
        availabilityDao.upsertAll(
            players.map { player ->
                PlayerAvailabilityEntity(
                    gameId = gameId,
                    playerId = player.playerId,
                    isAvailable = true,
                    isInjured = false,
                    availableFirstHalf = true,
                    availableSecondHalf = true,
                )
            },
        )
        return gameId
    }

    suspend fun updateGameDetails(
        game: GameEntity,
        opponent: String,
        location: String,
        scheduledAt: Long,
    ) {
        gameDao.updateGame(
            game.copy(
                opponent = opponent.ifBlank { "Opponent TBD" },
                location = location,
                scheduledAt = scheduledAt,
            ),
        )
    }

    suspend fun setPlayerAvailability(gameId: String, playerId: String, halfNumber: Int, isAvailable: Boolean) {
        val existing = availabilityDao.getByGame(gameId).firstOrNull { it.playerId == playerId }
            ?: PlayerAvailabilityEntity(gameId, playerId)
        val updated = when (halfNumber) {
            1 -> existing.copy(
                isAvailable = isAvailable || existing.availableSecondHalf,
                availableFirstHalf = isAvailable,
            )
            2 -> existing.copy(
                isAvailable = existing.availableFirstHalf || isAvailable,
                availableSecondHalf = isAvailable,
            )
            else -> existing.copy(
                isAvailable = isAvailable,
                availableFirstHalf = isAvailable,
                availableSecondHalf = isAvailable,
            )
        }
        availabilityDao.upsertAll(listOf(updated))
    }

    suspend fun clearPlayerInjury(gameId: String, playerId: String, returnImmediately: Boolean = false) {
        val existing = availabilityDao.getByGame(gameId).firstOrNull { it.playerId == playerId } ?: return
        val game = gameDao.getGame(gameId) ?: return
        availabilityDao.upsertAll(listOf(existing.copy(isInjured = false)))
        if (returnImmediately && game.status != GameStatus.FINAL) {
            restorePlayerToCurrentAssignment(
                game = game,
                returningPlayerId = playerId,
                targetHalf = game.currentHalf,
                roundIndex = game.currentRound,
                injuryContext = existing,
            )
        }
        if (game.status != GameStatus.FINAL) {
            restorePlayerToFutureAssignments(
                game = game,
                returningPlayerId = playerId,
                targetHalf = game.currentHalf,
                startRoundExclusive = game.currentRound,
                injuryContext = existing,
            )
        }
        availabilityDao.upsertAll(
            listOf(
                existing.copy(
                    isInjured = false,
                    injuredAssignmentId = null,
                    injuredPosition = null,
                    injuredHalfNumber = null,
                    injuredRoundIndex = null,
                ),
            ),
        )
    }

    suspend fun generateAssignments(gameId: String): LineupGenerationResult {
        val game = gameDao.getGame(gameId) ?: error("Missing game $gameId")
        if (game.status == GameStatus.LIVE || game.status == GameStatus.FINAL) {
            return LineupGenerationResult(emptyList(), listOf("Live or finalized games cannot be regenerated."))
        }
        val players = playerDao.getPlayersBySeason(game.seasonId).filter { it.active }
        val availability = availabilityDao.getByGame(gameId).associateBy { it.playerId }
        val availablePlayers = players.filter { player ->
            val availabilityEntry = availability[player.playerId]
            (availabilityEntry?.availableFirstHalf ?: true) || (availabilityEntry?.availableSecondHalf ?: true)
        }
        val history = buildHistory(game.seasonId)
        val result = lineupGenerator.generate(
            template = game.template(),
            players = availablePlayers.map { player ->
                val availabilityEntry = availability[player.playerId]
                LineupPlayer(
                    id = player.playerId,
                    name = player.name,
                    preferredKeeper = player.preferredKeeper,
                    availableHalfNumbers = buildSet {
                        if (availabilityEntry?.availableFirstHalf != false) add(1)
                        if (availabilityEntry?.availableSecondHalf != false) add(2)
                    },
                )
            },
            historyByPlayer = history,
            manualGroupLocks = game.manualGroupLocks(),
        )
        assignmentDao.deleteByGame(gameId)
        assignmentDao.insertAll(
            result.assignments.map { assignment ->
                AssignmentEntity(
                    assignmentId = UUID.randomUUID().toString(),
                    gameId = gameId,
                    playerId = assignment.playerId,
                    halfNumber = assignment.halfNumber,
                    roundIndex = assignment.roundIndex,
                    position = assignment.position,
                    positionGroup = assignment.positionGroup,
                )
            },
        )
        gameDao.updateGame(
            game.copy(
                status = GameStatus.PREGAME,
                plannerNotes = result.warnings.joinToString("\n"),
            ),
        )
        return result
    }

    suspend fun updateManualGroupLock(
        gameId: String,
        halfNumber: Int,
        positionGroup: PositionGroup,
        playerIds: List<String>,
    ) {
        val game = gameDao.getGame(gameId) ?: return
        if (game.status == GameStatus.LIVE || game.status == GameStatus.FINAL) return

        val updatedLocks = game.manualGroupLocks()
            .filterNot { it.halfNumber == halfNumber && it.positionGroup == positionGroup } +
            com.example.soccergamemanager.domain.ManualGroupLock(
                halfNumber = halfNumber,
                positionGroup = positionGroup,
                playerIds = playerIds.distinct(),
            )

        gameDao.updateGame(
            game.copy(
                manualGroupLocksJson = updatedLocks
                    .filter { it.playerIds.isNotEmpty() }
                    .toJson(),
            ),
        )
    }

    suspend fun cycleAssignmentPlayer(assignmentId: String) {
        val assignment = assignmentDao.getAssignment(assignmentId) ?: return
        val game = gameDao.getGame(assignment.gameId) ?: return
        if (game.status == GameStatus.LIVE || game.status == GameStatus.FINAL) return

        val players = playerDao.getPlayersBySeason(game.seasonId)
            .filter { it.active }
            .sortedBy { it.name }
        val availability = availabilityDao.getByGame(game.gameId).associateBy { it.playerId }
        val roundAssignments = assignmentDao.getByRound(game.gameId, assignment.halfNumber, assignment.roundIndex)
        val occupiedPlayerIds = roundAssignments.map { it.playerId }.toMutableSet()
        occupiedPlayerIds.remove(assignment.playerId)

        val candidates = players.filter { player ->
            availability[player.playerId]?.isAvailableForHalf(assignment.halfNumber) != false &&
                (player.playerId == assignment.playerId || player.playerId !in occupiedPlayerIds)
        }
        if (candidates.isEmpty()) return

        val currentIndex = candidates.indexOfFirst { it.playerId == assignment.playerId }
        val nextPlayer = candidates[(if (currentIndex == -1) 0 else currentIndex + 1) % candidates.size]
        if (nextPlayer.playerId == assignment.playerId) return

        val swapAssignment = roundAssignments.firstOrNull { it.playerId == nextPlayer.playerId }
        val updates = buildList {
            add(assignment.copy(playerId = nextPlayer.playerId))
            if (swapAssignment != null) {
                add(swapAssignment.copy(playerId = assignment.playerId))
            }
        }
        assignmentDao.updateAssignments(updates)
    }

    suspend fun setAssignmentPlayer(assignmentId: String, replacementPlayerId: String) {
        val assignment = assignmentDao.getAssignment(assignmentId) ?: return
        val game = gameDao.getGame(assignment.gameId) ?: return
        if (game.status == GameStatus.LIVE || game.status == GameStatus.FINAL) return

        val players = playerDao.getPlayersBySeason(game.seasonId)
        val replacement = players.firstOrNull { it.playerId == replacementPlayerId && it.active } ?: return
        val availability = availabilityDao.getByGame(game.gameId).associateBy { it.playerId }
        if (availability[replacement.playerId]?.isAvailableForHalf(assignment.halfNumber) == false) return

        val roundAssignments = assignmentDao.getByRound(game.gameId, assignment.halfNumber, assignment.roundIndex)
        val swapAssignment = roundAssignments.firstOrNull {
            it.assignmentId != assignment.assignmentId && it.playerId == replacementPlayerId
        }
        val updates = buildList {
            add(assignment.copy(playerId = replacementPlayerId))
            if (swapAssignment != null) {
                add(swapAssignment.copy(playerId = assignment.playerId))
            }
        }
        if (swapAssignment == null && roundAssignments.any { it.assignmentId != assignment.assignmentId && it.playerId == replacementPlayerId }) {
            return
        }
        assignmentDao.updateAssignments(updates)
    }

    suspend fun applyLiveSub(assignmentId: String, replacementPlayerId: String) {
        val assignment = assignmentDao.getAssignment(assignmentId) ?: return
        val game = gameDao.getGame(assignment.gameId) ?: return
        if (game.status == GameStatus.FINAL) return

        val roundAssignments = assignmentDao.getByRound(game.gameId, assignment.halfNumber, assignment.roundIndex)
        if (roundAssignments.any { it.assignmentId != assignment.assignmentId && it.playerId == replacementPlayerId }) return
        assignmentDao.updateAssignments(listOf(assignment.copy(playerId = replacementPlayerId)))
    }

    suspend fun applyInjurySub(assignmentId: String, replacementPlayerId: String) {
        val assignment = assignmentDao.getAssignment(assignmentId) ?: return
        val game = gameDao.getGame(assignment.gameId) ?: return
        if (game.status == GameStatus.FINAL) return

        val injuredPlayerId = assignment.playerId
        markInjured(
            gameId = game.gameId,
            playerId = injuredPlayerId,
            injured = true,
            assignment = assignment,
        )
        applyLiveSub(assignmentId, replacementPlayerId)
        removePlayersFromFutureAssignments(
            game = game,
            removedPlayerIds = setOf(injuredPlayerId),
            targetHalf = game.currentHalf,
            startRoundExclusive = game.currentRound,
        )
    }

    suspend fun prepareSecondHalfAvailability(gameId: String, returningPlayerIds: Set<String>) {
        val game = gameDao.getGame(gameId) ?: return
        val injuredEntries = availabilityDao.getByGame(gameId).filter { it.isInjured }
        injuredEntries.forEach { entry ->
            availabilityDao.upsertAll(
                listOf(entry.copy(isInjured = entry.playerId !in returningPlayerIds)),
            )
        }
        val heldOutPlayerIds = injuredEntries
            .map { it.playerId }
            .filterNot { it in returningPlayerIds }
            .toSet()
        if (heldOutPlayerIds.isNotEmpty()) {
            removePlayersFromFutureAssignments(
                game = game,
                removedPlayerIds = heldOutPlayerIds,
                targetHalf = (game.currentHalf + 1).coerceAtMost(game.template().halfCount),
                startRoundExclusive = 0,
            )
        }
    }

    suspend fun startGame(gameId: String) {
        val game = gameDao.getGame(gameId) ?: return
        if (game.status == GameStatus.FINAL) return
        gameDao.updateGame(
            game.copy(
                status = GameStatus.LIVE,
                lockedAt = game.lockedAt ?: System.currentTimeMillis(),
                currentHalf = game.currentHalf.coerceAtLeast(1),
                currentRound = game.currentRound.coerceAtLeast(1),
            ),
        )
    }

    suspend fun updateLiveState(gameId: String, currentHalf: Int, currentRound: Int, elapsedSeconds: Int) {
        updateLiveState(gameId, currentHalf, currentRound, elapsedSeconds, 0)
    }

    suspend fun updateLiveState(
        gameId: String,
        currentHalf: Int,
        currentRound: Int,
        elapsedSecondsInHalf: Int,
        elapsedSecondsInRound: Int,
    ) {
        val game = gameDao.getGame(gameId) ?: return
        if (game.status == GameStatus.FINAL) return
        gameDao.updateGame(
            game.copy(
                currentHalf = currentHalf,
                currentRound = currentRound,
                elapsedSecondsInHalf = elapsedSecondsInHalf,
                elapsedSecondsInRound = elapsedSecondsInRound,
            ),
        )
    }

    suspend fun advanceRound(gameId: String) {
        val game = gameDao.getGame(gameId) ?: return
        val template = game.template()
        val nextRound = if (game.currentRound < template.roundsPerHalf) {
            game.currentRound + 1
        } else {
            game.currentRound
        }
        updateLiveState(
            gameId = gameId,
            currentHalf = game.currentHalf,
            currentRound = nextRound,
            elapsedSecondsInHalf = game.elapsedSecondsInHalf,
            elapsedSecondsInRound = 0,
        )
    }

    suspend fun advanceHalf(gameId: String) {
        val game = gameDao.getGame(gameId) ?: return
        val template = game.template()
        if (game.currentHalf >= template.halfCount) return
        updateLiveState(
            gameId = gameId,
            currentHalf = game.currentHalf + 1,
            currentRound = 1,
            elapsedSecondsInHalf = 0,
            elapsedSecondsInRound = 0,
        )
    }

    suspend fun recordGoal(
        gameId: String,
        side: GoalSide,
        scorerPlayerId: String?,
        assisterPlayerId: String?,
        currentHalf: Int,
        currentRound: Int,
        elapsedSeconds: Int,
    ) {
        goalDao.insertGoal(
            GoalEventEntity(
                goalEventId = UUID.randomUUID().toString(),
                gameId = gameId,
                scoredBy = side,
                scorerPlayerId = scorerPlayerId,
                assisterPlayerId = assisterPlayerId,
                halfNumber = currentHalf,
                roundIndex = currentRound,
                elapsedSecondsInHalf = elapsedSeconds,
            ),
        )
    }

    suspend fun finalizeGame(gameId: String) {
        val game = gameDao.getGame(gameId) ?: return
        gameDao.updateGame(
            game.copy(
                status = GameStatus.FINAL,
                finalizedAt = System.currentTimeMillis(),
                lockedAt = game.lockedAt ?: System.currentTimeMillis(),
            ),
        )
    }

    suspend fun calculateMetrics(seasonId: String) = metricsCalculator.calculate(
        players = playerDao.getPlayersBySeason(seasonId),
        finalizedGames = gameDao.getFinalizedGamesBySeason(seasonId),
        assignments = assignmentDao.getFinalizedAssignmentsBySeason(seasonId),
        goals = goalDao.getFinalizedGoalsBySeason(seasonId),
    )

    suspend fun buildReport(gameId: String) = reportFormatter.format(
        game = gameDao.getGame(gameId) ?: error("Missing game $gameId"),
        players = playerDao.getPlayersBySeason(gameDao.getGame(gameId)?.seasonId ?: error("Missing game $gameId")),
        assignments = assignmentDao.getByGame(gameId),
        goals = goalDao.getByGame(gameId),
        analytics = metricsCalculator.buildMatchReportAnalytics(
            game = gameDao.getGame(gameId) ?: error("Missing game $gameId"),
            players = playerDao.getPlayersBySeason(gameDao.getGame(gameId)?.seasonId ?: error("Missing game $gameId")),
            assignments = assignmentDao.getByGame(gameId),
            goals = goalDao.getByGame(gameId),
            seasonMetrics = calculateMetrics(gameDao.getGame(gameId)?.seasonId ?: error("Missing game $gameId")),
        ),
    )

    private suspend fun buildHistory(seasonId: String): Map<String, PlayerSeasonHistory> {
        val games = gameDao.getFinalizedGamesBySeason(seasonId)
        val assignments = assignmentDao.getFinalizedAssignmentsBySeason(seasonId)
        val groupedAssignments = assignments.groupBy { it.playerId }
        return groupedAssignments.mapValues { (_, playerAssignments) ->
            val minutes = playerAssignments.sumOf { assignment ->
                val template = games.firstOrNull { it.gameId == assignment.gameId }?.template() ?: GameTemplateConfig.defaultU9()
                template.halfDurationMinutes.toDouble() / template.roundsPerHalf.toDouble()
            }
            PlayerSeasonHistory(
                minutesPlayed = minutes,
                keeperAssignments = playerAssignments.count {
                    it.position == com.example.soccergamemanager.domain.FieldPosition.GOALIE && it.roundIndex == 1
                },
                groupCounts = playerAssignments.groupingBy { it.positionGroup }.eachCount(),
                positionCounts = playerAssignments.groupingBy { it.position }.eachCount(),
            )
        }
    }

    private suspend fun markInjured(
        gameId: String,
        playerId: String,
        injured: Boolean,
        assignment: AssignmentEntity? = null,
    ) {
        val existing = availabilityDao.getByGame(gameId).firstOrNull { it.playerId == playerId }
            ?: PlayerAvailabilityEntity(gameId = gameId, playerId = playerId)
        availabilityDao.upsertAll(
            listOf(
                existing.copy(
                    isInjured = injured,
                    injuredAssignmentId = if (injured) assignment?.assignmentId else null,
                    injuredPosition = if (injured) assignment?.position else null,
                    injuredHalfNumber = if (injured) assignment?.halfNumber else null,
                    injuredRoundIndex = if (injured) assignment?.roundIndex else null,
                ),
            ),
        )
    }

    private suspend fun removePlayersFromFutureAssignments(
        game: GameEntity,
        removedPlayerIds: Set<String>,
        targetHalf: Int,
        startRoundExclusive: Int,
    ) {
        val assignmentsById = assignmentDao.getByGame(game.gameId).associateBy { it.assignmentId }.toMutableMap()
        val players = playerDao.getPlayersBySeason(game.seasonId).filter { it.active }
        val availabilityMap = availabilityDao.getByGame(game.gameId).associateBy { it.playerId }
        val updates = mutableListOf<AssignmentEntity>()

        assignmentsById.values
            .filter { assignment ->
                assignment.halfNumber == targetHalf &&
                    assignment.roundIndex > startRoundExclusive &&
                    assignment.playerId in removedPlayerIds
            }
            .sortedWith(compareBy(AssignmentEntity::halfNumber, AssignmentEntity::roundIndex, AssignmentEntity::position))
            .forEach { assignment ->
                val currentRoundAssignments = assignmentsById.values.filter {
                    it.halfNumber == assignment.halfNumber && it.roundIndex == assignment.roundIndex
                }
                val occupiedIds = currentRoundAssignments
                    .filter { it.assignmentId != assignment.assignmentId }
                    .map { it.playerId }
                    .toSet()
                val replacement = players
                    .filter { candidate ->
                        candidate.playerId !in removedPlayerIds &&
                            candidate.playerId !in occupiedIds &&
                            availabilityMap[candidate.playerId]?.isAvailableForHalf(assignment.halfNumber) != false &&
                            availabilityMap[candidate.playerId]?.isInjured != true
                    }
                    .sortedBy { it.name }
                    .firstOrNull()
                    ?: return@forEach

                val updated = assignment.copy(playerId = replacement.playerId)
                assignmentsById[assignment.assignmentId] = updated
                updates += updated
            }

        if (updates.isNotEmpty()) {
            assignmentDao.updateAssignments(updates)
        }
    }

    private suspend fun restorePlayerToFutureAssignments(
        game: GameEntity,
        returningPlayerId: String,
        targetHalf: Int,
        startRoundExclusive: Int,
        injuryContext: PlayerAvailabilityEntity,
    ) {
        val playersById = playerDao.getPlayersBySeason(game.seasonId)
            .filter { it.active }
            .associateBy { it.playerId }
        val returningPlayer = playersById[returningPlayerId] ?: return
        val availabilityMap = availabilityDao.getByGame(game.gameId).associateBy { it.playerId }
        if (availabilityMap[returningPlayerId]?.isAvailableForHalf(targetHalf) == false || availabilityMap[returningPlayerId]?.isInjured == true) return

        val assignmentsById = assignmentDao.getByGame(game.gameId).associateBy { it.assignmentId }.toMutableMap()
        val allAssignments = assignmentsById.values.toList()
        val futureAssignments = allAssignments.filter {
            it.halfNumber == targetHalf && it.roundIndex > startRoundExclusive
        }
        if (futureAssignments.isEmpty()) return

        val lockedPlayerIdsForGroup = game.manualGroupLocks()
            .firstOrNull { it.halfNumber == targetHalf && returningPlayerId in it.playerIds }
            ?.let { lock ->
                game.manualGroupLocks()
                    .firstOrNull { it.halfNumber == targetHalf && it.positionGroup == lock.positionGroup }
                    ?.playerIds
                    ?.toSet()
                    .orEmpty()
            }
            .orEmpty()
        val targetGroup = determineReturnGroup(
            game = game,
            assignments = allAssignments,
            returningPlayer = returningPlayer,
            targetHalf = targetHalf,
        )
        val preferredPositions = determinePreferredPositions(
            assignments = allAssignments,
            returningPlayerId = returningPlayerId,
            targetHalf = targetHalf,
            targetGroup = targetGroup,
            preferredPositionOverride = injuryContext.injuredPosition,
        )
        val futureLoadByPlayer = futureAssignments.groupingBy { it.playerId }.eachCount().toMutableMap()
        val updates = mutableListOf<AssignmentEntity>()
        val futureRounds = futureAssignments.map { it.roundIndex }.distinct().sorted()

        futureRounds.forEach { roundIndex ->
            val roundAssignments = assignmentsById.values.filter {
                it.halfNumber == targetHalf && it.roundIndex == roundIndex
            }
            if (roundAssignments.any { it.playerId == returningPlayerId }) return@forEach

            val replacementTarget = roundAssignments
                .filter { it.positionGroup == targetGroup }
                .sortedWith(
                    compareBy<AssignmentEntity>(
                        { if (it.playerId in lockedPlayerIdsForGroup) 1 else 0 },
                        {
                            val index = preferredPositions.indexOf(it.position)
                            if (index == -1) Int.MAX_VALUE else index
                        },
                        { -(futureLoadByPlayer[it.playerId] ?: 0) },
                        { playersById[it.playerId]?.name.orEmpty() },
                    ),
                )
                .firstOrNull()
                ?: return@forEach

            val updated = replacementTarget.copy(playerId = returningPlayerId)
            assignmentsById[updated.assignmentId] = updated
            updates += updated
            futureLoadByPlayer[replacementTarget.playerId] = (futureLoadByPlayer[replacementTarget.playerId] ?: 1) - 1
            futureLoadByPlayer[returningPlayerId] = (futureLoadByPlayer[returningPlayerId] ?: 0) + 1
        }

        if (updates.isNotEmpty()) {
            assignmentDao.updateAssignments(updates)
        }
    }

    private suspend fun restorePlayerToCurrentAssignment(
        game: GameEntity,
        returningPlayerId: String,
        targetHalf: Int,
        roundIndex: Int,
        injuryContext: PlayerAvailabilityEntity,
    ) {
        val playersById = playerDao.getPlayersBySeason(game.seasonId)
            .filter { it.active }
            .associateBy { it.playerId }
        val returningPlayer = playersById[returningPlayerId] ?: return
        val availabilityMap = availabilityDao.getByGame(game.gameId).associateBy { it.playerId }
        if (availabilityMap[returningPlayerId]?.isAvailableForHalf(targetHalf) == false || availabilityMap[returningPlayerId]?.isInjured == true) return

        val exactAssignmentId = injuryContext.injuredAssignmentId ?: return
        val injuredAssignment = assignmentDao.getAssignment(exactAssignmentId) ?: return
        if (injuredAssignment.halfNumber != targetHalf || injuredAssignment.roundIndex != roundIndex) return

        assignmentDao.updateAssignments(
            listOf(injuredAssignment.copy(playerId = returningPlayerId)),
        )
    }

    private fun determineReturnGroup(
        game: GameEntity,
        assignments: List<AssignmentEntity>,
        returningPlayer: PlayerEntity,
        targetHalf: Int,
    ): PositionGroup {
        game.manualGroupLocks()
            .firstOrNull { it.halfNumber == targetHalf && returningPlayer.playerId in it.playerIds }
            ?.positionGroup
            ?.let { return it }

        assignments
            .filter { it.playerId == returningPlayer.playerId && it.halfNumber == targetHalf }
            .groupingBy { it.positionGroup }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?.let { return it }

        assignments
            .filter { it.playerId == returningPlayer.playerId }
            .groupingBy { it.positionGroup }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?.let { return it }

        return if (returningPlayer.preferredKeeper) PositionGroup.GOALIE else PositionGroup.DEFENSE
    }

    private fun determinePreferredPositions(
        assignments: List<AssignmentEntity>,
        returningPlayerId: String,
        targetHalf: Int,
        targetGroup: PositionGroup,
        preferredPositionOverride: FieldPosition? = null,
    ): List<com.example.soccergamemanager.domain.FieldPosition> {
        val positionsInGroup = when (targetGroup) {
            PositionGroup.GOALIE -> listOf(com.example.soccergamemanager.domain.FieldPosition.GOALIE)
            PositionGroup.DEFENSE -> listOf(
                com.example.soccergamemanager.domain.FieldPosition.LEFT_DEFENSE,
                com.example.soccergamemanager.domain.FieldPosition.RIGHT_DEFENSE,
            )
            PositionGroup.LR_MID -> listOf(
                com.example.soccergamemanager.domain.FieldPosition.LEFT_MIDFIELDER,
                com.example.soccergamemanager.domain.FieldPosition.RIGHT_MIDFIELDER,
            )
            PositionGroup.CM_STRIKER -> listOf(
                com.example.soccergamemanager.domain.FieldPosition.CENTER_MIDFIELDER,
                com.example.soccergamemanager.domain.FieldPosition.STRIKER,
            )
        }
        val halfPositionCounts = assignments
            .filter {
                it.playerId == returningPlayerId &&
                    it.halfNumber == targetHalf &&
                    it.positionGroup == targetGroup
            }
            .groupingBy { it.position }
            .eachCount()
        val allPositionCounts = assignments
            .filter { it.playerId == returningPlayerId && it.positionGroup == targetGroup }
            .groupingBy { it.position }
            .eachCount()

        return positionsInGroup.sortedWith(
            compareByDescending<com.example.soccergamemanager.domain.FieldPosition> { if (it == preferredPositionOverride) 1 else 0 }
                .thenByDescending { halfPositionCounts[it] ?: 0 }
                .thenByDescending { allPositionCounts[it] ?: 0 }
                .thenBy { positionsInGroup.indexOf(it) },
        )
    }
}
