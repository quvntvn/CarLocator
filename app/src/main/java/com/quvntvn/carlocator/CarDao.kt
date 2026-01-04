package com.quvntvn.carlocator

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    // Récupère toutes les voitures enregistrées
    @Query("SELECT * FROM car_location")
    fun getAllCars(): Flow<List<CarLocation>>

    // Sauvegarde ou met à jour une voiture (Nom ou Position)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCar(car: CarLocation)

    // Supprime une voiture
    @Delete
    suspend fun deleteCar(car: CarLocation)
}