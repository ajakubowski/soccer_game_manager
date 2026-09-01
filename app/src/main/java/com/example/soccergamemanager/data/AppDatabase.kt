package com.example.soccergamemanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SeasonEntity::class,
        PlayerEntity::class,
        GameEntity::class,
        PlayerAvailabilityEntity::class,
        AssignmentEntity::class,
        GoalEventEntity::class,
        TeamSyncStateEntity::class,
        EntitySyncVersionEntity::class,
        PendingMutationEntity::class,
        SyncConflictEntity::class,
        GameControllerLeaseEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun seasonDao(): SeasonDao
    abstract fun playerDao(): PlayerDao
    abstract fun gameDao(): GameDao
    abstract fun availabilityDao(): AvailabilityDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun goalDao(): GoalDao
    abstract fun syncDao(): SyncDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "soccer-manager.db")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)
                .addMigrations(MIGRATION_7_8)
                .addMigrations(MIGRATION_8_9)
                .addMigrations(MIGRATION_9_10)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE games ADD COLUMN elapsedSecondsInRound INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE goal_events ADD COLUMN scorerPlayerId TEXT",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE player_availability ADD COLUMN isInjured INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE games ADD COLUMN manualGroupLocksJson TEXT NOT NULL DEFAULT '[]'",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE goal_events ADD COLUMN assisterPlayerId TEXT",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE player_availability ADD COLUMN availableFirstHalf INTEGER NOT NULL DEFAULT 1",
                )
                database.execSQL(
                    "ALTER TABLE player_availability ADD COLUMN availableSecondHalf INTEGER NOT NULL DEFAULT 1",
                )
                database.execSQL(
                    "ALTER TABLE player_availability ADD COLUMN injuredAssignmentId TEXT",
                )
                database.execSQL(
                    "ALTER TABLE player_availability ADD COLUMN injuredPosition TEXT",
                )
                database.execSQL(
                    "ALTER TABLE player_availability ADD COLUMN injuredHalfNumber INTEGER",
                )
                database.execSQL(
                    "ALTER TABLE player_availability ADD COLUMN injuredRoundIndex INTEGER",
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE games ADD COLUMN liveNotes TEXT NOT NULL DEFAULT ''",
                )
                database.execSQL(
                    "ALTER TABLE games ADD COLUMN postGameNotes TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE goal_events ADD COLUMN notes TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE games ADD COLUMN extraLineupSlotsJson TEXT NOT NULL DEFAULT '[]'",
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS team_sync_state (
                        localTeamId TEXT NOT NULL PRIMARY KEY,
                        cloudTeamId TEXT NOT NULL,
                        lastPulledRevision INTEGER NOT NULL DEFAULT 0,
                        lastSyncAt INTEGER,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        pendingCount INTEGER NOT NULL DEFAULT 0,
                        conflictCount INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT,
                        lastPublishedLineupVersion INTEGER
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS entity_sync_versions (
                        localTeamId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        serverVersion INTEGER NOT NULL,
                        syncedPayloadHash TEXT NOT NULL,
                        PRIMARY KEY (localTeamId, entityType, entityId)
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_entity_sync_versions_localTeamId ON entity_sync_versions(localTeamId)")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_mutations (
                        mutationId TEXT NOT NULL PRIMARY KEY,
                        localTeamId TEXT NOT NULL,
                        cloudTeamId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        expectedVersion INTEGER NOT NULL,
                        payloadJson TEXT,
                        cellJson TEXT,
                        createdAt INTEGER NOT NULL,
                        attemptCount INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_pending_mutations_localTeamId ON pending_mutations(localTeamId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_pending_mutations_localTeamId_entityType_entityId ON pending_mutations(localTeamId, entityType, entityId)")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_conflicts (
                        mutationId TEXT NOT NULL PRIMARY KEY,
                        localTeamId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        expectedVersion INTEGER NOT NULL,
                        actualVersion INTEGER NOT NULL,
                        serverEntityJson TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_conflicts_localTeamId ON sync_conflicts(localTeamId)")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS game_controller_leases (
                        gameId TEXT NOT NULL PRIMARY KEY,
                        localTeamId TEXT NOT NULL,
                        deviceId TEXT NOT NULL,
                        holderName TEXT NOT NULL,
                        expiresAt INTEGER NOT NULL,
                        claimedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_game_controller_leases_localTeamId ON game_controller_leases(localTeamId)")
            }
        }
    }
}
