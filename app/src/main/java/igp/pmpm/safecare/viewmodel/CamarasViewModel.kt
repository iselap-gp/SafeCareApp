package igp.pmpm.safecare.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import igp.pmpm.safecare.data.RepositorioSistemaSeguridad
import igp.pmpm.safecare.model.CamaraZonaInterior
import igp.pmpm.safecare.model.EstadoAlarma
import igp.pmpm.safecare.model.EstadoSistemaSeguridad
import igp.pmpm.safecare.model.EstadoZonaInteriorUi
import igp.pmpm.safecare.model.ReglasAlertasUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CamarasUiState(
    val cargando: Boolean = true,
    val conectadoFirebase: Boolean = false,
    val sensorPuertaAbierta: Boolean = false,
    val movimientoPorZona: Map<String, Boolean> = emptyMap(),
    val alertaInactividadPorZona: Map<String, Boolean> = emptyMap(),
    val movimientoGlobal: Boolean = false,
    val modoMascotasActivo: Boolean = false,
    val modoNinosActivo: Boolean = false,
    val modoMayoresActivo: Boolean = false,
    val alarmaArmada: Boolean = false,
    val esModoFuera: Boolean = false,
    val ultimoEvento: String = "",
    val streamEntradaBase64: String = "",
    val nombresCortosZona: Map<String, String> = emptyMap(),
    val error: String? = null
) {
    val alertaIntrusionPuertaEntrada: Boolean
        get() = ReglasAlertasUi.alertaIntrusionPuerta(
            alarmaArmada,
            esModoFuera,
            sensorPuertaAbierta
        )

    val alertaControlInfantilEntrada: Boolean
        get() = ReglasAlertasUi.alertaControlInfantil(
            alarmaArmada,
            modoNinosActivo,
            sensorPuertaAbierta
        )

    val alertaEntradaCritica: Boolean
        get() = ReglasAlertasUi.alertaPuertaCritica(
            alarmaArmada,
            esModoFuera,
            modoNinosActivo,
            sensorPuertaAbierta
        )

    val transmisionEntradaActiva: Boolean
        get() = streamEntradaBase64.isNotBlank()

    val tieneAlertaEntradaActiva: Boolean
        get() = alertaEntradaCritica

    fun hayAlgunaAlertaActiva(): Boolean {
        if (tieneAlertaEntradaActiva) return true
        return CamaraZonaInterior.REGISTRO.any { zona ->
            estadoZonaInterior(zona.idFirebase).tieneAlertaActiva
        }
    }

    fun estadoZonaInterior(zonaId: String): EstadoZonaInteriorUi {
        val zona = CamaraZonaInterior.buscarPorId(zonaId) ?: return EstadoZonaInteriorUi(
            zonaId = zonaId,
            sensorMovimientoDetectado = movimientoGlobal,
            modoMascotasActivo = modoMascotasActivo,
            modoMayoresActivo = modoMayoresActivo,
            alarmaArmada = alarmaArmada,
            esModoFuera = esModoFuera,
            alertaInactividadForzada = alertaInactividadPorZona[zonaId] == true
        )
        return EstadoZonaInteriorUi.calcular(
            zona = zona,
            movimientoPorZona = movimientoPorZona,
            movimientoGlobal = movimientoGlobal,
            modoMascotasActivo = modoMascotasActivo,
            modoMayoresActivo = modoMayoresActivo,
            alarmaArmada = alarmaArmada,
            esModoFuera = esModoFuera,
            alertaInactividadPorZona = alertaInactividadPorZona
        )
    }
}

class CamarasViewModel(
    private val idUsuario: String,
    private val repositorioSistema: RepositorioSistemaSeguridad = RepositorioSistemaSeguridad(),
    private val nombresCortosZona: Map<String, String> = emptyMap()
) : ViewModel() {

    private val _estadoPantalla = MutableStateFlow(CamarasUiState())
    val estadoPantalla: StateFlow<CamarasUiState> = _estadoPantalla.asStateFlow()

    init {
        if (idUsuario.isBlank()) {
            _estadoPantalla.update {
                it.copy(
                    cargando = false,
                    error = "Sesión no válida. Vuelve a iniciar sesión."
                )
            }
        } else {
            observarSistemaVigilancia()
        }
    }

    private fun observarSistemaVigilancia() {
        viewModelScope.launch {
            combine(
                repositorioSistema.observarSistema(idUsuario),
                repositorioSistema.observarStreamCamaraEntrada(idUsuario)
            ) { sistema, streamBase64 ->
                sistema to streamBase64
            }
                .catch { error ->
                    _estadoPantalla.update {
                        it.copy(
                            cargando = false,
                            conectadoFirebase = false,
                            error = error.message ?: "No se pudo conectar con Firebase."
                        )
                    }
                }
                .collect { (sistema, streamBase64) ->
                    actualizarDesdeFirebase(sistema, streamBase64)
                }
        }
    }

    fun limpiarError() {
        _estadoPantalla.update { it.copy(error = null) }
    }

    private fun actualizarDesdeFirebase(
        sistema: EstadoSistemaSeguridad,
        streamBase64: String
    ) {
        val anterior = _estadoPantalla.value
        val estadoAlarma = EstadoAlarma.desdeFirebase(sistema.estadoAlarma)
        _estadoPantalla.value = CamarasUiState(
            cargando = false,
            conectadoFirebase = true,
            sensorPuertaAbierta = sistema.sensorPuerta,
            movimientoPorZona = sistema.movimientoPorZona,
            alertaInactividadPorZona = sistema.alertaInactividadPorZona,
            movimientoGlobal = sistema.sensorMovimiento,
            modoMascotasActivo = sistema.modoMascotasActivo,
            modoNinosActivo = sistema.modoNinosActivo,
            modoMayoresActivo = sistema.modoMayoresActivo,
            alarmaArmada = estadoAlarma.estaArmado,
            esModoFuera = estadoAlarma.esModoFuera,
            ultimoEvento = sistema.ultimoEvento,
            streamEntradaBase64 = streamBase64,
            nombresCortosZona = nombresCortosZona,
            error = anterior.error
        )
    }
}

class CamarasViewModelFactory(
    private val nombresCortosZona: Map<String, String> = emptyMap()
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CamarasViewModel::class.java)) {
            "ViewModel desconocido: ${modelClass.name}"
        }
        val idUsuario = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        return CamarasViewModel(idUsuario, nombresCortosZona = nombresCortosZona) as T
    }
}
