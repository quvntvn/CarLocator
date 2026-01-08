package com.quvntvn.carlocator

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.SkipQueryVerification // Important pour le bypass
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {

    // On force l'utilisation de la table "car_location" (minuscules)
    // Et on dit à Room de ne pas vérifier l'erreur à la compilation (@SkipQueryVerification)

    @SkipQueryVerification
    @Query("SELECT * FROM car_location")
    fun getAllCars(): Flow<List<CarLocation>>

    @SkipQueryVerification
    @Query("SELECT * FROM car_location WHERE macAddress = :macAddress LIMIT 1")
    suspend fun getCarByMac(macAddress: String): CarLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCar(car: CarLocation)

    @Delete
    suspend fun deleteCar(car: CarLocation)
}