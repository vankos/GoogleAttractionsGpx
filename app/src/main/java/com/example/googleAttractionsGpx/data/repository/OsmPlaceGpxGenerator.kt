package com.example.googleAttractionsGpx.data.repository

import com.example.googleAttractionsGpx.domain.models.Coordinates
import com.example.googleAttractionsGpx.domain.models.PointData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charsets

class OsmPlaceGpxGenerator(
    private val centerCoordinates: Coordinates,
    private val radius: Int = 5000
) : GpxGeneratorBase() {

    override suspend fun getData(): List<PointData> {
        val osmPlaces = fetchOverpassAttractions(
            centerCoordinates.latitude,
            centerCoordinates.longitude,
            radius
        )
        
        return osmPlaces.map { osmPlace ->
            PointData(
                coordinates = Coordinates(osmPlace.lat, osmPlace.lon),
                name = osmPlace.name,
                description = osmPlace.description
            )
        }
    }

    private suspend fun fetchOverpassAttractions(lat: Double, lon: Double, radius: Int): List<OsmPlace> {
        // Build the Overpass QL query
        val query = """
            [out:json];
            (node["tourism"="attraction"](around:$radius,$lat,$lon);
            way["tourism"="attraction"](around:$radius,$lat,$lon);
            relation["tourism"="attraction"](around:$radius,$lat,$lon);
            nwr["leisure"="park"](around:$radius,$lat,$lon);
            nwr["amenity"="feeding place"](around:$radius,$lat,$lon);
            nwr["landuse"="village_green"](around:$radius,$lat,$lon);
            nwr["man_made"="lighthouse"](around:$radius,$lat,$lon);
            nwr[natural=anthill](around:$radius,$lat,$lon);
            nwr[natural=sinkhole](around:$radius,$lat,$lon);
            nwr[natural=arch](around:$radius,$lat,$lon);
            nwr[natural=bay](around:$radius,$lat,$lon);
            nwr[natural=cape](around:$radius,$lat,$lon);
            nwr[natural=couloir](around:$radius,$lat,$lon);
            nwr[natural=crater](around:$radius,$lat,$lon);
            nwr[natural=dune](around:$radius,$lat,$lon);
            nwr[natural=fumarole](around:$radius,$lat,$lon);
            nwr[natural=geyser](around:$radius,$lat,$lon);
            nwr[natural=glacier](around:$radius,$lat,$lon);
            nwr[natural=hot_spring](around:$radius,$lat,$lon);
            nwr[leisure=nature_reserve](around:$radius,$lat,$lon);
            nwr[boundary=protected_area](around:$radius,$lat,$lon);
            nwr[natural=reef](around:$radius,$lat,$lon);
            nwr[natural=stone](around:$radius,$lat,$lon);
            nwr[natural=termite_mound](around:$radius,$lat,$lon);
            nwr[natural=valley](around:$radius,$lat,$lon);
            nwr[natural=volcano](around:$radius,$lat,$lon);
            nwr[waterway=waterfall](around:$radius,$lat,$lon);
            nwr[tourism=attraction](around:$radius,$lat,$lon);
            nwr[route=foot](around:$radius,$lat,$lon);
            nwr[route=hiking](around:$radius,$lat,$lon);
            nwr[tourism=aquarium](around:$radius,$lat,$lon);
            nwr[attraction=animal](around:$radius,$lat,$lon);
            nwr[historic=archaeological_site](around:$radius,$lat,$lon);
            nwr[historic=battlefield](around:$radius,$lat,$lon);
            nwr[historic=boundary_stone](around:$radius,$lat,$lon);
            nwr[historic=castle](around:$radius,$lat,$lon);
            nwr[historic=city_gate](around:$radius,$lat,$lon);
            nwr[barrier=city_wall](around:$radius,$lat,$lon);
            nwr["abandoned:amenity"="prison_camp"](around:$radius,$lat,$lon);
            nwr[man_made=geoglyph](around:$radius,$lat,$lon);
            nwr[tourism=hanami](around:$radius,$lat,$lon);
            nwr["historic"]
               ["historic"!="memorial"]
               ["historic"!="wayside_shrine"]
               ["historic"!="wayside_cross"](around:$radius,$lat,$lon);
            nwr[attraction=maze](around:$radius,$lat,$lon);
            nwr[geological=outcrop](around:$radius,$lat,$lon);
            nwr[geological=palaeontological_site](around:$radius,$lat,$lon);
            nwr[leisure=bird_hide](around:$radius,$lat,$lon);
            nwr[highway=trailhead](around:$radius,$lat,$lon);
            nwr[tourism=viewpoint](around:$radius,$lat,$lon);
            nwr[tourism=zoo](around:$radius,$lat,$lon);
            nwr["aerialway"]
               ["aerialway"!="pylon"](around:$radius,$lat,$lon);
            nwr[railway=funicular](around:$radius,$lat,$lon);
            nwr[tourism=artwork]
               ["artwork_type"!="statue"]
               ["artwork_type"!="bust"](around:$radius,$lat,$lon);
            );
            out center;
        """.trimIndent()

        val url = URL("https://overpass-api.de/api/interpreter")
        return withContext(Dispatchers.IO) {
            // Do a POST request with the query in the request body
            val connection = url.openConnection()
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.connect()

            connection.getOutputStream().use { output ->
                // Overpass interprets "data" param as the query
                val postData = "data=" + URLEncoder.encode(query, "UTF-8")
                output.write(postData.toByteArray(Charsets.UTF_8))
            }

            val response = connection.getInputStream().use { it.readBytes().toString(Charsets.UTF_8) }

            parseOverpassJson(response)
        }
    }

    /** Parse the JSON response from Overpass to a list of OsmPlace */
    private fun parseOverpassJson(jsonResponse: String): List<OsmPlace> {
        val result = mutableListOf<OsmPlace>()
        val obj = JSONObject(jsonResponse)
        val elements = obj.optJSONArray("elements") ?: return emptyList()

        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val type = el.optString("type") // node, way, relation
            val tags = el.optJSONObject("tags") ?: JSONObject()

            // lat/lon might be "lat","lon" for nodes
            // or "center.lat","center.lon" for ways/relations
            val lat = if (el.has("lat")) el.optDouble("lat", 0.0)
            else el.optJSONObject("center")?.optDouble("lat", 0.0) ?: 0.0
            val lon = if (el.has("lon")) el.optDouble("lon", 0.0)
            else el.optJSONObject("center")?.optDouble("lon", 0.0) ?: 0.0

            val nameRu = tags.optString("name:ru", "")
            val nameEn = tags.optString("name:en", "")
            val nameDefault = tags.optString("name", "")

            // Priority: Russian, then English, then default
            val name = when {
                nameRu.isNotBlank() -> nameRu
                nameEn.isNotBlank() -> nameEn
                nameDefault.isNotBlank() -> nameDefault
                else -> "Unknown Place"
            }

            // Build description from available tags
            val descriptionParts = mutableListOf<String>()
            
            // Add tourism type if available
            val tourism = tags.optString("tourism", "")
            if (tourism.isNotBlank()) {
                descriptionParts.add("Tourism: $tourism")
            }
            
            // Add leisure type if available
            val leisure = tags.optString("leisure", "")
            if (leisure.isNotBlank()) {
                descriptionParts.add("Leisure: $leisure")
            }
            
            // Add historic type if available
            val historic = tags.optString("historic", "")
            if (historic.isNotBlank()) {
                descriptionParts.add("Historic: $historic")
            }
            
            // Add natural feature if available
            val natural = tags.optString("natural", "")
            if (natural.isNotBlank()) {
                descriptionParts.add("Natural: $natural")
            }
            
            // Add description tag if available
            val description = tags.optString("description", "")
            if (description.isNotBlank()) {
                descriptionParts.add(description)
            }

            val finalDescription = if (descriptionParts.isNotEmpty()) {
                descriptionParts.joinToString("; ")
            } else {
                "OSM Place"
            }

            if (lat != 0.0 && lon != 0.0 && name.isNotBlank()) {
                result.add(OsmPlace(name, lat, lon, finalDescription))
            }
        }

        return result
    }

    private data class OsmPlace(
        val name: String,
        val lat: Double,
        val lon: Double,
        val description: String
    )
}
