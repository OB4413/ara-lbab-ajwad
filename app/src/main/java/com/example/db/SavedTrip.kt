package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_trips")
data class SavedTrip(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val driverName: String,
    val stopsJson: String
)
