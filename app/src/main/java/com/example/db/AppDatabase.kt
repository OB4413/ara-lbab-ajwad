package com.example.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SavedTrip::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedTripDao(): SavedTripDao
}
