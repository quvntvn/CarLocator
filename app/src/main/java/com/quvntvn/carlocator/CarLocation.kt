package com.quvntvn.carlocator

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.quvntvn.carlocator.AppDatabase
import com.quvntvn.carlocator.CarDao
import com.quvntvn.carlocator.CarLocation
import kotlinx.coroutines.flow.Flow


// On force le nom de la table ici pour être sûr
@Entity(tableName = "car_location")
data class CarLocation(
    @PrimaryKey val macAddress: String,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)