package igp.pmpm.safecare.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import igp.pmpm.safecare.data.MensajesValidacionAutenticacion
import igp.pmpm.safecare.data.RepositorioAutenticacion
import igp.pmpm.safecare.data.RepositorioContactosEmergencia
import igp.pmpm.safecare.model.ConfiguracionLlamadaEmergencia
import igp.pmpm.safecare.model.ContactoEmergencia
import igp.pmpm.safecare.model.Usuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException

data class EstadoAutenticacionPantalla(
    val cargando: Boolean = false,
    val mensaje: String? = null,
    val registroExitoso: Boolean = false,
    val inicioSesionExitoso: Boolean = false
)

class AutenticacionViewModel(
    private val repositorio: RepositorioAutenticacion = RepositorioAutenticacion(),
    private val repositorioContactos: RepositorioContactosEmergencia = RepositorioContactosEmergencia()
) : ViewModel() {

    private val _estadoPantalla = MutableStateFlow(EstadoAutenticacionPantalla())
    val estadoPantalla: StateFlow<EstadoAutenticacionPantalla> = _estadoPantalla.asStateFlow()

        fun limpiarMensaje() {
        _estadoPantalla.update { it.copy(mensaje = null) }
    }

        fun limpiarExitoRegistro() {
        _estadoPantalla.update { it.copy(registroExitoso = false) }
    }

        fun limpiarExitoInicioSesion() {
        _estadoPantalla.update { it.copy(inicioSesionExitoso = false) }
    }

        fun registrar(
        nombre: String,
        correoElectronico: String,
        contrasena: String,
        contactoEmergencia: String
    ): Boolean {
        val nombreRecortado = nombre.trim()
        val correoRecortado = correoElectronico.trim()
        val contactoRecortado = contactoEmergencia.trim()
        if (!validarCamposRegistro(
                nombreRecortado,
                correoRecortado,
                contrasena,
                contactoRecortado
            )
        ) {
            return false
        }
        _estadoPantalla.update { it.copy(cargando = true, mensaje = null) }
        viewModelScope.launch {
            try {
                val resultado = repositorio.crearCuentaConCorreoYContrasena(
                    correoRecortado,
                    contrasena
                )
                val usuarioAuth = requireNotNull(resultado.user) { "No hay FirebaseUser tras el registro." }
                val usuarioPerfil = Usuario(
                    idUsuario = usuarioAuth.uid,
                    nombre = nombreRecortado,
                    correoElectronico = correoRecortado
                )
                repositorio.guardarPerfilEnBaseDeDatos(usuarioPerfil)
                repositorioContactos.guardarContactoPerfil(
                    idUsuario = usuarioAuth.uid,
                    nombre = nombreRecortado,
                    telefono = contactoRecortado
                )
                repositorioContactos.guardarConfiguracionLlamada(
                    idUsuario = usuarioAuth.uid,
                    configuracion = ConfiguracionLlamadaEmergencia(
                        llamarA112 = false,
                        idContactoSeleccionado = ContactoEmergencia.ID_CONTACTO_PERFIL
                    )
                )
                _estadoPantalla.update {
                    it.copy(
                        cargando = false,
                        registroExitoso = true,
                        mensaje = null
                    )
                }
            } catch (error: Exception) {
                _estadoPantalla.update {
                    it.copy(
                        cargando = false,
                        mensaje = mapearErrorFirebase(error, esInicioSesion = false)
                    )
                }
            }
        }
        return true
    }

        fun iniciarSesion(correoElectronico: String, contrasena: String): Boolean {
        val correoRecortado = correoElectronico.trim()
        if (!validarCamposLogin(correoRecortado, contrasena)) {
            return false
        }
        _estadoPantalla.update { it.copy(cargando = true, mensaje = null) }
        viewModelScope.launch {
            try {
                repositorio.iniciarSesionConCorreoYContrasena(
                    correoRecortado,
                    contrasena
                )
                _estadoPantalla.update {
                    it.copy(
                        cargando = false,
                        inicioSesionExitoso = true,
                        mensaje = null
                    )
                }
            } catch (error: Exception) {
                _estadoPantalla.update {
                    it.copy(
                        cargando = false,
                        mensaje = mapearErrorFirebase(error, esInicioSesion = true)
                    )
                }
            }
        }
        return true
    }

        private fun validarCamposRegistro(
        nombre: String,
        correo: String,
        contrasena: String,
        contactoEmergencia: String
    ): Boolean {
        if (nombre.isEmpty()) {
            _estadoPantalla.update { it.copy(mensaje = MensajesValidacionAutenticacion.NOMBRE_VACIO) }
            return false
        }
        if (correo.isEmpty()) {
            _estadoPantalla.update { it.copy(mensaje = MensajesValidacionAutenticacion.CORREO_VACIO) }
            return false
        }
        if (!esFormatoCorreoValido(correo)) {
            _estadoPantalla.update {
                it.copy(
                    mensaje = MensajesValidacionAutenticacion.CORREO_FORMATO_INVALIDO
                )
            }
            return false
        }
        if (contrasena.isEmpty()) {
            _estadoPantalla.update { it.copy(mensaje = MensajesValidacionAutenticacion.CONTRASENA_VACIA) }
            return false
        }
        if (contrasena.length < 6) {
            _estadoPantalla.update {
                it.copy(mensaje = MensajesValidacionAutenticacion.CONTRASENA_DEMASIADO_CORTA)
            }
            return false
        }
        if (contactoEmergencia.isEmpty()) {
            _estadoPantalla.update {
                it.copy(
                    mensaje = MensajesValidacionAutenticacion.CONTACTO_EMERGENCIA_VACIO
                )
            }
            return false
        }
        return true
    }

        private fun validarCamposLogin(correo: String, contrasena: String): Boolean {
        if (correo.isEmpty()) {
            _estadoPantalla.update { it.copy(mensaje = MensajesValidacionAutenticacion.CORREO_VACIO) }
            return false
        }
        if (!esFormatoCorreoValido(correo)) {
            _estadoPantalla.update {
                it.copy(
                    mensaje = MensajesValidacionAutenticacion.CORREO_FORMATO_INVALIDO
                )
            }
            return false
        }
        if (contrasena.isEmpty()) {
            _estadoPantalla.update { it.copy(mensaje = MensajesValidacionAutenticacion.CONTRASENA_VACIA) }
            return false
        }
        return true
    }

        private fun esFormatoCorreoValido(correo: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(correo).matches()
    }

        private fun mapearErrorFirebase(throwable: Throwable, esInicioSesion: Boolean): String {
        return when (val causa = desenvolver(throwable)) {
            is FirebaseAuthWeakPasswordException ->
                "La contraseña es demasiado débil. Usa al menos 6 caracteres."

            is FirebaseAuthInvalidCredentialsException ->
                if (esInicioSesion) "Correo o contraseña incorrectos."
                else "Las credenciales no son válidas."

            is FirebaseAuthUserCollisionException ->
                "Ese correo ya está registrado."

            is FirebaseAuthException -> when (causa.errorCode) {
                "ERROR_USER_NOT_FOUND",
                "ERROR_WRONG_PASSWORD",
                "ERROR_INVALID_CREDENTIAL",
                "ERROR_INVALID_LOGIN_CREDENTIALS" ->
                    if (esInicioSesion) "Correo o contraseña incorrectos."
                    else "Las credenciales no son válidas."

                "ERROR_INVALID_EMAIL" ->
                    "El correo no es válido."

                "ERROR_USER_DISABLED" ->
                    "Esta cuenta está desactivada."

                "ERROR_TOO_MANY_REQUESTS" ->
                    "Demasiados intentos. Inténtalo más tarde."

                else -> if (esInicioSesion) "Correo o contraseña incorrectos."
                else "No se pudo completar la operación."
            }

            is IllegalStateException -> causa.message
                ?: "No se pudo completar la operación."

            else -> if (esInicioSesion) "Correo o contraseña incorrectos."
            else "Ha ocurrido un error. Inténtalo de nuevo."
        }
    }

        private fun desenvolver(throwable: Throwable): Throwable {
        if (throwable is ExecutionException && throwable.cause != null) {
            return desenvolver(throwable.cause!!)
        }
        return throwable
    }
}
