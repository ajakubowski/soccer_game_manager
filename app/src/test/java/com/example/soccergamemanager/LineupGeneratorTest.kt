package com.example.soccergamemanager

import com.example.soccergamemanager.domain.FieldPosition
import com.example.soccergamemanager.domain.GameTemplateConfig
import com.example.soccergamemanager.domain.LineupGenerator
import com.example.soccergamemanager.domain.LineupPlayer
import com.example.soccergamemanager.domain.ManualGroupLock
import com.example.soccergamemanager.domain.PositionGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineupGeneratorTest {
    private val generator = LineupGenerator()

    @Test
    fun generates_all_rounds_and_positions_for_default_template() {
        val players = (1..11).map { index ->
            LineupPlayer(
                id = "p$index",
                name = "Player $index",
                preferredKeeper = index <= 3,
            )
        }

        val result = generator.generate(GameTemplateConfig.defaultU9(), players, emptyMap())

        assertTrue(result.warnings.isEmpty())
        assertEquals(112, result.assignments.size)
        (1..2).forEach { half ->
            (1..GameTemplateConfig.defaultU9().roundsPerHalf).forEach { round ->
                val roundAssignments = result.assignments.filter { it.halfNumber == half && it.roundIndex == round }
                assertEquals(7, roundAssignments.size)
                assertEquals(1, roundAssignments.count { it.position == FieldPosition.GOALIE })
                assertEquals(7, roundAssignments.map { it.playerId }.distinct().size)
            }
            val keepers = result.assignments
                .filter { it.halfNumber == half && it.position == FieldPosition.GOALIE }
                .map { it.playerId }
                .distinct()
            assertEquals(1, keepers.size)
        }
    }

    @Test
    fun rotates_field_group_between_halves_when_possible() {
        val players = (1..11).map { index ->
            LineupPlayer(
                id = "p$index",
                name = "Player $index",
                preferredKeeper = index <= 2,
            )
        }

        val result = generator.generate(GameTemplateConfig.defaultU9(), players, emptyMap())
        val firstHalfGroup = result.assignments
            .filter { it.halfNumber == 1 }
            .groupBy { it.playerId }
            .mapValues { (_, assignments) -> assignments.first().positionGroup }
        val secondHalfGroup = result.assignments
            .filter { it.halfNumber == 2 }
            .groupBy { it.playerId }
            .mapValues { (_, assignments) -> assignments.first().positionGroup }

        firstHalfGroup.forEach { (playerId, group) ->
            val nextGroup = secondHalfGroup[playerId]
            if (group != PositionGroup.GOALIE && nextGroup != null && nextGroup != PositionGroup.GOALIE) {
                assertNotEquals(group, nextGroup)
            }
        }
    }

    @Test
    fun warns_when_roster_is_too_small_to_fill_three_field_groups() {
        val players = (1..8).map { index ->
            LineupPlayer(
                id = "p$index",
                name = "Player $index",
                preferredKeeper = index == 1,
            )
        }

        val result = generator.generate(GameTemplateConfig.defaultU9(), players, emptyMap())

        assertFalse(result.assignments.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun derives_rounds_per_half_from_half_length_and_sub_window_plus_one_extra() {
        val template = GameTemplateConfig(halfDurationMinutes = 25, substitutionWindowMinutes = 5)

        assertEquals(6, template.roundsPerHalf)
    }

    @Test
    fun respects_manual_goalie_locks_by_half() {
        val players = (1..11).map { index ->
            LineupPlayer(
                id = "p$index",
                name = "Player $index",
                preferredKeeper = index <= 2,
            )
        }

        val result = generator.generate(
            template = GameTemplateConfig.defaultU9(),
            players = players,
            historyByPlayer = emptyMap(),
            manualGroupLocks = listOf(
                ManualGroupLock(halfNumber = 1, positionGroup = PositionGroup.GOALIE, playerIds = listOf("p4")),
                ManualGroupLock(halfNumber = 2, positionGroup = PositionGroup.GOALIE, playerIds = listOf("p5")),
            ),
        )

        assertEquals(
            setOf("p4"),
            result.assignments.filter { it.halfNumber == 1 && it.position == FieldPosition.GOALIE }.map { it.playerId }.toSet(),
        )
        assertEquals(
            setOf("p5"),
            result.assignments.filter { it.halfNumber == 2 && it.position == FieldPosition.GOALIE }.map { it.playerId }.toSet(),
        )
    }

    @Test
    fun ignores_duplicate_manual_lock_across_groups_in_same_half() {
        val players = (1..11).map { index ->
            LineupPlayer(
                id = "p$index",
                name = "Player $index",
                preferredKeeper = index <= 2,
            )
        }

        val result = generator.generate(
            template = GameTemplateConfig.defaultU9(),
            players = players,
            historyByPlayer = emptyMap(),
            manualGroupLocks = listOf(
                ManualGroupLock(halfNumber = 1, positionGroup = PositionGroup.DEFENSE, playerIds = listOf("p3")),
                ManualGroupLock(halfNumber = 1, positionGroup = PositionGroup.LR_MID, playerIds = listOf("p3")),
            ),
        )

        val firstHalfAssignmentsForPlayer = result.assignments
            .filter { it.halfNumber == 1 && it.playerId == "p3" }
            .map { it.positionGroup }
            .toSet()

        assertEquals(setOf(PositionGroup.DEFENSE), firstHalfAssignmentsForPlayer)
        assertTrue(result.warnings.any { it.contains("Ignored duplicate manual lock for Player 3") })
    }

    @Test
    fun variation_seed_can_generate_a_different_valid_lineup() {
        val players = (1..11).map { index ->
            LineupPlayer(
                id = "p$index",
                name = "Player $index",
                preferredKeeper = index <= 3,
            )
        }

        val baseline = generator.generate(
            template = GameTemplateConfig.defaultU9(),
            players = players,
            historyByPlayer = emptyMap(),
            variationSeed = 0,
        )
        val regenerated = generator.generate(
            template = GameTemplateConfig.defaultU9(),
            players = players,
            historyByPlayer = emptyMap(),
            variationSeed = 12345,
        )

        assertEquals(baseline.assignments.size, regenerated.assignments.size)
        assertTrue(
            baseline.assignments.zip(regenerated.assignments).any { (first, second) ->
                first.playerId != second.playerId || first.position != second.position
            },
        )
    }

    @Test
    fun keeps_returning_players_in_same_position_between_rounds() {
        val players = (1..8).map { index ->
            LineupPlayer(
                id = "p$index",
                name = "Player $index",
                preferredKeeper = index == 1,
            )
        }

        val result = generator.generate(GameTemplateConfig.defaultU9(), players, emptyMap())

        (1..2).forEach { half ->
            listOf(PositionGroup.DEFENSE, PositionGroup.LR_MID, PositionGroup.CM_STRIKER).forEach { group ->
                val groupAssignments = result.assignments
                    .filter { it.halfNumber == half && it.positionGroup == group }

                (2..GameTemplateConfig.defaultU9().roundsPerHalf).forEach { round ->
                    val previousRound = groupAssignments
                        .filter { it.roundIndex == round - 1 }
                        .associateBy { it.playerId }
                    val currentRound = groupAssignments
                        .filter { it.roundIndex == round }
                        .associateBy { it.playerId }

                    previousRound.keys
                        .intersect(currentRound.keys)
                        .forEach { playerId ->
                            assertEquals(previousRound.getValue(playerId).position, currentRound.getValue(playerId).position)
                        }
                }
            }
        }
    }
}
