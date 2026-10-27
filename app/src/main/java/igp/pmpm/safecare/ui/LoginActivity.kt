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
import com.google.firebase.auth.FirebaseAuth
import igp.pmpm.safecare.MainActivity
import igp.pmpm.safecare.data.MensajesValidacionAutenticacion
import igp.pmpm.safecare.databinding.ActivityLoginBinding
import igp.pmpm.safecare.viewmodel.AutenticacionViewModel
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val viewModel: AutenticacionViewModel by viewModels()

        override fun onCreate(guardado: Bundle?) {
        super.onCreate(guardado)
        if (FirebaseAuth.getInstance().currentUser != null) {
            abrirPantallaPrincipal()
            return
        }
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollLogin) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
        configurarListenersCampos()
        observarEstadoPantallaAutenticacion()
        configurarClicks()
    }

        private fun configurarClicks() {
        binding.btnLogin.setOnClickListener { intentarInicioSesion() }
        binding.btnIrARegistro.setOnClickListener { abrirPantallaRegistro() }
    }

        private fun configurarListenersCampos() {
        binding.etCorreo.doAfterTextChanged { binding.tilCorreo.error = null }
        binding.etContrasena.doAfterTextChanged { binding.tilContrasena.error = null }
    }

        private fun observarEstadoPantallaAutenticacion() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estadoPantalla.collect { estadoPantalla ->
                    binding.progressLogin.isVisible = estadoPantalla.cargando
                    binding.btnLogin.isEnabled = !estadoPantalla.cargando
                    binding.btnIrARegistro.isEnabled = !estadoPantalla.cargando
                    val mensaje = estadoPantalla.mensaje
                    if (mensaje != null) {
                        mostrarRetroalimentacionInicioSesion(mensaje)
                        viewModel.limpiarMensaje()
                    }
                    if (estadoPantalla.inicioSesionExitoso) {
                        abrirPantallaPrincipal()
                        viewModel.limpiarExitoInicioSesion()
                    }
                }
            }
        }
    }

        private fun intentarInicioSesion() {
        limpiarErroresEntrada()
        val textoCorreo = binding.etCorreo.text?.toString().orEmpty()
        val textoContrasena = binding.etContrasena.text?.toString().orEmpty()
        if (!validacionRapidaInicioSesion(textoCorreo, textoContrasena)) {
            return
        }
        viewModel.iniciarSesion(textoCorreo, textoContrasena)
    }

        private fun validacionRapidaInicioSesion(textoCorreo: String, textoContrasena: String): Boolean {
        var esValido = true
        if (textoCorreo.isBlank()) {
            binding.tilCorreo.error = MensajesValidacionAutenticacion.CORREO_VACIO
            esValido = false
        }
        if (textoContrasena.isBlank()) {
            binding.tilContrasena.error = MensajesValidacionAutenticacion.CONTRASENA_VACIA
            esValido = false
        }
        if (textoCorreo.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(
                textoCorreo.trim()
            ).matches()
        ) {
            binding.tilCorreo.error = MensajesValidacionAutenticacion.CORREO_FORMATO_INVALIDO
            esValido = false
        }
        return esValido
    }

        private fun limpiarErroresEntrada() {
        binding.tilCorreo.error = null
        binding.tilContrasena.error = null
    }

        private fun mostrarRetroalimentacionInicioSesion(mensaje: String) {
        when (mensaje) {
            MensajesValidacionAutenticacion.CORREO_VACIO ->
                binding.tilCorreo.error = mensaje

            MensajesValidacionAutenticacion.CORREO_FORMATO_INVALIDO ->
                binding.tilCorreo.error = mensaje

            MensajesValidacionAutenticacion.CONTRASENA_VACIA ->
                binding.tilContrasena.error = mensaje

            else -> Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
        }
    }

        private fun abrirPantallaRegistro() {
        startActivity(Intent(this, RegistroActivity::class.java))
    }

        private fun abrirPantallaPrincipal() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
