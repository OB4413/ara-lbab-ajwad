package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class NearbyMessage(
    val type: MessageType,
    val stops: List<String> = emptyList(),
    val selectedStop: String? = null,
    val passengerName: String? = null
)

enum class MessageType {
    STOPS,
    SELECTION
}
