package com.example.soccergamemanager

import com.example.soccergamemanager.data.AssignmentEntity
import com.example.soccergamemanager.data.GameEntity
import com.example.soccergamemanager.data.GoalEventEntity
import com.example.soccergamemanager.data.PlayerEntity
import com.example.soccergamemanager.data.toJson
import com.example.soccergamemanager.domain.FieldPosition
import com.example.soccergamemanager.domain.GameStatus
import com.example.soccergamemanager.domain.GameTemplateConfig
import com.example.soccergamemanager.domain.GoalSide
import com.example.soccergamemanager.domain.MetricsCalculator
import com.example.soccergamemanager.domain.PositionGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsCalculatorTest {
    private val calculator = MetricsCalculator()

    @Test
    fun calculates_minutes_keeper_counts_and_score_differential() {
        val template = GameTemplateConfig(halfDurationMinutes = 20, substitutionEventsPerHalf = 1)
        val players = listOf(
            PlayerEntity("p1", "s1", "Alex", preferredKeeper = true),
            PlayerEntity("p2", "s1", "Blake"),
            PlayerEntity("p3", "s1", "Casey"),
        )
        val game = GameEntity(
            gameId = "g1",
            seasonId = "s1",
            opponent = "Rivals",
            location = "Field 1",
            scheduledAt = 0L,
            status = GameStatus.FINAL,
            templateJson = template.toJson(),
        )
        val assignments = listOf(
            AssignmentEntity("a1", "g1", "p1", 1, 1, FieldPosition.GOALIE, PositionGroup.GOALIE),
            AssignmentEntity("a2", "g1", "p2", 1, 1, FieldPosition.LEFT_DEFENSE, PositionGroup.DEFENSE),
            AssignmentEntity("a3", "g1", "p3", 1, 1, FieldPosition.RIGHT_DEFENSE, PositionGroup.DEFENSE),
            AssignmentEntity("a4", "g1", "p1", 1, 2, FieldPosition.GOALIE, PositionGroup.GOALIE),
            AssignmentEntity("a5", "g1", "p2", 1, 2, FieldPosition.LEFT_DEFENSE, PositionGroup.DEFENSE),
            AssignmentEntity("a6", "g1", "p3", 1, 2, FieldPosition.RIGHT_DEFENSE, PositionGroup.DEFENSE),
        )
        val goals = listOf(
            GoalEventEntity("goal1", "g1", GoalSide.TEAM, "p2", "p3", 1, 1, 120),
            GoalEventEntity("goal2", "g1", GoalSide.OPPONENT, null, null, 1, 2, 540),
        )

        val metrics = calculator.calculate(players, listOf(game), assignments, goals)
        val alex = metrics.playerMetrics.first { it.playerId == "p1" }

        assertEquals(1, metrics.totalGames)
        assertEquals(1, metrics.teamGoals)
        assertEquals(1, metrics.opponentGoals)
        assertEquals(1, alex.keeperCount)
        assertTrue(alex.totalMinutes > 0)
        assertEquals(0, alex.scoreDifferentialWhileAssigned)
        assertEquals(1, metrics.playerMetrics.first { it.playerId == "p2" }.totalGoalsScored)
        assertEquals(1, metrics.playerMetrics.first { it.playerId == "p3" }.totalAssists)
    }
}
