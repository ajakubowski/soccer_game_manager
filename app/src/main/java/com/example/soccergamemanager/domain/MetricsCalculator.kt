package com.example.soccergamemanager.domain

import com.example.soccergamemanager.data.AssignmentEntity
import com.example.soccergamemanager.data.GameEntity
import com.example.soccergamemanager.data.GoalEventEntity
import com.example.soccergamemanager.data.PlayerEntity
import com.example.soccergamemanager.data.template

class MetricsCalculator {
    fun calculate(
        players: List<PlayerEntity>,
        finalizedGames: List<GameEntity>,
        assignments: List<AssignmentEntity>,
        goals: List<GoalEventEntity>,
    ): TeamMetrics {
        val playerById = players.associateBy { it.playerId }
        val metrics = players.associate { player ->
            player.playerId to MutablePlayerMetrics(playerName = player.name)
        }

        finalizedGames.forEach { game ->
            val template = game.template()
            val gameAssignments = assignments.filter { it.gameId == game.gameId }
            val gameGoals = goals.filter { it.gameId == game.gameId }
            val minutesPerRound = template.halfDurationMinutes.toDouble() / template.roundsPerHalf.toDouble()

            gameAssignments.forEach { assignment ->
                val snapshot = metrics[assignment.playerId] ?: return@forEach
                snapshot.totalMinutes += minutesPerRound
                snapshot.groupCounts[assignment.positionGroup] =
                    snapshot.groupCounts.getOrDefault(assignment.positionGroup, 0) + 1
                snapshot.positionCounts[assignment.position] =
                    snapshot.positionCounts.getOrDefault(assignment.position, 0) + 1
                if (assignment.position == FieldPosition.GOALIE && assignment.roundIndex == 1) {
                    snapshot.keeperCount += 1
                }
            }

            gameGoals.forEach { goal ->
                val impactedAssignments = gameAssignments.filter {
                    it.halfNumber == goal.halfNumber && it.roundIndex == goal.roundIndex
                }
                val delta = if (goal.scoredBy == GoalSide.TEAM) 1 else -1
                impactedAssignments.forEach { assignment ->
                    metrics[assignment.playerId]?.scoreDifferential =
                        metrics[assignment.playerId]?.scoreDifferential?.plus(delta) ?: delta
                }
            }
        }

        val goalsByHalf = goals.groupBy { it.halfNumber }.mapValues { (_, halfGoals) ->
            halfGoals.count { it.scoredBy == GoalSide.TEAM } to halfGoals.count { it.scoredBy == GoalSide.OPPONENT }
        }

        return TeamMetrics(
            totalGames = finalizedGames.size,
            teamGoals = goals.count { it.scoredBy == GoalSide.TEAM },
            opponentGoals = goals.count { it.scoredBy == GoalSide.OPPONENT },
            goalsByHalf = goalsByHalf,
            playerMetrics = metrics.map { (playerId, snapshot) ->
                PlayerMetrics(
                    playerId = playerId,
                    playerName = playerById[playerId]?.name ?: snapshot.playerName,
                    totalMinutes = snapshot.totalMinutes,
                    keeperCount = snapshot.keeperCount,
                    groupCounts = snapshot.groupCounts.toMap(),
                    positionCounts = snapshot.positionCounts.toMap(),
                    scoreDifferentialWhileAssigned = snapshot.scoreDifferential,
                )
            }.sortedBy { it.playerName },
        )
    }

    private data class MutablePlayerMetrics(
        val playerName: String,
        var totalMinutes: Double = 0.0,
        var keeperCount: Int = 0,
        val groupCounts: MutableMap<PositionGroup, Int> = mutableMapOf(),
        val positionCounts: MutableMap<FieldPosition, Int> = mutableMapOf(),
        var scoreDifferential: Int = 0,
    )
}
