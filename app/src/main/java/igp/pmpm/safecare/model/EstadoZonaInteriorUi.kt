package igp.pmpm.safecare.model

data class EstadoZonaInteriorUi(
    val zonaId: String,
    val sensorMovimientoDetectado: Boolean,
    val modoMascotasActivo: Boolean,
    val modoMayoresActivo: Boolean,
    val alarmaArmada: Boolean,
    val esModoFuera: Boolean,
    val alertaInactividadForzada: Boolean
) {
    val vigilanciaActiva: Boolean
        get() = alarmaArmada

    val alertaMovimiento: Boolean
        get() = vigilanciaActiva &&
            esModoFuera &&
            sensorMovimientoDetectado &&
            !modoMascotasActivo

    val mostrarFiltroMascotas: Boolean
        get() = vigilanciaActiva && modoMascotasActivo

    val mostrarVigilanciaMayores: Boolean
        get() = vigilanciaActiva && modoMayoresActivo

    val alertaInactividad: Boolean
        get() = vigilanciaActiva && modoMayoresActivo && alertaInactividadForzada

    val avisoSinActividadYMovimiento: Boolean
        get() = vigilanciaActiva &&
            modoMayoresActivo &&
            !sensorMovimientoDetectado

    val actividadDetectada: Boolean
        get() = vigilanciaActiva && modoMayoresActivo && sensorMovimientoDetectado

    val hayOverlay: Boolean
        get() = hayAlertaVisual

    val hayAlertaVisual: Boolean
        get() = alertaMovimiento || alertaInactividad

    val tieneAlertaActiva: Boolean
        get() = hayAlertaVisual

    companion object {
        fun calcular(
            zona: CamaraZonaInterior,
            movimientoPorZona: Map<String, Boolean>,
            movimientoGlobal: Boolean,
            modoMascotasActivo: Boolean,
            modoMayoresActivo: Boolean,
            alarmaArmada: Boolean,
            esModoFuera: Boolean,
            alertaInactividadPorZona: Map<String, Boolean> = emptyMap()
        ): EstadoZonaInteriorUi {
            val movimiento = movimientoPorZona[zona.idFirebase] ?: movimientoGlobal
            return EstadoZonaInteriorUi(
                zonaId = zona.idFirebase,
                sensorMovimientoDetectado = movimiento,
                modoMascotasActivo = modoMascotasActivo,
                modoMayoresActivo = modoMayoresActivo,
                alarmaArmada = alarmaArmada,
                esModoFuera = esModoFuera,
                alertaInactividadForzada = alertaInactividadPorZona[zona.idFirebase] == true
            )
        }
    }
}
