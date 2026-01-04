package com.quvntvn.carlocator

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    // Récupère la position (Flow permet d'être notifié en temps réel si ça change)
    @Query("SELECT * FROM car_location WHERE id = 1")
    fun getCarLocation(): Flow<CarLocation?>

    // Sauvegarde ou remplace la position existante
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCarLocation(location: CarLocation)

    // Supprime la position (si on veut reset)
    @Query("DELETE FROM car_location")
    suspend fun clearLocation()
}