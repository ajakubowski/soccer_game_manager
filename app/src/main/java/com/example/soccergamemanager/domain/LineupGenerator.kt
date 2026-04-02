package com.example.soccergamemanager.domain

class LineupGenerator {
    fun generate(
        template: GameTemplateConfig,
        players: List<LineupPlayer>,
        historyByPlayer: Map<String, PlayerSeasonHistory>,
        manualGroupLocks: List<ManualGroupLock> = emptyList(),
    ): LineupGenerationResult {
        val warnings = mutableListOf<String>()
        if (players.size < template.positions.size) {
            return LineupGenerationResult(
                assignments = emptyList(),
                warnings = listOf("At least ${template.positions.size} available players are needed to fill every position."),
            )
        }

        val playerById = players.associateBy { it.id }
        val sanitizedLocks = sanitizeManualLocks(
            template = template,
            manualGroupLocks = manualGroupLocks,
            playerById = playerById,
        )
        warnings += sanitizedLocks.warnings

        val halfKeeperSelections = selectKeepers(
            players = players,
            halfCount = template.halfCount,
            historyByPlayer = historyByPlayer,
            manualLocksByHalfGroup = sanitizedLocks.locksByHalfGroup,
        )
        val firstHalfGroups = mutableMapOf<String, PositionGroup>()
        val assignments = mutableListOf<GeneratedAssignment>()

        repeat(template.halfCount) { halfOffset ->
            val halfNumber = halfOffset + 1
            val keeper = halfKeeperSelections[halfOffset]
            val locksForHalf = sanitizedLocks.locksByHalfGroup[halfNumber].orEmpty()
            val lockedFieldPlayersByGroup = listOf(
                PositionGroup.DEFENSE,
                PositionGroup.LR_MID,
                PositionGroup.CM_STRIKER,
            ).associateWith { group ->
                locksForHalf[group].orEmpty().filterNot { it.id == keeper.id }
            }
            val fieldPlayers = players.filterNot { it.id == keeper.id }
            val capacities = createGroupCapacities(
                fieldPlayerCount = fieldPlayers.size,
                lockedCounts = lockedFieldPlayersByGroup.mapValues { it.value.size },
            )
            if (lockedFieldPlayersByGroup.values.sumOf { it.size } > fieldPlayers.size) {
                warnings += "Half $halfNumber has more locked field players than available field spots."
            }
            if (capacities.values.any { it < 3 }) {
                warnings += "Half $halfNumber cannot keep all field groups at three players with the current availability."
            }

            val priorHalfGroups = if (halfNumber == 2) firstHalfGroups else emptyMap()
            if (halfNumber == 2) {
                lockedFieldPlayersByGroup.forEach { (group, groupedPlayers) ->
                    groupedPlayers.forEach { player ->
                        if (priorHalfGroups[player.id] == group) {
                            warnings += "${player.name} is manually locked into ${group.label} for both halves."
                        }
                    }
                }
            }
            val groupedPlayers = assignFieldGroups(
                fieldPlayers = fieldPlayers,
                capacities = capacities,
                lockedPlayersByGroup = lockedFieldPlayersByGroup,
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
        manualLocksByHalfGroup: Map<Int, Map<PositionGroup, List<LineupPlayer>>>,
    ): List<LineupPlayer> {
        val keepers = mutableListOf<LineupPlayer>()
        repeat(halfCount) {
            val halfNumber = it + 1
            val lockedKeeper = manualLocksByHalfGroup[halfNumber]?.get(PositionGroup.GOALIE)?.firstOrNull()
            if (lockedKeeper != null) {
                keepers += lockedKeeper
                return@repeat
            }
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

    private fun createGroupCapacities(
        fieldPlayerCount: Int,
        lockedCounts: Map<PositionGroup, Int>,
    ): Map<PositionGroup, Int> {
        val fieldGroups = listOf(PositionGroup.DEFENSE, PositionGroup.LR_MID, PositionGroup.CM_STRIKER)
        val base = fieldPlayerCount / fieldGroups.size
        val extra = fieldPlayerCount % fieldGroups.size
        val capacities = fieldGroups
            .associateWith { group ->
                val index = fieldGroups.indexOf(group)
                base + if (index < extra) 1 else 0
            }
            .toMutableMap()

        fieldGroups.forEach { group ->
            val lockedCount = lockedCounts.getOrDefault(group, 0)
            var deficit = lockedCount - capacities.getOrDefault(group, 0)
            if (deficit <= 0) return@forEach

            capacities[group] = lockedCount
            val donorGroups = fieldGroups
                .filter { it != group }
                .sortedByDescending { capacities.getOrDefault(it, 0) - lockedCounts.getOrDefault(it, 0) }

            donorGroups.forEach { donor ->
                while (deficit > 0 && capacities.getOrDefault(donor, 0) > lockedCounts.getOrDefault(donor, 0)) {
                    capacities[donor] = capacities.getOrDefault(donor, 0) - 1
                    deficit -= 1
                }
            }
        }

        return capacities
    }

    private fun assignFieldGroups(
        fieldPlayers: List<LineupPlayer>,
        capacities: Map<PositionGroup, Int>,
        lockedPlayersByGroup: Map<PositionGroup, List<LineupPlayer>>,
        priorHalfGroups: Map<String, PositionGroup>,
        historyByPlayer: Map<String, PlayerSeasonHistory>,
    ): Map<PositionGroup, List<LineupPlayer>> {
        val result = mutableMapOf(
            PositionGroup.DEFENSE to lockedPlayersByGroup[PositionGroup.DEFENSE].orEmpty().toMutableList(),
            PositionGroup.LR_MID to lockedPlayersByGroup[PositionGroup.LR_MID].orEmpty().toMutableList(),
            PositionGroup.CM_STRIKER to lockedPlayersByGroup[PositionGroup.CM_STRIKER].orEmpty().toMutableList(),
        )
        val remaining = capacities
            .mapValues { (group, capacity) -> capacity - result.getValue(group).size }
            .toMutableMap()
        val lockedPlayerIds = lockedPlayersByGroup.values.flatten().map { it.id }.toSet()
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

        playerOrder
            .filterNot { it.id in lockedPlayerIds }
            .forEach { player ->
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

    private fun sanitizeManualLocks(
        template: GameTemplateConfig,
        manualGroupLocks: List<ManualGroupLock>,
        playerById: Map<String, LineupPlayer>,
    ): SanitizedLocks {
        val warnings = mutableListOf<String>()
        val locksByHalfGroup = mutableMapOf<Int, MutableMap<PositionGroup, List<LineupPlayer>>>()

        manualGroupLocks
            .groupBy { it.halfNumber }
            .forEach { (halfNumber, halfLocks) ->
                if (halfNumber !in 1..template.halfCount) {
                    warnings += "Ignored manual locks for half $halfNumber because that half does not exist."
                    return@forEach
                }
                val takenPlayerIds = mutableSetOf<String>()
                val groupsForHalf = mutableMapOf<PositionGroup, List<LineupPlayer>>()

                PositionGroup.entries.forEach { group ->
                    val requestedIds = halfLocks
                        .filter { it.positionGroup == group }
                        .flatMap { it.playerIds }
                        .distinct()

                    val selectedPlayers = mutableListOf<LineupPlayer>()
                    requestedIds.forEach { playerId ->
                        val player = playerById[playerId]
                        if (player == null) {
                            warnings += "Ignored a manual lock for an unavailable player in half $halfNumber ${group.label}."
                            return@forEach
                        }
                        if (!takenPlayerIds.add(playerId)) {
                            warnings += "Ignored duplicate manual lock for ${player.name} in half $halfNumber."
                            return@forEach
                        }
                        selectedPlayers += player
                    }

                    val normalizedPlayers = if (group == PositionGroup.GOALIE && selectedPlayers.size > 1) {
                        warnings += "Half $halfNumber goalie lock only supports one player. Keeping ${selectedPlayers.first().name}."
                        selectedPlayers.take(1)
                    } else {
                        selectedPlayers
                    }

                    if (normalizedPlayers.isNotEmpty()) {
                        groupsForHalf[group] = normalizedPlayers
                    }
                }

                locksByHalfGroup[halfNumber] = groupsForHalf
            }

        return SanitizedLocks(
            locksByHalfGroup = locksByHalfGroup,
            warnings = warnings,
        )
    }

    private data class SanitizedLocks(
        val locksByHalfGroup: Map<Int, Map<PositionGroup, List<LineupPlayer>>>,
        val warnings: List<String>,
    )

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
