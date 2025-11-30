package com.example.googleAttractionsGpx.data.repository

import com.example.googleAttractionsGpx.domain.models.Coordinates
import com.example.googleAttractionsGpx.domain.models.PointData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class AllAttractionsGenerator(
    private val googleApiKey: String,
    private val tripAdvisorApiKey: String,
    private val radiusMeters: Int = 5000
) : GpxGeneratorBase() {

    override fun getData(coordinates: Coordinates): List<PointData> = runBlocking(Dispatchers.IO) {
        // Google Places
        val googleDeferred = async {
            try {
                val googleGenerator = GooglePlaceGpxGenerator(googleApiKey)
                googleGenerator.getData(coordinates)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList<PointData>()
            }
        }

        // TripAdvisor
        val tripAdvisorDeferred = async {
            try {
                val tripAdvisorGenerator = TripAdvisorGpxGenerator(tripAdvisorApiKey)
                tripAdvisorGenerator.getData(coordinates)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList<PointData>()
            }
        }

        // OpenStreetMap
        val osmDeferred = async {
            try {
                val osmGenerator = OsmPlaceGpxGenerator(coordinates, radiusMeters)
                osmGenerator.getData(coordinates)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList<PointData>()
            }
        }

        // Wikidata
        val wikidataDeferred = async {
            try {
                val radiusKm = radiusMeters / 1000.0
                val wikidataGenerator = WikidataAttractionsGpxGenerator(radiusKm)
                wikidataGenerator.getData(coordinates)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList<PointData>()
            }
        }

        // Aggregate results
        val allPoints = mutableListOf<PointData>()
        allPoints.addAll(googleDeferred.await())
        allPoints.addAll(tripAdvisorDeferred.await())
        allPoints.addAll(osmDeferred.await())
        allPoints.addAll(wikidataDeferred.await())

        allPoints
    }
}
