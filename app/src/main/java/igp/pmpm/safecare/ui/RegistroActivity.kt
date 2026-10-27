package igp.pmpm.safecare.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import igp.pmpm.safecare.MainActivity
import igp.pmpm.safecare.data.MensajesValidacionAutenticacion
import igp.pmpm.safecare.databinding.ActivityRegistroBinding
import igp.pmpm.safecare.viewmodel.AutenticacionViewModel
import kotlinx.coroutines.launch

class RegistroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistroBinding

    private val viewModel: AutenticacionViewModel by viewModels()

        override fun onCreate(guardado: Bundle?) {
        super.onCreate(guardado)
        enableEdgeToEdge()
        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollRegistro) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
        configurarListenersCampos()
        observarEstadoPantallaRegistro()
        configurarClicks()
    }

        private fun configurarClicks() {
        binding.btnRegistro.setOnClickListener { intentarAlta() }
        binding.btnIrAInicioSesion.setOnClickListener { abrirPantallaInicioSesion() }
    }

        private fun configurarListenersCampos() {
        binding.etNombre.doAfterTextChanged { binding.tilNombre.error = null }
        binding.etCorreo.doAfterTextChanged { binding.tilCorreo.error = null }
        binding.etContrasena.doAfterTextChanged { binding.tilContrasena.error = null }
        binding.etContactoEmergencia.doAfterTextChanged { binding.tilContactoEmergencia.error = null }
    }

        private fun observarEstadoPantallaRegistro() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estadoPantalla.collect { estadoPantalla ->
                    binding.progressRegistro.isVisible = estadoPantalla.cargando
                    binding.btnRegistro.isEnabled = !estadoPantalla.cargando
                    binding.btnIrAInicioSesion.isEnabled = !estadoPantalla.cargando
                    val mensaje = estadoPantalla.mensaje
                    if (mensaje != null) {
                        mostrarRetroalimentacionRegistro(mensaje)
                        viewModel.limpiarMensaje()
                    }
                    if (estadoPantalla.registroExitoso) {
                        alCompletarRegistro()
                        viewModel.limpiarExitoRegistro()
                    }
                }
            }
        }
    }

        private fun intentarAlta() {
        limpiarErroresEntrada()
        val nombreUsuario = binding.etNombre.text?.toString().orEmpty()
        val correo = binding.etCorreo.text?.toString().orEmpty()
        val contrasena = binding.etContrasena.text?.toString().orEmpty()
        val contactoEmergencia = binding.etContactoEmergencia.text?.toString().orEmpty()
        if (!validacionRapidaRegistro(
                nombreUsuario,
                correo,
                contrasena,
                contactoEmergencia
            )
        ) {
            return
        }
        viewModel.registrar(
            nombreUsuario,
            correo,
            contrasena,
            contactoEmergencia
        )
    }

        private fun validacionRapidaRegistro(
        nombreUsuario: String,
        correo: String,
        contrasena: String,
        contactoEmergencia: String
    ): Boolean {
        var esValido = true
        if (nombreUsuario.isBlank()) {
            binding.tilNombre.error = MensajesValidacionAutenticacion.NOMBRE_VACIO
            esValido = false
        }
        if (correo.isBlank()) {
            binding.tilCorreo.error = MensajesValidacionAutenticacion.CORREO_VACIO
            esValido = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches()) {
            binding.tilCorreo.error = MensajesValidacionAutenticacion.CORREO_FORMATO_INVALIDO
            esValido = false
        }
        if (contrasena.isBlank()) {
            binding.tilContrasena.error = MensajesValidacionAutenticacion.CONTRASENA_VACIA
            esValido = false
        } else if (contrasena.length < 6) {
            binding.tilContrasena.error =
                MensajesValidacionAutenticacion.CONTRASENA_DEMASIADO_CORTA
            esValido = false
        }
        if (contactoEmergencia.isBlank()) {
            binding.tilContactoEmergencia.error =
                MensajesValidacionAutenticacion.CONTACTO_EMERGENCIA_VACIO
            esValido = false
        }
        return esValido
    }

        private fun limpiarErroresEntrada() {
        binding.tilNombre.error = null
        binding.tilCorreo.error = null
        binding.tilContrasena.error = null
        binding.tilContactoEmergencia.error = null
    }

        private fun mostrarRetroalimentacionRegistro(mensaje: String) {
        when (mensaje) {
            MensajesValidacionAutenticacion.NOMBRE_VACIO ->
                binding.tilNombre.error = mensaje

            MensajesValidacionAutenticacion.CORREO_VACIO,
            MensajesValidacionAutenticacion.CORREO_FORMATO_INVALIDO ->
                binding.tilCorreo.error = mensaje

            MensajesValidacionAutenticacion.CONTRASENA_VACIA,
            MensajesValidacionAutenticacion.CONTRASENA_DEMASIADO_CORTA ->
                binding.tilContrasena.error = mensaje

            MensajesValidacionAutenticacion.CONTACTO_EMERGENCIA_VACIO ->
                binding.tilContactoEmergencia.error = mensaje

            else -> Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
        }
    }

        private fun alCompletarRegistro() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

        private fun abrirPantallaInicioSesion() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
