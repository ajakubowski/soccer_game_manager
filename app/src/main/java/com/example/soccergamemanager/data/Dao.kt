package com.example.soccergamemanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.soccergamemanager.domain.FieldPosition
import kotlinx.coroutines.flow.Flow

data class GameAssignmentCount(
    val gameId: String,
    val assignmentCount: Int,
)

@Dao
interface SeasonDao {
    @Query("SELECT * FROM seasons ORDER BY year DESC, createdAt DESC")
    fun observeSeasons(): Flow<List<SeasonEntity>>

    @Query("SELECT * FROM seasons ORDER BY year DESC, createdAt DESC")
    suspend fun getAllSeasons(): List<SeasonEntity>

    @Query("SELECT * FROM seasons WHERE seasonId = :seasonId LIMIT 1")
    suspend fun getSeason(seasonId: String): SeasonEntity?

    @Query("SELECT COUNT(*) FROM seasons")
    suspend fun countSeasons(): Int

    @Upsert
    suspend fun insertSeason(season: SeasonEntity)

    @Upsert
    suspend fun insertSeasons(seasons: List<SeasonEntity>)

    @Update
    suspend fun updateSeason(season: SeasonEntity)

    @Delete
    suspend fun deleteSeason(season: SeasonEntity)
}

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players WHERE seasonId = :seasonId ORDER BY name ASC")
    fun observePlayersBySeason(seasonId: String): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE seasonId = :seasonId ORDER BY name ASC")
    suspend fun getPlayersBySeason(seasonId: String): List<PlayerEntity>

    @Query("SELECT * FROM players ORDER BY seasonId, name ASC")
    suspend fun getAllPlayers(): List<PlayerEntity>

    @Upsert
    suspend fun insertPlayer(player: PlayerEntity)

    @Upsert
    suspend fun insertPlayers(players: List<PlayerEntity>)

    @Update
    suspend fun updatePlayer(player: PlayerEntity)

    @Query("DELETE FROM players WHERE playerId = :playerId")
    suspend fun deleteById(playerId: String)
}

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE seasonId = :seasonId ORDER BY scheduledAt DESC")
    fun observeGamesBySeason(seasonId: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE gameId = :gameId LIMIT 1")
    fun observeGame(gameId: String): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE gameId = :gameId LIMIT 1")
    suspend fun getGame(gameId: String): GameEntity?

    @Query("SELECT * FROM games ORDER BY seasonId, scheduledAt DESC")
    suspend fun getAllGames(): List<GameEntity>

    @Query("SELECT * FROM games WHERE seasonId = :seasonId AND status = 'FINAL'")
    suspend fun getFinalizedGamesBySeason(seasonId: String): List<GameEntity>

    @Upsert
    suspend fun insertGame(game: GameEntity)

    @Upsert
    suspend fun insertGames(games: List<GameEntity>)

    @Update
    suspend fun updateGame(game: GameEntity)

    @Delete
    suspend fun deleteGame(game: GameEntity)

    @Query("DELETE FROM games WHERE gameId = :gameId")
    suspend fun deleteById(gameId: String)
}

@Dao
interface AvailabilityDao {
    @Query("SELECT * FROM player_availability WHERE gameId = :gameId")
    fun observeByGame(gameId: String): Flow<List<PlayerAvailabilityEntity>>

    @Query("SELECT * FROM player_availability WHERE gameId = :gameId")
    suspend fun getByGame(gameId: String): List<PlayerAvailabilityEntity>

    @Query("SELECT * FROM player_availability")
    suspend fun getAll(): List<PlayerAvailabilityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PlayerAvailabilityEntity>)

    @Query("DELETE FROM player_availability WHERE gameId = :gameId AND playerId = :playerId")
    suspend fun deleteById(gameId: String, playerId: String)
}

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignments WHERE gameId = :gameId ORDER BY halfNumber, roundIndex")
    fun observeByGame(gameId: String): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments WHERE gameId = :gameId ORDER BY halfNumber, roundIndex")
    suspend fun getByGame(gameId: String): List<AssignmentEntity>

    @Query("SELECT * FROM assignments ORDER BY gameId, halfNumber, roundIndex")
    suspend fun getAll(): List<AssignmentEntity>

    @Query("SELECT * FROM assignments WHERE assignmentId = :assignmentId LIMIT 1")
    suspend fun getAssignment(assignmentId: String): AssignmentEntity?

    @Query(
        "SELECT * FROM assignments WHERE gameId = :gameId AND halfNumber = :halfNumber AND roundIndex = :roundIndex",
    )
    suspend fun getByRound(gameId: String, halfNumber: Int, roundIndex: Int): List<AssignmentEntity>

    @Query("DELETE FROM assignments WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: String)

    @Query(
        """
        DELETE FROM assignments
        WHERE gameId = :gameId
            AND halfNumber = :halfNumber
            AND position = :position
            AND roundIndex BETWEEN :startRound AND :endRound
        """,
    )
    suspend fun deletePositionRange(
        gameId: String,
        halfNumber: Int,
        position: FieldPosition,
        startRound: Int,
        endRound: Int,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AssignmentEntity>)

    @Update
    suspend fun updateAssignments(items: List<AssignmentEntity>)

    @Query("DELETE FROM assignments WHERE assignmentId = :assignmentId")
    suspend fun deleteById(assignmentId: String)

    @Query(
        """
        SELECT assignments.* FROM assignments
        INNER JOIN games ON assignments.gameId = games.gameId
        WHERE games.seasonId = :seasonId AND games.status = 'FINAL'
        """,
    )
    suspend fun getFinalizedAssignmentsBySeason(seasonId: String): List<AssignmentEntity>

    @Query(
        """
        SELECT assignments.gameId AS gameId, COUNT(assignments.assignmentId) AS assignmentCount
        FROM assignments
        INNER JOIN games ON assignments.gameId = games.gameId
        WHERE games.seasonId = :seasonId
        GROUP BY assignments.gameId
        """,
    )
    fun observeAssignmentCountsBySeason(seasonId: String): Flow<List<GameAssignmentCount>>
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goal_events WHERE gameId = :gameId ORDER BY createdAt ASC")
    fun observeByGame(gameId: String): Flow<List<GoalEventEntity>>

    @Query("SELECT * FROM goal_events WHERE gameId = :gameId ORDER BY createdAt ASC")
    suspend fun getByGame(gameId: String): List<GoalEventEntity>

    @Query("SELECT * FROM goal_events WHERE goalEventId = :goalEventId LIMIT 1")
    suspend fun getGoal(goalEventId: String): GoalEventEntity?

    @Query("SELECT * FROM goal_events ORDER BY gameId, createdAt ASC")
    suspend fun getAll(): List<GoalEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(goals: List<GoalEventEntity>)

    @Update
    suspend fun updateGoal(goal: GoalEventEntity)

    @Delete
    suspend fun deleteGoal(goal: GoalEventEntity)

    @Query("DELETE FROM goal_events WHERE goalEventId = :goalEventId")
    suspend fun deleteById(goalEventId: String)

    @Query(
        """
        SELECT goal_events.* FROM goal_events
        INNER JOIN games ON goal_events.gameId = games.gameId
        WHERE games.seasonId = :seasonId AND games.status = 'FINAL'
        """,
    )
    suspend fun getFinalizedGoalsBySeason(seasonId: String): List<GoalEventEntity>
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM team_sync_state WHERE localTeamId = :localTeamId LIMIT 1")
    fun observeState(localTeamId: String): Flow<TeamSyncStateEntity?>

    @Query("SELECT * FROM team_sync_state WHERE localTeamId = :localTeamId LIMIT 1")
    suspend fun getState(localTeamId: String): TeamSyncStateEntity?

    @Query("SELECT * FROM team_sync_state")
    suspend fun getAllStates(): List<TeamSyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: TeamSyncStateEntity)

    @Query("DELETE FROM team_sync_state WHERE localTeamId = :localTeamId")
    suspend fun deleteState(localTeamId: String)

    @Query("SELECT * FROM entity_sync_versions WHERE localTeamId = :localTeamId")
    suspend fun getVersions(localTeamId: String): List<EntitySyncVersionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVersions(versions: List<EntitySyncVersionEntity>)

    @Query("DELETE FROM entity_sync_versions WHERE localTeamId = :localTeamId AND entityType = :entityType AND entityId = :entityId")
    suspend fun deleteVersion(localTeamId: String, entityType: String, entityId: String)

    @Query("DELETE FROM entity_sync_versions WHERE localTeamId = :localTeamId")
    suspend fun deleteVersionsByTeam(localTeamId: String)

    @Query("SELECT * FROM pending_mutations WHERE localTeamId = :localTeamId ORDER BY createdAt")
    suspend fun getPending(localTeamId: String): List<PendingMutationEntity>

    @Query("SELECT * FROM pending_mutations WHERE mutationId = :mutationId LIMIT 1")
    suspend fun getPendingById(mutationId: String): PendingMutationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPending(mutations: List<PendingMutationEntity>)

    @Query("DELETE FROM pending_mutations WHERE mutationId IN (:mutationIds)")
    suspend fun deletePending(mutationIds: List<String>)

    @Query("DELETE FROM pending_mutations WHERE localTeamId = :localTeamId")
    suspend fun deletePendingByTeam(localTeamId: String)

    @Query("DELETE FROM pending_mutations WHERE localTeamId = :localTeamId AND entityType = :entityType AND entityId = :entityId")
    suspend fun deletePendingForEntity(localTeamId: String, entityType: String, entityId: String)

    @Query("SELECT * FROM sync_conflicts WHERE localTeamId = :localTeamId ORDER BY createdAt DESC")
    suspend fun getConflicts(localTeamId: String): List<SyncConflictEntity>

    @Query("SELECT * FROM sync_conflicts WHERE localTeamId = :localTeamId ORDER BY createdAt DESC")
    fun observeConflicts(localTeamId: String): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE mutationId = :mutationId LIMIT 1")
    suspend fun getConflict(mutationId: String): SyncConflictEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConflicts(conflicts: List<SyncConflictEntity>)

    @Query("DELETE FROM sync_conflicts WHERE mutationId = :mutationId")
    suspend fun deleteConflict(mutationId: String)

    @Query("DELETE FROM sync_conflicts WHERE localTeamId = :localTeamId")
    suspend fun deleteConflictsByTeam(localTeamId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertControllerLease(lease: GameControllerLeaseEntity)

    @Query("SELECT * FROM game_controller_leases WHERE gameId = :gameId LIMIT 1")
    suspend fun getControllerLease(gameId: String): GameControllerLeaseEntity?

    @Query("DELETE FROM game_controller_leases WHERE localTeamId = :localTeamId")
    suspend fun deleteControllerLeasesByTeam(localTeamId: String)
}
