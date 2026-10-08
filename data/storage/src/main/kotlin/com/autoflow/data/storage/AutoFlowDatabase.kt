package com.autoflow.data.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.autoflow.data.storage.dao.AutomationDao
import com.autoflow.data.storage.dao.ExecutionDao
import com.autoflow.data.storage.dao.VariableDao
import com.autoflow.data.storage.entity.AutomationEntity
import com.autoflow.data.storage.entity.ExecutionEntity
import com.autoflow.data.storage.entity.ExecutionStepEntity
import com.autoflow.data.storage.entity.VariableEntity

@Database(
    entities = [
        AutomationEntity::class,
        VariableEntity::class,
        ExecutionEntity::class,
        ExecutionStepEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AutoFlowDatabase : RoomDatabase() {
    abstract fun automationDao(): AutomationDao
    abstract fun variableDao(): VariableDao
    abstract fun executionDao(): ExecutionDao

    companion object {
        private const val NAME = "autoflow.db"

        fun create(context: Context): AutoFlowDatabase =
            Room.databaseBuilder(context.applicationContext, AutoFlowDatabase::class.java, NAME).build()
    }
}
