package igp.pmpm.safecare.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import igp.pmpm.safecare.R
import igp.pmpm.safecare.databinding.FragmentCamarasBinding
import igp.pmpm.safecare.model.CamaraZonaInterior
import igp.pmpm.safecare.model.EstadoZonaInteriorUi
import igp.pmpm.safecare.viewmodel.CamarasUiState
import igp.pmpm.safecare.viewmodel.CamarasViewModel
import igp.pmpm.safecare.viewmodel.CamarasViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CamarasFragment : Fragment() {

    private var _vinculacion: FragmentCamarasBinding? = null
    private val vinculacion get() = _vinculacion!!

    private val viewModel: CamarasViewModel by viewModels {
        CamarasViewModelFactory(nombresCortosZonaDesdeRecursos())
    }

    private var animadorParpadeoPuerta: ObjectAnimator? = null
    private var animadorParpadeoSalon: ObjectAnimator? = null
    private var animadorParpadeoCocina: ObjectAnimator? = null
    private var bitmapStreamEntradaActual: android.graphics.Bitmap? = null

    override fun onCreateView(
        inflador: LayoutInflater,
        contenedor: ViewGroup?,
        estado: Bundle?
    ): View {
        _vinculacion = FragmentCamarasBinding.inflate(inflador, contenedor, false)
        return vinculacion.root
    }

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        configurarAccesoPerfilDesdeCabecera()
        observarEstadoCamaras()
    }

    override fun onDestroyView() {
        detenerParpadeoEntrada()
        detenerParpadeoSalon()
        detenerParpadeoCocina()
        bitmapStreamEntradaActual?.recycle()
        bitmapStreamEntradaActual = null
        _vinculacion = null
        super.onDestroyView()
    }

    private fun observarEstadoCamaras() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estadoPantalla.collect { estado ->
                    renderizarEstado(estado)
                }
            }
        }
    }

    private fun renderizarEstado(estado: CamarasUiState) {
        actualizarCabeceraVigilancia(estado)
        actualizarCamaraEntrada(estado)
        actualizarCamaraInterior(
            estado.estadoZonaInterior(CamaraZonaInterior.REGISTRO[0].idFirebase),
            VistasCamaraInterior(
                tarjeta = vinculacion.tarjetaCamaraSalon,
                badgeEnVivo = vinculacion.badgeEnVivoSalon,
                overlayAlertas = vinculacion.overlayAlertasSalon,
                tvAtencionAlertaActiva = vinculacion.tvAtencionAlertaSalon,
                tvAlertaMovimiento = vinculacion.tvAlertaMovimientoSalon,
                tvFiltroMascotas = vinculacion.tvFiltroMascotasSalon,
                tvVigilanciaMayores = vinculacion.tvVigilanciaMayoresSalon,
                tvAlertaInactividad = vinculacion.tvAlertaInactividadSalon,
                tvActividadDetectada = vinculacion.tvActividadDetectadaSalon,
                tvEstadoSensor = vinculacion.tvEstadoSensorSalon,
                iniciarParpadeo = ::iniciarParpadeoSalon,
                detenerParpadeo = ::detenerParpadeoSalon
            )
        )
        actualizarCamaraInterior(
            estado.estadoZonaInterior(CamaraZonaInterior.REGISTRO[1].idFirebase),
            VistasCamaraInterior(
                tarjeta = vinculacion.tarjetaCamaraCocina,
                badgeEnVivo = vinculacion.badgeEnVivoCocina,
                overlayAlertas = vinculacion.overlayAlertasCocina,
                tvAtencionAlertaActiva = vinculacion.tvAtencionAlertaCocina,
                tvAlertaMovimiento = vinculacion.tvAlertaMovimientoCocina,
                tvFiltroMascotas = vinculacion.tvFiltroMascotasCocina,
                tvVigilanciaMayores = vinculacion.tvVigilanciaMayoresCocina,
                tvAlertaInactividad = vinculacion.tvAlertaInactividadCocina,
                tvActividadDetectada = vinculacion.tvActividadDetectadaCocina,
                tvEstadoSensor = vinculacion.tvEstadoSensorCocina,
                iniciarParpadeo = ::iniciarParpadeoCocina,
                detenerParpadeo = ::detenerParpadeoCocina
            )
        )
        estado.error?.let { mensaje ->
            if (isAdded) {
                Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
            }
            viewModel.limpiarError()
        }
    }

    private fun actualizarCabeceraVigilancia(estado: CamarasUiState) {
        val hayAlerta = estado.hayAlgunaAlertaActiva()
        val contexto = requireContext()
        if (hayAlerta) {
            vinculacion.tvSubtituloVigilancia.text =
                getString(R.string.camaras_atencion_alerta_activa)
            vinculacion.tvSubtituloVigilancia.setTextColor(
                ContextCompat.getColor(contexto, R.color.safecare_alerta)
            )
        } else {
            vinculacion.tvSubtituloVigilancia.text =
                getString(R.string.camaras_subtitulo_hub)
            vinculacion.tvSubtituloVigilancia.setTextColor(
                ContextCompat.getColor(contexto, R.color.safecare_text_secondary)
            )
        }
    }

    private fun actualizarCamaraEntrada(estado: CamarasUiState) {
        val hayAlertaEntrada = estado.tieneAlertaEntradaActiva
        val hayOverlayPuerta = estado.alertaEntradaCritica
        val hayAlertaCritica = estado.alertaEntradaCritica

        renderizarStreamEntrada(estado.streamEntradaBase64)

        vinculacion.tvAtencionAlertaEntrada.isVisible = hayAlertaEntrada
        vinculacion.badgeEnVivoEntrada.isVisible = estado.transmisionEntradaActiva && !hayAlertaEntrada
        vinculacion.overlayAlertasEntrada.isVisible = hayOverlayPuerta
        vinculacion.tvControlInfantilEntrada.isVisible = estado.alertaControlInfantilEntrada
        vinculacion.tvAlertaPuertaEntrada.isVisible = hayOverlayPuerta && !estado.alertaControlInfantilEntrada

        if (hayAlertaCritica) {
            aplicarBordeAlerta(vinculacion.tarjetaCamaraEntrada)
            iniciarParpadeoEntrada()
        } else {
            aplicarBordeNormal(vinculacion.tarjetaCamaraEntrada)
            detenerParpadeoEntrada()
            vinculacion.overlayAlertasEntrada.alpha = 1f
        }
    }

    private fun actualizarCamaraInterior(zona: EstadoZonaInteriorUi, vistas: VistasCamaraInterior) {
        if (!zona.vigilanciaActiva) {
            vistas.tvAtencionAlertaActiva.isVisible = false
            vistas.badgeEnVivo.isVisible = true
            vistas.overlayAlertas.isVisible = false
            vistas.tvAlertaMovimiento.isVisible = false
            vistas.tvFiltroMascotas.isVisible = false
            vistas.tvVigilanciaMayores.isVisible = false
            vistas.tvAlertaInactividad.isVisible = false
            vistas.tvActividadDetectada.isVisible = false
            vistas.tvEstadoSensor.isVisible = false
            aplicarBordeNormal(vistas.tarjeta)
            vistas.detenerParpadeo()
            return
        }

        vistas.tvAtencionAlertaActiva.isVisible = zona.tieneAlertaActiva
        vistas.badgeEnVivo.isVisible = zona.vigilanciaActiva && !zona.tieneAlertaActiva
        val mostrarMovimiento = zona.alertaMovimiento
        val mostrarInactividad = zona.alertaInactividad
        vistas.overlayAlertas.isVisible = mostrarMovimiento || mostrarInactividad
        vistas.tvAlertaMovimiento.isVisible = mostrarMovimiento
        vistas.tvAlertaInactividad.isVisible = mostrarInactividad
        vistas.tvFiltroMascotas.isVisible =
            zona.mostrarFiltroMascotas && !mostrarMovimiento && !mostrarInactividad
        vistas.tvVigilanciaMayores.isVisible =
            zona.mostrarVigilanciaMayores && !mostrarInactividad && !mostrarMovimiento
        vistas.tvActividadDetectada.isVisible =
            zona.actividadDetectada && !mostrarInactividad && !mostrarMovimiento

        vistas.tvEstadoSensor.isVisible = false

        if (zona.tieneAlertaActiva) {
            aplicarBordeAlerta(vistas.tarjeta)
            if (mostrarMovimiento && zona.alertaMovimiento) {
                vistas.iniciarParpadeo()
            } else {
                vistas.detenerParpadeo()
                vistas.overlayAlertas.alpha = 1f
            }
        } else {
            aplicarBordeNormal(vistas.tarjeta)
            vistas.detenerParpadeo()
            vistas.overlayAlertas.alpha = 1f
        }
    }

    private fun nombresCortosZonaDesdeRecursos(): Map<String, String> =
        CamaraZonaInterior.REGISTRO.associate { zona ->
            zona.idFirebase to getString(zona.nombreCortoResId)
        }

    private fun renderizarStreamEntrada(streamBase64: String) {
        if (streamBase64.isBlank()) {
            vinculacion.imvStreamEntrada.isVisible = false
            vinculacion.imvIconoReproducirEntrada.isVisible = true
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                decodificarBase64ABitmap(streamBase64)
            } ?: return@launch
            if (!isAdded) {
                bitmap.recycle()
                return@launch
            }
            bitmapStreamEntradaActual?.recycle()
            bitmapStreamEntradaActual = bitmap
            vinculacion.imvStreamEntrada.setImageBitmap(bitmap)
            vinculacion.imvStreamEntrada.isVisible = true
            vinculacion.imvIconoReproducirEntrada.isVisible = false
        }
    }

    private fun decodificarBase64ABitmap(base64: String): android.graphics.Bitmap? {
        return runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun aplicarBordeAlerta(tarjeta: MaterialCardView) {
        tarjeta.strokeColor = ContextCompat.getColor(requireContext(), R.color.safecare_alerta)
        tarjeta.strokeWidth = grosorBordeEnDp(2f)
    }

    private fun aplicarBordeNormal(tarjeta: MaterialCardView) {
        tarjeta.strokeColor = ContextCompat.getColor(requireContext(), R.color.safecare_stroke)
        tarjeta.strokeWidth = grosorBordeEnDp(1f)
    }

    private fun iniciarParpadeoEntrada() {
        if (animadorParpadeoPuerta?.isRunning == true) return
        animadorParpadeoPuerta?.cancel()
        animadorParpadeoPuerta = crearAnimadorParpadeo(vinculacion.overlayAlertasEntrada)
    }

    private fun iniciarParpadeoSalon() {
        if (animadorParpadeoSalon?.isRunning == true) return
        animadorParpadeoSalon?.cancel()
        animadorParpadeoSalon = crearAnimadorParpadeo(vinculacion.overlayAlertasSalon)
    }

    private fun iniciarParpadeoCocina() {
        if (animadorParpadeoCocina?.isRunning == true) return
        animadorParpadeoCocina?.cancel()
        animadorParpadeoCocina = crearAnimadorParpadeo(vinculacion.overlayAlertasCocina)
    }

    private fun crearAnimadorParpadeo(vista: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(vista, View.ALPHA, 1f, 0.35f, 1f).apply {
            duration = 750L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun detenerParpadeoEntrada() {
        animadorParpadeoPuerta?.cancel()
        animadorParpadeoPuerta = null
    }

    private fun detenerParpadeoSalon() {
        animadorParpadeoSalon?.cancel()
        animadorParpadeoSalon = null
    }

    private fun detenerParpadeoCocina() {
        animadorParpadeoCocina?.cancel()
        animadorParpadeoCocina = null
    }

    private fun grosorBordeEnDp(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private fun configurarAccesoPerfilDesdeCabecera() {
        vinculacion.btnIconoPerfil.setOnClickListener {
            startActivity(Intent(requireContext(), PerfilActivity::class.java))
        }
    }

    private data class VistasCamaraInterior(
        val tarjeta: MaterialCardView,
        val badgeEnVivo: View,
        val tvAtencionAlertaActiva: View,
        val overlayAlertas: View,
        val tvAlertaMovimiento: View,
        val tvFiltroMascotas: View,
        val tvVigilanciaMayores: View,
        val tvAlertaInactividad: View,
        val tvActividadDetectada: View,
        val tvEstadoSensor: TextView,
        val iniciarParpadeo: () -> Unit,
        val detenerParpadeo: () -> Unit
    )
}
