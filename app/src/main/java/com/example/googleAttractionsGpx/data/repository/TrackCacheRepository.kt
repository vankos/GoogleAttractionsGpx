package com.example.googleAttractionsGpx.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class TrackCacheRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "TRACK_CACHE_PREFS"
        private const val KEY_CACHE = "track_uri_cache"

        fun serialize(cache: Map<String, String>): String {
            return cache.entries.joinToString("\n") { (k, v) ->
                "${encodeComponent(k)}=${encodeComponent(v)}"
            }
        }

        fun deserialize(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.lines()
                .filter { it.contains('=') }
                .associate { line ->
                    val idx = line.indexOf('=')
                    decodeComponent(line.substring(0, idx)) to decodeComponent(line.substring(idx + 1))
                }
        }

        private fun encodeComponent(s: String): String =
            s.replace("%", "%25").replace("=", "%3D").replace("\n", "%0A")

        private fun decodeComponent(s: String): String =
            s.replace("%0A", "\n").replace("%3D", "=").replace("%25", "%")
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getUri(trackName: String): String? {
        val cache = deserialize(prefs.getString(KEY_CACHE, null))
        return cache[trackName]
    }

    fun putUri(trackName: String, uri: String) {
        val cache = deserialize(prefs.getString(KEY_CACHE, null)).toMutableMap()
        cache[trackName] = uri
        prefs.edit { putString(KEY_CACHE, serialize(cache)) }
    }

    fun removeUri(trackName: String) {
        val cache = deserialize(prefs.getString(KEY_CACHE, null)).toMutableMap()
        cache.remove(trackName)
        prefs.edit { putString(KEY_CACHE, serialize(cache)) }
    }
}
