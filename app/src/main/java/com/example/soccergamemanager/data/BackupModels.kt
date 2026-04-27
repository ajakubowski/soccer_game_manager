package com.example.soccergamemanager.data

import kotlinx.serialization.Serializable

@Serializable
data class SoccerDataBackup(
    val backupVersion: Int = CURRENT_BACKUP_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val seasons: List<SeasonEntity> = emptyList(),
    val players: List<PlayerEntity> = emptyList(),
    val games: List<GameEntity> = emptyList(),
    val availability: List<PlayerAvailabilityEntity> = emptyList(),
    val assignments: List<AssignmentEntity> = emptyList(),
    val goals: List<GoalEventEntity> = emptyList(),
) {
    companion object {
        const val CURRENT_BACKUP_VERSION = 1
    }
}
