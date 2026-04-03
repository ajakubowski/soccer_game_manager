package com.example.soccergamemanager.domain

import com.example.soccergamemanager.data.AssignmentEntity
import com.example.soccergamemanager.data.GameEntity
import com.example.soccergamemanager.data.GoalEventEntity
import com.example.soccergamemanager.data.PlayerEntity
import com.example.soccergamemanager.data.template
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MetricsCalculator {
    fun calculate(
        players: List<PlayerEntity>,
        finalizedGames: List<GameEntity>,
        assignments: List<AssignmentEntity>,
        goals: List<GoalEventEntity>,
    ): TeamMetrics {
        val playerById = players.associateBy { it.playerId }
        val sortedGames = finalizedGames.sortedBy { it.scheduledAt }
        val metrics = players.associate { player ->
            player.playerId to MutablePlayerMetrics(playerName = player.name)
        }
        val playerTrendPoints = players.associate { it.playerId to mutableListOf<PlayerTrendPoint>() }
        val groupSummaries = PositionGroup.entries.associateWith { MutableGroupSummary() }

        var wins = 0
        var draws = 0
        var losses = 0

        val goalsByHalf = goals.groupBy { it.halfNumber }.mapValues { (_, halfGoals) ->
            halfGoals.count { it.scoredBy == GoalSide.TEAM } to halfGoals.count { it.scoredBy == GoalSide.OPPONENT }
        }

        val gameTrendPoints = sortedGames.map { game ->
            val template = game.template()
            val gameAssignments = assignments.filter { it.gameId == game.gameId }
            val gameGoals = goals.filter { it.gameId == game.gameId }
            val minutesPerRound = template.halfDurationMinutes.toDouble() / template.roundsPerHalf.toDouble()
            val gameGoalsFor = gameGoals.count { it.scoredBy == GoalSide.TEAM }
            val gameGoalsAgainst = gameGoals.count { it.scoredBy == GoalSide.OPPONENT }
            val gameDifferential = gameGoalsFor - gameGoalsAgainst

            when {
                gameDifferential > 0 -> wins += 1
                gameDifferential < 0 -> losses += 1
                else -> draws += 1
            }

            val localSummaries = players.associate { it.playerId to MutablePlayerGameSummary() }

            gameAssignments.forEach { assignment ->
                val snapshot = metrics[assignment.playerId] ?: return@forEach
                val local = localSummaries[assignment.playerId] ?: return@forEach

                snapshot.totalMinutes += minutesPerRound
                snapshot.groupCounts[assignment.positionGroup] =
                    snapshot.groupCounts.getOrDefault(assignment.positionGroup, 0) + 1
                snapshot.positionCounts[assignment.position] =
                    snapshot.positionCounts.getOrDefault(assignment.position, 0) + 1
                val positionSnapshot = snapshot.positionStats.getOrPut(assignment.position) { MutablePositionMetrics() }
                positionSnapshot.minutesPlayed += minutesPerRound
                snapshot.halfKeysByPosition.getOrPut(assignment.position) { mutableSetOf() }
                    .add("${assignment.gameId}-${assignment.halfNumber}")
                if (assignment.position == FieldPosition.GOALIE && assignment.roundIndex == 1) {
                    snapshot.keeperCount += 1
                }

                local.minutes += minutesPerRound
                local.positions += assignment.position
                local.groups += assignment.positionGroup
                if (assignment.position == FieldPosition.GOALIE) {
                    local.keeperRounds += 1
                }

                val groupSummary = groupSummaries.getValue(assignment.positionGroup)
                groupSummary.totalMinutes += minutesPerRound
                groupSummary.uniquePlayers += assignment.playerId
            }

            gameGoals.forEach { goal ->
                val impactedAssignments = gameAssignments.filter {
                    it.halfNumber == goal.halfNumber && it.roundIndex == goal.roundIndex
                }
                val delta = if (goal.scoredBy == GoalSide.TEAM) 1 else -1

                impactedAssignments.forEach { assignment ->
                    val snapshot = metrics[assignment.playerId] ?: return@forEach
                    val local = localSummaries[assignment.playerId] ?: return@forEach
                    snapshot.scoreDifferential += delta
                    local.differential += delta
                    val positionSnapshot = snapshot.positionStats.getOrPut(assignment.position) { MutablePositionMetrics() }
                    positionSnapshot.scoreDifferential += delta
                }

                impactedAssignments.map { it.positionGroup }.toSet().forEach { group ->
                    groupSummaries.getValue(group).totalDifferential += delta
                }

                if (goal.scoredBy == GoalSide.TEAM && goal.scorerPlayerId != null) {
                    val scorerSnapshot = metrics[goal.scorerPlayerId]
                    val scorerLocal = localSummaries[goal.scorerPlayerId]
                    if (scorerSnapshot != null) {
                        scorerSnapshot.totalGoalsScored += 1
                        if (scorerLocal != null) {
                            scorerLocal.goals += 1
                        }
                        val scorerAssignment = impactedAssignments.firstOrNull { it.playerId == goal.scorerPlayerId }
                        if (scorerAssignment != null) {
                            val positionSnapshot =
                                scorerSnapshot.positionStats.getOrPut(scorerAssignment.position) { MutablePositionMetrics() }
                            positionSnapshot.goalsScored += 1
                            groupSummaries.getValue(scorerAssignment.positionGroup).goalContributions += 1
                        }
                    }
                }

                if (goal.scoredBy == GoalSide.TEAM && goal.assisterPlayerId != null) {
                    val assisterSnapshot = metrics[goal.assisterPlayerId]
                    val assisterLocal = localSummaries[goal.assisterPlayerId]
                    if (assisterSnapshot != null) {
                        assisterSnapshot.totalAssists += 1
                        if (assisterLocal != null) {
                            assisterLocal.assists += 1
                        }
                        val assisterAssignment = impactedAssignments.firstOrNull { it.playerId == goal.assisterPlayerId }
                        if (assisterAssignment != null) {
                            val positionSnapshot =
                                assisterSnapshot.positionStats.getOrPut(assisterAssignment.position) { MutablePositionMetrics() }
                            positionSnapshot.assists += 1
                            groupSummaries.getValue(assisterAssignment.positionGroup).goalContributions += 1
                        }
                    }
                }
            }

            players.forEach { player ->
                val local = localSummaries.getValue(player.playerId)
                playerTrendPoints.getValue(player.playerId) += PlayerTrendPoint(
                    gameId = game.gameId,
                    label = shortGameLabel(game),
                    minutes = local.minutes,
                    goals = local.goals,
                    assists = local.assists,
                    differential = local.differential,
                    uniquePositions = local.positions.size,
                    uniqueGroups = local.groups.size,
                )
            }

            SeasonTrendPoint(
                gameId = game.gameId,
                opponent = game.opponent,
                dateLabel = shortGameLabel(game),
                teamGoals = gameGoalsFor,
                opponentGoals = gameGoalsAgainst,
                differential = gameDifferential,
                resultLabel = when {
                    gameDifferential > 0 -> "W"
                    gameDifferential < 0 -> "L"
                    else -> "D"
                },
                minutesBalanceScore = balanceScore(metrics.values.map { it.totalMinutes }),
                keeperBalanceScore = balanceScore(metrics.values.map { it.keeperCount.toDouble() }),
            )
        }

        val playerMetrics = metrics.map { (playerId, snapshot) ->
            PlayerMetrics(
                playerId = playerId,
                playerName = playerById[playerId]?.name ?: snapshot.playerName,
                totalMinutes = snapshot.totalMinutes,
                totalGoalsScored = snapshot.totalGoalsScored,
                totalAssists = snapshot.totalAssists,
                keeperCount = snapshot.keeperCount,
                groupCounts = snapshot.groupCounts.toMap(),
                positionCounts = snapshot.positionCounts.toMap(),
                scoreDifferentialWhileAssigned = snapshot.scoreDifferential,
                positionStats = FieldPosition.entries.associateWith { position ->
                    val positionSnapshot = snapshot.positionStats[position]
                    PositionStatMetrics(
                        halvesPlayed = snapshot.halfKeysByPosition[position]?.size ?: 0,
                        minutesPlayed = positionSnapshot?.minutesPlayed ?: 0.0,
                        goalsScored = positionSnapshot?.goalsScored ?: 0,
                        assists = positionSnapshot?.assists ?: 0,
                        scoreDifferential = positionSnapshot?.scoreDifferential ?: 0,
                    )
                },
            )
        }.sortedBy { it.playerName }

        val playerDevelopmentSnapshots = playerMetrics.map { playerMetric ->
            val trend = playerTrendPoints[playerMetric.playerId].orEmpty()
            PlayerDevelopmentSnapshot(
                playerId = playerMetric.playerId,
                playerName = playerMetric.playerName,
                totalMinutes = playerMetric.totalMinutes,
                totalGoals = playerMetric.totalGoalsScored,
                totalAssists = playerMetric.totalAssists,
                keeperAppearances = playerMetric.keeperCount,
                uniquePositions = playerMetric.positionCounts.filterValues { it > 0 }.size,
                uniqueGroups = playerMetric.groupCounts.filterValues { it > 0 }.size,
                positionVarietyScore = percentageScore(
                    playerMetric.positionCounts.filterValues { it > 0 }.size,
                    FieldPosition.entries.size,
                ),
                groupVarietyScore = percentageScore(
                    playerMetric.groupCounts.filterValues { it > 0 }.size,
                    PositionGroup.entries.size,
                ),
                trendPoints = trend,
            )
        }

        val fairnessSummary = buildFairnessSummary(playerMetrics)
        val strongestHalf = goalsByHalf.maxByOrNull { (_, score) -> score.first - score.second }?.key

        return TeamMetrics(
            totalGames = sortedGames.size,
            wins = wins,
            draws = draws,
            losses = losses,
            teamGoals = goals.count { it.scoredBy == GoalSide.TEAM },
            opponentGoals = goals.count { it.scoredBy == GoalSide.OPPONENT },
            totalAssists = goals.count { it.scoredBy == GoalSide.TEAM && it.assisterPlayerId != null },
            averageGoalDifferential = if (sortedGames.isEmpty()) {
                0.0
            } else {
                (goals.count { it.scoredBy == GoalSide.TEAM } - goals.count { it.scoredBy == GoalSide.OPPONENT })
                    .toDouble() / sortedGames.size.toDouble()
            },
            strongestHalf = strongestHalf,
            goalsByHalf = goalsByHalf,
            playerMetrics = playerMetrics,
            gameTrendPoints = gameTrendPoints,
            positionGroupSummaries = PositionGroup.entries.map { group ->
                val summary = groupSummaries.getValue(group)
                PositionGroupSeasonSummary(
                    positionGroup = group,
                    totalMinutes = summary.totalMinutes,
                    goalContributions = summary.goalContributions,
                    totalDifferential = summary.totalDifferential,
                    uniquePlayers = summary.uniquePlayers.size,
                )
            },
            fairnessSummary = fairnessSummary,
            playerDevelopmentSnapshots = playerDevelopmentSnapshots.sortedBy { it.playerName },
        )
    }

    fun buildMatchReportAnalytics(
        game: GameEntity,
        players: List<PlayerEntity>,
        assignments: List<AssignmentEntity>,
        goals: List<GoalEventEntity>,
        seasonMetrics: TeamMetrics? = null,
    ): MatchReportAnalytics {
        val playerNames = players.associateBy({ it.playerId }, { it.name })
        val template = game.template()
        val minutesPerRound = template.halfDurationMinutes.toDouble() / template.roundsPerHalf.toDouble()
        val teamGoals = goals.count { it.scoredBy == GoalSide.TEAM }
        val opponentGoals = goals.count { it.scoredBy == GoalSide.OPPONENT }
        val halfScores = (1..template.halfCount).map { half ->
            HalfScoreSummary(
                halfNumber = half,
                teamGoals = goals.count { it.halfNumber == half && it.scoredBy == GoalSide.TEAM },
                opponentGoals = goals.count { it.halfNumber == half && it.scoredBy == GoalSide.OPPONENT },
            )
        }

        val timelineEvents = buildList {
            for (half in 1..template.halfCount) {
                add(MatchTimelineEvent(MatchTimelineKind.HALF_START, "Half $half start", half, 0))
                for (round in 1..template.roundsPerHalf) {
                    val roundStartSeconds = (((round - 1) * template.halfDurationMinutes.toDouble()) / template.roundsPerHalf).roundToInt() * 60
                    add(MatchTimelineEvent(MatchTimelineKind.SUB_ROUND, "Sub $round", half, roundStartSeconds, round))
                }
                goals.filter { it.halfNumber == half }
                    .sortedBy { it.elapsedSecondsInHalf }
                    .forEach { goal ->
                        val scorerLabel = if (goal.scoredBy == GoalSide.TEAM) {
                            val scorer = goal.scorerPlayerId?.let(playerNames::get) ?: "Team goal"
                            val assister = goal.assisterPlayerId?.let(playerNames::get)
                            if (assister != null) "$scorer (Ast $assister)" else scorer
                        } else {
                            "${game.opponent.ifBlank { "Opponent" }} goal"
                        }
                        add(
                            MatchTimelineEvent(
                                kind = if (goal.scoredBy == GoalSide.TEAM) MatchTimelineKind.TEAM_GOAL else MatchTimelineKind.OPPONENT_GOAL,
                                label = scorerLabel,
                                halfNumber = half,
                                elapsedSecondsInHalf = goal.elapsedSecondsInHalf,
                                roundIndex = goal.roundIndex,
                            ),
                        )
                    }
                add(
                    MatchTimelineEvent(
                        MatchTimelineKind.HALF_END,
                        "Half $half end",
                        half,
                        template.halfDurationMinutes * 60,
                    ),
                )
            }
        }.sortedWith(compareBy(MatchTimelineEvent::halfNumber, MatchTimelineEvent::elapsedSecondsInHalf))

        val roundImpactSummaries = (1..template.halfCount).flatMap { half ->
            (1..template.roundsPerHalf).map { round ->
                val roundGoals = goals.filter { it.halfNumber == half && it.roundIndex == round }
                RoundImpactSummary(
                    halfNumber = half,
                    roundIndex = round,
                    goalsFor = roundGoals.count { it.scoredBy == GoalSide.TEAM },
                    goalsAgainst = roundGoals.count { it.scoredBy == GoalSide.OPPONENT },
                    differential = roundGoals.count { it.scoredBy == GoalSide.TEAM } - roundGoals.count { it.scoredBy == GoalSide.OPPONENT },
                )
            }
        }

        val positionGroupSummaries = PositionGroup.entries.map { group ->
            val groupAssignments = assignments.filter { it.positionGroup == group }
            val activeRoundKeys = groupAssignments.map { it.halfNumber to it.roundIndex }.toSet()
            val groupGoals = goals.filter { (it.halfNumber to it.roundIndex) in activeRoundKeys }
            val groupContributions = goals.count { goal ->
                if (goal.scoredBy != GoalSide.TEAM) return@count false
                val scorerAssignment = assignments.firstOrNull {
                    it.halfNumber == goal.halfNumber && it.roundIndex == goal.roundIndex && it.playerId == goal.scorerPlayerId
                }
                val assistAssignment = assignments.firstOrNull {
                    it.halfNumber == goal.halfNumber && it.roundIndex == goal.roundIndex && it.playerId == goal.assisterPlayerId
                }
                scorerAssignment?.positionGroup == group || assistAssignment?.positionGroup == group
            }
            PositionGroupGameSummary(
                positionGroup = group,
                totalMinutes = groupAssignments.size * minutesPerRound,
                goalsFor = groupGoals.count { it.scoredBy == GoalSide.TEAM },
                goalsAgainst = groupGoals.count { it.scoredBy == GoalSide.OPPONENT },
                differential = groupGoals.count { it.scoredBy == GoalSide.TEAM } - groupGoals.count { it.scoredBy == GoalSide.OPPONENT },
                goalContributions = groupContributions,
                playersUsed = groupAssignments.mapNotNull { playerNames[it.playerId] }.distinct(),
            )
        }

        val gameMeanMinutes = if (players.isEmpty()) 0.0 else {
            assignments.size * minutesPerRound / players.size.toDouble()
        }
        val seasonAveragesByPlayer = seasonMetrics?.playerDevelopmentSnapshots
            ?.associate { snapshot ->
                val average = if (snapshot.trendPoints.isEmpty()) 0.0 else snapshot.trendPoints.map { it.minutes }.average()
                snapshot.playerId to average
            }
            .orEmpty()
        val usageByPlayer = players.map { player ->
            val playerAssignments = assignments.filter { it.playerId == player.playerId }
            val playerGoals = goals.count { it.scoredBy == GoalSide.TEAM && it.scorerPlayerId == player.playerId }
            val playerAssists = goals.count { it.scoredBy == GoalSide.TEAM && it.assisterPlayerId == player.playerId }
            val minutes = playerAssignments.size * minutesPerRound
            PlayerUsageSummary(
                playerId = player.playerId,
                playerName = player.name,
                minutes = minutes,
                goals = playerGoals,
                assists = playerAssists,
                keeperRounds = playerAssignments.count { it.position == FieldPosition.GOALIE },
                positions = playerAssignments.map { it.position }.distinct(),
                groups = playerAssignments.map { it.positionGroup }.distinct(),
                fairnessDeltaMinutes = minutes - (seasonAveragesByPlayer[player.playerId] ?: gameMeanMinutes),
            )
        }.sortedByDescending { it.minutes }

        val bestHalf = halfScores.maxByOrNull { it.teamGoals - it.opponentGoals }
        val bestRound = roundImpactSummaries.maxByOrNull { it.differential * 100 + it.goalsFor }
        val goalContributors = usageByPlayer.filter { it.goals + it.assists > 0 }
        val versatilePlayers = usageByPlayer.filter { it.groups.size > 1 || it.positions.size > 2 }
        val fairnessExceptions = usageByPlayer.filter { abs(it.fairnessDeltaMinutes) >= minutesPerRound }

        val takeaways = buildList {
            if (bestHalf != null) {
                add(
                    CoachTakeaway(
                        title = "Strongest half",
                        body = "Half ${bestHalf.halfNumber} finished ${bestHalf.teamGoals}-${bestHalf.opponentGoals}.",
                    ),
                )
            }
            if (bestRound != null && bestRound.differential != 0) {
                add(
                    CoachTakeaway(
                        title = "Best stretch",
                        body = "Half ${bestRound.halfNumber}, round ${bestRound.roundIndex} had a ${formatDifferential(bestRound.differential)} differential.",
                    ),
                )
            }
            if (goalContributors.isNotEmpty()) {
                add(
                    CoachTakeaway(
                        title = "Goal contributions",
                        body = goalContributors.take(4).joinToString { usage ->
                            "${usage.playerName} ${usage.goals}G/${usage.assists}A"
                        },
                    ),
                )
            }
            if (versatilePlayers.isNotEmpty()) {
                add(
                    CoachTakeaway(
                        title = "Positional flexibility",
                        body = versatilePlayers.take(3).joinToString { it.playerName },
                    ),
                )
            }
            if (fairnessExceptions.isNotEmpty()) {
                add(
                    CoachTakeaway(
                        title = "Usage outliers",
                        body = fairnessExceptions.take(3).joinToString {
                            "${it.playerName} ${if (it.fairnessDeltaMinutes > 0) "+" else ""}${"%.1f".format(it.fairnessDeltaMinutes)} min vs norm"
                        },
                    ),
                )
            }
        }

        return MatchReportAnalytics(
            opponent = game.opponent,
            dateLabel = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(Date(game.scheduledAt)),
            location = game.location.ifBlank { "Not set" },
            status = game.status,
            teamGoals = teamGoals,
            opponentGoals = opponentGoals,
            halfScores = halfScores,
            timelineEvents = timelineEvents,
            roundImpactSummaries = roundImpactSummaries,
            positionGroupSummaries = positionGroupSummaries,
            playerUsage = usageByPlayer,
            takeaways = takeaways,
        )
    }

    private fun buildFairnessSummary(playerMetrics: List<PlayerMetrics>): FairnessSummary {
        val minutes = playerMetrics.map { it.totalMinutes }
        val keeperCounts = playerMetrics.map { it.keeperCount.toDouble() }
        val groupExposureValues = playerMetrics.flatMap { player ->
            PositionGroup.entries.map { group -> player.groupCounts[group]?.toDouble() ?: 0.0 }
        }
        val averageMinutes = minutes.average().takeIf { !it.isNaN() } ?: 0.0
        val overused = playerMetrics
            .filter { it.totalMinutes > averageMinutes * 1.2 && averageMinutes > 0 }
            .map { it.playerId }
        val underused = playerMetrics
            .filter { it.totalMinutes < averageMinutes * 0.8 && averageMinutes > 0 }
            .map { it.playerId }

        return FairnessSummary(
            minutesBalanceScore = balanceScore(minutes),
            groupExposureBalanceScore = balanceScore(groupExposureValues),
            keeperBalanceScore = balanceScore(keeperCounts),
            overusedPlayerIds = overused,
            underusedPlayerIds = underused,
        )
    }

    private fun balanceScore(values: List<Double>): Int {
        if (values.isEmpty()) return 100
        val average = values.average()
        if (average == 0.0) return 100
        val variance = values.sumOf { (it - average) * (it - average) } / values.size.toDouble()
        val coefficientOfVariation = sqrt(variance) / average
        return (100.0 - (coefficientOfVariation * 100.0)).roundToInt().coerceIn(0, 100)
    }

    private fun percentageScore(value: Int, maxValue: Int): Int =
        if (maxValue == 0) 0 else ((value.toDouble() / maxValue.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)

    private fun shortGameLabel(game: GameEntity): String =
        "${SimpleDateFormat("MMM d", Locale.US).format(Date(game.scheduledAt))} vs ${game.opponent}"

    private fun formatDifferential(value: Int): String = if (value > 0) "+$value" else value.toString()

    private data class MutablePlayerMetrics(
        val playerName: String,
        var totalMinutes: Double = 0.0,
        var totalGoalsScored: Int = 0,
        var totalAssists: Int = 0,
        var keeperCount: Int = 0,
        val groupCounts: MutableMap<PositionGroup, Int> = mutableMapOf(),
        val positionCounts: MutableMap<FieldPosition, Int> = mutableMapOf(),
        val positionStats: MutableMap<FieldPosition, MutablePositionMetrics> = mutableMapOf(),
        val halfKeysByPosition: MutableMap<FieldPosition, MutableSet<String>> = mutableMapOf(),
        var scoreDifferential: Int = 0,
    )

    private data class MutablePositionMetrics(
        var minutesPlayed: Double = 0.0,
        var goalsScored: Int = 0,
        var assists: Int = 0,
        var scoreDifferential: Int = 0,
    )

    private data class MutablePlayerGameSummary(
        var minutes: Double = 0.0,
        var goals: Int = 0,
        var assists: Int = 0,
        var differential: Int = 0,
        var keeperRounds: Int = 0,
        val positions: MutableSet<FieldPosition> = mutableSetOf(),
        val groups: MutableSet<PositionGroup> = mutableSetOf(),
    )

    private data class MutableGroupSummary(
        var totalMinutes: Double = 0.0,
        var goalContributions: Int = 0,
        var totalDifferential: Int = 0,
        val uniquePlayers: MutableSet<String> = mutableSetOf(),
    )
}
