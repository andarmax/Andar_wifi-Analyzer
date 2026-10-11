package com.andar.wifianalyzer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SpeedTestEntity::class], version = 1, exportSchema = false)
abstract class SpeedTestDatabase : RoomDatabase() {
    abstract fun speedTestDao(): SpeedTestDao

    companion object {
        @Volatile
        private var INSTANCE: SpeedTestDatabase? = null

        fun getDatabase(context: Context): SpeedTestDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpeedTestDatabase::class.java,
                    "wifi_speed_tests.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
