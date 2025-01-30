package com.example.googleAttractionsGpx

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Call
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.Charset
import kotlin.math.PI
import kotlin.math.cos

class MainActivity : ComponentActivity() {

    // Request location permission
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            // You can add logic here for granted/not granted permission
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if permission is granted; if not, request it
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        setContent {
            GpxGeneratorScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpxGeneratorScreen() {
    val context = LocalContext.current

    // We only store the API Key in SharedPreferences
    val sharedPrefs = remember {
        context.getSharedPreferences("MY_APP_PREFS", Context.MODE_PRIVATE)
    }

    // Coordinates
    var coordinatesText by remember { mutableStateOf(TextFieldValue("")) }
    // Google API Key
    var googleApiKeyText by remember { mutableStateOf(TextFieldValue("")) }
    // TripAdvisor API Key
    var tripAdvisorApiKeyText by remember { mutableStateOf(TextFieldValue("")) }
    // Final GPX result
    var gpxResult by remember { mutableStateOf("") }

    // On first screen launch, read the saved key and fill the field
    LaunchedEffect(Unit) {
        val googleKey = sharedPrefs.getString("API_KEY", "") ?: ""
        googleApiKeyText = TextFieldValue(googleKey)

        val tripKey = sharedPrefs.getString("TRIP_ADVISOR_API_KEY", "") ?: ""
        tripAdvisorApiKeyText = TextFieldValue(tripKey)
    }

    // Function to get the current coordinates using FusedLocationProviderClient
    fun fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        coordinatesText = TextFieldValue("${it.latitude},${it.longitude}")
                    }
                }
        }
    }

    // 1) Function to request Google Places API and generate GPX
    fun generateGpx() {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            withContext(Dispatchers.Main) {
                gpxResult = "Loading Google Places..."
            }
            val coords = coordinatesText.text.trim()
            val apiKey = googleApiKeyText.text.trim()
            if (coords.isNotEmpty() && apiKey.isNotEmpty()) {
                try {
                    // Request the list of places via Places API
                    val places = fetchPlacesByGrid(coords, apiKey)

                    // Convert results to GPX
                    val gpxString = convertPlacesToGpx(places)
                    val fileName = getFileName(coords, "Google")
                    val file = File(context.getExternalFilesDir(null), fileName)
                    file.writeText(gpxString, Charset.defaultCharset())
                    val uri: Uri = FileProvider.getUriForFile(
                        context,
                        "com.example.googleAttractionsGpx.fileProvider",
                        file
                    )

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/octet-stream")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    withContext(Dispatchers.Main) {
                        gpxResult = "Google Places GPX created."
                    }

                    context.startActivity(Intent.createChooser(intent, "Open GPX"))
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        gpxResult = "Error loading Google Places: ${e.message}"
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    gpxResult = "Please provide coordinates and an API key."
                }
            }
        }
    }

    // 2) Function to request Overpass Turbo API and generate GPX
    fun generateOsmGpx() {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            withContext(Dispatchers.Main) {
                gpxResult = "Loading OSM data..."
            }
            val coords = coordinatesText.text.trim()
            if (coords.isNotEmpty()) {
                try {
                    // Parse lat/lon
                    val (lat, lng) = coords.split(",").map { it.toDouble() }
                    // For example, we can use the same 4000m radius
                    val radius = 5000

                    val osmPlaces = fetchOverpassAttractions(lat, lng, radius)

                    // Convert results to GPX
                    val gpxString = convertOsmToGpx(osmPlaces)
                    val fileName = getFileName(coords, "OSM")
                    val file = File(context.getExternalFilesDir(null), fileName)
                    file.writeText(gpxString, Charset.defaultCharset())
                    val uri: Uri = FileProvider.getUriForFile(
                        context,
                        "com.example.googleAttractionsGpx.fileProvider",
                        file
                    )

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/octet-stream")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    withContext(Dispatchers.Main) {
                        gpxResult = "OSM GPX created."
                    }

                    context.startActivity(Intent.createChooser(intent, "Open OSM GPX"))
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        gpxResult = "Error loading OSM: ${e.message}"
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    gpxResult = "Please provide coordinates."
                }
            }
        }
    }

    // --- 3) TripAdvisor ---
    fun generateGpxTripAdvisor() {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            withContext(Dispatchers.Main) { gpxResult = "Loading TripAdvisor data..." }

            val coords = coordinatesText.text.trim()
            val apiKey = tripAdvisorApiKeyText.text.trim()
            if (coords.isNotEmpty() && apiKey.isNotEmpty()) {
                try {
                    val places = fetchTripAdvisorByGrid(coords, apiKey)
                    val gpxString = convertPlacesToGpx(places)

                    val fileName = getFileName(coords, "TripAdvisor")
                    val file = File(context.getExternalFilesDir(null), fileName)
                    file.writeText(gpxString, Charset.defaultCharset())

                    val uri = FileProvider.getUriForFile(
                        context, "com.example.googleAttractionsGpx.fileProvider", file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/octet-stream")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    withContext(Dispatchers.Main) {
                        gpxResult = "TripAdvisor GPX created."
                        context.startActivity(Intent.createChooser(intent, "Open GPX"))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        gpxResult = "Error: ${e.message}"
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    gpxResult = "Please provide coordinates and TripAdvisor API key."
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("GPX Generator") })
        }
    ) {
        Column(
            Modifier
                .padding(it)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            OutlinedTextField(
                value = coordinatesText,
                onValueChange = { newValue -> coordinatesText = newValue },
                label = { Text("Coordinates (lat,lng)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { fetchCurrentLocation() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Current coordinates")
            }

            // API Key field with saving to SharedPreferences
            OutlinedTextField(
                value = googleApiKeyText,
                onValueChange = { newValue ->
                    googleApiKeyText = newValue
                    // Each time it changes, we save it to SharedPreferences
                    with(sharedPrefs.edit()) {
                        putString("API_KEY", newValue.text)
                        apply()  // better use apply() to avoid blocking the UI thread
                    }
                },
                label = { Text("Places API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
            )

            // TripAdvisor API Key
            OutlinedTextField(
                value = tripAdvisorApiKeyText,
                onValueChange = {
                    tripAdvisorApiKeyText = it
                    with(sharedPrefs.edit()) {
                        putString("TRIP_ADVISOR_API_KEY", it.text)
                        apply()
                    }
                },
                label = { Text("TripAdvisor API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
            )

            // Button for Google Places GPX
            Button(
                onClick = { generateGpx() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate GPX (Google)")
            }

            // Button for OSM Overpass GPX
            Button(
                onClick = { generateOsmGpx() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate GPX (OSM)")
            }

            // Button for TripAdvisor
            Button(
                onClick = { generateGpxTripAdvisor() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate GPX (TripAdvisor)")
            }

            // Display the result (GPX) as text (or status info)
            Text(
                text = gpxResult,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================================================================
// 1) Google Places logic (grid approach) & data class
// ==================================================================
suspend fun fetchPlacesByGrid(coords: String, apiKey: String): List<PlaceInfo> {
    val (centerLat, centerLng) = coords.split(",").map { it.toDouble() }

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

            delay(50)
        }
    }

    return results.toList()
}

fun fetchNearbyPlacesSinglePage(
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

    val response = URL(urlString).readText()
    val jsonObject = JSONObject(response)

    val resultsArray = jsonObject.optJSONArray("results") ?: return emptyList()
    val placeList = mutableListOf<PlaceInfo>()

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

    return placeList
}

fun convertPlacesToGpx(places: List<PlaceInfo>): String {
    val sb = StringBuilder()
    sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append("\n")
    sb.append("""<gpx version="1.1" creator="ComposeGpxApp" xmlns="http://www.topografix.com/GPX/1/1">""")
        .append("\n")

    places.forEach { place ->
        sb.append("""  <wpt lat="${place.latitude}" lon="${place.longitude}">""").append("\n")
        val escapedName = place.name.replace("&", "&amp;")
        sb.append("""    <name>$escapedName</name>""").append("\n")
        // Insert rating, total number of reviews, and link into the description
        val escapedLink = place.mapsLink.replace("&", "&amp;")
        sb.append("""    <desc>Rating: ${place.rating}, Reviews: ${place.userRatingsTotal}, Link: $escapedLink</desc>""")
            .append("\n")
        sb.append("""  </wpt>""").append("\n")
    }

    sb.append("</gpx>")
    return sb.toString()
}

data class PlaceInfo(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val userRatingsTotal: Int,
    val mapsLink: String
)

/**
 * Simplified version: do a POST request to Overpass using standard Java.
 * We'll parse the JSON and gather results.
 */
@Suppress("BlockingMethodInNonBlockingContext")
suspend fun fetchOverpassAttractionsSimple(lat: Double, lon: Double, radius: Int): List<OsmPlace> {
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
           ["historic"!="wayside_cross"](around:$radius,$lat,$lon);
        nwr[attraction=maze](around:$radius,$lat,$lon);
        nwr[geological=outcrop](around:$radius,$lat,$lon);
        nwr[geological=palaeontological_site](around:$radius,$lat,$lon);
        nwr[leisure=bird_hide](around:$radius,$lat,$lon);
        nwr[highway=trailhead](around:$radius,$lat,$lon);
        nwr[tourism=viewpoint](around:$radius,$lat,$lon);
        nwr[tourism=zoo](around:$radius,$lat,$lon);
        nwr["aerialway"](around:$radius,$lat,$lon);
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

suspend fun fetchOverpassAttractions(lat: Double, lon: Double, radius: Int): List<OsmPlace> {
    return fetchOverpassAttractionsSimple(lat, lon, radius)
}

/** Parse the JSON response from Overpass to a list of OsmPlace */
fun parseOverpassJson(jsonResponse: String): List<OsmPlace> {
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

        val finalName = when {
            nameRu.isNotBlank() -> nameRu
            nameEn.isNotBlank() -> nameEn
            nameDefault.isNotBlank() -> nameDefault
            else -> "No name"
        }

        val descBuilder = StringBuilder()
        val keys = tags.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = tags.optString(k)
            descBuilder.append("$k=$v;&lt;br&gt;")
        }

        val googleLink = "https://www.google.com/maps?q=$lat,$lon"
        descBuilder.append("Link: $googleLink")

        result.add(OsmPlace(finalName, lat, lon, descBuilder.toString()))
    }

    return result
}

/**
 * Convert Overpass (OSM) places to GPX format
 */
fun convertOsmToGpx(places: List<OsmPlace>): String {
    val sb = StringBuilder()
    sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append("\n")
    sb.append("""<gpx version="1.1" creator="OSM GPX Generator" xmlns="http://www.topografix.com/GPX/1/1">""").append("\n")

    for (place in places) {
        sb.append("""  <wpt lat="${place.lat}" lon="${place.lon}">""").append("\n")
        val escapedName = place.name.replace("&", "&amp;")
        sb.append("""    <name>$escapedName</name>""").append("\n")
        val escapedDesc = place.description.replace("&", "&amp;")
        sb.append("""    <desc>$escapedDesc</desc>""").append("\n")
        sb.append("""  </wpt>""").append("\n")
    }

    sb.append("</gpx>")
    return sb.toString()
}

suspend fun fetchTripAdvisorByGrid(coords: String, apiKey: String): List<PlaceInfo> {
    val (centerLat, centerLng) = coords.split(",").map { it.toDouble() }

    val halfSideMeters = 4000.0
    val stepMeters = 4000.0
    val requestRadius = 20000

    val latDegPerMeter = 1.0 / 111320.0
    val cosLat = cos(centerLat * PI / 180.0)
    val lonDegPerMeter = 1.0 / (111320.0 * cosLat)

    val results = mutableSetOf<PlaceInfo>()
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
                cellLat, cellLon, requestRadius, apiKey
            )
            results.addAll(placesInCell)
        }
    }
    return results.toList()
}


suspend fun fetchTripAdvisorSinglePage(
    latitude: Double,
    longitude: Double,
    radius: Int,
    apiKey: String
): List<PlaceInfo> {
    val nearbyUrl = "https://api.content.tripadvisor.com/api/v1/location/nearby_search" +
            "?latLong=$latitude,$longitude" +
            "&category=attractions" +
            "&radius=$radius" +
            "&radiusUnit=m" +
            "&language=en" +
            "&key=$apiKey"

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

    val results = mutableListOf<PlaceInfo>()
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
        // Если нужно, проверяем наличие ошибок

        val name = detailsJson.optString("name", "No name")
        val latStr = detailsJson.optString("latitude", "0.0")
        val lngStr = detailsJson.optString("longitude", "0.0")
        val latVal = latStr.toDoubleOrNull() ?: 0.0
        val lngVal = lngStr.toDoubleOrNull() ?: 0.0

        val ratingStr = detailsJson.optString("rating", "0.0")
        val ratingVal = ratingStr.toDoubleOrNull() ?: 0.0

        val reviewsStr = detailsJson.optString("num_reviews", "0")
        val reviewsVal = reviewsStr.toIntOrNull() ?: 0

        if (ratingVal < 4.0 || reviewsVal < 10){
            continue
        }

        // Для ссылки можно взять Google Maps:
        val mapsLink = "https://www.google.com/maps?q=$latVal,$lngVal"

        results.add(
            PlaceInfo(
                name = name,
                latitude = latVal,
                longitude = lngVal,
                rating = ratingVal,
                userRatingsTotal = reviewsVal,
                mapsLink = mapsLink
            )
        )

        delay(50)
    }

    return results
}

/** Simple OSM place model for Overpass results */
data class OsmPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
    val description: String
)

private fun getFileName(coords: String, prefix: String): String {
    val locationName: String? = runBlocking {
        getLocationNameFromCoordinates(coords)
    }
    val now = java.time.LocalDateTime.now().toString().replace(":", "-")
    return "${prefix}_${locationName}_$now.gpx"
}

    suspend fun getLocationNameFromCoordinates(coords: String): String? {
        val (lat, lng) = coords.split(",").map { it.toDouble() }
        val queryUrl = "https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lng}&format=json&accept-language=${Locale.current}"
        return try {
            val jsonResponse = withContext(Dispatchers.IO) {
                URL(queryUrl).readText()
            }

            val jsonObject = JSONObject(jsonResponse)
            getLocationNameFromLocationInfo(jsonObject)
        } catch (ex: Exception) {
            null
        }
    }

    private fun getLocationNameFromLocationInfo(jsonObject: JSONObject?): String? {
        val address = jsonObject?.optJSONObject("address")
        val district = address?.optString("city_district", "")?: ""
        val city = address?.optString("city", "")?: ""
        val state = address?.optString("state", "")?: ""
        val country = address?.optString("country", "")?: ""
        val displayName = address?.optString("display_name", "")?: ""
        val final = when {

            district.isNotBlank() -> district
            city.isNotBlank() -> city
            state.isNotBlank() -> state
            country.isNotBlank() -> country
            displayName.isNotBlank() -> displayName
            else -> ""
        }

        return final
    }

