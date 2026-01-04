package com.quvntvn.carlocator

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

    @Query("SELECT * FROM car_location WHERE macAddress = :mac LIMIT 1")
    suspend fun getCarByMac(mac: String): CarLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCar(car: CarLocation)

    @Delete
    suspend fun deleteCar(car: CarLocation)
}