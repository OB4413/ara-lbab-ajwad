package com.example.schooltransport.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedTripDao {
    @Query("SELECT * FROM saved_trips")
    fun getAllTrips(): Flow<List<SavedTrip>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: SavedTrip)

    @Query("DELETE FROM saved_trips WHERE id = :id")
    suspend fun deleteTripById(id: Int)
}
