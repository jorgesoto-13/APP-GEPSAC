package com.gepsac.app.view.mapa

import com.gepsac.app.BuildConfig
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Búsqueda de lugares y dirección de un punto del mapa con Nominatim, el servicio gratuito de OpenStreetMap
 * (sin clave de API). Su política pide identificar la app con un User-Agent y no pasar de 1 consulta por segundo;
 * por eso la pantalla del mapa espera a que el usuario deje de mover el mapa antes de preguntar.
 *
 * Son llamadas de red: hay que hacerlas fuera del hilo principal.
 */
object Nominatim {
    private const val BASE = "https://nominatim.openstreetmap.org"
    private val agente = "GEPSAC-Android/1.0 (https://github.com/" + BuildConfig.REPO_GITHUB + ")"

    class Lugar(val latitud: Double, val longitud: Double)

    /** Dirección corta («Calle y número, barrio, ciudad») del punto indicado, o null si no se pudo obtener. */
    fun direccion(latitud: Double, longitud: Double): String? {
        val texto = leer("$BASE/reverse?format=jsonv2&zoom=18&addressdetails=1&lat=$latitud&lon=$longitud") ?: return null
        return try {
            val json = JSONObject(texto)
            val a = json.optJSONObject("address")
            if (a == null) json.optString("display_name").ifEmpty { null }
            else {
                val calle = listOf(a.optString("road"), a.optString("house_number")).filter { it.isNotEmpty() }.joinToString(" ")
                val barrio = primero(a, "suburb", "neighbourhood", "quarter", "city_district")
                val ciudad = primero(a, "city", "town", "village", "county")
                listOf(calle, barrio, ciudad).filter { it.isNotEmpty() }.distinct().joinToString(", ")
                    .ifEmpty { json.optString("display_name").ifEmpty { null } }
            }
        } catch (e: JSONException) {
            null
        }
    }

    /** Primer resultado de buscar [consulta] en Perú, o null si no se encontró nada. */
    fun buscar(consulta: String): Lugar? {
        val q = URLEncoder.encode(consulta.trim(), "UTF-8")
        val texto = leer("$BASE/search?format=jsonv2&limit=1&countrycodes=pe&q=$q") ?: return null
        return try {
            val arr = JSONArray(texto)
            if (arr.length() == 0) null
            else arr.getJSONObject(0).let { Lugar(it.getString("lat").toDouble(), it.getString("lon").toDouble()) }
        } catch (e: JSONException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun primero(a: JSONObject, vararg claves: String): String =
        claves.map { a.optString(it) }.firstOrNull { it.isNotEmpty() }.orEmpty()

    private fun leer(url: String): String? {
        return try {
            val con = URL(url).openConnection() as HttpURLConnection
            try {
                con.connectTimeout = 8_000
                con.readTimeout = 8_000
                con.setRequestProperty("User-Agent", agente)
                con.setRequestProperty("Accept-Language", "es")
                if (con.responseCode == HttpURLConnection.HTTP_OK) con.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                con.disconnect()
            }
        } catch (e: IOException) {
            null
        }
    }
}
