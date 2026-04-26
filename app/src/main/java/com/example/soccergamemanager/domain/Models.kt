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
    val availableHalfNumbers: Set<Int> = setOf(1, 2),
)

data class PlayerSeasonHistory(
    val minutesPlayed: Double = 0.0,
    val keeperAssignments: Int = 0,
    val groupCounts: Map<PositionGroup, Int> = emptyMap(),
    val positionCounts: Map<FieldPosition, Int> = emptyMap(),
)

@Serializable
data class ManualGroupLock(
    val halfNumber: Int,
    val positionGroup: PositionGroup,
    val playerIds: List<String> = emptyList(),
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
    val totalGoalsScored: Int,
    val totalAssists: Int,
    val keeperCount: Int,
    val groupCounts: Map<PositionGroup, Int>,
    val positionCounts: Map<FieldPosition, Int>,
    val scoreDifferentialWhileAssigned: Int,
    val positionStats: Map<FieldPosition, PositionStatMetrics>,
)

data class PositionStatMetrics(
    val halvesPlayed: Int = 0,
    val minutesPlayed: Double = 0.0,
    val goalsScored: Int = 0,
    val assists: Int = 0,
    val scoreDifferential: Int = 0,
)

data class TeamMetrics(
    val totalGames: Int,
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
    val teamGoals: Int,
    val opponentGoals: Int,
    val totalAssists: Int = 0,
    val averageGoalDifferential: Double = 0.0,
    val strongestHalf: Int? = null,
    val goalsByHalf: Map<Int, Pair<Int, Int>>,
    val playerMetrics: List<PlayerMetrics>,
    val gameTrendPoints: List<SeasonTrendPoint> = emptyList(),
    val positionGroupSummaries: List<PositionGroupSeasonSummary> = emptyList(),
    val fairnessSummary: FairnessSummary = FairnessSummary(),
    val playerDevelopmentSnapshots: List<PlayerDevelopmentSnapshot> = emptyList(),
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
    val analytics: MatchReportAnalytics? = null,
)

data class SeasonTrendPoint(
    val gameId: String,
    val opponent: String,
    val dateLabel: String,
    val teamGoals: Int,
    val opponentGoals: Int,
    val differential: Int,
    val resultLabel: String,
    val minutesBalanceScore: Int,
    val keeperBalanceScore: Int,
)

data class PositionGroupSeasonSummary(
    val positionGroup: PositionGroup,
    val totalMinutes: Double,
    val goalContributions: Int,
    val totalDifferential: Int,
    val uniquePlayers: Int,
)

data class FairnessSummary(
    val minutesBalanceScore: Int = 100,
    val groupExposureBalanceScore: Int = 100,
    val keeperBalanceScore: Int = 100,
    val overusedPlayerIds: List<String> = emptyList(),
    val underusedPlayerIds: List<String> = emptyList(),
)

data class PlayerTrendPoint(
    val gameId: String,
    val label: String,
    val minutes: Double,
    val goals: Int,
    val assists: Int,
    val differential: Int,
    val uniquePositions: Int,
    val uniqueGroups: Int,
)

data class PlayerDevelopmentSnapshot(
    val playerId: String,
    val playerName: String,
    val totalMinutes: Double,
    val totalGoals: Int,
    val totalAssists: Int,
    val keeperAppearances: Int,
    val uniquePositions: Int,
    val uniqueGroups: Int,
    val positionVarietyScore: Int,
    val groupVarietyScore: Int,
    val trendPoints: List<PlayerTrendPoint> = emptyList(),
)

data class HalfScoreSummary(
    val halfNumber: Int,
    val teamGoals: Int,
    val opponentGoals: Int,
)

enum class MatchTimelineKind {
    TEAM_GOAL,
    OPPONENT_GOAL,
    SUB_ROUND,
    HALF_START,
    HALF_END,
}

data class MatchTimelineEvent(
    val kind: MatchTimelineKind,
    val label: String,
    val halfNumber: Int,
    val elapsedSecondsInHalf: Int,
    val roundIndex: Int? = null,
)

data class RoundImpactSummary(
    val halfNumber: Int,
    val roundIndex: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val differential: Int,
)

data class PositionGroupGameSummary(
    val positionGroup: PositionGroup,
    val totalMinutes: Double,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val differential: Int,
    val goalContributions: Int,
    val playersUsed: List<String>,
)

data class PlayerUsageSummary(
    val playerId: String,
    val playerName: String,
    val minutes: Double,
    val goals: Int,
    val assists: Int,
    val keeperRounds: Int,
    val positions: List<FieldPosition>,
    val groups: List<PositionGroup>,
    val fairnessDeltaMinutes: Double,
)

data class CoachTakeaway(
    val title: String,
    val body: String,
)

data class MatchReportAnalytics(
    val opponent: String,
    val dateLabel: String,
    val location: String,
    val status: GameStatus,
    val teamGoals: Int,
    val opponentGoals: Int,
    val halfScores: List<HalfScoreSummary>,
    val timelineEvents: List<MatchTimelineEvent>,
    val roundImpactSummaries: List<RoundImpactSummary>,
    val positionGroupSummaries: List<PositionGroupGameSummary>,
    val playerUsage: List<PlayerUsageSummary>,
    val takeaways: List<CoachTakeaway>,
)
