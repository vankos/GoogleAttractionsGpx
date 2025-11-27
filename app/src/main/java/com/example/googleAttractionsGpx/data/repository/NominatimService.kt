package com.example.googleAttractionsGpx.data.repository

import com.example.googleAttractionsGpx.domain.models.Coordinates
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class NominatimService {

    fun getCountryLanguages(coordinates: Coordinates): List<String> {
        val address = fetchAddress(coordinates)
        val countryCode = address?.optString("country_code", "") ?: ""
        if (countryCode.isEmpty()) return emptyList()
        
        val upperCountryCode = countryCode.uppercase()
        return java.util.Locale.getAvailableLocales()
            .filter { it.country.equals(upperCountryCode, ignoreCase = true) }
            .map { it.language }
            .distinct()
    }

    fun getLocationName(coordinates: Coordinates, language: String = "en"): String? {
        val address = fetchAddress(coordinates, language) ?: return null
        
        val district = address.optString("city_district", "")
        val town = address.optString("town", "")
        val city = address.optString("city", "")
        val province = address.optString("province", "")
        val state = address.optString("state", "")
        val region = address.optString("region", "")
        val country = address.optString("country", "")
        val displayName = address.optString("display_name", "")

        return when {
            district.isNotBlank() -> district
            town.isNotBlank() -> town
            city.isNotBlank() -> city
            province.isNotBlank() -> province
            state.isNotBlank() -> state
            region.isNotBlank() -> region
            country.isNotBlank() -> country
            displayName.isNotBlank() -> displayName
            else -> ""
        }
    }

    private fun fetchAddress(coordinates: Coordinates, language: String? = null): JSONObject? {
        val langParam = if (language != null) "&accept-language=$language" else ""
        val queryUrl = "https://nominatim.openstreetmap.org/reverse?lat=${coordinates.latitude}&lon=${coordinates.longitude}&format=json$langParam"
        
        return try {
            val connection = URL(queryUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "GoogleAttractionsGpx/1.0")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(response)
            jsonObject.optJSONObject("address")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
