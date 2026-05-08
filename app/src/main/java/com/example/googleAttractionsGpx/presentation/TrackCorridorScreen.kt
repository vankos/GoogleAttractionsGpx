package com.example.googleAttractionsGpx.presentation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.googleAttractionsGpx.data.repository.*
import com.example.googleAttractionsGpx.domain.models.Coordinates
import com.example.googleAttractionsGpx.domain.models.PointData
import com.example.googleAttractionsGpx.domain.repository.IGpxGenerator
import com.example.googleAttractionsGpx.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackCorridorScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository: SettingsRepository = remember { SettingsRepositoryImpl(context) }
    val osmAnd = remember { OsmAndConnection(context) }
    val trackCache = remember { TrackCacheRepository(context) }

    var tracks by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTrack by remember { mutableStateOf<String?>(null) }
    var trackPoints by remember { mutableStateOf<List<Coordinates>>(emptyList()) }
    var totalLengthKm by remember { mutableStateOf(0.0) }

    var startKm by remember { mutableStateOf("") }
    var endKm by remember { mutableStateOf("") }
    var widthMeters by remember { mutableStateOf("200") }
    var includeTrack by remember { mutableStateOf(false) }

    var statusText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var needsFilePick by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && selectedTrack != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            trackCache.putUri(selectedTrack!!, uri.toString())
            scope.launch {
                loadTrackFromUri(context, uri)?.let { points ->
                    trackPoints = points
                    val dists = CorridorCalculator.cumulativeDistances(points)
                    totalLengthKm = dists.lastOrNull() ?: 0.0
                    statusText = "Track loaded: ${points.size} points, ${"%.1f".format(totalLengthKm)} km"
                } ?: run {
                    statusText = "Error: Selected file contains no track data"
                }
            }
        }
        needsFilePick = false
    }

    LaunchedEffect(needsFilePick) {
        if (needsFilePick) {
            filePickerLauncher.launch(arrayOf("application/gpx+xml", "application/octet-stream", "*/*"))
        }
    }

    fun fetchTracks() {
        if (!osmAnd.isOsmAndInstalled()) {
            statusText = "OsmAnd is not installed. Please install OsmAnd or OsmAnd+."
            return
        }
        isLoading = true
        statusText = "Connecting to OsmAnd…"
        osmAnd.bind(
            onConnected = {
                val activeResult = osmAnd.getActiveTracks()
                val importedResult = if (activeResult.tracks.isEmpty()) osmAnd.getImportedTracks() else null
                val allTracks = importedResult?.tracks ?: activeResult.tracks
                tracks = allTracks
                isLoading = false
                val diag = activeResult.diagnostics +
                    (if (importedResult != null) "\n${importedResult.diagnostics}" else "")
                statusText = if (allTracks.isEmpty()) "No active tracks found.\n$diag"
                else "Found ${allTracks.size} track(s)\n$diag"
            },
            onFailed = {
                isLoading = false
                statusText = "Failed to connect to OsmAnd service"
            }
        )
    }

    fun selectTrack(trackName: String) {
        selectedTrack = trackName
        val cachedUri = trackCache.getUri(trackName)
        if (cachedUri != null) {
            scope.launch {
                val uri = Uri.parse(cachedUri)
                val points = loadTrackFromUri(context, uri)
                if (points != null && points.isNotEmpty()) {
                    trackPoints = points
                    val dists = CorridorCalculator.cumulativeDistances(points)
                    totalLengthKm = dists.lastOrNull() ?: 0.0
                    statusText = "Track loaded: ${points.size} points, ${"%.1f".format(totalLengthKm)} km"
                } else {
                    trackCache.removeUri(trackName)
                    statusText = "Cached file not accessible. Please select the GPX file."
                    needsFilePick = true
                }
            }
        } else {
            statusText = "Please select the GPX file for \"$trackName\""
            needsFilePick = true
        }
    }

    fun generate() {
        val start = startKm.toDoubleOrNull()
        val end = endKm.toDoubleOrNull()
        val width = widthMeters.toIntOrNull()

        if (start == null || end == null || width == null) {
            statusText = "Please enter valid numbers for all fields"; return
        }
        if (start >= end) { statusText = "Start distance must be less than end distance"; return }
        if (end > totalLengthKm) {
            statusText = "End distance exceeds track length (${"%.1f".format(totalLengthKm)} km)"; return
        }
        if (width <= 0) { statusText = "Corridor width must be a positive number"; return }

        val sources = settingsRepository.selectedSources.toList()
        if (sources.isEmpty()) { statusText = "Enable at least one data source in Settings"; return }

        isLoading = true
        statusText = "Calculating corridor…"

        scope.launch {
            try {
                val segment = withContext(Dispatchers.Default) {
                    CorridorCalculator.extractSubSegment(trackPoints, start, end)
                }
                if (segment.isEmpty()) {
                    statusText = "Could not extract track segment"; isLoading = false; return@launch
                }

                val bounds = CorridorCalculator.computeCorridorBounds(segment, width)
                statusText = "Finding POIs in corridor (radius ${bounds.radiusMeters}m)…"

                val allPoints = mutableListOf<PointData>()
                val errors = mutableListOf<String>()
                val sourceColors = settingsRepository.sourceColors

                val deferreds = sources.map { id ->
                    async(Dispatchers.IO) {
                        val gen = buildCorridorGenerator(id, settingsRepository, context)
                        if (gen == null) Pair(id, emptyList<PointData>())
                        else try {
                            Pair(id, gen.getData(bounds.center, bounds.radiusMeters))
                        } catch (e: Exception) {
                            synchronized(errors) { errors.add("$id: ${e.message}") }
                            Pair(id, emptyList<PointData>())
                        }
                    }
                }
                val results = deferreds.map { it.await() }
                results.forEach { (id, pts) ->
                    val color = sourceColors[id]
                    val filtered = pts.filter { p ->
                        p.coordinates.latitude in bounds.minLat..bounds.maxLat &&
                        p.coordinates.longitude in bounds.minLng..bounds.maxLng
                    }
                    allPoints.addAll(filtered.map { it.copy(color = color) })
                }

                val gpxContent = if (includeTrack) {
                    buildCorridorGpxContent(allPoints, segment)
                } else {
                    buildGpxContent(allPoints)
                }

                val fileName = "corridor_${selectedTrack}_${"%.0f".format(start)}-${"%.0f".format(end)}km.gpx"
                    .replace("/", "_").replace("\\", "_")
                val file = File(context.getExternalFilesDir(null), fileName)
                withContext(Dispatchers.IO) { file.writeText(gpxContent, Charset.defaultCharset()) }
                generatedFile = file

                val stats = results.joinToString(", ") { (id, pts) -> "$id: ${pts.size}" }
                val filteredCount = allPoints.size
                val errStr = if (errors.isNotEmpty()) "\nErrors: ${errors.joinToString("; ")}" else ""
                statusText = if (filteredCount == 0) {
                    "No POIs found in the selected corridor$errStr"
                } else {
                    "Done! $filteredCount POIs in corridor. $stats$errStr"
                }
                isLoading = false
            } catch (e: Exception) {
                statusText = "Error: ${e.message}"
                isLoading = false
            }
        }
    }

    fun sendToOsmAnd() {
        val file = generatedFile ?: return
        val success = osmAnd.importGpx(file, file.name, show = true)
        statusText = if (success) "GPX sent to OsmAnd!" else "Failed to send to OsmAnd. Try sharing instead."
    }

    fun shareFile() {
        val file = generatedFile ?: return
        val uri = FileProvider.getUriForFile(context, "com.example.googleAttractionsGpx.fileProvider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open GPX"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Track Corridor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { fetchTracks() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Get Tracks from OsmAnd") }

            if (tracks.isNotEmpty()) {
                Text("Select a track:", style = MaterialTheme.typography.titleSmall)
                tracks.forEach { track ->
                    val name = track.substringAfterLast("/").removeSuffix(".gpx")
                    Surface(
                        onClick = { selectTrack(track) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (track == selectedTrack)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            name,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (trackPoints.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    "Track: ${"%.1f".format(totalLengthKm)} km, ${trackPoints.size} points",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startKm,
                        onValueChange = { startKm = it },
                        label = { Text("Start (km)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endKm,
                        onValueChange = { endKm = it },
                        label = { Text("End (km)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = widthMeters,
                    onValueChange = { widthMeters = it },
                    label = { Text("Corridor width (m)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeTrack, onCheckedChange = { includeTrack = it })
                    Text("Include corridor track in result GPX")
                }
                Button(
                    onClick = { generate() },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Generate POIs in Corridor") }
            }

            if (generatedFile != null) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { sendToOsmAnd() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Send to OsmAnd") }
                    OutlinedButton(
                        onClick = { shareFile() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Share") }
                }
            }

            if (statusText.isNotEmpty()) {
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private suspend fun loadTrackFromUri(context: android.content.Context, uri: Uri): List<Coordinates>? {
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                GpxTrackParser.parseTrackPoints(input)
            }
        } catch (e: Exception) {
            null
        }
    }
}

private fun buildCorridorGenerator(id: String, settings: SettingsRepository, context: android.content.Context): IGpxGenerator? = when (id) {
    "google"   -> GooglePlaceGpxGenerator(settings.googleApiKey)
    "osm"      -> OsmPlaceGpxGenerator()
    "trip"     -> TripAdvisorGpxGenerator(settings.tripAdvisorApiKey)
    "wikidata" -> WikidataAttractionsGpxGenerator()
    "inat"     -> INaturalistGpxGenerator(settings.iNaturalistUsername, context)
    "wiki"     -> WikipediaArticlesGpxGenerator()
    "nophoto"  -> NeedPhotoWikidataGpxGenerator(settings.needPhotoExclusions)
    else       -> null
}

fun buildCorridorGpxContent(points: List<PointData>, segment: List<Coordinates>): String {
    val sb = StringBuilder()
    sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append("\n")
    sb.append("""<gpx version="1.1" creator="StuffAroundRoute" xmlns="http://www.topografix.com/GPX/1/1" xmlns:osmand="https://osmand.net/docs/technical/osmand-file-formats/osmand-gpx">""")
        .append("\n")

    points.forEach { point ->
        sb.append("""  <wpt lat="${point.coordinates.latitude}" lon="${point.coordinates.longitude}">""").append("\n")
        val escapedName = point.name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        sb.append("""    <name>$escapedName</name>""").append("\n")
        val escapedDescription = point.description.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        sb.append("""    <desc>$escapedDescription</desc>""").append("\n")
        if (point.color != null) {
            sb.append("""    <extensions>""").append("\n")
            sb.append("""      <osmand:color>${point.color}</osmand:color>""").append("\n")
            sb.append("""    </extensions>""").append("\n")
        }
        sb.append("""  </wpt>""").append("\n")
    }

    sb.append("  <trk>\n")
    sb.append("    <name>Corridor segment</name>\n")
    sb.append("    <trkseg>\n")
    segment.forEach { coord ->
        sb.append("""      <trkpt lat="${coord.latitude}" lon="${coord.longitude}"/>""").append("\n")
    }
    sb.append("    </trkseg>\n")
    sb.append("  </trk>\n")
    sb.append("</gpx>\n")
    return sb.toString()
}
