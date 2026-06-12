package com.example.schooltransport.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.schooltransport.db.AppDatabase
import com.example.schooltransport.db.SavedTrip
import com.example.schooltransport.db.SavedTripRepository
import com.example.schooltransport.nearby.NearbyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class AppRole {
    NONE, DRIVER, PASSENGER
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val nearbyManager = NearbyManager(application)
    
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "app_database"
    ).build()
    
    private val repository = SavedTripRepository(db.savedTripDao())
    
    val savedTrips = repository.allTrips.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _currentRole = MutableStateFlow(AppRole.NONE)
    val currentRole = _currentRole.asStateFlow()

    fun setRole(role: AppRole) {
        _currentRole.value = role
    }

    fun saveTrip(name: String, driverName: String, stops: List<String>) {
        viewModelScope.launch {
            val stopsJson = Json.encodeToString(stops)
            repository.insert(SavedTrip(name = name, driverName = driverName, stopsJson = stopsJson))
        }
    }
    
    fun deleteTrip(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }
}
