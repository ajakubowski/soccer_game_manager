package com.example.soccergamemanager.domain

class LineupGenerator {
    fun generate(
        template: GameTemplateConfig,
        players: List<LineupPlayer>,
        historyByPlayer: Map<String, PlayerSeasonHistory>,
    ): LineupGenerationResult {
        val warnings = mutableListOf<String>()
        if (players.size < template.positions.size) {
            return LineupGenerationResult(
                assignments = emptyList(),
                warnings = listOf("At least ${template.positions.size} available players are needed to fill every position."),
            )
        }

        val halfKeeperSelections = selectKeepers(players, template.halfCount, historyByPlayer)
        val firstHalfGroups = mutableMapOf<String, PositionGroup>()
        val assignments = mutableListOf<GeneratedAssignment>()

        repeat(template.halfCount) { halfOffset ->
            val halfNumber = halfOffset + 1
            val keeper = halfKeeperSelections[halfOffset]
            val fieldPlayers = players.filterNot { it.id == keeper.id }
            val capacities = createGroupCapacities(fieldPlayers.size)
            if (capacities.values.any { it < 3 }) {
                warnings += "Half $halfNumber cannot keep all field groups at three players with the current availability."
            }

            val priorHalfGroups = if (halfNumber == 2) firstHalfGroups else emptyMap()
            val groupedPlayers = assignFieldGroups(
                fieldPlayers = fieldPlayers,
                capacities = capacities,
                priorHalfGroups = priorHalfGroups,
                historyByPlayer = historyByPlayer,
            )

            if (halfNumber == 1) {
                groupedPlayers.forEach { (group, grouped) ->
                    grouped.forEach { player -> firstHalfGroups[player.id] = group }
                }
            }

            for (roundIndex in 1..template.roundsPerHalf) {
                assignments += GeneratedAssignment(
                    halfNumber = halfNumber,
                    roundIndex = roundIndex,
                    playerId = keeper.id,
                    position = FieldPosition.GOALIE,
                    positionGroup = PositionGroup.GOALIE,
                )
            }

            listOf(
                PositionGroup.DEFENSE to listOf(FieldPosition.LEFT_DEFENSE, FieldPosition.RIGHT_DEFENSE),
                PositionGroup.LR_MID to listOf(FieldPosition.LEFT_MIDFIELDER, FieldPosition.RIGHT_MIDFIELDER),
                PositionGroup.CM_STRIKER to listOf(FieldPosition.CENTER_MIDFIELDER, FieldPosition.STRIKER),
            ).forEach { (group, positions) ->
                val groupAssignments = generateGroupAssignments(
                    halfNumber = halfNumber,
                    roundsPerHalf = template.roundsPerHalf,
                    positions = positions,
                    players = groupedPlayers[group].orEmpty(),
                    historyByPlayer = historyByPlayer,
                )
                assignments += groupAssignments
            }
        }

        return LineupGenerationResult(
            assignments = assignments.sortedWith(
                compareBy(
                    GeneratedAssignment::halfNumber,
                    GeneratedAssignment::roundIndex,
                    { template.positions.indexOf(it.position) },
                ),
            ),
            warnings = warnings.distinct(),
        )
    }

    private fun selectKeepers(
        players: List<LineupPlayer>,
        halfCount: Int,
        historyByPlayer: Map<String, PlayerSeasonHistory>,
    ): List<LineupPlayer> {
        val keepers = mutableListOf<LineupPlayer>()
        repeat(halfCount) {
            val pool = players
                .filter { candidate ->
                    keepers.none { it.id == candidate.id } || players.size < halfCount
                }
                .ifEmpty { players }

            val keeper = pool.minWithOrNull(
                compareBy<LineupPlayer>(
                    { if (it.preferredKeeper) 0 else 1 },
                    { historyByPlayer[it.id]?.keeperAssignments ?: 0 },
                    { historyByPlayer[it.id]?.minutesPlayed ?: 0.0 },
                    { it.name },
                ),
            ) ?: players.first()
            keepers += keeper
        }
        return keepers
    }

    private fun createGroupCapacities(fieldPlayerCount: Int): Map<PositionGroup, Int> {
        val fieldGroups = listOf(PositionGroup.DEFENSE, PositionGroup.LR_MID, PositionGroup.CM_STRIKER)
        val base = fieldPlayerCount / fieldGroups.size
        val extra = fieldPlayerCount % fieldGroups.size
        return fieldGroups.mapIndexed { index, group ->
            group to (base + if (index < extra) 1 else 0)
        }.toMap()
    }

    private fun assignFieldGroups(
        fieldPlayers: List<LineupPlayer>,
        capacities: Map<PositionGroup, Int>,
        priorHalfGroups: Map<String, PositionGroup>,
        historyByPlayer: Map<String, PlayerSeasonHistory>,
    ): Map<PositionGroup, List<LineupPlayer>> {
        val result = mutableMapOf(
            PositionGroup.DEFENSE to mutableListOf<LineupPlayer>(),
            PositionGroup.LR_MID to mutableListOf(),
            PositionGroup.CM_STRIKER to mutableListOf(),
        )
        val remaining = capacities.toMutableMap()
        val playerOrder = fieldPlayers.sortedWith(
            compareBy<LineupPlayer>(
                { candidate ->
                    listOf(PositionGroup.DEFENSE, PositionGroup.LR_MID, PositionGroup.CM_STRIKER)
                        .count { group ->
                            remaining.getOrDefault(group, 0) > 0 && priorHalfGroups[candidate.id] != group
                        }
                },
                { historyByPlayer[it.id]?.minutesPlayed ?: 0.0 },
                { it.name },
            ),
        )

        playerOrder.forEach { player ->
            val allowedGroups = listOf(PositionGroup.DEFENSE, PositionGroup.LR_MID, PositionGroup.CM_STRIKER)
                .filter { group ->
                    remaining.getOrDefault(group, 0) > 0 && priorHalfGroups[player.id] != group
                }
                .ifEmpty {
                    listOf(PositionGroup.DEFENSE, PositionGroup.LR_MID, PositionGroup.CM_STRIKER)
                        .filter { remaining.getOrDefault(it, 0) > 0 }
                }

            val selectedGroup = allowedGroups.minWithOrNull(
                compareBy<PositionGroup>(
                    { historyByPlayer[player.id]?.groupCounts?.get(it) ?: 0 },
                    { result.getValue(it).size },
                    { it.ordinal },
                ),
            ) ?: PositionGroup.DEFENSE

            result.getValue(selectedGroup) += player
            remaining[selectedGroup] = remaining.getOrDefault(selectedGroup, 1) - 1
        }

        return result
    }

    private fun generateGroupAssignments(
        halfNumber: Int,
        roundsPerHalf: Int,
        positions: List<FieldPosition>,
        players: List<LineupPlayer>,
        historyByPlayer: Map<String, PlayerSeasonHistory>,
    ): List<GeneratedAssignment> {
        if (players.isEmpty()) return emptyList()
        val slotCounts = players.associate { it.id to 0 }.toMutableMap()
        val positionCounts = mutableMapOf<Pair<String, FieldPosition>, Int>()
        val assignments = mutableListOf<GeneratedAssignment>()
        var previousAssignmentsByPosition = emptyMap<FieldPosition, String>()

        for (roundIndex in 1..roundsPerHalf) {
            val selectedPlayers = players
                .sortedWith(
                    compareBy<LineupPlayer>(
                        { slotCounts[it.id] ?: 0 },
                        { historyByPlayer[it.id]?.minutesPlayed ?: 0.0 },
                        { it.name },
                    ),
                )
                .take(positions.size.coerceAtMost(players.size))
                .toMutableList()

            selectedPlayers.forEach { player ->
                slotCounts[player.id] = slotCounts.getOrDefault(player.id, 0) + 1
            }

            val remainingPlayers = selectedPlayers.toMutableList()
            val currentAssignmentsByPosition = mutableMapOf<FieldPosition, String>()

            // If a player is staying on the field between rounds, keep them in the same exact
            // position and only use the open positions for the actual subs coming in.
            previousAssignmentsByPosition.forEach { (position, playerId) ->
                if (position !in positions) return@forEach
                val stayingPlayer = remainingPlayers.firstOrNull { it.id == playerId } ?: return@forEach
                currentAssignmentsByPosition[position] = stayingPlayer.id
                remainingPlayers.remove(stayingPlayer)
            }

            positions
                .filterNot { it in currentAssignmentsByPosition }
                .forEach { position ->
                    val player = remainingPlayers.minWithOrNull(
                    compareBy<LineupPlayer>(
                        {
                            (historyByPlayer[it.id]?.positionCounts?.get(position) ?: 0) +
                                positionCounts.getOrDefault(it.id to position, 0)
                        },
                        { slotCounts[it.id] ?: 0 },
                        { it.name },
                    ),
                ) ?: return@forEach

                    currentAssignmentsByPosition[position] = player.id
                    remainingPlayers.remove(player)
                }

            positions.forEach { position ->
                val playerId = currentAssignmentsByPosition[position] ?: return@forEach
                positionCounts[playerId to position] = positionCounts.getOrDefault(playerId to position, 0) + 1
                assignments += GeneratedAssignment(
                    halfNumber = halfNumber,
                    roundIndex = roundIndex,
                    playerId = playerId,
                    position = position,
                    positionGroup = position.group,
                )
            }
            previousAssignmentsByPosition = positions.associateWith { position ->
                currentAssignmentsByPosition.getValue(position)
            }
        }

        return assignments
    }
}
