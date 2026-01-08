package com.quvntvn.carlocator

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.quvntvn.carlocator.CarLocation

@Database(entities = [CarLocation::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "car_locator"
                )
                    .fallbackToDestructiveMigration() // Si le schéma de la DB change, on la recrée sans crasher
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
