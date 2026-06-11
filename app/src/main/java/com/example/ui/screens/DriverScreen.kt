package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nearby.NearbyManager
import com.example.nearby.PassengerSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverScreen(
    viewModel: com.example.viewmodel.AppViewModel,
    modifier: Modifier = Modifier
) {
    val nearbyManager = viewModel.nearbyManager
    val isAdvertising by nearbyManager.isAdvertising.collectAsState()
    val error by nearbyManager.error.collectAsState()
    
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Driver Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (error!!.contains("8007")) 
                            "Error 8007 (Bluetooth/Hardware Error). The web emulator does not have Bluetooth hardware, so offline peer-to-peer networking will fail here. Please download the APK via the settings menu to test on real devices!" 
                        else error!!,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { nearbyManager.clearError() }) {
                        Text("Dismiss")
                    }
                }
            }
        }
        
        if (!isAdvertising) {
            TripSetupUI(
                viewModel = viewModel,
                onStartTrip = { driverName, stops ->
                    nearbyManager.startAdvertising(driverName, stops)
                }
            )
        } else {
            ActiveTripUI(
                nearbyManager = nearbyManager,
                onStopTrip = { nearbyManager.stopAdvertising() }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripSetupUI(viewModel: com.example.viewmodel.AppViewModel, onStartTrip: (String, List<String>) -> Unit) {
    val savedTrips by viewModel.savedTrips.collectAsState()
    var driverName by remember { mutableStateOf("") }
    var currentStop by remember { mutableStateOf("") }
    var stops by remember { mutableStateOf(listOf<String>()) }
    var tripName by remember { mutableStateOf("") }
    var showSavedTripsDialog by remember { mutableStateOf(false) }

    if (showSavedTripsDialog) {
        AlertDialog(
            onDismissRequest = { showSavedTripsDialog = false },
            title = { Text("Saved Trips") },
            text = {
                if (savedTrips.isEmpty()) {
                    Text("No saved trips yet.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(savedTrips) { trip ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                onClick = {
                                    driverName = trip.driverName
                                    try {
                                        val typedStops = kotlinx.serialization.json.Json.decodeFromString<List<String>>(trip.stopsJson)
                                        stops = typedStops
                                    } catch (e: Exception) {
                                        stops = emptyList()
                                    }
                                    showSavedTripsDialog = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(if (trip.name.isNotBlank()) trip.name else "Trip #${trip.id}", fontWeight = FontWeight.Bold)
                                        Text("${trip.driverName} - ${trip.stopsJson.take(20)}...")
                                    }
                                    IconButton(onClick = { viewModel.deleteTrip(trip.id) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete Trip")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSavedTripsDialog = false }) { Text("Close") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Create New Trip", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { showSavedTripsDialog = true }) {
                Text("Load Saved")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = driverName,
            onValueChange = { driverName = it },
            label = { Text("Driver or Bus Name") },
            modifier = Modifier.fillMaxWidth().testTag("driver_name_input"),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = currentStop,
                onValueChange = { currentStop = it },
                label = { Text("Add Stop Point") },
                modifier = Modifier.weight(1f).testTag("stop_input"),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (currentStop.isNotBlank() && !stops.contains(currentStop)) {
                        stops = stops + currentStop
                        currentStop = ""
                    }
                },
                modifier = Modifier.testTag("add_stop_button"),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Stop")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // List of stops
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(stops) { stop ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stop, style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { stops = stops - stop }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove")
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = tripName,
                onValueChange = { tripName = it },
                label = { Text("Template Name (Optional)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            TextButton(
                onClick = {
                    val finalStops = if (currentStop.isNotBlank() && !stops.contains(currentStop)) {
                        stops + currentStop
                    } else stops
                    viewModel.saveTrip(tripName, driverName, finalStops)
                    tripName = ""
                },
                enabled = driverName.isNotBlank() && (stops.isNotEmpty() || currentStop.isNotBlank())
            ) {
                Text("Save Template")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = {
                val finalStops = if (currentStop.isNotBlank() && !stops.contains(currentStop)) {
                    stops + currentStop
                } else stops
                
                onStartTrip(driverName, finalStops)
            },
            enabled = driverName.isNotBlank() && (stops.isNotEmpty() || currentStop.isNotBlank()),
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("start_trip_button")
        ) {
            Text("Start Trip (Offline Mode)")
        }
    }
}

@Composable
fun ActiveTripUI(
    nearbyManager: NearbyManager,
    onStopTrip: () -> Unit
) {
    val passengers by nearbyManager.passengerSelections.collectAsState()
    val connections by nearbyManager.connectedEndpoints.collectAsState()
    val activeStops by nearbyManager.activeStops.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Trip Active (Advertising via Bluetooth/Wi-Fi)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Connected devices: ${connections.size}", style = MaterialTheme.typography.bodyMedium)
                Text("Total passenger requests: ${passengers.size}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Route Progress:", style = MaterialTheme.typography.titleMedium)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (activeStops.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("All stops completed!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activeStops, key = { it }) { stop ->
                    val passengersAtStop = passengers.filter { it.stop == stop }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stop, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { nearbyManager.completeStop(stop) }) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Complete Stop", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (passengersAtStop.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Getting off here:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                passengersAtStop.forEach { p ->
                                    Text("• ${p.name}", style = MaterialTheme.typography.bodyMedium)
                                }
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("No passengers requested this stop yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onStopTrip,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.StopCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("End Trip")
        }
    }
}
