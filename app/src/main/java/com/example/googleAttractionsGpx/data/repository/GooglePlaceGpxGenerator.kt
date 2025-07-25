package com.example.googleAttractionsGpx.data.repository

import com.example.googleAttractionsGpx.domain.models.Coordinates
import com.example.googleAttractionsGpx.domain.models.PointData
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.cos

class GooglePlaceGpxGenerator(private val apiKey: String) : GpxGeneratorBase() {

    data class PlaceInfo(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val rating: Double,
        val userRatingsTotal: Int,
        val mapsLink: String
    )

    override fun getData(coordinates: Coordinates): List<PointData> {
        val places = fetchPlacesByGrid(coordinates, apiKey)
        return places.map { placeInfo ->
            convertPlaceInfoToPointData(placeInfo)
        }
    }

    private fun fetchPlacesByGrid(coordinates: Coordinates, apiKey: String): List<PlaceInfo> {
        val centerLat = coordinates.latitude
        val centerLng = coordinates.longitude

        // Grid parameters
        val halfSideMeters = 4000.0   // ±4000m from the center (8km total)
        val stepMeters = 1000.0       // step for each cell
        val requestRadius = 600       // Google Places radius for each point

        // 1 degree of latitude is ~111,320m
        val latDegreePerMeter = 1.0 / 111320.0
        // For longitude, multiply by cos(latitude)
        val cosLat = cos(centerLat * PI / 180.0)
        val lonDegreePerMeter = 1.0 / (111320.0 * cosLat)

        val results = mutableSetOf<PlaceInfo>()

        val stepsCount = ((2 * halfSideMeters) / stepMeters).toInt()

        for (i in 0..stepsCount) {
            val offsetMetersLat = -halfSideMeters + i * stepMeters
            val offsetLatDegrees = offsetMetersLat * latDegreePerMeter

            for (j in 0..stepsCount) {
                val offsetMetersLon = -halfSideMeters + j * stepMeters
                val offsetLonDegrees = offsetMetersLon * lonDegreePerMeter

                val cellLat = centerLat + offsetLatDegrees
                val cellLon = centerLng + offsetLonDegrees

                val placesInCell = fetchNearbyPlacesSinglePage(
                    latitude = cellLat,
                    longitude = cellLon,
                    radius = requestRadius,
                    type = "tourist_attraction",
                    apiKey = apiKey
                )
                results.addAll(placesInCell)

                // Small delay to avoid hitting API rate limits
                Thread.sleep(50)
            }
        }

        return results.toList()
    }

    private fun fetchNearbyPlacesSinglePage(
        latitude: Double,
        longitude: Double,
        radius: Int,
        type: String,
        apiKey: String
    ): List<PlaceInfo> {
        val baseUrl = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
        val locationParam = "$latitude,$longitude"
        val encodedLocation = URLEncoder.encode(locationParam, "UTF-8")

        val urlString = "$baseUrl?" +
                "location=$encodedLocation&" +
                "radius=$radius&" +
                "type=$type&" +
                "key=$apiKey"

        return try {
            val response = URL(urlString).readText()
            parseGooglePlacesResponse(response)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseGooglePlacesResponse(jsonResponse: String): List<PlaceInfo> {
        val placeList = mutableListOf<PlaceInfo>()
        
        try {
            val jsonObject = JSONObject(jsonResponse)
            val resultsArray = jsonObject.optJSONArray("results") ?: return emptyList()
            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                val name = item.optString("name")
                val geometry = item.optJSONObject("geometry")
                val location = geometry?.optJSONObject("location")
                val latResult = location?.optDouble("lat", 0.0) ?: 0.0
                val lngResult = location?.optDouble("lng", 0.0) ?: 0.0
                val rating = item.optDouble("rating", 0.0)
                val userRatingsTotal = item.optInt("user_ratings_total", 0)
                val placeId = item.optString("place_id", "")

                val rawLink = "https://www.google.com/maps/search/?api=1&query=Google&query_place_id=$placeId"
                if (rating < 4.0 || userRatingsTotal < 20){
                    continue
                }

                placeList.add(
                    PlaceInfo(
                        name = name,
                        latitude = latResult,
                        longitude = lngResult,
                        rating = rating,
                        userRatingsTotal = userRatingsTotal,
                        mapsLink = rawLink
                    )
                )
        }
        }catch (e: Exception) {
            // Return empty list if parsing fails
        }
        
        return placeList
    }
    
    private fun findMatchingBracket(json: String, startIndex: Int): Int {
        var count = 0
        for (i in startIndex until json.length) {
            when (json[i]) {
                '[' -> count++
                ']' -> {
                    count--
                    if (count == 0) return i
                }
            }
        }
        return -1
    }
    
    private fun splitJsonObjects(content: String): List<String> {
        val objects = mutableListOf<String>()
        var braceCount = 0
        var start = 0
        
        for (i in content.indices) {
            when (content[i]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        objects.add(content.substring(start, i + 1))
                        start = i + 1
                        // Skip comma and whitespace
                        while (start < content.length && (content[start] == ',' || content[start].isWhitespace())) {
                            start++
                        }
                    }
                }
            }
        }
        
        return objects
    }
    
    private fun parsePlace(objectStr: String): PlaceInfo? {
        return try {
            val name = extractStringValue(objectStr, "name") ?: return null
            val placeId = extractStringValue(objectStr, "place_id") ?: return null
            val rating = extractDoubleValue(objectStr, "rating") ?: 0.0
            val userRatingsTotal = extractIntValue(objectStr, "user_ratings_total") ?: 0
            
            // Extract coordinates from geometry.location
            val geometryStart = objectStr.indexOf("\"geometry\":")
            if (geometryStart == -1) return null
            
            val locationStart = objectStr.indexOf("\"location\":", geometryStart)
            if (locationStart == -1) return null
            
            val lat = extractDoubleValue(objectStr.substring(locationStart), "lat") ?: return null
            val lng = extractDoubleValue(objectStr.substring(locationStart), "lng") ?: return null
            
            val mapsLink = "https://www.google.com/maps/search/?api=1&query=Google&query_place_id=$placeId"
            
            PlaceInfo(
                name = name,
                latitude = lat,
                longitude = lng,
                rating = rating,
                userRatingsTotal = userRatingsTotal,
                mapsLink = mapsLink
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractStringValue(json: String, key: String): String? {
        val pattern = "\"$key\":\\s*\"([^\"]*)\""
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)
    }
    
    private fun extractDoubleValue(json: String, key: String): Double? {
        val pattern = "\"$key\":\\s*([0-9.-]+)"
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }
    
    private fun extractIntValue(json: String, key: String): Int? {
        val pattern = "\"$key\":\\s*([0-9]+)"
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun convertPlaceInfoToPointData(placeInfo: PlaceInfo): PointData {
        return PointData(
            coordinates = Coordinates(
                latitude = placeInfo.latitude,
                longitude = placeInfo.longitude
            ),
            name = placeInfo.name,
            description = "Rating: ${placeInfo.rating}, Reviews: ${placeInfo.userRatingsTotal}, Link: ${placeInfo.mapsLink}"
        )
    }
}
