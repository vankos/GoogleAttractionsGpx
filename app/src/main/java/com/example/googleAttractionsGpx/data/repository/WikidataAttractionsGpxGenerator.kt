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

class WikidataAttractionsGpxGenerator(private val radius: Double = 5.0) : GpxGeneratorBase() {
    
    private val systemLanguage = Locale.getDefault().language
    
    override fun getData(coordinates: Coordinates): List<PointData> {
        val pointDataList = mutableListOf<PointData>()
        
        try {
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
            val description = buildString {
                if (place.instanceOf.isNotEmpty()) {
                    append("Instance of: ${place.instanceOf}")
                    append("\n\n")
                }
                append("Wikidata Item: ${place.itemUrl}")
            }
            
            PointData(
                name = place.label,
                coordinates = Coordinates(place.latitude, place.longitude),
                description = description
            )
        }
    }
    
    private fun queryWikidataPlaces(coordinates: Coordinates, radiusKm: Double): List<WikidataPlace> {
        val places = mutableListOf<WikidataPlace>()
        
        try {
            val sparqlQuery = """
            SELECT ?item ?itemLabel ?lat ?lon 
            (GROUP_CONCAT(DISTINCT ?instanceOfLabel; separator=", ") AS ?instanceOfLabels)
            WHERE {
                {
                    SELECT ?item ?lat ?lon ?instanceOfLabel
                    WHERE {
                        SERVICE wikibase:around {
                          ?item wdt:P625 ?location.
                          bd:serviceParam wikibase:center "Point(${coordinates.longitude},${coordinates.latitude})"^^geo:wktLiteral.
                          bd:serviceParam wikibase:radius  "${radiusKm}" .
                        }
                        ?item wdt:P31/wdt:P279* wd:Q570116 .
                        
                        ?item p:P625/psv:P625 ?coordinate_node .
                        ?coordinate_node wikibase:geoLatitude ?lat .
                        ?coordinate_node wikibase:geoLongitude ?lon .
                        
                        OPTIONAL { ?item wdt:P31 ?instanceOf . }
                        
                        SERVICE wikibase:label { bd:serviceParam wikibase:language "${systemLanguage},en,[AUTO_LANGUAGE]". }
                    }
                }
                SERVICE wikibase:label { bd:serviceParam wikibase:language "${systemLanguage},en,[AUTO_LANGUAGE]". }
            }
            GROUP BY ?item ?itemLabel ?lat ?lon
            """.trimIndent()
            
            val encodedQuery = URLEncoder.encode(sparqlQuery, "UTF-8")
            val url = "https://query.wikidata.org/sparql?query=$encodedQuery&format=json"
            
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "WikidataAttractionsGpxGenerator/1.0")
            connection.setRequestProperty("Accept", "application/json")
            
            val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val json = JSONObject(response)
            
            val bindings = json.getJSONObject("results").getJSONArray("bindings")
            
            for (i in 0 until bindings.length()) {
                val binding = bindings.getJSONObject(i)
                
                val label = binding.optJSONObject("itemLabel")?.getString("value") ?: "Unknown"
                val itemUrl = binding.getJSONObject("item").getString("value")
                val instanceOf = binding.optJSONObject("instanceOfLabels")?.getString("value") ?: ""

                // Extract coordinates from separate lat/lon fields
                val lat = binding.getJSONObject("lat").getDouble("value")
                val lon = binding.getJSONObject("lon").getDouble("value")
                
                places.add(WikidataPlace(label, lat, lon, itemUrl, instanceOf))
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
        val itemUrl: String,
        val instanceOf : String
    )
}
