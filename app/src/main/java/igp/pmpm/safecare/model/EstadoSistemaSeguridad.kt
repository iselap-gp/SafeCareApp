package igp.pmpm.safecare.model

data class EstadoSistemaSeguridad(
    val estadoAlarma: String = EstadoAlarma.DESACTIVADO.valorFirebase,
    val sensorPuerta: Boolean = false,
    val sensorMovimiento: Boolean = false,
    val botonPanicoActivo: Boolean = false,
    val ultimoEvento: String = "",
    val timestampArmado: Long = 0L,
    val modoMayoresActivo: Boolean = false,
    val modoMascotasActivo: Boolean = false,
    val modoNinosActivo: Boolean = false,
    val movimientoPorZona: Map<String, Boolean> = emptyMap(),
    val alertaInactividadPorZona: Map<String, Boolean> = emptyMap()
) {
    fun movimientoEnZona(zonaId: String): Boolean =
        movimientoPorZona[zonaId] ?: sensorMovimiento

    fun hayMovimientoEnAlgunaZonaInterior(): Boolean =
        CamaraZonaInterior.REGISTRO.any { movimientoEnZona(it.idFirebase) }

    fun aMapaFirebase(): Map<String, Any> = mapOf(
        "estadoAlarma" to estadoAlarma,
        "sensorPuerta" to sensorPuerta,
        "sensorMovimiento" to sensorMovimiento,
        "botonPanicoActivo" to botonPanicoActivo,
        "ultimoEvento" to ultimoEvento,
        "timestampArmado" to timestampArmado,
        "modoMayoresActivo" to modoMayoresActivo,
        "modoMascotasActivo" to modoMascotasActivo,
        "modoNinosActivo" to modoNinosActivo
    )

    companion object {
        fun porDefecto(): EstadoSistemaSeguridad = EstadoSistemaSeguridad(
            estadoAlarma = EstadoAlarma.DESACTIVADO.valorFirebase,
            ultimoEvento = "Sistema en espera"
        )
    }
}
