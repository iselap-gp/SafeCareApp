package igp.pmpm.safecare.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.GsonBuilder
import igp.pmpm.safecare.data.RepositorioSistemaSeguridad
import igp.pmpm.safecare.data.RutasFirebase
import igp.pmpm.safecare.model.CamaraZonaInterior
import igp.pmpm.safecare.model.EstadoSistemaSeguridad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConsolaSimulacionUiState(
    val cargando: Boolean = true,
    val sensorPuertaAbierta: Boolean = false,
    val movimientoPorZona: Map<String, Boolean> = emptyMap(),
    val alertaInactividadPorZona: Map<String, Boolean> = emptyMap(),
    val modoMayoresActivo: Boolean = false,
    val ultimoEvento: String = "",
    val jsonCrudoFirebase: String = "",
    val transmisionCamaraActiva: Boolean = false,
    val framesCamaraEnviados: Int = 0,
    val escribiendo: Boolean = false,
    val error: String? = null
)

class ConsolaSimulacionViewModel(
    private val idUsuario: String,
    private val repositorio: RepositorioSistemaSeguridad = RepositorioSistemaSeguridad()
) : ViewModel() {

    private val _estadoPantalla = MutableStateFlow(ConsolaSimulacionUiState())
    val estadoPantalla: StateFlow<ConsolaSimulacionUiState> = _estadoPantalla.asStateFlow()

    init {
        if (idUsuario.isBlank()) {
            _estadoPantalla.update {
                it.copy(
                    cargando = false,
                    error = "Sesión no válida. Vuelve a iniciar sesión."
                )
            }
        } else {
            observarSistemaFirebase()
        }
    }

    private fun observarSistemaFirebase() {
        viewModelScope.launch {
            repositorio.observarSistema(idUsuario)
                .catch { error ->
                    _estadoPantalla.update {
                        it.copy(
                            cargando = false,
                            error = error.message ?: "No se pudo cargar el estado del sistema."
                        )
                    }
                }
                .collect { sistema -> actualizarDesdeSistema(sistema) }
        }
    }

    fun cambiarSimulacionPuerta(abierta: Boolean) {
        if (idUsuario.isBlank() || _estadoPantalla.value.escribiendo) return
        if (_estadoPantalla.value.sensorPuertaAbierta == abierta) return
        ejecutarEscritura {
            repositorio.simularSensorPuerta(idUsuario, abierta)
        }
    }

    fun cambiarSimulacionMovimientoZona(zonaId: String, detectado: Boolean) {
        if (idUsuario.isBlank() || _estadoPantalla.value.escribiendo) return
        val actual = _estadoPantalla.value.movimientoPorZona[zonaId] ?: false
        if (actual == detectado) return
        ejecutarEscritura {
            repositorio.simularSensorMovimientoZona(idUsuario, zonaId, detectado)
        }
    }

    fun cambiarSimulacionInactividadZona(zonaId: String, activa: Boolean) {
        if (idUsuario.isBlank() || _estadoPantalla.value.escribiendo) return
        val actual = _estadoPantalla.value.alertaInactividadPorZona[zonaId] ?: false
        if (actual == activa) return
        if (activa && !_estadoPantalla.value.modoMayoresActivo) {
            _estadoPantalla.update {
                it.copy(error = "Activa Modo Mayores en Ajustes antes de simular inactividad.")
            }
            return
        }
        ejecutarEscritura {
            repositorio.simularAlertaInactividadZona(idUsuario, zonaId, activa)
        }
    }

    fun inyectarInactividadMayores(zonaId: String) {
        cambiarSimulacionInactividadZona(zonaId, activa = true)
    }

    fun inyectarInactividadMayores() {
        inyectarInactividadMayores(CamaraZonaInterior.REGISTRO.first().idFirebase)
    }

    fun restablecerSensoresRepositor() {
        if (idUsuario.isBlank() || _estadoPantalla.value.escribiendo) return
        ejecutarEscritura {
            repositorio.restablecerSensoresSimulacion(idUsuario)
        }
    }

    fun marcarTransmisionCamaraActiva(activa: Boolean) {
        _estadoPantalla.update {
            it.copy(
                transmisionCamaraActiva = activa,
                framesCamaraEnviados = if (activa) it.framesCamaraEnviados else 0
            )
        }
    }

    suspend fun subirFrameTransmisionCamara(frameBase64: String) {
        if (idUsuario.isBlank() || frameBase64.isBlank()) return
        runCatching {
            repositorio.subirFrameStreamCamaraEntrada(idUsuario, frameBase64)
        }.onFailure { error ->
            _estadoPantalla.update {
                it.copy(error = error.message ?: "No se pudo subir el fotograma.")
            }
        }.onSuccess {
            _estadoPantalla.update {
                it.copy(framesCamaraEnviados = it.framesCamaraEnviados + 1)
            }
        }
    }

    fun detenerTransmisionCamaraEnFirebase() {
        if (idUsuario.isBlank()) return
        viewModelScope.launch {
            runCatching { repositorio.limpiarStreamCamaraEntrada(idUsuario) }
                .onFailure { error ->
                    _estadoPantalla.update {
                        it.copy(error = error.message ?: "No se pudo detener la transmisión.")
                    }
                }
            _estadoPantalla.update {
                it.copy(transmisionCamaraActiva = false, framesCamaraEnviados = 0)
            }
        }
    }

    fun limpiarError() {
        _estadoPantalla.update { it.copy(error = null) }
    }

    private fun actualizarDesdeSistema(sistema: EstadoSistemaSeguridad) {
        val anterior = _estadoPantalla.value
        _estadoPantalla.value = ConsolaSimulacionUiState(
            cargando = false,
            sensorPuertaAbierta = sistema.sensorPuerta,
            movimientoPorZona = sistema.movimientoPorZona,
            alertaInactividadPorZona = sistema.alertaInactividadPorZona,
            modoMayoresActivo = sistema.modoMayoresActivo,
            ultimoEvento = sistema.ultimoEvento,
            jsonCrudoFirebase = formatearJsonTelemetria(sistema),
            transmisionCamaraActiva = anterior.transmisionCamaraActiva,
            framesCamaraEnviados = anterior.framesCamaraEnviados,
            escribiendo = anterior.escribiendo,
            error = anterior.error
        )
    }

    private fun formatearJsonTelemetria(sistema: EstadoSistemaSeguridad): String {
        val paquete = linkedMapOf(
            "protocolo" to "Firebase Realtime Database (NoSQL JSON)",
            "operacion" to "ValueEventListener.onDataChange → lectura en tiempo real",
            "rutaFirebase" to "${RutasFirebase.SISTEMAS}/$idUsuario",
            "timestampRecepcionLocalMs" to System.currentTimeMillis(),
            "payload" to sistema.aMapaFirebase()
        )
        return GsonBuilder()
            .setPrettyPrinting()
            .create()
            .toJson(paquete)
    }

    private fun ejecutarEscritura(bloque: suspend () -> Unit) {
        viewModelScope.launch {
            _estadoPantalla.update { it.copy(escribiendo = true, error = null) }
            runCatching { bloque() }
                .onFailure { error ->
                    _estadoPantalla.update {
                        it.copy(
                            error = error.message ?: "No se pudo escribir en Firebase."
                        )
                    }
                }
            _estadoPantalla.update { it.copy(escribiendo = false) }
        }
    }
}

class ConsolaSimulacionViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ConsolaSimulacionViewModel::class.java)) {
            "ViewModel desconocido: ${modelClass.name}"
        }
        val idUsuario = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        return ConsolaSimulacionViewModel(idUsuario) as T
    }
}
