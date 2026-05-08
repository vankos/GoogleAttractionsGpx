package com.example.googleAttractionsGpx

import com.example.googleAttractionsGpx.data.repository.TrackCacheRepository
import org.junit.Assert.*
import org.junit.Test

class TrackCacheRepositoryTest {

    @Test
    fun serializeDeserialize_roundTrip() {
        val cache = mutableMapOf("track1" to "content://file1", "track2" to "content://file2")
        val serialized = TrackCacheRepository.serialize(cache)
        val deserialized = TrackCacheRepository.deserialize(serialized)
        assertEquals(cache, deserialized)
    }

    @Test
    fun deserialize_null_returnsEmptyMap() {
        val result = TrackCacheRepository.deserialize(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun deserialize_empty_returnsEmptyMap() {
        val result = TrackCacheRepository.deserialize("")
        assertTrue(result.isEmpty())
    }
}
