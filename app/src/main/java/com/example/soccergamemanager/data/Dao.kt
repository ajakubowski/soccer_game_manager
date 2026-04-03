package com.example.soccergamemanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class GameAssignmentCount(
    val gameId: String,
    val assignmentCount: Int,
)

@Dao
interface SeasonDao {
    @Query("SELECT * FROM seasons ORDER BY year DESC, createdAt DESC")
    fun observeSeasons(): Flow<List<SeasonEntity>>

    @Query("SELECT * FROM seasons WHERE seasonId = :seasonId LIMIT 1")
    suspend fun getSeason(seasonId: String): SeasonEntity?

    @Query("SELECT COUNT(*) FROM seasons")
    suspend fun countSeasons(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeason(season: SeasonEntity)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: PlayerEntity)

    @Update
    suspend fun updatePlayer(player: PlayerEntity)
}

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE seasonId = :seasonId ORDER BY scheduledAt DESC")
    fun observeGamesBySeason(seasonId: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE gameId = :gameId LIMIT 1")
    fun observeGame(gameId: String): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE gameId = :gameId LIMIT 1")
    suspend fun getGame(gameId: String): GameEntity?

    @Query("SELECT * FROM games WHERE seasonId = :seasonId AND status = 'FINAL'")
    suspend fun getFinalizedGamesBySeason(seasonId: String): List<GameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameEntity)

    @Update
    suspend fun updateGame(game: GameEntity)
}

@Dao
interface AvailabilityDao {
    @Query("SELECT * FROM player_availability WHERE gameId = :gameId")
    fun observeByGame(gameId: String): Flow<List<PlayerAvailabilityEntity>>

    @Query("SELECT * FROM player_availability WHERE gameId = :gameId")
    suspend fun getByGame(gameId: String): List<PlayerAvailabilityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PlayerAvailabilityEntity>)
}

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignments WHERE gameId = :gameId ORDER BY halfNumber, roundIndex")
    fun observeByGame(gameId: String): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments WHERE gameId = :gameId ORDER BY halfNumber, roundIndex")
    suspend fun getByGame(gameId: String): List<AssignmentEntity>

    @Query("SELECT * FROM assignments WHERE assignmentId = :assignmentId LIMIT 1")
    suspend fun getAssignment(assignmentId: String): AssignmentEntity?

    @Query(
        "SELECT * FROM assignments WHERE gameId = :gameId AND halfNumber = :halfNumber AND roundIndex = :roundIndex",
    )
    suspend fun getByRound(gameId: String, halfNumber: Int, roundIndex: Int): List<AssignmentEntity>

    @Query("DELETE FROM assignments WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AssignmentEntity>)

    @Update
    suspend fun updateAssignments(items: List<AssignmentEntity>)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEventEntity)

    @Query(
        """
        SELECT goal_events.* FROM goal_events
        INNER JOIN games ON goal_events.gameId = games.gameId
        WHERE games.seasonId = :seasonId AND games.status = 'FINAL'
        """,
    )
    suspend fun getFinalizedGoalsBySeason(seasonId: String): List<GoalEventEntity>
}
