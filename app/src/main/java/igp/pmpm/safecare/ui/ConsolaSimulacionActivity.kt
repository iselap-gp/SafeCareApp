package igp.pmpm.safecare.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import igp.pmpm.safecare.model.CamaraZonaInterior
import igp.pmpm.safecare.R
import igp.pmpm.safecare.databinding.ActivityConsolaSimulacionBinding
import igp.pmpm.safecare.viewmodel.ConsolaSimulacionUiState
import igp.pmpm.safecare.viewmodel.ConsolaSimulacionViewModel
import igp.pmpm.safecare.viewmodel.ConsolaSimulacionViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ConsolaSimulacionActivity : AppCompatActivity() {

    private lateinit var vinculacion: ActivityConsolaSimulacionBinding

    private val viewModel: ConsolaSimulacionViewModel by viewModels {
        ConsolaSimulacionViewModelFactory()
    }

    private var transmisorCamara: TransmisorCamaraEsp32? = null
    private var trabajoTransmision: Job? = null

    private val listenerPuerta = CompoundButton.OnCheckedChangeListener { _, abierta ->
        viewModel.cambiarSimulacionPuerta(abierta)
    }
    private val listenerMovimientoSalon = CompoundButton.OnCheckedChangeListener { _, detectado ->
        viewModel.cambiarSimulacionMovimientoZona(CamaraZonaInterior.REGISTRO[0].idFirebase, detectado)
    }
    private val listenerMovimientoCocina = CompoundButton.OnCheckedChangeListener { _, detectado ->
        viewModel.cambiarSimulacionMovimientoZona(CamaraZonaInterior.REGISTRO[1].idFirebase, detectado)
    }
    private val listenerInactividadSalon = CompoundButton.OnCheckedChangeListener { _, activa ->
        viewModel.cambiarSimulacionInactividadZona(CamaraZonaInterior.REGISTRO[0].idFirebase, activa)
    }
    private val listenerInactividadCocina = CompoundButton.OnCheckedChangeListener { _, activa ->
        viewModel.cambiarSimulacionInactividadZona(CamaraZonaInterior.REGISTRO[1].idFirebase, activa)
    }

    private val solicitudPermisoCamara = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            alternarTransmisionCamara()
        } else {
            Toast.makeText(
                this,
                R.string.consola_simulacion_camara_permiso_denegado,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        enableEdgeToEdge()
        vinculacion = ActivityConsolaSimulacionBinding.inflate(layoutInflater)
        setContentView(vinculacion.root)
        ViewCompat.setOnApplyWindowInsetsListener(vinculacion.contenedorPrincipalConsolaSimulacion) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
        vinculacion.barraHerramientasConsolaSimulacion.setNavigationOnClickListener { finish() }

        transmisorCamara = TransmisorCamaraEsp32(this, vinculacion.vistaPreviaCamaraEsp32)
        configurarControlesSimulacion()
        observarEstadoConsola()
        if (viewModel.estadoPantalla.value.transmisionCamaraActiva && tienePermisoCamara()) {
            iniciarTransmisionCamara(reanudarTrasRotacion = true)
        }
    }

    override fun onDestroy() {
        trabajoTransmision?.cancel()
        trabajoTransmision = null
        transmisorCamara?.detener()
        if (isFinishing && viewModel.estadoPantalla.value.transmisionCamaraActiva) {
            viewModel.detenerTransmisionCamaraEnFirebase()
        }
        super.onDestroy()
    }

    private fun configurarControlesSimulacion() {
        vinculacion.switchSimularPuerta.setOnCheckedChangeListener(listenerPuerta)
        vinculacion.switchSimularMovimientoSalon.setOnCheckedChangeListener(listenerMovimientoSalon)
        vinculacion.switchSimularMovimientoCocina.setOnCheckedChangeListener(listenerMovimientoCocina)
        vinculacion.switchSimularInactividadSalon.setOnCheckedChangeListener(listenerInactividadSalon)
        vinculacion.switchSimularInactividadCocina.setOnCheckedChangeListener(listenerInactividadCocina)
        vinculacion.btnRestablecerSensores.setOnClickListener {
            viewModel.restablecerSensoresRepositor()
        }
        vinculacion.btnTransmisionCamaraEsp32.setOnClickListener {
            if (tienePermisoCamara()) {
                alternarTransmisionCamara()
            } else {
                solicitudPermisoCamara.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun alternarTransmisionCamara() {
        if (viewModel.estadoPantalla.value.transmisionCamaraActiva) {
            detenerTransmisionCamara(local = false)
        } else {
            iniciarTransmisionCamara()
        }
    }

    private fun iniciarTransmisionCamara(reanudarTrasRotacion: Boolean = false) {
        lifecycleScope.launch {
            val resultado = transmisorCamara?.iniciar()
            if (resultado?.isFailure == true) {
                Toast.makeText(
                    this@ConsolaSimulacionActivity,
                    resultado.exceptionOrNull()?.message ?: "No se pudo abrir la cámara.",
                    Toast.LENGTH_LONG
                ).show()
                if (reanudarTrasRotacion) {
                    viewModel.marcarTransmisionCamaraActiva(false)
                }
                return@launch
            }
            if (!reanudarTrasRotacion) {
                viewModel.marcarTransmisionCamaraActiva(true)
            }
            trabajoTransmision?.cancel()
            trabajoTransmision = lifecycleScope.launch {
                while (isActive && viewModel.estadoPantalla.value.transmisionCamaraActiva) {
                    val frameBase64 = transmisorCamara?.capturarJpegBase64()
                    if (!frameBase64.isNullOrBlank()) {
                        viewModel.subirFrameTransmisionCamara(frameBase64)
                    }
                    delay(INTERVALO_FOTOGRAMAS_MS)
                }
            }
        }
    }

    private fun detenerTransmisionCamara(local: Boolean) {
        trabajoTransmision?.cancel()
        trabajoTransmision = null
        transmisorCamara?.detener()
        if (!local) {
            viewModel.detenerTransmisionCamaraEnFirebase()
        } else {
            viewModel.marcarTransmisionCamaraActiva(false)
        }
    }

    private fun tienePermisoCamara(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun observarEstadoConsola() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estadoPantalla.collect { estado ->
                    sincronizarControlesConFirebase(estado)
                    actualizarUiTransmisionCamara(estado)
                    vinculacion.tvUltimoEventoSimulacion.text =
                        estado.ultimoEvento.ifBlank { getString(R.string.consola_simulacion_sin_eventos) }
                    vinculacion.tvJsonTelemetria.text = estado.jsonCrudoFirebase.ifBlank {
                        getString(R.string.consola_simulacion_telemetria_esperando)
                    }
                    vinculacion.switchSimularPuerta.isEnabled = !estado.escribiendo
                    vinculacion.switchSimularMovimientoSalon.isEnabled = !estado.escribiendo
                    vinculacion.switchSimularMovimientoCocina.isEnabled = !estado.escribiendo
                    vinculacion.switchSimularInactividadSalon.isEnabled = !estado.escribiendo
                    vinculacion.switchSimularInactividadCocina.isEnabled = !estado.escribiendo
                    vinculacion.btnRestablecerSensores.isEnabled = !estado.escribiendo
                    estado.error?.let { mensaje ->
                        Toast.makeText(this@ConsolaSimulacionActivity, mensaje, Toast.LENGTH_LONG).show()
                        viewModel.limpiarError()
                    }
                }
            }
        }
    }

    private fun actualizarUiTransmisionCamara(estado: ConsolaSimulacionUiState) {
        if (estado.transmisionCamaraActiva) {
            vinculacion.btnTransmisionCamaraEsp32.text =
                getString(R.string.consola_simulacion_btn_detener_camara)
            vinculacion.tvEstadoTransmisionCamara.text = getString(
                R.string.consola_simulacion_camara_activa,
                estado.framesCamaraEnviados
            )
            vinculacion.tvEstadoTransmisionCamara.setTextColor(
                ContextCompat.getColor(this, R.color.safecare_exito)
            )
        } else {
            vinculacion.btnTransmisionCamaraEsp32.text =
                getString(R.string.consola_simulacion_btn_iniciar_camara)
            vinculacion.tvEstadoTransmisionCamara.text =
                getString(R.string.consola_simulacion_camara_inactiva)
            vinculacion.tvEstadoTransmisionCamara.setTextColor(
                ContextCompat.getColor(this, R.color.safecare_text_secondary)
            )
        }
    }

    private fun sincronizarControlesConFirebase(estado: ConsolaSimulacionUiState) {
        if (estado.cargando) return

        val puerta = estado.sensorPuertaAbierta
        val salon = estado.movimientoPorZona[CamaraZonaInterior.REGISTRO[0].idFirebase] == true
        val cocina = estado.movimientoPorZona[CamaraZonaInterior.REGISTRO[1].idFirebase] == true
        val inactividadSalon = estado.alertaInactividadPorZona[CamaraZonaInterior.REGISTRO[0].idFirebase] == true
        val inactividadCocina = estado.alertaInactividadPorZona[CamaraZonaInterior.REGISTRO[1].idFirebase] == true

        if (vinculacion.switchSimularPuerta.isChecked == puerta &&
            vinculacion.switchSimularMovimientoSalon.isChecked == salon &&
            vinculacion.switchSimularMovimientoCocina.isChecked == cocina &&
            vinculacion.switchSimularInactividadSalon.isChecked == inactividadSalon &&
            vinculacion.switchSimularInactividadCocina.isChecked == inactividadCocina
        ) {
            return
        }

        vinculacion.switchSimularPuerta.setOnCheckedChangeListener(null)
        vinculacion.switchSimularMovimientoSalon.setOnCheckedChangeListener(null)
        vinculacion.switchSimularMovimientoCocina.setOnCheckedChangeListener(null)
        vinculacion.switchSimularInactividadSalon.setOnCheckedChangeListener(null)
        vinculacion.switchSimularInactividadCocina.setOnCheckedChangeListener(null)

        vinculacion.switchSimularPuerta.isChecked = puerta
        vinculacion.switchSimularMovimientoSalon.isChecked = salon
        vinculacion.switchSimularMovimientoCocina.isChecked = cocina
        vinculacion.switchSimularInactividadSalon.isChecked = inactividadSalon
        vinculacion.switchSimularInactividadCocina.isChecked = inactividadCocina

        vinculacion.switchSimularPuerta.setOnCheckedChangeListener(listenerPuerta)
        vinculacion.switchSimularMovimientoSalon.setOnCheckedChangeListener(listenerMovimientoSalon)
        vinculacion.switchSimularMovimientoCocina.setOnCheckedChangeListener(listenerMovimientoCocina)
        vinculacion.switchSimularInactividadSalon.setOnCheckedChangeListener(listenerInactividadSalon)
        vinculacion.switchSimularInactividadCocina.setOnCheckedChangeListener(listenerInactividadCocina)
    }

    private companion object {
        const val INTERVALO_FOTOGRAMAS_MS = 400L
    }
}
