package com.quvntvn.carlocator

import androidx.room.Entity
import androidx.room.PrimaryKey

// On force le nom de la table ici pour être sûr
@Entity(tableName = "car_location")
data class CarLocation(
    @PrimaryKey val macAddress: String,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)