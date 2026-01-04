package com.quvntvn.carlocator

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CarLocation::class], version = 2, exportSchema = false)
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
                    "car_locator_db"
                )
                    .fallbackToDestructiveMigration() // Ajoute ça : si la DB change, on reset tout sans crasher
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}