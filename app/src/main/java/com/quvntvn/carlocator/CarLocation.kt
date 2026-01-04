package com.quvntvn.carlocator

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "car_location")
data class CarLocation(
    @PrimaryKey val macAddress: String, // ID est devenu macAddress
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = 0
)