package com.example.soccergamemanager.domain

import kotlinx.serialization.Serializable

@Serializable
enum class PositionGroup(val label: String) {
    DEFENSE("Defense"),
    LR_MID("L/R Mid"),
    CM_STRIKER("CM/Striker"),
    GOALIE("Goalie"),
}

@Serializable
enum class FieldPosition(val label: String, val group: PositionGroup) {
    GOALIE("Goalie", PositionGroup.GOALIE),
    LEFT_DEFENSE("Left Defense", PositionGroup.DEFENSE),
    RIGHT_DEFENSE("Right Defense", PositionGroup.DEFENSE),
    LEFT_MIDFIELDER("Left Midfielder", PositionGroup.LR_MID),
    CENTER_MIDFIELDER("Center Midfielder", PositionGroup.CM_STRIKER),
    RIGHT_MIDFIELDER("Right Midfielder", PositionGroup.LR_MID),
    STRIKER("Striker", PositionGroup.CM_STRIKER),
}

@Serializable
enum class GameStatus {
    PLANNED,
    PREGAME,
    LIVE,
    FINAL,
}

@Serializable
enum class GoalSide {
    TEAM,
    OPPONENT,
}

@Serializable
data class GameTemplateConfig(
    val name: String = "U9 Match",
    val halfCount: Int = 2,
    val halfDurationMinutes: Int = 25,
    val substitutionWindowMinutes: Int = 4,
    val substitutionEventsPerHalf: Int = 3,
    val nextSubAlertSeconds: Int = 60,
    val positions: List<FieldPosition> = DEFAULT_POSITIONS,
) {
    val roundsPerHalf: Int
        get() {
            val minutesPerRound = substitutionWindowMinutes.coerceAtLeast(1)
            val estimatedRounds = (halfDurationMinutes.coerceAtLeast(1) + minutesPerRound - 1) / minutesPerRound
            return estimatedRounds + 1
        }

    companion object {
        val DEFAULT_POSITIONS = listOf(
            FieldPosition.LEFT_DEFENSE,
            FieldPosition.RIGHT_DEFENSE,
            FieldPosition.LEFT_MIDFIELDER,
            FieldPosition.RIGHT_MIDFIELDER,
            FieldPosition.CENTER_MIDFIELDER,
            FieldPosition.STRIKER,
            FieldPosition.GOALIE,
        )

        fun defaultU9() = GameTemplateConfig()
    }
}

data class LineupPlayer(
    val id: String,
    val name: String,
    val preferredKeeper: Boolean,
)

data class PlayerSeasonHistory(
    val minutesPlayed: Double = 0.0,
    val keeperAssignments: Int = 0,
    val groupCounts: Map<PositionGroup, Int> = emptyMap(),
    val positionCounts: Map<FieldPosition, Int> = emptyMap(),
)

data class GeneratedAssignment(
    val halfNumber: Int,
    val roundIndex: Int,
    val playerId: String,
    val position: FieldPosition,
    val positionGroup: PositionGroup,
)

data class LineupGenerationResult(
    val assignments: List<GeneratedAssignment>,
    val warnings: List<String>,
)

data class PlayerMetrics(
    val playerId: String,
    val playerName: String,
    val totalMinutes: Double,
    val keeperCount: Int,
    val groupCounts: Map<PositionGroup, Int>,
    val positionCounts: Map<FieldPosition, Int>,
    val scoreDifferentialWhileAssigned: Int,
)

data class TeamMetrics(
    val totalGames: Int,
    val teamGoals: Int,
    val opponentGoals: Int,
    val goalsByHalf: Map<Int, Pair<Int, Int>>,
    val playerMetrics: List<PlayerMetrics>,
)

data class AssignmentCell(
    val halfNumber: Int,
    val roundIndex: Int,
    val position: FieldPosition,
    val playerName: String,
)

data class PrintableReport(
    val title: String,
    val plainText: String,
    val html: String,
)
