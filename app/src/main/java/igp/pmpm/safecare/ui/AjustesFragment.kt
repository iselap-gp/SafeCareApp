package igp.pmpm.safecare.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import igp.pmpm.safecare.R
import igp.pmpm.safecare.databinding.FragmentAjustesBinding
import igp.pmpm.safecare.viewmodel.AjustesUiState
import igp.pmpm.safecare.viewmodel.AjustesViewModel
import igp.pmpm.safecare.viewmodel.AjustesViewModelFactory
import kotlinx.coroutines.launch

class AjustesFragment : Fragment() {

    private var _vinculacion: FragmentAjustesBinding? = null
    private val vinculacion get() = _vinculacion!!

    private val autenticacion: FirebaseAuth = FirebaseAuth.getInstance()
    private val viewModel: AjustesViewModel by viewModels { AjustesViewModelFactory() }

    private val listenerModoMayores = CompoundButton.OnCheckedChangeListener { _, activo ->
        viewModel.cambiarModoMayores(activo)
    }
    private val listenerModoMascotas = CompoundButton.OnCheckedChangeListener { _, activo ->
        viewModel.cambiarModoMascotas(activo)
    }
    private val listenerModoNinos = CompoundButton.OnCheckedChangeListener { _, activo ->
        viewModel.cambiarModoNinos(activo)
    }

    override fun onCreateView(
        inflador: LayoutInflater,
        contenedor: ViewGroup?,
        estado: Bundle?
    ): View {
        _vinculacion = FragmentAjustesBinding.inflate(inflador, contenedor, false)
        return vinculacion.root
    }

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        configurarNavegacionCuenta()
        configurarSistemaLocal()
        configurarSwitchesProteccionFamiliar()
        observarEstadoAjustes()
    }

    override fun onDestroyView() {
        _vinculacion = null
        super.onDestroyView()
    }

    private fun observarEstadoAjustes() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estadoPantalla.collect { estado ->
                    sincronizarSwitchesConFirebase(estado)
                    vinculacion.switchModoMayores.isEnabled = !estado.escribiendo
                    vinculacion.switchModoMascotas.isEnabled = !estado.escribiendo
                    vinculacion.switchModoNinos.isEnabled = !estado.escribiendo
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

    private fun sincronizarSwitchesConFirebase(estado: AjustesUiState) {
        if (vinculacion.switchModoMayores.isChecked == estado.modoMayoresActivo &&
            vinculacion.switchModoMascotas.isChecked == estado.modoMascotasActivo &&
            vinculacion.switchModoNinos.isChecked == estado.modoNinosActivo
        ) {
            return
        }

        vinculacion.switchModoMayores.setOnCheckedChangeListener(null)
        vinculacion.switchModoMascotas.setOnCheckedChangeListener(null)
        vinculacion.switchModoNinos.setOnCheckedChangeListener(null)

        vinculacion.switchModoMayores.isChecked = estado.modoMayoresActivo
        vinculacion.switchModoMascotas.isChecked = estado.modoMascotasActivo
        vinculacion.switchModoNinos.isChecked = estado.modoNinosActivo

        vinculacion.switchModoMayores.setOnCheckedChangeListener(listenerModoMayores)
        vinculacion.switchModoMascotas.setOnCheckedChangeListener(listenerModoMascotas)
        vinculacion.switchModoNinos.setOnCheckedChangeListener(listenerModoNinos)
    }

    private fun configurarSwitchesProteccionFamiliar() {
        vinculacion.switchModoMayores.setOnCheckedChangeListener(listenerModoMayores)
        vinculacion.switchModoMascotas.setOnCheckedChangeListener(listenerModoMascotas)
        vinculacion.switchModoNinos.setOnCheckedChangeListener(listenerModoNinos)
    }

    private fun configurarNavegacionCuenta() {
        vinculacion.filaInformacionCuenta.setOnClickListener { abrirPantallaPerfil() }
        vinculacion.filaCambiarContrasena.setOnClickListener {
            startActivity(Intent(requireContext(), CambiarContrasenaActivity::class.java))
        }
        vinculacion.filaContactoEmergencia.setOnClickListener {
            startActivity(Intent(requireContext(), ContactosEmergenciaActivity::class.java))
        }
        vinculacion.btnCerrarSesionAjustes.setOnClickListener { cerrarSesionYVolverAlLogin() }
    }

    private fun configurarSistemaLocal() {
        vinculacion.filaSensibilidadSensor.setOnClickListener {
            startActivity(Intent(requireContext(), ConsolaSimulacionActivity::class.java))
        }
        vinculacion.btnConfigurarDispositivosEsp32.setOnClickListener {
            mostrarDialogoConfiguracionEsp32()
        }
    }

    private fun abrirPantallaPerfil() {
        startActivity(Intent(requireContext(), PerfilActivity::class.java))
    }

    private fun mostrarDialogoConfiguracionEsp32() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialogo_esp32_titulo)
            .setMessage(R.string.dialogo_esp32_mensaje)
            .setPositiveButton(R.string.dialogo_esp32_boton_aceptar) { dialogo, _ -> dialogo.dismiss() }
            .show()
    }

    private fun cerrarSesionYVolverAlLogin() {
        autenticacion.signOut()
        startActivity(
            Intent(requireContext(), LoginActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        )
        requireActivity().finish()
    }
}
