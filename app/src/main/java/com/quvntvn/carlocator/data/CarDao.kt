package com.quvntvn.carlocator.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {

    @Query("SELECT * FROM car_location")
    fun getAllCars(): Flow<List<CarLocation>>

    @Query("SELECT * FROM car_location")
    suspend fun getAllCarsOnce(): List<CarLocation>

    @Query("SELECT * FROM car_location WHERE macAddress = :macAddress LIMIT 1")
    suspend fun getCarByMac(macAddress: String): CarLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCar(car: CarLocation)

    @Delete
    suspend fun deleteCar(car: CarLocation)

    @Query("UPDATE car_location SET name = :newName WHERE macAddress = :macAddress")
    suspend fun updateCarName(macAddress: String, newName: String)
}
