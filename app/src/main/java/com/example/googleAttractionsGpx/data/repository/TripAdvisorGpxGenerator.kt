package com.example.googleAttractionsGpx.data.repository

import com.example.googleAttractionsGpx.domain.models.Coordinates
import com.example.googleAttractionsGpx.domain.models.PointData
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos

class TripAdvisorGpxGenerator(private val apiKey: String) : GpxGeneratorBase() {

    data class TripAdvisorPlace(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val rating: Double,
        val numReviews: Int,
        val locationId: String
    )

    override fun getData(coordinates: Coordinates): List<PointData> {
        val places = fetchTripAdvisorByGrid(coordinates)
        return places.map { place ->
            convertTripAdvisorPlaceToPointData(place)
        }
    }

    private fun fetchTripAdvisorByGrid(coordinates: Coordinates): List<TripAdvisorPlace> {
        val centerLat = coordinates.latitude
        val centerLng = coordinates.longitude

        val halfSideMeters = 5000.0
        val stepMeters = 5000.0
        val requestRadius = 2500

        val latDegPerMeter = 1.0 / 111320.0
        val cosLat = cos(centerLat * PI / 180.0)
        val lonDegPerMeter = 1.0 / (111320.0 * cosLat)

        val results = mutableSetOf<TripAdvisorPlace>()
        val stepsCount = ((2 * halfSideMeters) / stepMeters).toInt()

        for (i in 0..stepsCount) {
            val offsetLatM = -halfSideMeters + i * stepMeters
            val offsetLatDeg = offsetLatM * latDegPerMeter

            for (j in 0..stepsCount) {
                val offsetLonM = -halfSideMeters + j * stepMeters
                val offsetLonDeg = offsetLonM * lonDegPerMeter

                val cellLat = centerLat + offsetLatDeg
                val cellLon = centerLng + offsetLonDeg

                val placesInCell = fetchTripAdvisorSinglePage(
                    cellLat, cellLon, requestRadius
                )
                results.addAll(placesInCell)
            }
        }
        return results.toList()
    }

    private fun fetchTripAdvisorSinglePage(
        latitude: Double,
        longitude: Double,
        radius: Int
    ): List<TripAdvisorPlace> {
        val nearbyUrl = "https://api.content.tripadvisor.com/api/v1/location/search" +
                "?latLong=$latitude,$longitude" +
                "&category=attractions" +
                "&radius=$radius" +
                "&radiusUnit=m" +
                "&language=en" +
                "&key=$apiKey" +
                "&searchQuery=attractions"

        val client = OkHttpClient()
        val nearbyRequest = Request.Builder()
            .url(nearbyUrl)
            .get()
            .addHeader("accept", "application/json")
            .addHeader("referer", "https://github.co")
            .build()

        val nearbyResponse = client.newCall(nearbyRequest).execute()
        val nearbyBody = nearbyResponse.body?.string().orEmpty()
        nearbyResponse.close()

        val nearbyJson = JSONObject(nearbyBody)
        val dataArray = nearbyJson.optJSONArray("data") ?: return emptyList()
        val locationIds = mutableListOf<String>()

        for (i in 0 until dataArray.length()) {
            val item = dataArray.getJSONObject(i)
            val locId = item.optString("location_id", "")
            if (locId.isNotEmpty()) {
                locationIds.add(locId)
            }
        }

        val results = mutableListOf<TripAdvisorPlace>()
        for (locId in locationIds) {
            val detailsUrl = "https://api.content.tripadvisor.com/api/v1/location/$locId/details" +
                    "?language=en&key=$apiKey"

            val detailsRequest = Request.Builder()
                .url(detailsUrl)
                .get()
                .addHeader("accept", "application/json")
                .addHeader("referer", "https://github.co")
                .build()

            val detailsResponse = client.newCall(detailsRequest).execute()
            val detailsBody = detailsResponse.body?.string().orEmpty()
            detailsResponse.close()
            val detailsJson = JSONObject(detailsBody)

            val name = detailsJson.optString("name", "No name")
            val latStr = detailsJson.optString("latitude", "0.0")
            val lngStr = detailsJson.optString("longitude", "0.0")
            val latVal = latStr.toDoubleOrNull() ?: 0.0
            val lngVal = lngStr.toDoubleOrNull() ?: 0.0

            val ratingStr = detailsJson.optString("rating", "0.0")
            val ratingVal = ratingStr.toDoubleOrNull() ?: 0.0

            val reviewsStr = detailsJson.optString("num_reviews", "0")
            val reviewsVal = reviewsStr.toIntOrNull() ?: 0

            // Filter places with rating >= 4.0 and reviews >= 10 (same as original logic)
            if (ratingVal < 4.0 || reviewsVal < 10) {
                continue
            }

            results.add(
                TripAdvisorPlace(
                    name = name,
                    latitude = latVal,
                    longitude = lngVal,
                    rating = ratingVal,
                    numReviews = reviewsVal,
                    locationId = locId
                )
            )

            // Rate limiting
            Thread.sleep(50)
        }

        return results
    }

    private fun convertTripAdvisorPlaceToPointData(place: TripAdvisorPlace): PointData {
        val coordinates = Coordinates(place.latitude, place.longitude)
        val description = "Rating: ${place.rating}/5.0 (${place.numReviews} reviews) - TripAdvisor ID: ${place.locationId}"
        
        return PointData(
            name = place.name,
            coordinates = coordinates,
            description = description
        )
    }
}
