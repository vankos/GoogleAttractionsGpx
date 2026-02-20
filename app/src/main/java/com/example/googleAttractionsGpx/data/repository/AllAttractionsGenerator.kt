package com.example.googleAttractionsGpx.data.repository

import com.example.googleAttractionsGpx.domain.models.Coordinates
import com.example.googleAttractionsGpx.domain.models.PointData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class AllAttractionsGenerator(
    private val googleApiKey: String,
    private val tripAdvisorApiKey: String,
    private val onSourceComplete: ((sourceName: String, count: Int, error: Exception?) -> Unit)? = null
) : GpxGeneratorBase() {

    override fun getData(coordinates: Coordinates, radiusMeters: Int): List<PointData> = runBlocking(Dispatchers.IO) {
        // Google Places
        val googleDeferred = async {
            try {
                val googleGenerator = GooglePlaceGpxGenerator(googleApiKey)
                googleGenerator.getData(coordinates, radiusMeters)
            } catch (e: Exception) {
                e.printStackTrace()
                onSourceComplete?.invoke("Google", 0, e)
                emptyList<PointData>()
            }
        }

        // TripAdvisor
        val tripAdvisorDeferred = async {
            try {
                val tripAdvisorGenerator = TripAdvisorGpxGenerator(tripAdvisorApiKey)
                tripAdvisorGenerator.getData(coordinates, radiusMeters)
            } catch (e: Exception) {
                e.printStackTrace()
                onSourceComplete?.invoke("TripAdvisor", 0, e)
                emptyList<PointData>()
            }
        }

        // OpenStreetMap
        val osmDeferred = async {
            try {
                val osmGenerator = OsmPlaceGpxGenerator()
                osmGenerator.getData(coordinates, radiusMeters)
            } catch (e: Exception) {
                e.printStackTrace()
                onSourceComplete?.invoke("OSM", 0, e)
                emptyList<PointData>()
            }
        }

        // Wikidata
        val wikidataDeferred = async {
            try {
                val wikidataGenerator = WikidataAttractionsGpxGenerator()
                wikidataGenerator.getData(coordinates, radiusMeters)
            } catch (e: Exception) {
                e.printStackTrace()
                onSourceComplete?.invoke("Wikidata", 0, e)
                emptyList<PointData>()
            }
        }

        // Aggregate results
        val allPoints = mutableListOf<PointData>()
        val googlePoints = googleDeferred.await()
        allPoints.addAll(googlePoints)
        onSourceComplete?.invoke("Google", googlePoints.size, null)

        val tripAdvisorPoints = tripAdvisorDeferred.await()
        allPoints.addAll(tripAdvisorPoints)
        onSourceComplete?.invoke("TripAdvisor", tripAdvisorPoints.size, null)

        val osmPoints = osmDeferred.await()
        allPoints.addAll(osmPoints)
        onSourceComplete?.invoke("OSM", osmPoints.size, null)

        val wikidataPoints = wikidataDeferred.await()
        allPoints.addAll(wikidataPoints)
        onSourceComplete?.invoke("Wikidata", wikidataPoints.size, null)

        allPoints
    }
}
