package com.example.db

import kotlinx.coroutines.flow.Flow

class SavedTripRepository(private val dao: SavedTripDao) {
    val allTrips: Flow<List<SavedTrip>> = dao.getAllTrips()

    suspend fun insert(trip: SavedTrip) = dao.insertTrip(trip)
    suspend fun deleteById(id: Int) = dao.deleteTripById(id)
}
