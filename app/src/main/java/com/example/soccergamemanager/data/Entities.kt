package com.example.soccergamemanager.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.soccergamemanager.domain.FieldPosition
import com.example.soccergamemanager.domain.GameStatus
import com.example.soccergamemanager.domain.GameTemplateConfig
import com.example.soccergamemanager.domain.GoalSide
import com.example.soccergamemanager.domain.ManualGroupLock
import com.example.soccergamemanager.domain.PositionGroup
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val appJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Entity(tableName = "seasons")
data class SeasonEntity(
    @PrimaryKey val seasonId: String,
    val name: String,
    val year: Int,
    val defaultTemplateJson: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "players",
    foreignKeys = [
        ForeignKey(
            entity = SeasonEntity::class,
            parentColumns = ["seasonId"],
            childColumns = ["seasonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("seasonId")],
)
data class PlayerEntity(
    @PrimaryKey val playerId: String,
    val seasonId: String,
    val name: String,
    val jerseyNumber: String = "",
    val notes: String = "",
    val preferredKeeper: Boolean = false,
    val active: Boolean = true,
)

@Entity(
    tableName = "games",
    foreignKeys = [
        ForeignKey(
            entity = SeasonEntity::class,
            parentColumns = ["seasonId"],
            childColumns = ["seasonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("seasonId")],
)
data class GameEntity(
    @PrimaryKey val gameId: String,
    val seasonId: String,
    val opponent: String,
    val location: String,
    val scheduledAt: Long,
    val status: GameStatus = GameStatus.PLANNED,
    val templateJson: String,
    val manualGroupLocksJson: String = "[]",
    val plannerNotes: String = "",
    val currentHalf: Int = 1,
    val currentRound: Int = 1,
    val elapsedSecondsInHalf: Int = 0,
    val elapsedSecondsInRound: Int = 0,
    val lockedAt: Long? = null,
    val finalizedAt: Long? = null,
)

@Entity(
    tableName = "player_availability",
    primaryKeys = ["gameId", "playerId"],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["gameId"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["playerId"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playerId")],
)
data class PlayerAvailabilityEntity(
    val gameId: String,
    val playerId: String,
    val isAvailable: Boolean = true,
    val isInjured: Boolean = false,
)

@Entity(
    tableName = "assignments",
    indices = [Index("gameId"), Index("playerId"), Index("halfNumber"), Index("roundIndex")],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["gameId"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["playerId"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AssignmentEntity(
    @PrimaryKey val assignmentId: String,
    val gameId: String,
    val playerId: String,
    val halfNumber: Int,
    val roundIndex: Int,
    val position: FieldPosition,
    val positionGroup: PositionGroup,
)

@Entity(
    tableName = "goal_events",
    indices = [Index("gameId"), Index("halfNumber"), Index("roundIndex")],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["gameId"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GoalEventEntity(
    @PrimaryKey val goalEventId: String,
    val gameId: String,
    val scoredBy: GoalSide,
    val scorerPlayerId: String? = null,
    val assisterPlayerId: String? = null,
    val halfNumber: Int,
    val roundIndex: Int,
    val elapsedSecondsInHalf: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

data class GameDetail(
    val game: GameEntity,
    val players: List<PlayerEntity>,
    val availability: List<PlayerAvailabilityEntity>,
    val assignments: List<AssignmentEntity>,
    val goals: List<GoalEventEntity>,
)

fun SeasonEntity.template(): GameTemplateConfig = appJson.decodeFromString(defaultTemplateJson)

fun GameEntity.template(): GameTemplateConfig = appJson.decodeFromString(templateJson)

fun GameEntity.manualGroupLocks(): List<ManualGroupLock> = appJson.decodeFromString(manualGroupLocksJson)

fun GameTemplateConfig.toJson(): String = appJson.encodeToString(this)

fun List<ManualGroupLock>.toJson(): String = appJson.encodeToString(this)
