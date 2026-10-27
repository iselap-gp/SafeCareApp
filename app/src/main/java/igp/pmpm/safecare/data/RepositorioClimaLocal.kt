package igp.pmpm.safecare.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import igp.pmpm.safecare.R
import igp.pmpm.safecare.model.ClimaCabecera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class RepositorioClimaLocal(
    private val gson: Gson = Gson()
) {

    suspend fun obtenerClimaActual(latitud: Double, longitud: Double): ClimaCabecera? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$latitud&longitude=$longitud" +
                        "&current=temperature_2m,weather_code" +
                        "&timezone=auto"
                )
                val conexion = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIEMPO_ESPERA_MS
                    readTimeout = TIEMPO_ESPERA_MS
                    requestMethod = "GET"
                }
                conexion.inputStream.bufferedReader().use { lector ->
                    val respuesta = gson.fromJson(lector, RespuestaOpenMeteo::class.java)
                    val actual = respuesta.current ?: return@withContext null
                    mapearClima(actual.weatherCode, actual.temperature2m)
                }
            }.onFailure { error ->
                Log.e(TAG, "Error al obtener clima: ${error.message}")
            }.getOrNull()
        }

    private fun mapearClima(codigoTiempo: Int, temperatura: Double): ClimaCabecera {
        val (descripcion, icono) = interpretarCodigoTiempo(codigoTiempo)
        return ClimaCabecera(
            descripcion = descripcion,
            temperaturaCelsius = temperatura.toInt(),
            iconoResId = icono
        )
    }

    private fun interpretarCodigoTiempo(codigo: Int): Pair<String, Int> {
        return when (codigo) {
            0 -> "Despejado" to R.drawable.ic_clima_sol
            1, 2, 3 -> "Parcialmente nublado" to R.drawable.ic_clima_nube
            45, 48 -> "Niebla" to R.drawable.ic_clima_niebla
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 ->
                "Lluvia" to R.drawable.ic_clima_lluvia
            71, 73, 75, 77, 85, 86 -> "Nieve" to R.drawable.ic_clima_nieve
            95, 96, 99 -> "Tormenta" to R.drawable.ic_clima_tormenta
            else -> "Nublado" to R.drawable.ic_clima_nube
        }.let { (texto, icono) ->
            texto.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
            } to icono
        }
    }

    private data class RespuestaOpenMeteo(
        val current: CondicionesActuales?
    )

    private data class CondicionesActuales(
        @SerializedName("temperature_2m") val temperature2m: Double = 0.0,
        @SerializedName("weather_code") val weatherCode: Int = 0
    )

    private companion object {
        const val TAG = "RepositorioClimaLocal"
        const val TIEMPO_ESPERA_MS = 10_000
    }
}
