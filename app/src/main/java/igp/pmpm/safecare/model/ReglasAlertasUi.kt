package igp.pmpm.safecare.model

object ReglasAlertasUi {

    fun alertaPuertaConAlarmaArmada(
        alarmaArmada: Boolean,
        puertaAbierta: Boolean
    ): Boolean = alarmaArmada && puertaAbierta

    fun alertaIntrusionPuerta(
        alarmaArmada: Boolean,
        esModoFuera: Boolean,
        puertaAbierta: Boolean
    ): Boolean = alertaPuertaConAlarmaArmada(alarmaArmada, puertaAbierta)

    fun alertaControlInfantil(
        alarmaArmada: Boolean,
        modoNinosActivo: Boolean,
        puertaAbierta: Boolean
    ): Boolean = alarmaArmada && modoNinosActivo && puertaAbierta

    fun alertaPuertaCritica(
        alarmaArmada: Boolean,
        esModoFuera: Boolean,
        modoNinosActivo: Boolean,
        puertaAbierta: Boolean
    ): Boolean = alertaPuertaConAlarmaArmada(alarmaArmada, puertaAbierta)

    fun evaluarAlertaPuerta(
        alarmaArmada: Boolean,
        esModoFuera: Boolean,
        puertaAbierta: Boolean,
        modoNinosActivo: Boolean
    ): Boolean = alertaPuertaCritica(alarmaArmada, esModoFuera, modoNinosActivo, puertaAbierta)

    fun alertaMovimientoIntrusion(
        alarmaArmada: Boolean,
        esModoFuera: Boolean,
        movimientoDetectado: Boolean,
        modoMascotasActivo: Boolean
    ): Boolean = alarmaArmada &&
        esModoFuera &&
        movimientoDetectado &&
        !modoMascotasActivo

    fun evaluarAlertaMovimiento(
        alarmaArmada: Boolean,
        esModoFuera: Boolean,
        movimientoDetectado: Boolean,
        modoMascotasActivo: Boolean
    ): Boolean = alertaMovimientoIntrusion(
        alarmaArmada,
        esModoFuera,
        movimientoDetectado,
        modoMascotasActivo
    )

    fun alertaInactividadMayores(
        alarmaArmada: Boolean,
        modoMayoresActivo: Boolean,
        alertaInactividadPorZona: Map<String, Boolean>
    ): Boolean = alarmaArmada &&
        modoMayoresActivo &&
        alertaInactividadPorZona.values.any { it }

    fun hayAlertaGlobal(
        botonPanicoActivo: Boolean,
        alarmaArmada: Boolean,
        esModoFuera: Boolean,
        puertaAbierta: Boolean,
        movimientoInteriorDetectado: Boolean,
        modoMascotasActivo: Boolean,
        modoNinosActivo: Boolean,
        modoMayoresActivo: Boolean,
        alertaInactividadPorZona: Map<String, Boolean>
    ): Boolean = botonPanicoActivo ||
        alertaPuertaCritica(alarmaArmada, esModoFuera, modoNinosActivo, puertaAbierta) ||
        alertaMovimientoIntrusion(
            alarmaArmada,
            esModoFuera,
            movimientoInteriorDetectado,
            modoMascotasActivo
        ) ||
        alertaInactividadMayores(alarmaArmada, modoMayoresActivo, alertaInactividadPorZona)
}
