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

class WikipediaArticlesGpxGenerator : GpxGeneratorBase() {
    
    private val systemLanguage = Locale.getDefault().language
    private val nominatimService = NominatimService()
    
    override fun getData(coordinates: Coordinates, radiusMeters: Int): List<PointData> {
        val pointDataList = mutableListOf<PointData>()
        
        try {
            val countryLanguages = getCountryLanguagesFromCoordinates(coordinates)
            val wikidataResults = queryWikidataPlaces(coordinates, radiusMeters / 1000.0)
            // Convert Wikidata results to PointData
            pointDataList.addAll(convertWikidataToPointData(wikidataResults, countryLanguages))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return pointDataList
    }

    private fun getCountryLanguagesFromCoordinates(coordinates: Coordinates): List<String> {
        return nominatimService.getCountryLanguages(coordinates)
    }
    
    private fun convertWikidataToPointData(wikidataPlaces: List<WikidataPlace>, countryLanguages: List<String>): List<PointData> {
        return wikidataPlaces.map { place ->
            // Create description with Wikipedia links
            val description = if (place.wikipediaLinks.isNotEmpty()) {
                val formattedLinks = formatWikipediaLinks(place.wikipediaLinks, countryLanguages)
                "Instance of:\n${place.instanceOf}; " +
                "\n\nWikipedia articles:\n${formattedLinks}"
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
    
    private fun formatWikipediaLinks(wikipediaLinks: String, countryLanguages: List<String>): String {
        if (wikipediaLinks.isEmpty()) return ""
        
        // Parse individual Wikipedia links from comma-separated string
        val links = wikipediaLinks.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
        
        // Sort links to prioritize system language first, then country languages
        val sortedLinks = links.sortedWith { link1, link2 ->
            val isSystemLang1 = link1.contains("${systemLanguage}.wikipedia.org")
            val isSystemLang2 = link2.contains("${systemLanguage}.wikipedia.org")
            
            val isCountryLang1 = countryLanguages.any { link1.contains("${it}.wikipedia.org") }
            val isCountryLang2 = countryLanguages.any { link2.contains("${it}.wikipedia.org") }
            
            when {
                isSystemLang1 && !isSystemLang2 -> -1  // link1 is system language, comes first
                !isSystemLang1 && isSystemLang2 -> 1   // link2 is system language, comes first
                isCountryLang1 && !isCountryLang2 -> -1 // link1 is country language
                !isCountryLang1 && isCountryLang2 -> 1  // link2 is country language
                else -> 0  // keep original order for other links
            }
        }
        
        // Join links with empty lines between them
        return sortedLinks.joinToString("\n\n")
    }
    
    private fun queryWikidataPlaces(coordinates: Coordinates, radiusKm: Double): List<WikidataPlace> {
        val places = mutableListOf<WikidataPlace>()
        
        try {
            val sparqlQuery = """
            SELECT ?item ?itemLabel ?lat ?lon 
            (GROUP_CONCAT(DISTINCT ?sitelink; separator=", ") AS ?sitelinks)
            (GROUP_CONCAT(DISTINCT ?instanceOfLabel; separator=", ") AS ?instanceOfLabels)
            WHERE {
                    {
                     SELECT ?item ?lat ?lon ?sitelink ?instanceOfLabel
                     WHERE {
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
            connection.setRequestProperty("User-Agent", "WikipediaGpxGenerator/1.0")
            connection.setRequestProperty("Accept", "application/json")
            
            val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val json = JSONObject(response)
            
            val bindings = json.getJSONObject("results").getJSONArray("bindings")
            
            for (i in 0 until bindings.length()) {
                val binding = bindings.getJSONObject(i)
                
                val label = binding.getJSONObject("itemLabel").getString("value")
                val wikiLinks = binding.getJSONObject("sitelinks").getString("value")
                val instanceOf = binding.getJSONObject("instanceOfLabels").getString("value")

                // Extract coordinates from separate lat/lon fields
                val lat = binding.getJSONObject("lat").getDouble("value")
                val lon = binding.getJSONObject("lon").getDouble("value")
                
                places.add(WikidataPlace( label, lat, lon, wikiLinks, instanceOf))
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
        val wikipediaLinks: String,
        val instanceOf : String
    )
}