package com.princeyadav.grayout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.princeyadav.grayout.model.Schedule

@Database(entities = [Schedule::class], version = 1, exportSchema = false)
abstract class GrayoutDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: GrayoutDatabase? = null

        fun getInstance(context: Context): GrayoutDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    GrayoutDatabase::class.java,
                    "grayout_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
