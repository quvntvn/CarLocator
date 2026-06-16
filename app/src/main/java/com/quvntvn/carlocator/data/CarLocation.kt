package com.quvntvn.carlocator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "car_location") // Le nom officiel de la table est ici (minuscules)
data class CarLocation(
    @PrimaryKey val macAddress: String,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)
