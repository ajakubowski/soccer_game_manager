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
    ],
    version = 6,
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

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "soccer-manager.db")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
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
    }
}
