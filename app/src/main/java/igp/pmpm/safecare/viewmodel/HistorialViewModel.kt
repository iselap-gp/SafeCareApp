package igp.pmpm.safecare.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import igp.pmpm.safecare.data.RepositorioSistemaSeguridad
import igp.pmpm.safecare.model.EstadoAlarma
import igp.pmpm.safecare.model.EstadoSistemaSeguridad
import igp.pmpm.safecare.model.EventoSistema
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class FilaHistorialUi {
    data class CabeceraDia(val etiqueta: String) : FilaHistorialUi()
    data class Evento(val evento: EventoSistema) : FilaHistorialUi()
}

data class HistorialUiState(
    val cargando: Boolean = true,
    val listaEventos: List<EventoSistema> = emptyList(),
    val filasAgrupadas: List<FilaHistorialUi> = emptyList(),
    val filtroSeleccionado: String = HistorialViewModel.FILTRO_TODOS,
    val contadorAlarmas: Int = 0,
    val contadorPanicos: Int = 0,
    val contadorSensores: Int = 0,
    val contadorSistema: Int = 0,
    val modoMayoresActivo: Boolean = false,
    val alertaInactividadPorZona: Map<String, Boolean> = emptyMap(),
    val sensorMovimientoDetectado: Boolean = false,
    val alarmaArmada: Boolean = false,
    val botonPanicoActivo: Boolean = false,
    val ultimoEvento: String = "",
    val error: String? = null
)

class HistorialViewModel(
    private val idUsuario: String,
    private val repositorioSistema: RepositorioSistemaSeguridad = RepositorioSistemaSeguridad()
) : ViewModel() {

    private val _filtroSeleccionado = MutableStateFlow(FILTRO_TODOS)
    val filtroSeleccionado: StateFlow<String> = _filtroSeleccionado.asStateFlow()

    private val _estadoPantalla = MutableStateFlow(HistorialUiState())
    val estadoPantalla: StateFlow<HistorialUiState> = _estadoPantalla.asStateFlow()

    init {
        if (idUsuario.isBlank()) {
            _estadoPantalla.update {
                it.copy(
                    cargando = false,
                    error = "Sesión no válida. Vuelve a iniciar sesión."
                )
            }
        } else {
            observarDatosHistorial()
        }
    }

    fun setFiltro(tipo: String) {
        if (_filtroSeleccionado.value == tipo) return
        _filtroSeleccionado.value = tipo
    }

    private fun observarDatosHistorial() {
        viewModelScope.launch {
            combine(
                repositorioSistema.observarHistorial(idUsuario),
                repositorioSistema.observarSistema(idUsuario),
                _filtroSeleccionado
            ) { eventos, sistema, filtro ->
                Triple(eventos, sistema, filtro)
            }
                .catch { error ->
                    _estadoPantalla.update {
                        it.copy(
                            cargando = false,
                            error = error.message ?: "No se pudo cargar el historial."
                        )
                    }
                }
                .collect { (eventos, sistema, filtro) ->
                    actualizarEstado(eventos, sistema, filtro)
                }
        }
    }

    private fun actualizarEstado(
        eventos: List<EventoSistema>,
        sistema: EstadoSistemaSeguridad,
        filtro: String
    ) {
        val eventosFiltrados = filtrarEventosPorTipo(eventos, filtro)
        val estadoAnterior = _estadoPantalla.value
        _estadoPantalla.value = HistorialUiState(
            cargando = false,
            listaEventos = eventosFiltrados,
            filasAgrupadas = agruparEventosPorDia(eventosFiltrados),
            filtroSeleccionado = filtro,
            contadorAlarmas = contarPorTipo(eventos, EventoSistema.TIPO_ALARMA),
            contadorPanicos = contarPorTipo(eventos, EventoSistema.TIPO_PANICO),
            contadorSensores = contarPorTipo(eventos, EventoSistema.TIPO_SENSOR),
            contadorSistema = contarPorTipo(eventos, EventoSistema.TIPO_SISTEMA),
            modoMayoresActivo = sistema.modoMayoresActivo,
            alertaInactividadPorZona = sistema.alertaInactividadPorZona,
            sensorMovimientoDetectado = sistema.hayMovimientoEnAlgunaZonaInterior(),
            alarmaArmada = EstadoAlarma.desdeFirebase(sistema.estadoAlarma).estaArmado,
            botonPanicoActivo = sistema.botonPanicoActivo,
            ultimoEvento = sistema.ultimoEvento,
            error = estadoAnterior.error
        )
    }

    fun limpiarError() {
        _estadoPantalla.update { it.copy(error = null) }
    }

    private fun filtrarEventosPorTipo(
        eventos: List<EventoSistema>,
        filtro: String
    ): List<EventoSistema> {
        if (filtro == FILTRO_TODOS) return eventos
        return eventos.filter { it.tipo.equals(filtro, ignoreCase = true) }
    }

    private fun contarPorTipo(eventos: List<EventoSistema>, tipo: String): Int {
        return eventos.count { it.tipo.equals(tipo, ignoreCase = true) }
    }

    companion object {
        const val FILTRO_TODOS = "TODOS"
        const val FILTRO_ALARMA = EventoSistema.TIPO_ALARMA
        const val FILTRO_PANICO = EventoSistema.TIPO_PANICO
        const val FILTRO_SENSOR = EventoSistema.TIPO_SENSOR
        const val FILTRO_SISTEMA = EventoSistema.TIPO_SISTEMA

        private val formateadorDia = DateTimeFormatter.ofPattern(
            "EEEE, d MMM",
            Locale.forLanguageTag("es-ES")
        )

        fun agruparEventosPorDia(eventos: List<EventoSistema>): List<FilaHistorialUi> {
            if (eventos.isEmpty()) return emptyList()

            val hoy = LocalDate.now()
            val ayer = hoy.minusDays(1)
            val zona = ZoneId.systemDefault()
            val filas = mutableListOf<FilaHistorialUi>()
            var diaAnterior: LocalDate? = null

            eventos
                .sortedByDescending { it.timestamp }
                .forEach { evento ->
                    val fechaEvento = instanteAFecha(evento.timestamp, zona)
                    if (fechaEvento != diaAnterior) {
                        filas.add(FilaHistorialUi.CabeceraDia(etiquetaDia(fechaEvento, hoy, ayer)))
                        diaAnterior = fechaEvento
                    }
                    filas.add(FilaHistorialUi.Evento(evento))
                }

            return filas
        }

        private fun instanteAFecha(timestamp: Long, zona: ZoneId): LocalDate? {
            if (timestamp <= 0L) return null
            return runCatching {
                Instant.ofEpochMilli(timestamp).atZone(zona).toLocalDate()
            }.getOrNull()
        }

        private fun etiquetaDia(fecha: LocalDate?, hoy: LocalDate, ayer: LocalDate): String {
            if (fecha == null) return "—"
            return when (fecha) {
                hoy -> "Hoy"
                ayer -> "Ayer"
                else -> fecha.format(formateadorDia).replaceFirstChar { c ->
                    if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
                }
            }
        }
    }
}

class HistorialViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HistorialViewModel::class.java)) {
            "ViewModel desconocido: ${modelClass.name}"
        }
        val idUsuario = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        return HistorialViewModel(idUsuario) as T
    }
}
