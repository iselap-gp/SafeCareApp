package igp.pmpm.safecare.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import igp.pmpm.safecare.MainActivity
import igp.pmpm.safecare.R
import igp.pmpm.safecare.databinding.DialogoEnviandoAlertaBinding
import igp.pmpm.safecare.databinding.FragmentPanelBinding
import igp.pmpm.safecare.model.EstadoAlarma
import igp.pmpm.safecare.viewmodel.EventoContactoPanico
import igp.pmpm.safecare.viewmodel.PanelUiState
import igp.pmpm.safecare.viewmodel.PanelViewModel
import igp.pmpm.safecare.viewmodel.PanelViewModelFactory
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.Locale
import androidx.core.graphics.toColorInt

class PanelFragment : Fragment() {

    private var _vinculacion: FragmentPanelBinding? = null
    private val vinculacion get() = _vinculacion!!

    private val autenticacion: FirebaseAuth = FirebaseAuth.getInstance()

    private val viewModel: PanelViewModel by viewModels { PanelViewModelFactory() }

    private var ignorarCambioChip = false
    private var dialogoPanico: androidx.appcompat.app.AlertDialog? = null
    private var listenerUbicacion: LocationListener? = null

    private val solicitudPermisoUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            obtenerUbicacionYCargarClima()
        } else {
            viewModel.marcarClimaSinUbicacion()
        }
    }

    override fun onCreateView(
        inflador: LayoutInflater,
        contenedor: ViewGroup?,
        estado: Bundle?
    ): View {
        _vinculacion = FragmentPanelBinding.inflate(inflador, contenedor, false)
        return vinculacion.root
    }

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        if (autenticacion.currentUser == null) {
            redirigirAlLogin()
            return
        }
        configurarSelectorModoVigilancia()
        configurarAccesoPerfilDesdeCabecera()
        configurarAccesosRapidosNavegacion()
        configurarControlesRapidosAlarma()
        configurarBotonEmergenciaConDialogo()
        configurarBotonDesactivarAlertaPanico()
        observarEstadoPanel()
        observarEventosPanico()
    }

    override fun onResume() {
        super.onResume()
        viewModel.actualizarFechaCabecera()
        solicitarUbicacionYCargarClima()
    }

    override fun onDestroyView() {
        listenerUbicacion?.let { listener ->
            runCatching {
                requireContext().getSystemService(LocationManager::class.java)
                    ?.removeUpdates(listener)
            }
        }
        listenerUbicacion = null
        dialogoPanico?.dismiss()
        dialogoPanico = null
        _vinculacion = null
        super.onDestroyView()
    }

    private fun observarEstadoPanel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estadoPantalla.collect { estado ->
                    renderizarEstado(estado)
                }
            }
        }
    }

    private fun observarEventosPanico() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventoPanico.collect { evento ->
                    ejecutarAccionContactoEmergencia(evento)
                }
            }
        }
    }

    private fun renderizarEstado(estado: PanelUiState) {
        actualizarSaludo(estado.nombreUsuario)
        actualizarCabeceraPersonal(estado)
        sincronizarChipsConFirebase(estado.estadoAlarma)
        actualizarHeroEstado(estado)
        actualizarBotonRestablecerAlerta(estado)
        actualizarSensoresCompactos(estado)
        vinculacion.btnActivarAlarma.isEnabled = !estado.escribiendo
        vinculacion.btnDesactivarAlarma.isEnabled = !estado.escribiendo
        vinculacion.btnAvisoEmergencia.isEnabled = !estado.escribiendo
        estado.error?.let { mensaje ->
            if (isAdded) {
                Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
            }
            viewModel.limpiarError()
        }
    }

    private fun actualizarSaludo(nombrePerfil: String) {
        val nombre = nombrePerfil.trim().takeIf { it.isNotEmpty() }
            ?: obtenerNombreReservaDesdeAuth()
        vinculacion.tvBienvenidaConNombre.text = getString(
            R.string.demo_bienvenida_con_nombre,
            nombre
        )
    }

    private fun actualizarHeroEstado(estado: PanelUiState) {
        val contexto = requireContext()
        val idFondo = when {
            estado.hayAlertaCritica -> R.drawable.gradient_cabecera_alerta
            estado.alarmaArmada && estado.esModoCasa -> R.drawable.gradient_cabecera_modo_casa
            estado.alarmaArmada -> R.drawable.gradient_cabecera_modo_fuera
            else -> R.drawable.gradient_cabecera_reposo
        }
        vinculacion.contenedorFondoDegradadoEstado.setBackgroundResource(idFondo)

        val textoHero = when {
            estado.hayAlertaCritica -> getString(R.string.panel_hero_alerta)
            estado.alarmaArmada && estado.timestampArmado > 0L ->
                formatearTextoHeroProtegido(estado.timestampArmado)
            estado.alarmaArmada -> getString(R.string.panel_hero_protegido)
            else -> getString(R.string.panel_hero_espera)
        }
        vinculacion.tvMensajeEstadoGlobal.text = textoHero

        when {
            estado.hayAlertaCritica -> {
                aplicarEstiloHeroSobreFondoVivo(enEstadoDeAlerta = true)
            }
            estado.alarmaArmada -> {
                aplicarEstiloHeroSobreFondoVivo(enEstadoDeAlerta = false, esModoCasa = estado.esModoCasa)
            }
            else -> {
                val colorReposo = ContextCompat.getColor(contexto, R.color.safecare_exito)
                vinculacion.tvMensajeEstadoGlobal.setTextColor(colorReposo)
                vinculacion.imagenIconoEscudo.setColorFilter(colorReposo, PorterDuff.Mode.SRC_IN)
            }
        }
    }

    private fun formatearTextoHeroProtegido(timestampArmado: Long): String {
        val duracion = Duration.ofMillis(
            (System.currentTimeMillis() - timestampArmado).coerceAtLeast(0L)
        )
        val horas = duracion.toHours()
        return if (horas >= 1L) {
            val minutos = duracion.toMinutes() % 60
            getString(R.string.panel_hero_protegido_horas_minutos, horas.toInt(), minutos.toInt())
        } else {
            val minutos = duracion.toMinutes().coerceAtLeast(1L)
            getString(R.string.panel_hero_protegido_minutos, minutos.toInt())
        }
    }

    private fun aplicarEstiloHeroSobreFondoVivo(
        enEstadoDeAlerta: Boolean,
        esModoCasa: Boolean = false
    ) {
        val blanco = ContextCompat.getColor(requireContext(), R.color.white)
        vinculacion.tvMensajeEstadoGlobal.setTextColor(blanco)
        if (enEstadoDeAlerta || esModoCasa) {
            vinculacion.imagenIconoEscudo.setColorFilter(blanco, PorterDuff.Mode.SRC_IN)
        } else {
            vinculacion.imagenIconoEscudo.setColorFilter(
                "#FFF8E1".toColorInt(),
                PorterDuff.Mode.SRC_IN
            )
        }
    }

    private fun sincronizarChipsConFirebase(estadoAlarma: EstadoAlarma) {
        ignorarCambioChip = true
        when (estadoAlarma) {
            EstadoAlarma.CASA -> {
                vinculacion.chipModoCasa.isChecked = true
                vinculacion.chipModoFuera.isChecked = false
            }
            EstadoAlarma.FUERA -> {
                vinculacion.chipModoCasa.isChecked = false
                vinculacion.chipModoFuera.isChecked = true
            }
            EstadoAlarma.DESACTIVADO -> Unit
        }
        ignorarCambioChip = false
    }

    private fun configurarSelectorModoVigilancia() {
        vinculacion.grupoSelectorModo.setOnCheckedStateChangeListener { _, ids ->
            if (ignorarCambioChip) return@setOnCheckedStateChangeListener
            if (ids.isEmpty()) {
                ignorarCambioChip = true
                restaurarChipSegunEstadoAlarma(viewModel.estadoPantalla.value.estadoAlarma)
                ignorarCambioChip = false
                return@setOnCheckedStateChangeListener
            }
            if (!viewModel.estadoPantalla.value.alarmaArmada) return@setOnCheckedStateChangeListener
            val modo = when (ids.first()) {
                R.id.chipModoFuera -> EstadoAlarma.FUERA
                else -> EstadoAlarma.CASA
            }
            viewModel.cambiarModo(modo)
        }
    }

    private fun restaurarChipSegunEstadoAlarma(estadoAlarma: EstadoAlarma) {
        when (estadoAlarma) {
            EstadoAlarma.FUERA -> vinculacion.chipModoFuera.isChecked = true
            EstadoAlarma.CASA -> vinculacion.chipModoCasa.isChecked = true
            EstadoAlarma.DESACTIVADO -> {
                if (!vinculacion.chipModoCasa.isChecked && !vinculacion.chipModoFuera.isChecked) {
                    vinculacion.chipModoCasa.isChecked = true
                }
            }
        }
    }

    private fun actualizarSensoresCompactos(estado: PanelUiState) {
        actualizarMiniTarjetaMovimiento(estado)
        actualizarMiniTarjetaPuerta(estado)
    }

    private fun actualizarMiniTarjetaMovimiento(estado: PanelUiState) {
        val contexto = requireContext()
        if (!estado.alarmaArmada) {
            vinculacion.tvDetalleMovimiento.text = getString(R.string.panel_sensor_en_espera)
            vinculacion.tvAvisoFiltroMascotas.isVisible = false
            vinculacion.tvAvisoModoFueraMovimiento.isVisible = false
            vinculacion.tvAvisoModoCasaMovimiento.isVisible = false
            vinculacion.tvDetalleMovimiento.setTextColor(
                ContextCompat.getColor(contexto, R.color.safecare_text_secondary)
            )
            vinculacion.tarjetaSensorMovimiento.setCardBackgroundColor(
                ContextCompat.getColor(contexto, R.color.safecare_surface_tint)
            )
            vinculacion.tarjetaSensorMovimiento.strokeColor =
                ContextCompat.getColor(contexto, R.color.safecare_stroke)
            vinculacion.tarjetaSensorMovimiento.strokeWidth = grosorBordeTarjetaEnDp(1f)
            return
        }

        vinculacion.tvDetalleMovimiento.text = if (estado.sensorMovimientoDetectado) {
            getString(R.string.panel_resumen_movimiento_si)
        } else {
            getString(R.string.panel_resumen_movimiento_no)
        }
        vinculacion.tvAvisoFiltroMascotas.isVisible = estado.modoMascotasActivo
        vinculacion.tvAvisoModoFueraMovimiento.isVisible = estado.alertaIntrusionMovimiento
        vinculacion.tvAvisoModoCasaMovimiento.isVisible =
            estado.alarmaArmada &&
            estado.esModoCasa &&
            estado.sensorMovimientoDetectado &&
            !estado.modoMascotasActivo

        if (estado.alertaIntrusionMovimiento) {
            vinculacion.tvDetalleMovimiento.setTextColor(
                ContextCompat.getColor(contexto, R.color.safecare_alerta)
            )
            vinculacion.tarjetaSensorMovimiento.setCardBackgroundColor(
                ContextCompat.getColor(contexto, R.color.safecare_alerta_fondo)
            )
            vinculacion.tarjetaSensorMovimiento.strokeColor =
                ContextCompat.getColor(contexto, R.color.safecare_alerta)
            vinculacion.tarjetaSensorMovimiento.strokeWidth = grosorBordeTarjetaEnDp(2f)
        } else {
            vinculacion.tvDetalleMovimiento.setTextColor(
                ContextCompat.getColor(contexto, R.color.safecare_primary)
            )
            vinculacion.tarjetaSensorMovimiento.setCardBackgroundColor(
                ContextCompat.getColor(contexto, R.color.safecare_surface_tint)
            )
            vinculacion.tarjetaSensorMovimiento.strokeColor =
                ContextCompat.getColor(contexto, R.color.safecare_stroke)
            vinculacion.tarjetaSensorMovimiento.strokeWidth = grosorBordeTarjetaEnDp(1f)
        }
    }

    private fun actualizarMiniTarjetaPuerta(estado: PanelUiState) {
        val contexto = requireContext()
        if (!estado.alarmaArmada) {
            vinculacion.tvDetallePuerta.text = getString(R.string.panel_sensor_en_espera)
            vinculacion.tvAvisoControlInfantilPuerta.isVisible = false
            vinculacion.tvAvisoPuertaAlarma.isVisible = false
            vinculacion.tvDetallePuerta.setTextColor(
                ContextCompat.getColor(contexto, R.color.safecare_text_secondary)
            )
            vinculacion.tarjetaPuertaPrincipal.setCardBackgroundColor(
                ContextCompat.getColor(contexto, R.color.safecare_surface_tint)
            )
            vinculacion.tarjetaPuertaPrincipal.strokeColor =
                ContextCompat.getColor(contexto, R.color.safecare_divider)
            vinculacion.tarjetaPuertaPrincipal.strokeWidth = grosorBordeTarjetaEnDp(0.5f)
            return
        }

        val alertaControlInfantil = estado.alertaControlInfantilPuerta
        val alertaPuertaArmada = estado.alertaIntrusionPuerta

        vinculacion.tvDetallePuerta.text = if (estado.sensorPuertaAbierta) {
            getString(R.string.panel_resumen_puerta_abierta)
        } else {
            getString(R.string.panel_resumen_puerta_cerrada)
        }
        vinculacion.tvAvisoControlInfantilPuerta.isVisible = alertaControlInfantil
        vinculacion.tvAvisoPuertaAlarma.isVisible = alertaPuertaArmada

        if (alertaControlInfantil || alertaPuertaArmada) {
            vinculacion.tvDetallePuerta.setTextColor(
                ContextCompat.getColor(contexto, R.color.safecare_alerta)
            )
            vinculacion.tarjetaPuertaPrincipal.setCardBackgroundColor(
                ContextCompat.getColor(contexto, R.color.safecare_alerta_fondo)
            )
            vinculacion.tarjetaPuertaPrincipal.strokeColor =
                ContextCompat.getColor(contexto, R.color.safecare_alerta)
            vinculacion.tarjetaPuertaPrincipal.strokeWidth = grosorBordeTarjetaEnDp(2f)
        } else {
            vinculacion.tvDetallePuerta.setTextColor(
                ContextCompat.getColor(contexto, R.color.safecare_primary)
            )
            vinculacion.tarjetaPuertaPrincipal.setCardBackgroundColor(
                ContextCompat.getColor(contexto, R.color.safecare_surface_tint)
            )
            vinculacion.tarjetaPuertaPrincipal.strokeColor =
                ContextCompat.getColor(contexto, R.color.safecare_divider)
            vinculacion.tarjetaPuertaPrincipal.strokeWidth = grosorBordeTarjetaEnDp(0.5f)
        }
    }

    private fun grosorBordeTarjetaEnDp(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private fun actualizarBotonRestablecerAlerta(estado: PanelUiState) {
        vinculacion.btnDesactivarAlertaPanico.isVisible = estado.botonPanicoActivo
        vinculacion.btnDesactivarAlertaPanico.isEnabled =
            estado.botonPanicoActivo && !estado.escribiendo
    }

    private fun configurarBotonDesactivarAlertaPanico() {
        vinculacion.btnDesactivarAlertaPanico.setOnClickListener {
            viewModel.limpiarAlertaPanico()
        }
    }

    private fun configurarAccesoPerfilDesdeCabecera() {
        vinculacion.btnIconoPerfil.setOnClickListener {
            startActivity(Intent(requireContext(), PerfilActivity::class.java))
        }
    }

    private fun configurarAccesosRapidosNavegacion() {
        vinculacion.tarjetaAccesoCamaras.setOnClickListener {
            (activity as? MainActivity)?.navegarAPestana(R.id.navegacion_camaras)
        }
        vinculacion.tarjetaAccesoHistorial.setOnClickListener {
            (activity as? MainActivity)?.navegarAPestana(R.id.navegacion_historial)
        }
    }

    private fun configurarControlesRapidosAlarma() {
        vinculacion.btnActivarAlarma.setOnClickListener {
            val modoChip = if (vinculacion.chipModoFuera.isChecked) {
                EstadoAlarma.FUERA
            } else {
                EstadoAlarma.CASA
            }
            viewModel.activarAlarma(modoChip)
        }
        vinculacion.btnDesactivarAlarma.setOnClickListener {
            viewModel.desactivarAlarma()
        }
    }

    private fun configurarBotonEmergenciaConDialogo() {
        vinculacion.btnAvisoEmergencia.setOnClickListener {
            mostrarDialogoPanicoYActivarEnFirebase()
        }
    }

    private fun mostrarDialogoPanicoYActivarEnFirebase() {
        val enlaceVista = DialogoEnviandoAlertaBinding.inflate(layoutInflater)
        dialogoPanico = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialogo_panico_titulo)
            .setView(enlaceVista.root)
            .setCancelable(false)
            .create()
        dialogoPanico?.show()
        viewModel.activarBotonPanico()
        vinculacion.root.postDelayed({
            dialogoPanico?.dismiss()
            dialogoPanico = null
        }, 2800L)
    }

    private fun ejecutarAccionContactoEmergencia(evento: EventoContactoPanico) {
        val telefono = evento.telefono.trim()
        if (telefono.isEmpty()) {
            Toast.makeText(
                requireContext(),
                R.string.panel_panico_sin_contacto,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val digitos = telefono.filter { it.isDigit() || it == '+' }
        if (!esTelefonoMarcable(digitos)) {
            Toast.makeText(
                requireContext(),
                getString(R.string.panel_panico_contacto_invalido, telefono),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        Toast.makeText(
            requireContext(),
            evento.mensajeEvento,
            Toast.LENGTH_LONG
        ).show()
        val intentMarcador = Intent(Intent.ACTION_DIAL, "tel:$digitos".toUri())
        startActivity(intentMarcador)
    }

    private fun esTelefonoMarcable(numero: String): Boolean {
        val soloDigitos = numero.filter { it.isDigit() }
        return soloDigitos in TELEFONOS_EMERGENCIA || soloDigitos.length >= 6
    }

    private fun redirigirAlLogin() {
        startActivity(
            Intent(requireContext(), LoginActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        )
        requireActivity().finish()
    }

    private fun obtenerNombreReservaDesdeAuth(): String {
        val usuario = autenticacion.currentUser ?: return getString(R.string.demo_nombre_reserva)
        usuario.displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.let {
                return it.replaceFirstChar { c ->
                    if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
                }
            }
        return usuario.email
            ?.substringBefore("@")
            ?.takeIf { it.isNotEmpty() }
            ?.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
            }
            ?: getString(R.string.demo_nombre_reserva)
    }

    private fun actualizarCabeceraPersonal(estado: PanelUiState) {
        if (estado.fechaCabecera.isNotBlank()) {
            vinculacion.tvFechaHoy.text = estado.fechaCabecera
        }
        when {
            estado.climaCargando -> {
                vinculacion.imagenClimaSimulado.setImageResource(R.drawable.ic_clima_nube)
                vinculacion.imagenClimaSimulado.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.white)
                )
                vinculacion.tvClimaSimulado.text = getString(R.string.panel_clima_cargando)
            }
            estado.climaCabecera != null -> {
                val clima = estado.climaCabecera
                vinculacion.imagenClimaSimulado.clearColorFilter()
                vinculacion.imagenClimaSimulado.setImageResource(clima.iconoResId)
                vinculacion.imagenClimaSimulado.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.white)
                )
                vinculacion.tvClimaSimulado.text = getString(
                    R.string.panel_clima_formato,
                    clima.descripcion,
                    clima.temperaturaCelsius
                )
            }
            !estado.mensajeClima.isNullOrBlank() -> {
                vinculacion.imagenClimaSimulado.setImageResource(R.drawable.ic_clima_nube)
                vinculacion.imagenClimaSimulado.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.white)
                )
                vinculacion.tvClimaSimulado.text = estado.mensajeClima
            }
        }
    }

    private fun solicitarUbicacionYCargarClima() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            obtenerUbicacionYCargarClima()
        } else {
            solicitudPermisoUbicacion.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun obtenerUbicacionYCargarClima() {
        val gestorUbicacion = requireContext().getSystemService(LocationManager::class.java)
            ?: run {
                viewModel.marcarClimaSinUbicacion()
                return
            }

        val ubicacionConocida = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).firstNotNullOfOrNull { proveedor ->
            gestorUbicacion.getLastKnownLocation(proveedor)
        }

        if (ubicacionConocida != null) {
            viewModel.cargarClima(ubicacionConocida.latitude, ubicacionConocida.longitude)
            return
        }

        listenerUbicacion?.let { gestorUbicacion.removeUpdates(it) }
        val listener = object : LocationListener {
            override fun onLocationChanged(ubicacion: Location) {
                gestorUbicacion.removeUpdates(this)
                listenerUbicacion = null
                viewModel.cargarClima(ubicacion.latitude, ubicacion.longitude)
            }
        }
        listenerUbicacion = listener
        gestorUbicacion.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER,
            0L,
            0f,
            listener,
            Looper.getMainLooper()
        )
    }

    private companion object {
        val TELEFONOS_EMERGENCIA = setOf("112", "911")
    }
}
