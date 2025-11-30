package com.example.googleAttractionsGpx.presentation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.app.AlertDialog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.example.googleAttractionsGpx.data.repository.SettingsRepositoryImpl
import com.example.googleAttractionsGpx.domain.repository.SettingsRepository
import com.example.googleAttractionsGpx.data.repository.GooglePlaceGpxGenerator
import com.example.googleAttractionsGpx.data.repository.OsmPlaceGpxGenerator
import com.example.googleAttractionsGpx.data.repository.TripAdvisorGpxGenerator
import com.example.googleAttractionsGpx.data.repository.WikipediaArticlesGpxGenerator
import com.example.googleAttractionsGpx.data.repository.NominatimService
import com.example.googleAttractionsGpx.domain.models.Coordinates
import com.example.googleAttractionsGpx.domain.repository.IGpxGenerator
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import kotlin.math.PI
import kotlin.math.cos

import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {

    // Request location permission
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted){
            // Show an alert dialog if permission is not granted
            AlertDialog.Builder(this)
                .setTitle("Permission Denied")
                .setMessage("Location permission is required to use this app. Please grant the permission in the app settings.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    finishAffinity()
                }
                .show()
        }
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
    val settingsRepository : SettingsRepository = SettingsRepositoryImpl(context)
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
        val googleKey = settingsRepository.googleApiKey
        googleApiKeyText = TextFieldValue(googleKey)

        val tripKey = settingsRepository.tripAdvisorApiKey
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

    // Generic function to generate GPX using any IGpxGenerator implementation
    fun generateGpxGeneric(
        generator: IGpxGenerator,
        loadingMessage: String,
        successMessage: String,
        errorMessage: String,
        filePrefix: String,
        requiresApiKey: Boolean = false,
        apiKey: String = ""
    ) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            withContext(Dispatchers.Main) {
                gpxResult = loadingMessage
            }
            val coords = coordinatesText.text.trim()
            
            val hasRequiredInputs = if (requiresApiKey) {
                coords.isNotEmpty() && apiKey.isNotEmpty()
            } else {
                coords.isNotEmpty()
            }
            
            if (hasRequiredInputs) {
                try {
                    val centerCoordinates = Coordinates.fromString(coords)
                    
                    // Get data using the provided generator
                    val pointDataList = generator.getData(centerCoordinates)
                    
                    // Generate GPX from the data
                    val gpxString = generator.generateGpx(pointDataList)
                    
                    // Save to file
                    val fileName = getFileName(coords, filePrefix)
                    val file = File(context.getExternalFilesDir(null), fileName)
                    file.writeText(gpxString, Charset.defaultCharset())
                    
                    // Create intent to open the file
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
                        gpxResult = successMessage
                        context.startActivity(Intent.createChooser(intent, "Open GPX"))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        gpxResult = "$errorMessage: ${e.message}"
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    val missingInputMessage = if (requiresApiKey) {
                        "Please provide coordinates and API key."
                    } else {
                        "Please provide coordinates."
                    }
                    gpxResult = missingInputMessage
                }
            }
        }
    }

    // 1) Function to request Google Places API and generate GPX
    fun generateGpx() {
        val generator = GooglePlaceGpxGenerator(googleApiKeyText.text.trim())
        generateGpxGeneric(
            generator = generator,
            loadingMessage = "Loading Google Places...",
            successMessage = "Google Places GPX created.",
            errorMessage = "Error loading Google Places",
            filePrefix = "Google",
            requiresApiKey = true,
            apiKey = googleApiKeyText.text.trim()
        )
    }

    // 2) Function to request Overpass Turbo API and generate GPX
    fun generateOsmGpx() {
        val coords = coordinatesText.text.trim()
        if (coords.isNotEmpty()) {
            val coordinates = Coordinates.fromString(coords)
            val generator = OsmPlaceGpxGenerator(coordinates)
            generateGpxGeneric(
                generator = generator,
                loadingMessage = "Loading OSM data...",
                successMessage = "OSM GPX created.",
                errorMessage = "Error loading OSM",
                filePrefix = "OSM",
                requiresApiKey = false
            )
        } else {
            gpxResult = "Please provide coordinates."
        }
    }

    // 3) Function to request TripAdvisor API and generate GPX
    fun generateGpxTripAdvisor() {
        val generator = TripAdvisorGpxGenerator(tripAdvisorApiKeyText.text.trim())
        generateGpxGeneric(
            generator = generator,
            loadingMessage = "Loading TripAdvisor data...",
            successMessage = "TripAdvisor GPX created.",
            errorMessage = "Error loading TripAdvisor",
            filePrefix = "TripAdvisor",
            requiresApiKey = true,
            apiKey = tripAdvisorApiKeyText.text.trim()
        )
    }

    // 4) Function to request Wikipedia articles and generate GPX
    fun generateWikipediaGpx() {
        val generator = WikipediaArticlesGpxGenerator()
        generateGpxGeneric(
            generator = generator,
            loadingMessage = "Loading Wikipedia articles...",
            successMessage = "Wikipedia GPX created.",
            errorMessage = "Error loading Wikipedia articles",
            filePrefix = "Wikipedia",
            requiresApiKey = false
        )
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
                    settingsRepository.googleApiKey = newValue.text
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
                    settingsRepository.tripAdvisorApiKey = it.text
                },
                label = { Text("TripAdvisor API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Group main GPX generators
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // Button for Wikipedia
            Button(
                onClick = { generateWikipediaGpx() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate GPX (Wikipedia)")
            }

            // Display the result (GPX) as text (or status info)
            Text(
                text = gpxResult,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun getFileName(coords: String, prefix: String): String {
    val locationName: String? = try {
        val coordinates = Coordinates.fromString(coords)
        val service = NominatimService()
        // Use java.util.Locale for system language
        val language = java.util.Locale.getDefault().language
        service.getLocationName(coordinates, language)
    } catch (e: Exception) {
        null
    }
    val now = java.time.LocalDateTime.now().toString().replace(":", "-")
    return "${prefix}_${locationName}_$now.gpx"
}

@Preview(showBackground = true)
@Composable
fun GpxGeneratorScreenPreview() {
    GpxGeneratorScreen()
}


