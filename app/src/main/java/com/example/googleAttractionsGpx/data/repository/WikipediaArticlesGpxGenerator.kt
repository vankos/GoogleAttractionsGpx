package com.example.googleAttractionsGpx.data.repository

import com.example.googleAttractionsGpx.domain.models.Coordinates
import com.example.googleAttractionsGpx.domain.models.PointData
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import org.json.JSONObject
import org.json.JSONArray

class WikipediaArticlesGpxGenerator(private val radius: Double = 5.0) : GpxGeneratorBase() {
    
    private val systemLanguage = Locale.getDefault().language
    
    override fun getData(coordinates: Coordinates): List<PointData> {
        val pointDataList = mutableListOf<PointData>()
        
        try {
            // Get country code for language prioritization
            val countryCode = getCountryCode(coordinates)
            
            // Query Wikidata for articles near coordinates
            val wikidataResults = queryWikidataPlaces(coordinates, radius)
            
            // Convert Wikidata results to PointData
            pointDataList.addAll(convertWikidataToPointData(wikidataResults))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return pointDataList
    }
    
    private fun convertWikidataToPointData(wikidataPlaces: List<WikidataPlace>): List<PointData> {
        return wikidataPlaces.map { place ->
            // Create description with Wikipedia links
            val description = if (place.wikipediaLinks.isNotEmpty()) {
                "Wikipedia articles: ${place.wikipediaLinks}"
            } else {
                ""
            }
            
            PointData(
                name = place.label,
                coordinates = Coordinates(place.latitude, place.longitude),
                description = description
            )
        }
    }
    
    private fun getCountryCode(coordinates: Coordinates): String {
        return try {
            val url = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${coordinates.latitude}&longitude=${coordinates.longitude}&localityLanguage=en"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "WikipediaGpxGenerator/1.0")
            
            val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val json = JSONObject(response)
            
            json.optString("countryCode", "US").lowercase()
        } catch (e: Exception) {
            "us" // Default fallback
        }
    }
    
    private fun queryWikidataPlaces(coordinates: Coordinates, radiusKm: Double): List<WikidataPlace> {
        val places = mutableListOf<WikidataPlace>()
        
        try {
            val sparqlQuery = """
            SELECT ?item ?itemLabel ?lat ?lon (GROUP_CONCAT(DISTINCT ?sitelink; separator=", ") AS ?sitelinks) WHERE {
              SERVICE wikibase:around {
                ?item wdt:P625 ?location.
                bd:serviceParam wikibase:center "Point(${coordinates.longitude},${coordinates.latitude})"^^geo:wktLiteral.
                bd:serviceParam wikibase:radius  "${radiusKm}" .
              }
              ?item wdt:P625 ?coord .
              ?item p:P625/psv:P625 ?coordinate_node .
              ?coordinate_node wikibase:geoLatitude ?lat .
              ?coordinate_node wikibase:geoLongitude ?lon .
            
              ?sitelink schema:about ?item .
              ?sitelink schema:isPartOf ?wiki .
              FILTER(CONTAINS(STR(?wiki), "wikipedia.org"))
            
              SERVICE wikibase:label { bd:serviceParam wikibase:language "${systemLanguage},en,[AUTO_LANGUAGE]". }
            }
            GROUP BY ?item ?itemLabel ?lat ?lon
            """.trimIndent()
            
            val encodedQuery = URLEncoder.encode(sparqlQuery, "UTF-8")
            val url = "https://query.wikidata.org/sparql?query=$encodedQuery&format=json"
            
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "WikipediaGpxGenerator/1.0")
            connection.setRequestProperty("Accept", "application/json")
            
            val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val json = JSONObject(response)
            
            val bindings = json.getJSONObject("results").getJSONArray("bindings")
            
            for (i in 0 until bindings.length()) {
                val binding = bindings.getJSONObject(i)
                
                val label = binding.getJSONObject("itemLabel").getString("value")
                val wikiLinks = binding.getJSONObject("sitelinks").getString("value")

                // Extract coordinates from separate lat/lon fields
                val lat = binding.getJSONObject("lat").getDouble("value")
                val lon = binding.getJSONObject("lon").getDouble("value")
                
                places.add(WikidataPlace( label, lat, lon, wikiLinks))
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return places
    }
    
    private data class WikidataPlace(
        val label: String,
        val latitude: Double,
        val longitude: Double,
        val wikipediaLinks: String
    )
}