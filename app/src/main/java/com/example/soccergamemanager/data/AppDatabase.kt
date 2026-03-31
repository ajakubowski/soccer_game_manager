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
    version = 3,
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
    }
}
