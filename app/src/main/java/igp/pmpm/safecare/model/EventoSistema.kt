package igp.pmpm.safecare.model

data class EventoSistema(
    val id: String = "",
    val timestamp: Long = 0L,
    val descripcion: String = "",
    val tipo: String = TIPO_SISTEMA
) {
    companion object {
        const val TIPO_ALARMA = "ALARMA"
        const val TIPO_SENSOR = "SENSOR"
        const val TIPO_PANICO = "PANICO"
        const val TIPO_SISTEMA = "SISTEMA"
    }
}
