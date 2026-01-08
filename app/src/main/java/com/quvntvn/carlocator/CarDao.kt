package com.quvntvn.carlocator

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.quvntvn.carlocator.CarLocation
import com.quvntvn.carlocator.AppDatabase


@Dao
interface CarDao {
    @Query("SELECT * FROM CarLocation")
    fun getAllCars(): Flow<List<CarLocation>>

    @Query("SELECT * FROM CarLocation WHERE macAddress = :macAddress LIMIT 1")
    suspend fun getCarByMac(macAddress: String): CarLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCar(car: CarLocation)

    @Delete
    suspend fun deleteCar(car: CarLocation)
}