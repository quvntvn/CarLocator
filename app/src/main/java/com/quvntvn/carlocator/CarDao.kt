package com.quvntvn.carlocator

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    // CORRECTION ICI : "FROM car_location" au lieu de CarLocation
    @Query("SELECT * FROM car_location")
    fun getAllCars(): Flow<List<CarLocation>>

    // CORRECTION ICI : "FROM car_location"
    @Query("SELECT * FROM car_location WHERE macAddress = :macAddress LIMIT 1")
    suspend fun getCarByMac(macAddress: String): CarLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCar(car: CarLocation)

    @Delete
    suspend fun deleteCar(car: CarLocation)
}