package com.quvntvn.carlocator // Vérifie que c'est bien ton package

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "car_location")
data class CarLocation(
    @PrimaryKey val id: Int = 1, // On met ID 1 car on stocke une seule voiture pour l'instant
    val latitude: Double,
    val longitude: Double,
    val address: String = "", // Optionnel : l'adresse lisible
    val timestamp: Long = System.currentTimeMillis()
)