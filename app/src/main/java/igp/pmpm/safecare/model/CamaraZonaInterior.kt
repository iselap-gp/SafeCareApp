package igp.pmpm.safecare.model

import igp.pmpm.safecare.R

data class CamaraZonaInterior(
    val idFirebase: String,
    val etiquetaResId: Int,
    val nombreCortoResId: Int
) {
    companion object {
        const val ZONA_ENTRADA = "entrada"
        const val CAMPO_SENSOR_MOVIMIENTO = "sensorMovimiento"
        const val CAMPO_ALERTA_INACTIVIDAD = "alertaInactividad"

        val REGISTRO: List<CamaraZonaInterior> = listOf(
            CamaraZonaInterior(
                idFirebase = "salon",
                etiquetaResId = R.string.camaras_etiqueta_salon,
                nombreCortoResId = R.string.camaras_nombre_zona_salon
            ),
            CamaraZonaInterior(
                idFirebase = "cocina",
                etiquetaResId = R.string.camaras_etiqueta_cocina,
                nombreCortoResId = R.string.camaras_nombre_zona_cocina
            )
        )

        fun buscarPorId(id: String): CamaraZonaInterior? =
            REGISTRO.firstOrNull { it.idFirebase == id }
    }
}
