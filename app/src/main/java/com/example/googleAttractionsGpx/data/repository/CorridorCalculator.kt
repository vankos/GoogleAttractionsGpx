package com.example.googleAttractionsGpx.data.repository

import com.example.googleAttractionsGpx.domain.models.Coordinates
import kotlin.math.*

data class CorridorBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
    val center: Coordinates,
    val radiusMeters: Int,
)

object CorridorCalculator {

    private const val EARTH_RADIUS_KM = 6371.0

    /** Returns cumulative distances in kilometers for each point. */
    fun cumulativeDistances(points: List<Coordinates>): List<Double> {
        if (points.isEmpty()) return emptyList()
        val distances = mutableListOf(0.0)
        for (i in 1 until points.size) {
            distances.add(distances[i - 1] + haversineKm(points[i - 1], points[i]))
        }
        return distances
    }

    /**
     * Extracts the sub-segment of points between [startKm] and [endKm].
     * Interpolates start/end points if they fall between track points.
     */
    fun extractSubSegment(points: List<Coordinates>, startKm: Double, endKm: Double): List<Coordinates> {
        require(startKm <= endKm) { "Start distance must be less than or equal to end distance" }
        if (points.isEmpty()) return emptyList()

        val cumDist = cumulativeDistances(points)
        val result = mutableListOf<Coordinates>()

        for (i in points.indices) {
            if (cumDist[i] >= startKm && cumDist[i] <= endKm) {
                if (result.isEmpty() && i > 0 && cumDist[i - 1] < startKm) {
                    val ratio = (startKm - cumDist[i - 1]) / (cumDist[i] - cumDist[i - 1])
                    result.add(interpolate(points[i - 1], points[i], ratio))
                }
                result.add(points[i])
            } else if (cumDist[i] > endKm) {
                if (i > 0 && cumDist[i - 1] <= endKm) {
                    val ratio = (endKm - cumDist[i - 1]) / (cumDist[i] - cumDist[i - 1])
                    result.add(interpolate(points[i - 1], points[i], ratio))
                }
                break
            }
        }

        if (result.isEmpty() && points.isNotEmpty() && startKm <= cumDist.last()) {
            for (i in 1 until points.size) {
                if (cumDist[i] >= startKm) {
                    val ratio = (startKm - cumDist[i - 1]) / (cumDist[i] - cumDist[i - 1])
                    result.add(interpolate(points[i - 1], points[i], ratio))
                    break
                }
            }
        }

        return result
    }

    /** Computes bounding box expanded by [widthMeters] in all directions. */
    fun computeCorridorBounds(segment: List<Coordinates>, widthMeters: Int): CorridorBounds {
        require(segment.isNotEmpty()) { "Segment must not be empty" }

        val minLat = segment.minOf { it.latitude }
        val maxLat = segment.maxOf { it.latitude }
        val minLng = segment.minOf { it.longitude }
        val maxLng = segment.maxOf { it.longitude }

        val centerLat = (minLat + maxLat) / 2.0
        val centerLng = (minLng + maxLng) / 2.0

        val latOffset = widthMeters / 111_320.0
        val lngOffset = widthMeters / (111_320.0 * cos(Math.toRadians(centerLat)))

        val expandedMinLat = minLat - latOffset
        val expandedMaxLat = maxLat + latOffset
        val expandedMinLng = minLng - lngOffset
        val expandedMaxLng = maxLng + lngOffset

        val center = Coordinates(centerLat, centerLng)

        val corner = Coordinates(expandedMaxLat, expandedMaxLng)
        val radiusMeters = (haversineKm(center, corner) * 1000).toInt()

        return CorridorBounds(
            minLat = expandedMinLat,
            maxLat = expandedMaxLat,
            minLng = expandedMinLng,
            maxLng = expandedMaxLng,
            center = center,
            radiusMeters = radiusMeters,
        )
    }

    private fun haversineKm(a: Coordinates, b: Coordinates): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2.0 * EARTH_RADIUS_KM * asin(sqrt(h))
    }

    private fun interpolate(a: Coordinates, b: Coordinates, ratio: Double): Coordinates {
        return Coordinates(
            latitude = a.latitude + (b.latitude - a.latitude) * ratio,
            longitude = a.longitude + (b.longitude - a.longitude) * ratio,
        )
    }
}
