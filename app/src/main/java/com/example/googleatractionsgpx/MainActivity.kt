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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.Charset

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
            val coords = coordinatesText.text.trim()
            val apiKey = apiKeyText.text.trim()
            if (coords.isNotEmpty() && apiKey.isNotEmpty()) {
                try {
                    // Запрашиваем список мест через Places API
                    val places = fetchNearbyPlaces(coords, apiKey)

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

/**
 * Функция для запроса в Google Places API
 *
 * @param coords строка "lat,lng"
 * @param apiKey ваш Google Places API Key
 */
@Throws(Exception::class)
fun fetchNearbyPlaces(coords: String, apiKey: String): List<PlaceInfo> {
    // Пример запроса на "Nearby Search" Google Places
    // Документация: https://developers.google.com/places/web-service/search
    // Ключевые параметры: location, radius, type
    // Здесь мы ищем достопримечательности (tourist_attraction), радиус условный (2000 м)

    val (lat, lng) = coords.split(",").map { it.trim() }
    val locationParam = "$lat,$lng"
    val radius = 2000
    val type = "tourist_attraction"
    val encodedLocation = URLEncoder.encode(locationParam, "UTF-8")
    val urlString =
        "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                "location=$encodedLocation&radius=$radius&type=$type&key=$apiKey"

    val url = URL(urlString)
    val response = url.readText()

    // Разбираем JSON (очень упрощённый парсинг)
    val jsonObject = JSONObject(response)
    val resultsArray = jsonObject.getJSONArray("results")
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
        val googleMapsLink = "https://www.google.com/maps/place/?q=place_id:$placeId"

        placeList.add(
            PlaceInfo(
                name = name,
                latitude = latResult,
                longitude = lngResult,
                rating = rating,
                userRatingsTotal = userRatingsTotal,
                mapsLink = googleMapsLink
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
        sb.append("""    <name>${place.name}</name>""").append("\n")
        // В description пропихиваем рейтинг, количество отзывов и ссылку
        sb.append("""    <desc>Рейтинг: ${place.rating}, Отзывов: ${place.userRatingsTotal}, Ссылка: ${place.mapsLink}</desc>""")
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