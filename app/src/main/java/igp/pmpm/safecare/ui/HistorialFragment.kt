package igp.pmpm.safecare.ui

import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import igp.pmpm.safecare.R
import igp.pmpm.safecare.databinding.FragmentHistorialBinding
import igp.pmpm.safecare.databinding.ItemEventoHistorialBinding
import igp.pmpm.safecare.model.EventoSistema
import igp.pmpm.safecare.model.ReglasAlertasUi
import igp.pmpm.safecare.viewmodel.FilaHistorialUi
import igp.pmpm.safecare.viewmodel.HistorialUiState
import igp.pmpm.safecare.viewmodel.HistorialViewModel
import igp.pmpm.safecare.viewmodel.HistorialViewModelFactory
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistorialFragment : Fragment(R.layout.fragment_historial) {

    private val viewModel: HistorialViewModel by viewModels { HistorialViewModelFactory() }

    private val adaptador = HistorialAgrupadoAdapter()

    private var ignorarCambioChipFiltro = false

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        val vinculacion = FragmentHistorialBinding.bind(vista)
        vinculacion.btnIconoPerfil.setOnClickListener {
            startActivity(Intent(requireContext(), PerfilActivity::class.java))
        }
        vinculacion.listaEventosRecientes.isNestedScrollingEnabled = false
        vinculacion.listaEventosRecientes.setHasFixedSize(false)
        vinculacion.listaEventosRecientes.adapter = adaptador
        configurarFiltrosHistorial(vinculacion)
        observarEstadoHistorial(vinculacion)
    }

    private fun configurarFiltrosHistorial(vinculacion: FragmentHistorialBinding) {
        vinculacion.grupoFiltrosHistorial.setOnCheckedStateChangeListener { grupo, ids ->
            if (ignorarCambioChipFiltro) return@setOnCheckedStateChangeListener
            if (ids.isEmpty()) {
                ignorarCambioChipFiltro = true
                vinculacion.chipFiltroTodos.isChecked = true
                ignorarCambioChipFiltro = false
                return@setOnCheckedStateChangeListener
            }
            val filtro = when (grupo.findViewById<View>(ids.first()).id) {
                R.id.chipFiltroAlarmas -> HistorialViewModel.FILTRO_ALARMA
                R.id.chipFiltroPanicos -> HistorialViewModel.FILTRO_PANICO
                R.id.chipFiltroSensores -> HistorialViewModel.FILTRO_SENSOR
                R.id.chipFiltroSistema -> HistorialViewModel.FILTRO_SISTEMA
                else -> HistorialViewModel.FILTRO_TODOS
            }
            viewModel.setFiltro(filtro)
        }
    }

    private fun observarEstadoHistorial(vinculacion: FragmentHistorialBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estadoPantalla.collect { estado ->
                    sincronizarChipsConFiltro(vinculacion, estado.filtroSeleccionado)
                    actualizarContadores(vinculacion, estado)
                    actualizarTarjetaMayores(vinculacion, estado)
                    adaptador.submitList(estado.filasAgrupadas)
                    estado.error?.let { mensaje ->
                        if (isAdded) {
                            Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
                        }
                        viewModel.limpiarError()
                    }
                }
            }
        }
    }

    private fun sincronizarChipsConFiltro(
        vinculacion: FragmentHistorialBinding,
        filtro: String
    ) {
        ignorarCambioChipFiltro = true
        when (filtro) {
            HistorialViewModel.FILTRO_ALARMA ->
                vinculacion.chipFiltroAlarmas.isChecked = true
            HistorialViewModel.FILTRO_PANICO ->
                vinculacion.chipFiltroPanicos.isChecked = true
            HistorialViewModel.FILTRO_SENSOR ->
                vinculacion.chipFiltroSensores.isChecked = true
            HistorialViewModel.FILTRO_SISTEMA ->
                vinculacion.chipFiltroSistema.isChecked = true
            else -> vinculacion.chipFiltroTodos.isChecked = true
        }
        ignorarCambioChipFiltro = false
    }

    private fun actualizarContadores(
        vinculacion: FragmentHistorialBinding,
        estado: HistorialUiState
    ) {
        vinculacion.tvContadorAlarmas.text = estado.contadorAlarmas.toString()
        vinculacion.tvContadorPanicos.text = estado.contadorPanicos.toString()
        vinculacion.tvContadorSensores.text = estado.contadorSensores.toString()
        vinculacion.tvContadorSistema.text = estado.contadorSistema.toString()
    }

    private fun actualizarTarjetaMayores(
        vinculacion: FragmentHistorialBinding,
        estado: HistorialUiState
    ) {
        val hayInactividad = ReglasAlertasUi.alertaInactividadMayores(
            alarmaArmada = estado.alarmaArmada,
            modoMayoresActivo = estado.modoMayoresActivo,
            alertaInactividadPorZona = estado.alertaInactividadPorZona
        )
        vinculacion.tarjetaCuidadoMayoresHistorial.isVisible =
            estado.modoMayoresActivo && estado.alarmaArmada
        if (!estado.modoMayoresActivo || !estado.alarmaArmada) return

        vinculacion.tvDetalleActividadMayores.text = textoInactividadMayores(estado, hayInactividad)
        val colorInactividad = if (hayInactividad) {
            Color.parseColor("#E65100")
        } else {
            ContextCompat.getColor(requireContext(), R.color.safecare_exito)
        }
        vinculacion.tvDetalleActividadMayores.setTextColor(colorInactividad)
    }

    private fun textoInactividadMayores(estado: HistorialUiState, hayInactividad: Boolean): String {
        if (estado.botonPanicoActivo) {
            return estado.ultimoEvento.ifBlank {
                getString(R.string.historial_mayores_actividad_normal)
            }
        }
        if (hayInactividad) {
            return estado.ultimoEvento.takeIf {
                it.contains("inactividad", ignoreCase = true)
            } ?: getString(R.string.panel_inactividad_sin_movimiento)
        }
        return if (estado.sensorMovimientoDetectado) {
            getString(R.string.historial_mayores_actividad_normal)
        } else {
            getString(R.string.historial_mayores_vigilando)
        }
    }
}

private class HistorialAgrupadoAdapter :
    ListAdapter<FilaHistorialUi, RecyclerView.ViewHolder>(DIF_FILA_HISTORIAL) {

    override fun getItemViewType(posicion: Int): Int {
        return when (getItem(posicion)) {
            is FilaHistorialUi.CabeceraDia -> TIPO_CABECERA_DIA
            is FilaHistorialUi.Evento -> TIPO_EVENTO
        }
    }

    override fun onCreateViewHolder(padre: ViewGroup, tipoVista: Int): RecyclerView.ViewHolder {
        val inflador = LayoutInflater.from(padre.context)
        return when (tipoVista) {
            TIPO_CABECERA_DIA -> CabeceraDiaViewHolder(
                inflador.inflate(R.layout.item_historial_cabecera_dia, padre, false) as TextView
            )
            else -> HistorialEventoViewHolder(
                ItemEventoHistorialBinding.inflate(inflador, padre, false)
            )
        }
    }

    override fun onBindViewHolder(soporte: RecyclerView.ViewHolder, posicion: Int) {
        when (val fila = getItem(posicion)) {
            is FilaHistorialUi.CabeceraDia ->
                (soporte as CabeceraDiaViewHolder).atar(fila.etiqueta)
            is FilaHistorialUi.Evento ->
                (soporte as HistorialEventoViewHolder).atar(fila.evento)
        }
    }

    companion object {
        private const val TIPO_CABECERA_DIA = 0
        private const val TIPO_EVENTO = 1
    }
}

private class CabeceraDiaViewHolder(
    private val texto: TextView
) : RecyclerView.ViewHolder(texto) {

    fun atar(etiqueta: String) {
        texto.text = etiqueta
    }
}

private val DIF_FILA_HISTORIAL = object : DiffUtil.ItemCallback<FilaHistorialUi>() {
    override fun areItemsTheSame(anterior: FilaHistorialUi, nuevo: FilaHistorialUi): Boolean {
        return when {
            anterior is FilaHistorialUi.CabeceraDia && nuevo is FilaHistorialUi.CabeceraDia ->
                anterior.etiqueta == nuevo.etiqueta
            anterior is FilaHistorialUi.Evento && nuevo is FilaHistorialUi.Evento ->
                anterior.evento.id == nuevo.evento.id && anterior.evento.id.isNotEmpty()
            else -> false
        }
    }

    override fun areContentsTheSame(anterior: FilaHistorialUi, nuevo: FilaHistorialUi) =
        anterior == nuevo
}

private class HistorialEventoViewHolder(
    private val vinculacion: ItemEventoHistorialBinding
) : RecyclerView.ViewHolder(vinculacion.root) {

    private val formateadorHora = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-ES"))

    fun atar(evento: EventoSistema) {
        val contexto = vinculacion.root.context
        val blanco = ContextCompat.getColor(contexto, R.color.white)
        aplicarEstiloPorTipo(evento.tipo, blanco)
        vinculacion.tvTituloEvento.text = evento.descripcion
        vinculacion.tvSubtituloEvento.text = etiquetaTipo(evento.tipo)
        vinculacion.tvSubtituloEvento.isVisible = evento.tipo.isNotBlank()
        vinculacion.tvHoraEvento.text = formatearHora(evento.timestamp)
    }

    private fun aplicarEstiloPorTipo(tipo: String, tinteIcono: Int) {
        when (tipo.uppercase(Locale.ROOT)) {
            EventoSistema.TIPO_PANICO -> {
                vinculacion.contenedorFondoIconoEvento.setBackgroundResource(
                    R.drawable.fondo_icono_historial_alerta
                )
                vinculacion.imagenIconoEvento.setImageResource(android.R.drawable.ic_dialog_alert)
            }
            EventoSistema.TIPO_ALARMA -> {
                vinculacion.contenedorFondoIconoEvento.setBackgroundResource(
                    R.drawable.fondo_icono_historial_info
                )
                vinculacion.imagenIconoEvento.setImageResource(R.drawable.ic_activar_alarma)
            }
            EventoSistema.TIPO_SENSOR -> {
                vinculacion.contenedorFondoIconoEvento.setBackgroundColor(
                    ContextCompat.getColor(vinculacion.root.context, R.color.safecare_divider)
                )
                vinculacion.imagenIconoEvento.setImageResource(R.drawable.ic_sensor_movimiento)
            }
            else -> {
                vinculacion.contenedorFondoIconoEvento.setBackgroundResource(
                    R.drawable.fondo_icono_historial_ok
                )
                vinculacion.imagenIconoEvento.setImageResource(android.R.drawable.checkbox_on_background)
            }
        }
        vinculacion.imagenIconoEvento.imageTintList = ColorStateList.valueOf(tinteIcono)
    }

    private fun etiquetaTipo(tipo: String): String {
        return when (tipo.uppercase(Locale.ROOT)) {
            EventoSistema.TIPO_PANICO -> "Pánico"
            EventoSistema.TIPO_ALARMA -> "Alarma"
            EventoSistema.TIPO_SENSOR -> "Sensor"
            EventoSistema.TIPO_SISTEMA -> "Sistema"
            else -> tipo
        }
    }

    private fun formatearHora(timestamp: Long): String {
        if (timestamp <= 0L) return "—"
        return runCatching {
            Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(formateadorHora)
        }.getOrElse { "—" }
    }
}
