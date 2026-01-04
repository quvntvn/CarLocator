package com.quvntvn.carlocator

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "car_location")
data class CarLocation(
    @PrimaryKey val macAddress: String, // L'ID unique est maintenant l'adresse MAC
    val name: String, // Le nom personnalisé (ex: "Clio", "BMW")
    val latitude: Double? = null, // Null = pas encore garée
    val longitude: Double? = null,
    val timestamp: Long = 0
)