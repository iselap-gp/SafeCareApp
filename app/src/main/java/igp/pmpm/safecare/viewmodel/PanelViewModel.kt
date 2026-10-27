package igp.pmpm.safecare.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import igp.pmpm.safecare.data.RepositorioClimaLocal
import igp.pmpm.safecare.data.RepositorioAutenticacion
import igp.pmpm.safecare.data.RepositorioContactosEmergencia
import igp.pmpm.safecare.data.RepositorioSistemaSeguridad
import igp.pmpm.safecare.model.ClimaCabecera
import igp.pmpm.safecare.model.ConfiguracionLlamadaEmergencia
import igp.pmpm.safecare.model.ContactoEmergencia
import igp.pmpm.safecare.model.EstadoAlarma
import igp.pmpm.safecare.model.EstadoSistemaSeguridad
import igp.pmpm.safecare.model.ReglasAlertasUi
import igp.pmpm.safecare.model.Usuario
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PanelUiState(
    val cargando: Boolean = true,
    val conectadoFirebase: Boolean = false,
    val estadoAlarma: EstadoAlarma = EstadoAlarma.DESACTIVADO,
    val sensorPuertaAbierta: Boolean = false,
    val sensorMovimientoDetectado: Boolean = false,
    val botonPanicoActivo: Boolean = false,
    val ultimoEvento: String = "",
    val timestampArmado: Long = 0L,
    val nombreUsuario: String = "",
    val llamarA112: Boolean = true,
    val telefonoContactoSeleccionado: String = "",
    val nombreContactoSeleccionado: String = "",
    val modoMayoresActivo: Boolean = false,
    val modoMascotasActivo: Boolean = false,
    val modoNinosActivo: Boolean = false,
    val alertaInactividadPorZona: Map<String, Boolean> = emptyMap(),
    val fechaCabecera: String = "",
    val climaCabecera: ClimaCabecera? = null,
    val climaCargando: Boolean = false,
    val mensajeClima: String? = null,
    val error: String? = null,
    val escribiendo: Boolean = false
) {
    val hayAlertaCritica: Boolean
        get() = ReglasAlertasUi.hayAlertaGlobal(
            botonPanicoActivo = botonPanicoActivo,
            alarmaArmada = alarmaArmada,
            esModoFuera = esModoFuera,
            puertaAbierta = sensorPuertaAbierta,
            movimientoInteriorDetectado = sensorMovimientoDetectado,
            modoMascotasActivo = modoMascotasActivo,
            modoNinosActivo = modoNinosActivo,
            modoMayoresActivo = modoMayoresActivo,
            alertaInactividadPorZona = alertaInactividadPorZona
        )

    val alarmaArmada: Boolean
        get() = estadoAlarma.estaArmado

    val esModoCasa: Boolean
        get() = estadoAlarma.esModoCasa

    val esModoFuera: Boolean
        get() = estadoAlarma.esModoFuera

    val alertaControlInfantilPuerta: Boolean
        get() = ReglasAlertasUi.alertaControlInfantil(
            alarmaArmada,
            modoNinosActivo,
            sensorPuertaAbierta
        )

    val alertaIntrusionPuerta: Boolean
        get() = ReglasAlertasUi.alertaIntrusionPuerta(
            alarmaArmada,
            esModoFuera,
            sensorPuertaAbierta
        )

    val alertaInactividadMayores: Boolean
        get() = ReglasAlertasUi.alertaInactividadMayores(
            alarmaArmada,
            modoMayoresActivo,
            alertaInactividadPorZona
        )

    val alertaIntrusionMovimiento: Boolean
        get() = ReglasAlertasUi.alertaMovimientoIntrusion(
            alarmaArmada,
            esModoFuera,
            sensorMovimientoDetectado,
            modoMascotasActivo
        )
}

data class EventoContactoPanico(
    val telefono: String,
    val mensajeEvento: String
)

class PanelViewModel(
    private val idUsuario: String,
    private val repositorioSistema: RepositorioSistemaSeguridad = RepositorioSistemaSeguridad(),
    private val repositorioAutenticacion: RepositorioAutenticacion = RepositorioAutenticacion(),
    private val repositorioContactos: RepositorioContactosEmergencia = RepositorioContactosEmergencia(),
    private val repositorioClima: RepositorioClimaLocal = RepositorioClimaLocal()
) : ViewModel() {

    private val _estadoPantalla = MutableStateFlow(PanelUiState())
    val estadoPantalla: StateFlow<PanelUiState> = _estadoPantalla.asStateFlow()

    private val _eventoPanico = MutableSharedFlow<EventoContactoPanico>(extraBufferCapacity = 1)
    val eventoPanico: SharedFlow<EventoContactoPanico> = _eventoPanico.asSharedFlow()

    init {
        if (idUsuario.isBlank()) {
            _estadoPantalla.update {
                it.copy(
                    cargando = false,
                    conectadoFirebase = false,
                    error = "Sesión no válida. Vuelve a iniciar sesión."
                )
            }
        } else {
            actualizarFechaCabecera()
            iniciarObservadoresFirebase()
        }
    }

    private fun iniciarObservadoresFirebase() {
        viewModelScope.launch {
            runCatching { repositorioSistema.inicializarSiNoExiste(idUsuario) }
                .onFailure { error ->
                    _estadoPantalla.update {
                        it.copy(
                            cargando = false,
                            error = error.message ?: "No se pudo inicializar el sistema."
                        )
                    }
                }

            combine(
                repositorioSistema.observarSistema(idUsuario),
                repositorioAutenticacion.observarPerfil(idUsuario),
                repositorioContactos.observarConfiguracionLlamada(idUsuario),
                repositorioContactos.observarContactos(idUsuario)
            ) { sistema, perfil, configuracion, contactos ->
                mapearEstadoUi(sistema, perfil, configuracion, contactos)
            }
                .catch { error ->
                    _estadoPantalla.update {
                        it.copy(
                            cargando = false,
                            conectadoFirebase = false,
                            error = error.message ?: "Error al escuchar Firebase."
                        )
                    }
                }
                .collect { nuevoEstado ->
                    _estadoPantalla.value = nuevoEstado
                }
        }
    }

    fun cambiarModo(modo: EstadoAlarma) {
        if (idUsuario.isBlank()) return
        if (_estadoPantalla.value.estadoAlarma == modo || _estadoPantalla.value.escribiendo) return
        ejecutarEscritura {
            repositorioSistema.cambiarModoAlarma(idUsuario, modo)
        }
    }

    fun activarAlarma(modoSeleccionadoEnChip: EstadoAlarma) {
        if (modoSeleccionadoEnChip == EstadoAlarma.DESACTIVADO) return
        if (
            _estadoPantalla.value.estadoAlarma == modoSeleccionadoEnChip &&
            _estadoPantalla.value.alarmaArmada
        ) {
            return
        }
        cambiarModo(modoSeleccionadoEnChip)
    }

    fun desactivarAlarma() {
        cambiarModo(EstadoAlarma.DESACTIVADO)
    }

    fun activarBotonPanico() {
        if (idUsuario.isBlank()) return
        if (_estadoPantalla.value.escribiendo) return
        val estado = _estadoPantalla.value
        val telefono = resolverTelefonoDestinoPanico(estado)
        val mensaje = construirMensajePanico(estado, telefono)
        ejecutarEscritura(
            onExito = {
                _eventoPanico.tryEmit(EventoContactoPanico(telefono, mensaje))
            }
        ) {
            repositorioSistema.activarPanico(idUsuario, mensaje)
        }
    }

    fun limpiarAlertaPanico() {
        if (idUsuario.isBlank()) return
        if (!_estadoPantalla.value.botonPanicoActivo || _estadoPantalla.value.escribiendo) return
        ejecutarEscritura {
            repositorioSistema.desactivarPanico(idUsuario)
        }
    }

    fun limpiarError() {
        _estadoPantalla.update { it.copy(error = null) }
    }

    fun actualizarFechaCabecera() {
        _estadoPantalla.update { it.copy(fechaCabecera = formatearFechaHoyLegible()) }
    }

    fun cargarClima(latitud: Double, longitud: Double) {
        viewModelScope.launch {
            _estadoPantalla.update {
                it.copy(climaCargando = true, mensajeClima = null)
            }
            val clima = repositorioClima.obtenerClimaActual(latitud, longitud)
            _estadoPantalla.update { estado ->
                estado.copy(
                    climaCargando = false,
                    climaCabecera = clima,
                    mensajeClima = if (clima == null) MENSAJE_CLIMA_NO_DISPONIBLE else null
                )
            }
        }
    }

    fun marcarClimaSinUbicacion() {
        _estadoPantalla.update {
            it.copy(
                climaCargando = false,
                climaCabecera = null,
                mensajeClima = MENSAJE_CLIMA_SIN_UBICACION
            )
        }
    }

    private fun formatearFechaHoyLegible(): String {
        return runCatching {
            val hoy = LocalDate.now()
            val es = Locale.forLanguageTag("es-ES")
            val formato = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM yyyy", es)
            hoy.format(formato).replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase(es) else c.toString()
            }
        }.getOrElse { "" }
    }

    private fun mapearEstadoUi(
        sistema: EstadoSistemaSeguridad,
        perfil: Usuario?,
        configuracion: ConfiguracionLlamadaEmergencia,
        contactos: List<ContactoEmergencia>
    ): PanelUiState {
        val estadoAnterior = _estadoPantalla.value
        val contactoSeleccionado = resolverContactoSeleccionado(configuracion, contactos)
        return PanelUiState(
            cargando = false,
            conectadoFirebase = true,
            estadoAlarma = EstadoAlarma.desdeFirebase(sistema.estadoAlarma),
            sensorPuertaAbierta = sistema.sensorPuerta,
            sensorMovimientoDetectado = sistema.hayMovimientoEnAlgunaZonaInterior(),
            botonPanicoActivo = sistema.botonPanicoActivo,
            ultimoEvento = sistema.ultimoEvento,
            timestampArmado = sistema.timestampArmado,
            nombreUsuario = perfil?.nombre.orEmpty(),
            llamarA112 = configuracion.llamarA112,
            telefonoContactoSeleccionado = contactoSeleccionado?.telefono.orEmpty(),
            nombreContactoSeleccionado = contactoSeleccionado?.nombre.orEmpty(),
            modoMayoresActivo = sistema.modoMayoresActivo,
            modoMascotasActivo = sistema.modoMascotasActivo,
            modoNinosActivo = sistema.modoNinosActivo,
            alertaInactividadPorZona = sistema.alertaInactividadPorZona,
            fechaCabecera = estadoAnterior.fechaCabecera,
            climaCabecera = estadoAnterior.climaCabecera,
            climaCargando = estadoAnterior.climaCargando,
            mensajeClima = estadoAnterior.mensajeClima,
            error = estadoAnterior.error,
            escribiendo = estadoAnterior.escribiendo
        )
    }

    private fun resolverContactoSeleccionado(
        configuracion: ConfiguracionLlamadaEmergencia,
        contactos: List<ContactoEmergencia>
    ): ContactoEmergencia? {
        if (configuracion.llamarA112 || configuracion.idContactoSeleccionado.isBlank()) {
            return null
        }
        return contactos.find { contacto ->
            contacto.id == configuracion.idContactoSeleccionado
        }
    }

    private fun resolverTelefonoDestinoPanico(estado: PanelUiState): String {
        if (estado.llamarA112) return TELEFONO_EMERGENCIAS
        val telefonoContacto = estado.telefonoContactoSeleccionado.trim()
        if (telefonoContacto.isNotEmpty()) return telefonoContacto
        return TELEFONO_EMERGENCIAS
    }

    private fun contactoPersonalizadoNoDisponible(estado: PanelUiState): Boolean =
        !estado.llamarA112 && estado.telefonoContactoSeleccionado.trim().isEmpty()

    private fun construirMensajePanico(estado: PanelUiState, telefono: String): String {
        if (contactoPersonalizadoNoDisponible(estado)) {
            return MENSAJE_PANICO_FALLBACK_112
        }
        return when {
            estado.llamarA112 ->
                "Botón de pánico activado — aviso al 112"
            estado.nombreContactoSeleccionado.isNotBlank() ->
                "Botón de pánico activado — aviso a ${estado.nombreContactoSeleccionado}"
            telefono.isNotBlank() ->
                "Botón de pánico activado — aviso a $telefono"
            else ->
                MENSAJE_PANICO_FALLBACK_112
        }
    }

    private fun ejecutarEscritura(
        onExito: (() -> Unit)? = null,
        bloque: suspend () -> Unit
    ) {
        viewModelScope.launch {
            _estadoPantalla.update { it.copy(escribiendo = true, error = null) }
            runCatching { bloque() }
                .onSuccess { onExito?.invoke() }
                .onFailure { error ->
                    _estadoPantalla.update {
                        it.copy(
                            error = error.message ?: "No se pudo guardar en Firebase."
                        )
                    }
                }
            _estadoPantalla.update { it.copy(escribiendo = false) }
        }
    }

    private companion object {
        const val TELEFONO_EMERGENCIAS = "112"
        const val MENSAJE_PANICO_FALLBACK_112 =
            "Botón de pánico: Contacto no encontrado. Redirigiendo a Emergencias (112)"
        const val MENSAJE_CLIMA_SIN_UBICACION = "Activa la ubicación para ver el clima local"
        const val MENSAJE_CLIMA_NO_DISPONIBLE = "Clima no disponible en este momento"
    }
}

class PanelViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PanelViewModel::class.java)) {
            "ViewModel desconocido: ${modelClass.name}"
        }
        val idUsuario = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        return PanelViewModel(
            idUsuario = idUsuario,
            repositorioContactos = RepositorioContactosEmergencia()
        ) as T
    }
}
