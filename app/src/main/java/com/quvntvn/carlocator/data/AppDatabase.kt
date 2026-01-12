package com.quvntvn.carlocator.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CarLocation::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            // Si l'instance existe déjà, on la retourne
            return INSTANCE ?: synchronized(this) {
                // Sinon on la crée (protection multi-thread)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "car_locator_database"
                )
                    .fallbackToDestructiveMigration() // Optionnel : recrée la DB si on change la structure
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
