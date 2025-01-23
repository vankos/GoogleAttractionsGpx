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

    // Запрашиваем разрешения на использование геопозиции
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            // Можно добавить логику при получении/неполучении разрешения
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Проверяем, есть ли разрешение, если нет – запрашиваем
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

    // В SharedPreferences мы сохраним только API-ключ
    val sharedPrefs = remember {
        context.getSharedPreferences("MY_APP_PREFS", Context.MODE_PRIVATE)
    }

    // Координаты
    var coordinatesText by remember { mutableStateOf(TextFieldValue("")) }
    // API Key
    var apiKeyText by remember { mutableStateOf(TextFieldValue("")) }
    // Финальный GPX результат
    var gpxResult by remember { mutableStateOf("") }

    // При первом запуске экрана читаем сохранённый ключ и заполняем поле
    LaunchedEffect(Unit) {
        val savedKey = sharedPrefs.getString("API_KEY", "") ?: ""
        apiKeyText = TextFieldValue(savedKey)
    }

    // Функция для получения текущих координат через FusedLocationProviderClient
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

    // Функция для запроса в Google Places API и генерации GPX
    fun generateGpx() {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            withContext(Dispatchers.Main) {
                gpxResult = "Загрузка"
            }
            val coords = coordinatesText.text.trim()
            val apiKey = apiKeyText.text.trim()
            if (coords.isNotEmpty() && apiKey.isNotEmpty()) {
                try {
                    // Запрашиваем список мест через Places API
                    // Разбиваем на сетку ~500м и собираем все места
                    val places = fetchPlacesByGrid(coords, apiKey)

                    // Конвертим результат в GPX
                    val gpxString = convertPlacesToGpx(places)
                    val fileName = getFileName()
                    val file = File(context.getExternalFilesDir(null),fileName)
                    file.writeText(gpxString, Charset.defaultCharset())
                    val uri: Uri = FileProvider.getUriForFile(context, "com.example.googleatractionsgpx.fileProvider", file)

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/octet-stream")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    withContext(Dispatchers.Main) {
                        gpxResult = "Готово"
                    }

                    context.startActivity(Intent.createChooser(intent, "Open test.gpx"))
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        gpxResult = "Ошибка при загрузке: ${e.message}"
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    gpxResult = "Заполните координаты и ключ API."
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
                Text("Сгенерировать GPX")
            }

            // Отображаем результат (GPX) в текстовом виде
            Text(
                text = gpxResult,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================================================================
// Функции для «сетки», запросов к Places и генерации GPX
// ==================================================================

/**
 * Разбивает площадь ±1000 м от центра на «квадратики» 500 м
 * и для каждой точки делает запрос по радиусу ~300–500 м.
 * Собирает результаты в Set (убирая дубли), потом выдаёт как List.
 */
suspend fun fetchPlacesByGrid(coords: String, apiKey: String): List<PlaceInfo> {
    val (centerLat, centerLng) = coords.split(",").map { it.toDouble() }

    // Устанавливаем «параметры сетки»
    val halfSideMeters = 3000.0  // ±1000 м от центра (итого 2км)
    val stepMeters = 500.0       // размер «клетки» = 500 м
    val requestRadius = 300      // радиус для Google Places в каждой точке

    val latDegreePerMeter = 1.0 / 111320.0  // приблизительно ~ 1 градус широты = 111,320 м
    val cosLat = cos(centerLat * PI / 180.0)
    val lonDegreePerMeter = 1.0 / (111320.0 * cosLat)

    val results = mutableSetOf<PlaceInfo>()

    // Определим, сколько «шагов» в каждую сторону
    // Например, 2км / 500м = 4 шага. Но т.к. начинается от -1000 до +1000 включительно, это 5 точек.
    val stepsCount = ((2 * halfSideMeters) / stepMeters).toInt() // (2000 / 500) = 4, но ниже мы будем идти 0..4

    for (i in 0..stepsCount) {
        // Сколько метров сместиться от центра по широте
        val offsetMetersLat = -halfSideMeters + i * stepMeters
        val offsetLatDegrees = offsetMetersLat * latDegreePerMeter

        for (j in 0..stepsCount) {
            val offsetMetersLon = -halfSideMeters + j * stepMeters
            val offsetLonDegrees = offsetMetersLon * lonDegreePerMeter

            // Рассчитываем координаты «ячейки»
            val cellLat = centerLat + offsetLatDegrees
            val cellLon = centerLng + offsetLonDegrees

            // Делаем запрос для этой ячейки
            val placesInCell = fetchNearbyPlacesSinglePage(
                latitude = cellLat,
                longitude = cellLon,
                radius = requestRadius,
                type = "tourist_attraction",
                apiKey = apiKey
            )
            results.addAll(placesInCell)

            // Чтобы не «заспамить» Google запросами, небольшая задержка
            delay(300)
        }
    }

    return results.toList()
}

/**
 * Делаем одиночный запрос Places Nearby Search на одну «ячейку»
 * (без пейджинга, возвращает до 20 результатов).
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

    // Сформируем URL. Если надо, можно добавить &keyword=..., &language=..., etc.
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

        // Для ссылки на гугл-карты можно использовать https://maps.google.com/?q=PLACE_ID
        // или более осмысленно – https://www.google.com/maps/place/?q=place_id:PLACE_ID
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
 * Преобразует список мест в строку формата GPX
 */
fun convertPlacesToGpx(places: List<PlaceInfo>): String {
    // Заголовок для стандартного GPX 1.1
    val sb = StringBuilder()
    sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append("\n")
    sb.append("""<gpx version="1.1" creator="ComposeGpxApp" xmlns="http://www.topografix.com/GPX/1/1">""")
        .append("\n")

    places.forEach { place ->
        sb.append("""  <wpt lat="${place.latitude}" lon="${place.longitude}">""").append("\n")
        val escapedName = place.name.replace("&", "&amp;")
        sb.append("""    <name>${escapedName}</name>""").append("\n")
        // В description пропихиваем рейтинг, количество отзывов и ссылку
        val escapedLink = place.mapsLink.replace("&", "&amp;")
        sb.append("""    <desc>Рейтинг: ${place.rating}, Отзывов: ${place.userRatingsTotal}, Ссылка: ${escapedLink}</desc>""")
            .append("\n")
        sb.append("""  </wpt>""").append("\n")
    }

    sb.append("</gpx>")
    return sb.toString()
}

/**
 * Модель для хранения результата от Google Places
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