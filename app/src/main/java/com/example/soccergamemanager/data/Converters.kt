package com.example.soccergamemanager.data

import androidx.room.TypeConverter
import com.example.soccergamemanager.domain.FieldPosition
import com.example.soccergamemanager.domain.GameStatus
import com.example.soccergamemanager.domain.GoalSide
import com.example.soccergamemanager.domain.PositionGroup

class Converters {
    @TypeConverter
    fun fromGameStatus(value: GameStatus): String = value.name

    @TypeConverter
    fun toGameStatus(value: String): GameStatus = GameStatus.valueOf(value)

    @TypeConverter
    fun fromFieldPosition(value: FieldPosition): String = value.name

    @TypeConverter
    fun toFieldPosition(value: String): FieldPosition = FieldPosition.valueOf(value)

    @TypeConverter
    fun fromPositionGroup(value: PositionGroup): String = value.name

    @TypeConverter
    fun toPositionGroup(value: String): PositionGroup = PositionGroup.valueOf(value)

    @TypeConverter
    fun fromGoalSide(value: GoalSide): String = value.name

    @TypeConverter
    fun toGoalSide(value: String): GoalSide = GoalSide.valueOf(value)
}
