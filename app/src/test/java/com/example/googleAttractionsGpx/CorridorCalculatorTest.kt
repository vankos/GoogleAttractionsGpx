package com.example.googleAttractionsGpx

import com.example.googleAttractionsGpx.data.repository.CorridorCalculator
import com.example.googleAttractionsGpx.domain.models.Coordinates
import org.junit.Assert.*
import org.junit.Test

class CorridorCalculatorTest {

    private val points = listOf(
        Coordinates(48.000, 11.000),
        Coordinates(48.001, 11.000),
        Coordinates(48.002, 11.000),
        Coordinates(48.003, 11.000),
        Coordinates(48.004, 11.000),
    )

    @Test
    fun cumulativeDistances_computedCorrectly() {
        val distances = CorridorCalculator.cumulativeDistances(points)
        assertEquals(5, distances.size)
        assertEquals(0.0, distances[0], 0.001)
        assertTrue(distances[1] > 0.1 && distances[1] < 0.12)
        assertTrue(distances[4] > 0.4 && distances[4] < 0.5)
    }

    @Test
    fun extractSubSegment_fullRange() {
        val sub = CorridorCalculator.extractSubSegment(points, 0.0, 10.0)
        assertEquals(5, sub.size)
    }

    @Test
    fun extractSubSegment_middleRange() {
        val sub = CorridorCalculator.extractSubSegment(points, 0.1, 0.35)
        assertTrue("Expected at least 2 points, got ${sub.size}", sub.size >= 2)
        // First point should be interpolated near 48.0009 or be point[1] at 48.001
        assertTrue("First point lat=${sub.first().latitude}", sub.first().latitude >= 48.0008)
    }

    @Test
    fun computeBounds_expandsByWidth() {
        val segment = listOf(
            Coordinates(48.000, 11.000),
            Coordinates(48.001, 11.000),
        )
        val bounds = CorridorCalculator.computeCorridorBounds(segment, 1000)
        assertTrue(bounds.minLat < 48.000)
        assertTrue(bounds.maxLat > 48.001)
        assertTrue(bounds.minLng < 11.000)
        assertTrue(bounds.maxLng > 11.000)
    }

    @Test
    fun computeBounds_centerAndRadius() {
        val segment = listOf(
            Coordinates(48.000, 11.000),
            Coordinates(48.002, 11.000),
        )
        val bounds = CorridorCalculator.computeCorridorBounds(segment, 200)
        assertEquals(48.001, bounds.center.latitude, 0.001)
        assertEquals(11.000, bounds.center.longitude, 0.001)
        assertTrue(bounds.radiusMeters > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun extractSubSegment_startAfterEnd_throws() {
        CorridorCalculator.extractSubSegment(points, 0.3, 0.1)
    }
}
