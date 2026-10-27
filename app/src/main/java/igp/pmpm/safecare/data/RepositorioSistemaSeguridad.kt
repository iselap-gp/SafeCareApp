package igp.pmpm.safecare.data

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import igp.pmpm.safecare.model.CamaraZonaInterior
import igp.pmpm.safecare.model.EstadoAlarma
import igp.pmpm.safecare.model.EstadoSistemaSeguridad
import igp.pmpm.safecare.model.EventoSistema
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RepositorioSistemaSeguridad(
    private val baseDatos: FirebaseDatabase = FirebaseDatabase.getInstance()
) {

    fun observarSistema(idUsuario: String): Flow<EstadoSistemaSeguridad> = callbackFlow {
        val referencia = referenciaSistema(idUsuario)
        val listener = object : ValueEventListener {
            override fun onDataChange(instantanea: DataSnapshot) {
                val estado = mapearInstantaneaAEstado(instantanea)
                trySend(estado)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observarSistema cancelado: ${error.message}")
                trySend(EstadoSistemaSeguridad.porDefecto())
            }
        }
        referencia.addValueEventListener(listener)
        awaitClose { referencia.removeEventListener(listener) }
    }

    fun observarConexionFirebase(): Flow<Boolean> = callbackFlow {
        val referencia = baseDatos.getReference(".info/connected")
        val listener = object : ValueEventListener {
            override fun onDataChange(instantanea: DataSnapshot) {
                trySend(instantanea.getValue(Boolean::class.java) == true)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observarConexionFirebase cancelado: ${error.message}")
                trySend(false)
            }
        }
        referencia.addValueEventListener(listener)
        awaitClose { referencia.removeEventListener(listener) }
    }

    fun observarHistorial(idUsuario: String): Flow<List<EventoSistema>> = callbackFlow {
        val referencia = referenciaEventos(idUsuario)
        val listener = object : ValueEventListener {
            override fun onDataChange(instantanea: DataSnapshot) {
                val eventos = instantanea.children
                    .mapNotNull { hijo -> mapearInstantaneaAEvento(hijo) }
                    .sortedByDescending { it.timestamp }
                    .take(LIMITE_EVENTOS_HISTORIAL)
                trySend(eventos)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observarHistorial cancelado: ${error.message}")
                trySend(emptyList())
            }
        }
        referencia.addValueEventListener(listener)
        awaitClose { referencia.removeEventListener(listener) }
    }

    suspend fun inicializarSiNoExiste(idUsuario: String) = withContext(Dispatchers.IO) {
        val referencia = referenciaSistema(idUsuario)
        val instantanea = esperarTarea(referencia.get())
        if (!instantanea.exists()) {
            esperarTareaVacia(referencia.setValue(EstadoSistemaSeguridad.porDefecto()))
            registrarEvento(
                idUsuario,
                "Sistema SafeCare inicializado",
                EventoSistema.TIPO_SISTEMA
            )
        }
    }

    suspend fun actualizarCampos(idUsuario: String, campos: Map<String, Any>) =
        withContext(Dispatchers.IO) {
            esperarTareaVacia(referenciaSistema(idUsuario).updateChildren(campos))
        }

    suspend fun cambiarModoAlarma(idUsuario: String, modo: EstadoAlarma) {
        val mensaje = when (modo) {
            EstadoAlarma.CASA -> "Modo CASA activado desde la app"
            EstadoAlarma.FUERA -> "Modo FUERA activado desde la app"
            EstadoAlarma.DESACTIVADO -> "Sistema desactivado desde la app"
        }
        val campos = mutableMapOf<String, Any>(
            "estadoAlarma" to modo.valorFirebase,
            "ultimoEvento" to mensaje
        )
        when (modo) {
            EstadoAlarma.CASA, EstadoAlarma.FUERA ->
                campos["timestampArmado"] = System.currentTimeMillis()
            EstadoAlarma.DESACTIVADO ->
                campos["timestampArmado"] = 0L
        }
        actualizarCampos(idUsuario, campos)
        registrarEvento(idUsuario, mensaje, EventoSistema.TIPO_ALARMA)
    }

    suspend fun activarPanico(idUsuario: String, mensajeEvento: String) {
        actualizarCampos(
            idUsuario,
            mapOf(
                "botonPanicoActivo" to true,
                "ultimoEvento" to mensajeEvento
            )
        )
        registrarEvento(idUsuario, mensajeEvento, EventoSistema.TIPO_PANICO)
    }

    suspend fun desactivarPanico(idUsuario: String) {
        val mensaje = "Alerta de pánico desactivada"
        actualizarCampos(
            idUsuario,
            mapOf(
                "botonPanicoActivo" to false,
                "ultimoEvento" to mensaje
            )
        )
        registrarEvento(idUsuario, mensaje, EventoSistema.TIPO_PANICO)
    }

    suspend fun obtenerEstadoSistema(idUsuario: String): EstadoSistemaSeguridad =
        leerEstadoActual(idUsuario)

    suspend fun actualizarModoMayores(idUsuario: String, activo: Boolean) {
        if (!activo) {
            val campos = mutableMapOf<String, Any>(
                CAMPO_MODO_MAYORES to false,
                "ultimoEvento" to "Modo Personas Mayores desactivado"
            )
            CamaraZonaInterior.REGISTRO.forEach { zona ->
                campos[rutaAlertaInactividadZona(zona.idFirebase)] = false
            }
            actualizarCampos(idUsuario, campos)
            registrarEvento(idUsuario, "Modo Personas Mayores desactivado", EventoSistema.TIPO_SISTEMA)
            return
        }
        actualizarModoProteccionFamiliar(
            idUsuario = idUsuario,
            campoFirebase = CAMPO_MODO_MAYORES,
            activo = true,
            nombreModo = "Personas Mayores"
        )
    }

    suspend fun actualizarModoMascotas(idUsuario: String, activo: Boolean) {
        actualizarModoProteccionFamiliar(
            idUsuario = idUsuario,
            campoFirebase = CAMPO_MODO_MASCOTAS,
            activo = activo,
            nombreModo = "Mascotas"
        )
    }

    suspend fun actualizarModoNinos(idUsuario: String, activo: Boolean) {
        actualizarModoProteccionFamiliar(
            idUsuario = idUsuario,
            campoFirebase = CAMPO_MODO_NINOS,
            activo = activo,
            nombreModo = "Niños"
        )
    }

    suspend fun simularSensorPuerta(idUsuario: String, abierta: Boolean) {
        val estadoAnterior = leerEstadoActual(idUsuario)
        val mensajePanel = if (abierta) {
            "Simulación ESP32: puerta principal ABIERTA"
        } else {
            "Simulación ESP32: puerta principal CERRADA"
        }
        actualizarCampos(
            idUsuario,
            mapOf(
                CAMPO_SENSOR_PUERTA to abierta,
                "ultimoEvento" to mensajePanel
            )
        )
        if (!estadoAnterior.sensorPuerta && abierta) {
            val alarma = EstadoAlarma.desdeFirebase(estadoAnterior.estadoAlarma)
            if (alarma.estaArmado) {
                registrarEvento(
                    idUsuario,
                    MENSAJE_HISTORIAL_PUERTA_ABIERTA,
                    EventoSistema.TIPO_SENSOR
                )
            }
        }
    }

    suspend fun simularSensorMovimiento(idUsuario: String, detectado: Boolean) {
        simularSensorMovimientoZona(idUsuario, CamaraZonaInterior.REGISTRO.first().idFirebase, detectado)
    }

    suspend fun simularSensorMovimientoZona(
        idUsuario: String,
        zonaId: String,
        detectado: Boolean
    ) {
        val zona = CamaraZonaInterior.buscarPorId(zonaId) ?: return
        val estadoAnterior = leerEstadoActual(idUsuario)
        val movimientoAnterior = estadoAnterior.movimientoEnZona(zonaId)
        val nombreZona = nombreZonaParaMensaje(zonaId)
        val mensajePanel = if (detectado) {
            "Simulación ESP32: movimiento detectado en $nombreZona"
        } else {
            "Simulación ESP32: sin movimiento en $nombreZona"
        }
        val campos = mutableMapOf<String, Any>(
            rutaSensorMovimientoZona(zonaId) to detectado,
            "ultimoEvento" to mensajePanel
        )
        val movimientoGlobal = calcularMovimientoGlobalTrasCambioZona(
            estadoAnterior.movimientoPorZona,
            zonaId,
            detectado,
            estadoAnterior.sensorMovimiento
        )
        campos[CAMPO_SENSOR_MOVIMIENTO] = movimientoGlobal
        actualizarCampos(idUsuario, campos)
        if (!movimientoAnterior && detectado) {
            val alarma = EstadoAlarma.desdeFirebase(estadoAnterior.estadoAlarma)
            if (alarma.estaArmado && alarma.esModoFuera && !estadoAnterior.modoMascotasActivo) {
                registrarEvento(
                    idUsuario,
                    "Movimiento detectado en $nombreZona",
                    EventoSistema.TIPO_SENSOR
                )
            }
        }
    }

    suspend fun inyectarInactividadMayores(idUsuario: String, zonaId: String) {
        simularAlertaInactividadZona(idUsuario, zonaId, activa = true)
    }

    suspend fun simularAlertaInactividadZona(
        idUsuario: String,
        zonaId: String,
        activa: Boolean
    ) {
        val estadoAnterior = leerEstadoActual(idUsuario)
        if (activa && !EstadoAlarma.desdeFirebase(estadoAnterior.estadoAlarma).estaArmado) return
        if (activa && !estadoAnterior.modoMayoresActivo) return

        val nombreZona = nombreZonaParaMensaje(zonaId)
        val campos = mutableMapOf<String, Any>(
            rutaAlertaInactividadZona(zonaId) to activa
        )
        if (activa) {
            campos["ultimoEvento"] = "Alerta de inactividad prolongada — Modo Mayores ($nombreZona)"
        } else {
            campos["ultimoEvento"] = "Alerta de inactividad desactivada — $nombreZona"
        }
        actualizarCampos(idUsuario, campos)
        if (activa) {
            registrarEvento(
                idUsuario,
                "Alerta de inactividad prolongada — Modo Mayores ($nombreZona)",
                EventoSistema.TIPO_SENSOR
            )
        }
    }

    suspend fun inyectarInactividadMayores(idUsuario: String) {
        inyectarInactividadMayores(idUsuario, CamaraZonaInterior.REGISTRO.first().idFirebase)
    }

    suspend fun restablecerSensoresSimulacion(idUsuario: String) {
        val mensaje = "Sensores restablecidos a reposo (simulación ESP32)"
        val campos = mutableMapOf<String, Any>(
            CAMPO_SENSOR_PUERTA to false,
            CAMPO_SENSOR_MOVIMIENTO to false,
            "ultimoEvento" to mensaje
        )
        CamaraZonaInterior.REGISTRO.forEach { zona ->
            campos[rutaSensorMovimientoZona(zona.idFirebase)] = false
            campos[rutaAlertaInactividadZona(zona.idFirebase)] = false
        }
        actualizarCampos(idUsuario, campos)
        registrarEvento(idUsuario, mensaje, EventoSistema.TIPO_SISTEMA)
    }

    fun observarStreamCamaraEntrada(idUsuario: String): Flow<String> = callbackFlow {
        val referencia = referenciaStreamCamaraEntrada(idUsuario)
        val listener = object : ValueEventListener {
            override fun onDataChange(instantanea: DataSnapshot) {
                trySend(instantanea.getValue(String::class.java).orEmpty())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observarStreamCamaraEntrada cancelado: ${error.message}")
                trySend("")
            }
        }
        referencia.addValueEventListener(listener)
        awaitClose { referencia.removeEventListener(listener) }
    }

    suspend fun subirFrameStreamCamaraEntrada(idUsuario: String, frameBase64: String) =
        withContext(Dispatchers.IO) {
            esperarTareaVacia(referenciaStreamCamaraEntrada(idUsuario).setValue(frameBase64))
        }

    suspend fun limpiarStreamCamaraEntrada(idUsuario: String) = withContext(Dispatchers.IO) {
        esperarTareaVacia(referenciaStreamCamaraEntrada(idUsuario).removeValue())
    }

    private suspend fun actualizarModoProteccionFamiliar(
        idUsuario: String,
        campoFirebase: String,
        activo: Boolean,
        nombreModo: String
    ) {
        val mensaje = if (activo) {
            "Modo $nombreModo activado"
        } else {
            "Modo $nombreModo desactivado"
        }
        actualizarCampos(
            idUsuario,
            mapOf(
                campoFirebase to activo,
                "ultimoEvento" to mensaje
            )
        )
        registrarEvento(idUsuario, mensaje, EventoSistema.TIPO_SISTEMA)
    }

    private suspend fun registrarEvento(
        idUsuario: String,
        descripcion: String,
        tipo: String
    ) = withContext(Dispatchers.IO) {
        val referenciaNueva = referenciaEventos(idUsuario).push()
        val idEvento = referenciaNueva.key.orEmpty()
        val evento = EventoSistema(
            id = idEvento,
            timestamp = System.currentTimeMillis(),
            descripcion = descripcion,
            tipo = tipo
        )
        esperarTareaVacia(referenciaNueva.setValue(evento))
    }

    private suspend fun leerEstadoActual(idUsuario: String): EstadoSistemaSeguridad =
        withContext(Dispatchers.IO) {
            val instantanea = esperarTarea(referenciaSistema(idUsuario).get())
            mapearInstantaneaAEstado(instantanea)
        }

    private fun referenciaSistema(idUsuario: String) =
        baseDatos.reference.child(RutasFirebase.SISTEMAS).child(idUsuario)

    private fun referenciaStreamCamaraEntrada(idUsuario: String) =
        referenciaSistema(idUsuario).child("camaras").child("entrada").child("stream")

    private fun referenciaEventos(idUsuario: String) =
        baseDatos.reference.child(RutasFirebase.EVENTOS).child(idUsuario)

    private fun mapearInstantaneaAEstado(instantanea: DataSnapshot): EstadoSistemaSeguridad {
        if (!instantanea.exists()) return EstadoSistemaSeguridad.porDefecto()
        return EstadoSistemaSeguridad(
            estadoAlarma = instantanea.child("estadoAlarma").getValue(String::class.java)
                ?: EstadoAlarma.DESACTIVADO.valorFirebase,
            sensorPuerta = instantanea.child("sensorPuerta").getValue(Boolean::class.java) ?: false,
            sensorMovimiento = instantanea.child("sensorMovimiento").getValue(Boolean::class.java)
                ?: false,
            botonPanicoActivo = instantanea.child("botonPanicoActivo").getValue(Boolean::class.java)
                ?: false,
            ultimoEvento = instantanea.child("ultimoEvento").getValue(String::class.java).orEmpty(),
            timestampArmado = instantanea.child("timestampArmado").getValue(Long::class.java) ?: 0L,
            modoMayoresActivo = instantanea.child("modoMayoresActivo").getValue(Boolean::class.java)
                ?: false,
            modoMascotasActivo = instantanea.child("modoMascotasActivo").getValue(Boolean::class.java)
                ?: false,
            modoNinosActivo = instantanea.child("modoNinosActivo").getValue(Boolean::class.java) ?: false,
            movimientoPorZona = mapearMovimientoPorZona(instantanea),
            alertaInactividadPorZona = mapearAlertaInactividadPorZona(instantanea)
        )
    }

    private fun mapearAlertaInactividadPorZona(instantanea: DataSnapshot): Map<String, Boolean> {
        val camaras = instantanea.child("camaras")
        return CamaraZonaInterior.REGISTRO.associate { zona ->
            val valor = camaras.child(zona.idFirebase)
                .child(CamaraZonaInterior.CAMPO_ALERTA_INACTIVIDAD)
                .getValue(Boolean::class.java) ?: false
            zona.idFirebase to valor
        }
    }

    private fun mapearMovimientoPorZona(instantanea: DataSnapshot): Map<String, Boolean> {
        val global = instantanea.child(CAMPO_SENSOR_MOVIMIENTO).getValue(Boolean::class.java) ?: false
        val camaras = instantanea.child("camaras")
        return CamaraZonaInterior.REGISTRO.associate { zona ->
            val valorZona = camaras.child(zona.idFirebase)
                .child(CamaraZonaInterior.CAMPO_SENSOR_MOVIMIENTO)
                .getValue(Boolean::class.java)
            zona.idFirebase to (valorZona ?: global)
        }
    }

    private fun rutaSensorMovimientoZona(zonaId: String): String =
        "camaras/$zonaId/${CamaraZonaInterior.CAMPO_SENSOR_MOVIMIENTO}"

    private fun rutaAlertaInactividadZona(zonaId: String): String =
        "camaras/$zonaId/${CamaraZonaInterior.CAMPO_ALERTA_INACTIVIDAD}"

    private fun calcularMovimientoGlobalTrasCambioZona(
        movimientoActual: Map<String, Boolean>,
        zonaId: String,
        detectado: Boolean,
        fallbackGlobal: Boolean
    ): Boolean {
        val combinado = CamaraZonaInterior.REGISTRO.associate { zona ->
            val valor = when {
                zona.idFirebase == zonaId -> detectado
                movimientoActual.containsKey(zona.idFirebase) ->
                    movimientoActual[zona.idFirebase] == true
                else -> fallbackGlobal
            }
            zona.idFirebase to valor
        }
        return combinado.values.any { it }
    }

    private fun nombreZonaParaMensaje(zonaId: String): String =
        when (zonaId) {
            "salon" -> "Salón"
            "cocina" -> "Cocina"
            else -> zonaId.replaceFirstChar { it.uppercase() }
        }

    private fun mapearInstantaneaAEvento(hijo: DataSnapshot): EventoSistema? {
        val evento = hijo.getValue(EventoSistema::class.java) ?: return null
        return evento.copy(id = hijo.key ?: evento.id)
    }

    private fun <T> esperarTarea(tarea: Task<T>): T {
        return try {
            Tasks.await(tarea, TIEMPO_ESPERA_SEG, TimeUnit.SECONDS)
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: TimeoutException) {
            throw IllegalStateException(
                "La operación con Firebase ha excedido el tiempo de espera.",
                error
            )
        }
    }

    private fun esperarTareaVacia(tarea: Task<Void>) {
        esperarTarea(tarea)
    }

    private companion object {
        const val CAMPO_SENSOR_PUERTA = "sensorPuerta"
        const val CAMPO_SENSOR_MOVIMIENTO = "sensorMovimiento"
        const val CAMPO_MODO_MAYORES = "modoMayoresActivo"
        const val CAMPO_MODO_MASCOTAS = "modoMascotasActivo"
        const val CAMPO_MODO_NINOS = "modoNinosActivo"
        const val LIMITE_EVENTOS_HISTORIAL = 30
        const val TIEMPO_ESPERA_SEG = 30L
        const val TAG = "RepositorioSistema"
        const val MENSAJE_HISTORIAL_PUERTA_ABIERTA = "Puerta principal abierta"
    }
}
