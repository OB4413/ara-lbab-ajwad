package com.example.nearby

import android.content.Context
import android.util.Log
import com.example.models.MessageType
import com.example.models.NearbyMessage
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

data class PassengerSelection(val endpointId: String, val name: String, val stop: String)

class NearbyManager(val context: Context) {
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val STRATEGY = Strategy.P2P_STAR
    private val SERVICE_ID = "com.example.schooltransport"

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering = _isDiscovering.asStateFlow()

    private val _connectedEndpoints = MutableStateFlow<List<String>>(emptyList())
    val connectedEndpoints = _connectedEndpoints.asStateFlow()

    // Passenger State
    private val _discoveredDriver = MutableStateFlow<String?>(null)
    val discoveredDriver = _discoveredDriver.asStateFlow()
    
    // Passenger receives these stops
    private val _receivedStops = MutableStateFlow<List<String>>(emptyList())
    val receivedStops = _receivedStops.asStateFlow()

    // Driver State
    private val _activeStops = MutableStateFlow<List<String>>(emptyList())
    val activeStops = _activeStops.asStateFlow()
    private val _passengerSelections = MutableStateFlow<List<PassengerSelection>>(emptyList())
    val passengerSelections = _passengerSelections.asStateFlow()
    
    // Global errors
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    
    // Endpoint name mapping
    private val endpointNames = mutableMapOf<String, String>()
    
    private var myName = "Unknown"

    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val jsonString = String(payload.asBytes()!!, StandardCharsets.UTF_8)
                try {
                    val message = Json.decodeFromString<NearbyMessage>(jsonString)
                    when (message.type) {
                        MessageType.STOPS -> {
                            _receivedStops.value = message.stops
                        }
                        MessageType.SELECTION -> {
                            val selection = PassengerSelection(
                                endpointId = endpointId,
                                name = message.passengerName ?: endpointNames[endpointId] ?: "Unknown",
                                stop = message.selectedStop ?: "Unknown"
                            )
                            _passengerSelections.update { current ->
                                current.filter { it.endpointId != endpointId } + selection
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NearbyManager", "Error parsing payload", e)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback: ConnectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                endpointNames[endpointId] = connectionInfo.endpointName
                connectionsClient.acceptConnection(endpointId, payloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                    _connectedEndpoints.update { it + endpointId }
                    if (_isAdvertising.value) {
                        // Driver just connected to a passenger, send them the stops
                        sendStopsToPassenger(endpointId)
                    }
                }
            }

            override fun onDisconnected(endpointId: String) {
                _connectedEndpoints.update { it.filter { id -> id != endpointId } }
                // Keep passenger records even if they temporarily disconnect
            }
        }

    fun startAdvertising(driverName: String, stops: List<String>) {
        this.myName = driverName
        this._activeStops.value = stops
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            driverName, SERVICE_ID, connectionLifecycleCallback, options
        ).addOnSuccessListener {
            _isAdvertising.value = true
            _error.value = null
        }.addOnFailureListener { e ->
            _isAdvertising.value = false
            _error.value = "Failed to start advertising: ${e.message}"
        }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        _isAdvertising.value = false
        disconnectAll()
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Automatically connect to the first driver found for simplicity
            _discoveredDriver.value = info.endpointName
            connectionsClient.requestConnection(
                myName, endpointId, connectionLifecycleCallback
            ).addOnFailureListener { e ->
                _error.value = "Failed to connect: ${e.message}"
            }
        }

        override fun onEndpointLost(endpointId: String) {
            if (_discoveredDriver.value == endpointNames[endpointId]) {
                _discoveredDriver.value = null
            }
        }
    }

    fun startDiscovery(passengerName: String) {
        this.myName = passengerName
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(
            SERVICE_ID, endpointDiscoveryCallback, options
        ).addOnSuccessListener {
            _isDiscovering.value = true
            _error.value = null
        }.addOnFailureListener { e ->
            _isDiscovering.value = false
            _error.value = "Failed to start discovery: ${e.message}"
        }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        _isDiscovering.value = false
        _discoveredDriver.value = null
        disconnectAll()
    }

    private fun disconnectAll() {
        connectionsClient.stopAllEndpoints()
        _connectedEndpoints.value = emptyList()
        _receivedStops.value = emptyList()
        _passengerSelections.value = emptyList()
        _activeStops.value = emptyList()
        endpointNames.clear()
    }

    private fun sendStopsToPassenger(endpointId: String) {
        val msg = NearbyMessage(type = MessageType.STOPS, stops = _activeStops.value)
        sendPayload(endpointId, msg)
    }

    fun completeStop(stop: String) {
        _activeStops.update { current -> current.filter { it != stop } }
        _passengerSelections.update { current -> current.filter { it.stop != stop } }
        // Notify passengers that stops have updated
        _connectedEndpoints.value.forEach { sendStopsToPassenger(it) }
    }

    fun sendSelectionToDriver(stop: String) {
        val msg = NearbyMessage(type = MessageType.SELECTION, selectedStop = stop, passengerName = myName)
        _connectedEndpoints.value.forEach { endpointId ->
            sendPayload(endpointId, msg)
        }
    }

    private fun sendPayload(endpointId: String, message: NearbyMessage) {
        val jsonString = Json.encodeToString(message)
        val bytesPayload = Payload.fromBytes(jsonString.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(endpointId, bytesPayload)
    }
    
    fun clearError() {
        _error.value = null
    }
}
