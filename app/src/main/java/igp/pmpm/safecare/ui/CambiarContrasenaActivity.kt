package igp.pmpm.safecare.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseAuth
import igp.pmpm.safecare.R
import igp.pmpm.safecare.data.RepositorioAutenticacion
import igp.pmpm.safecare.databinding.ActivityCambiarContrasenaBinding
import kotlinx.coroutines.launch

class CambiarContrasenaActivity : AppCompatActivity() {

    private lateinit var vinculacion: ActivityCambiarContrasenaBinding
    private val repositorioAutenticacion = RepositorioAutenticacion()

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        if (FirebaseAuth.getInstance().currentUser == null) {
            Toast.makeText(this, R.string.perfil_toast_sesion_invalida, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        enableEdgeToEdge()
        vinculacion = ActivityCambiarContrasenaBinding.inflate(layoutInflater)
        setContentView(vinculacion.root)
        ViewCompat.setOnApplyWindowInsetsListener(vinculacion.contenedorPrincipalCambiarContrasena) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
        vinculacion.barraHerramientasCambiarContrasena.setNavigationOnClickListener { finish() }
        vinculacion.btnGuardarContrasena.setOnClickListener { guardarNuevaContrasena() }
    }

    private fun guardarNuevaContrasena() {
        limpiarErroresCampos()

        val actual = vinculacion.etContrasenaActual.text?.toString().orEmpty()
        val nueva = vinculacion.etContrasenaNueva.text?.toString().orEmpty()
        val repetir = vinculacion.etContrasenaRepetir.text?.toString().orEmpty()

        when {
            actual.isEmpty() -> {
                vinculacion.layoutContrasenaActual.error =
                    getString(R.string.cambiar_contrasena_error_actual_vacia)
                return
            }
            nueva.isEmpty() -> {
                vinculacion.layoutContrasenaNueva.error =
                    getString(R.string.cambiar_contrasena_error_nueva_vacia)
                return
            }
            nueva.length < 6 -> {
                vinculacion.layoutContrasenaNueva.error =
                    getString(R.string.error_contrasena_corta)
                return
            }
            nueva != repetir -> {
                vinculacion.layoutContrasenaRepetir.error =
                    getString(R.string.cambiar_contrasena_error_no_coinciden)
                return
            }
            nueva == actual -> {
                vinculacion.layoutContrasenaNueva.error =
                    getString(R.string.cambiar_contrasena_error_misma_que_actual)
                return
            }
        }

        vinculacion.btnGuardarContrasena.isEnabled = false
        lifecycleScope.launch {
            runCatching {
                repositorioAutenticacion.actualizarContrasena(actual, nueva)
            }.onSuccess {
                Toast.makeText(
                    this@CambiarContrasenaActivity,
                    R.string.cambiar_contrasena_exito,
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }.onFailure { error ->
                val mensaje = resolverMensajeError(error)
                Toast.makeText(
                    this@CambiarContrasenaActivity,
                    mensaje,
                    Toast.LENGTH_LONG
                ).show()
            }
            vinculacion.btnGuardarContrasena.isEnabled = true
        }
    }

    private fun resolverMensajeError(error: Throwable): String {
        val causa = error.cause ?: error
        return when (causa) {
            is FirebaseAuthInvalidCredentialsException ->
                getString(R.string.cambiar_contrasena_error_actual_incorrecta)
            is FirebaseAuthWeakPasswordException ->
                getString(R.string.error_contrasena_corta)
            else -> causa.message ?: getString(R.string.cambiar_contrasena_error_generico)
        }
    }

    private fun limpiarErroresCampos() {
        vinculacion.layoutContrasenaActual.error = null
        vinculacion.layoutContrasenaNueva.error = null
        vinculacion.layoutContrasenaRepetir.error = null
    }
}
