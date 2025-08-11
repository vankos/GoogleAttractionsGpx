package com.example.googleAttractionsGpx.domain.models

data class Coordinates(
    val latitude: Double,
    val longitude: Double
) {
    override fun toString(): String = "$latitude,$longitude"
    
    companion object {
        fun fromString(coords: String): Coordinates {
            val parts = if (coords.contains(",")) {
                coords.split(",").map { it.trim().toDouble() }
            } else {
                coords.split(" ").filter { it.isNotBlank() }.map { it.toDouble() }
            }
            val (lat, lng) = parts
            return Coordinates(lat, lng)
        }
    }
}
