package com.example.googleAttractionsGpx.domain.models

data class Coordinates(
    val latitude: Double,
    val longitude: Double
) {
    override fun toString(): String = "$latitude,$longitude"
    
    companion object {
        fun fromString(coords: String): Coordinates {
            val (lat, lng) = coords.split(",").map { it.toDouble() }
            return Coordinates(lat, lng)
        }
    }
}
