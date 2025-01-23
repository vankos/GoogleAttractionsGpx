package com.example.googleatractionsgpx

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
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    // API Key
    var apiKeyText by remember { mutableStateOf(TextFieldValue("")) }
    // Final GPX result
    var gpxResult by remember { mutableStateOf("") }

    // On first screen launch, read the saved key and fill the field
    LaunchedEffect(Unit) {
        val savedKey = sharedPrefs.getString("API_KEY", "") ?: ""
        apiKeyText = TextFieldValue(savedKey)
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

    // Function to request Google Places API and generate GPX
    fun generateGpx() {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            withContext(Dispatchers.Main) {
                gpxResult = "Loading"
            }
            val coords = coordinatesText.text.trim()
            val apiKey = apiKeyText.text.trim()
            if (coords.isNotEmpty() && apiKey.isNotEmpty()) {
                try {
                    // Request the list of places via Places API
                    // Split into ~500m grid and gather all places
                    val places = fetchPlacesByGrid(coords, apiKey)

                    // Convert results to GPX
                    val gpxString = convertPlacesToGpx(places)
                    val fileName = getFileName()
                    val file = File(context.getExternalFilesDir(null), fileName)
                    file.writeText(gpxString, Charset.defaultCharset())
                    val uri: Uri = FileProvider.getUriForFile(
                        context,
                        "com.example.googleatractionsgpx.fileProvider",
                        file
                    )

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/octet-stream")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    withContext(Dispatchers.Main) {
                        gpxResult = "Done"
                    }

                    context.startActivity(Intent.createChooser(intent, "Open test.gpx"))
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        gpxResult = "Error loading: ${e.message}"
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    gpxResult = "Please provide coordinates and an API key."
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
                label = { Text("Координаты (lat,lng)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { fetchCurrentLocation() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Текущие координаты")
            }

            // Поле для API Key с сохранением в SharedPreferences
            OutlinedTextField(
                value = apiKeyText,
                onValueChange = { newValue ->
                    apiKeyText = newValue
                    // Каждый раз при изменении – сохраняем в SharedPreferences
                    with(sharedPrefs.edit()) {
                        putString("API_KEY", newValue.text)
                        apply()  // лучше apply(), чтобы не блокировать UI-поток
                    }
                },
                label = { Text("Places API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
            )

            Button(
                onClick = { generateGpx() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate GPX")
            }

            // Display the result (GPX) as text
            Text(
                text = gpxResult,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================================================================
// Functions for the "grid", queries to Places, and GPX generation
// ==================================================================

/**
 * Splits the area ±4000m from the center into 500m squares,
 * and for each point makes a request with ~300–500m radius.
 * Collects results into a Set (removing duplicates), then returns them as a List.
 */
suspend fun fetchPlacesByGrid(coords: String, apiKey: String): List<PlaceInfo> {
    val (centerLat, centerLng) = coords.split(",").map { it.toDouble() }

    // Grid parameters
    val halfSideMeters = 4000.0  // ±4000m from the center (8km total)
    val stepMeters = 500.0       // cell size = 500m
    val requestRadius = 500      // Google Places radius for each point

    // 1 degree of latitude is ~111,320m
    val latDegreePerMeter = 1.0 / 111320.0
    // For longitude, multiply by cos(latitude)
    val cosLat = cos(centerLat * PI / 180.0)
    val lonDegreePerMeter = 1.0 / (111320.0 * cosLat)

    val results = mutableSetOf<PlaceInfo>()

    // Calculate how many steps in each direction
    val stepsCount = ((2 * halfSideMeters) / stepMeters).toInt()
    // e.g. for 2000 / 500 = 4, but we'll loop 0..4

    for (i in 0..stepsCount) {
        val offsetMetersLat = -halfSideMeters + i * stepMeters
        val offsetLatDegrees = offsetMetersLat * latDegreePerMeter

        for (j in 0..stepsCount) {
            val offsetMetersLon = -halfSideMeters + j * stepMeters
            val offsetLonDegrees = offsetMetersLon * lonDegreePerMeter

            // Calculate the "cell" coordinates
            val cellLat = centerLat + offsetLatDegrees
            val cellLon = centerLng + offsetLonDegrees

            // Make a request for this cell
            val placesInCell = fetchNearbyPlacesSinglePage(
                latitude = cellLat,
                longitude = cellLon,
                radius = requestRadius,
                type = "tourist_attraction",
                apiKey = apiKey
            )
            results.addAll(placesInCell)

            // Small delay so as not to spam Google with too many requests at once
            delay(150)
        }
    }

    return results.toList()
}

/**
 * Makes a single Places Nearby Search request for one "cell"
 * (no paging, returns up to 20 results).
 */
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

    // Build the URL. If needed, add &keyword=..., &language=..., etc.
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

        // For Google Maps link you can use https://maps.google.com/?q=PLACE_ID
        // or https://www.google.com/maps/place/?q=place_id:PLACE_ID
        val rawLink = "https://www.google.com/maps/search/?api=1&query=Google&query_place_id=$placeId"

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

/**
 * Converts a list of places into a GPX format string
 */
fun convertPlacesToGpx(places: List<PlaceInfo>): String {
    // Header for the standard GPX 1.1
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

/**
 * Data model to store the result from Google Places
 */
data class PlaceInfo(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val userRatingsTotal: Int,
    val mapsLink: String
)

private fun getFileName(): String {
    val fileNamePrefix = "Google attractions "
    val fileName = "$fileNamePrefix${java.time.LocalDateTime.now()}.gpx"
    return fileName
}
