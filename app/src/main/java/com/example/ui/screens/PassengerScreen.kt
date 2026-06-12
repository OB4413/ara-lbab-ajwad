package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nearby.NearbyManager

@Composable
fun PassengerScreen(
    nearbyManager: NearbyManager,
    modifier: Modifier = Modifier
) {
    val isDiscovering by nearbyManager.isDiscovering.collectAsState()
    val connectedDriver by nearbyManager.discoveredDriver.collectAsState()
    val connectedEndpoints by nearbyManager.connectedEndpoints.collectAsState()
    val error by nearbyManager.error.collectAsState()
    
    // Connected if we found a driver and connections is not empty
    val isConnected = connectedEndpoints.isNotEmpty()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Passenger Dashboard",
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

        if (!isDiscovering && !isConnected) {
            PassengerSetupUI(onStartDiscovery = { name ->
                nearbyManager.startDiscovery(name)
            })
        } else if (isDiscovering && !isConnected) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Searching for Driver...")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { nearbyManager.stopDiscovery() }) {
                        Text("Cancel")
                    }
                }
            }
        } else if (isConnected) {
            ConnectedUI(
                driverName = connectedDriver ?: "Driver",
                nearbyManager = nearbyManager,
                onDisconnect = { nearbyManager.stopDiscovery() }
            )
        }
    }
}

@Composable
fun PassengerSetupUI(onStartDiscovery: (String) -> Unit) {
    var passengerName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Enter your name to connect with the driver:")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = passengerName,
            onValueChange = { passengerName = it },
            label = { Text("Your Name") },
            modifier = Modifier.fillMaxWidth().testTag("passenger_name_input"),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { onStartDiscovery(passengerName) },
            enabled = passengerName.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("find_bus_button")
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Find Bus (Offline Mode)")
        }
    }
}

@Composable
fun ConnectedUI(
    driverName: String,
    nearbyManager: NearbyManager,
    onDisconnect: () -> Unit
) {
    val stops by nearbyManager.receivedStops.collectAsState()
    var selectedStop by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Connected to Bus", fontWeight = FontWeight.Bold)
                    Text("Driver: $driverName", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (stops.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Waiting for stops from driver...")
                }
            }
        } else {
            Text("Where do you want to get off?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(stops) { stop ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedStop = stop
                                nearbyManager.sendSelectionToDriver(stop)
                            },
                        border = if (selectedStop == stop) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedStop == stop) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(stop, style = MaterialTheme.typography.bodyLarge, fontWeight = if(selectedStop == stop) FontWeight.Bold else FontWeight.Normal)
                            if (selectedStop == stop) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text("Selected", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onDisconnect,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.StopCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Disconnect")
        }
    }
}
