package igp.pmpm.safecare.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import igp.pmpm.safecare.data.RepositorioContactosEmergencia
import igp.pmpm.safecare.model.ConfiguracionLlamadaEmergencia
import igp.pmpm.safecare.model.ContactoEmergencia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactosEmergenciaUiState(
    val cargando: Boolean = true,
    val listaContactos: List<ContactoEmergencia> = emptyList(),
    val configuracionLlamada: ConfiguracionLlamadaEmergencia = ConfiguracionLlamadaEmergencia(),
    val escribiendo: Boolean = false,
    val error: String? = null
)

class ContactosEmergenciaViewModel(
    private val idUsuario: String,
    private val repositorio: RepositorioContactosEmergencia = RepositorioContactosEmergencia()
) : ViewModel() {

    private val _estadoPantalla = MutableStateFlow(ContactosEmergenciaUiState())
    val estadoPantalla: StateFlow<ContactosEmergenciaUiState> = _estadoPantalla.asStateFlow()

    init {
        if (idUsuario.isBlank()) {
            _estadoPantalla.update {
                it.copy(
                    cargando = false,
                    error = "Sesión no válida. Vuelve a iniciar sesión."
                )
            }
        } else {
            observarDatosFirebase()
        }
    }

    private fun observarDatosFirebase() {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                repositorio.observarContactos(idUsuario),
                repositorio.observarConfiguracionLlamada(idUsuario)
            ) { contactos, configuracion ->
                Pair(contactos, configuracion)
            }
                .catch { error ->
                    _estadoPantalla.update {
                        it.copy(
                            cargando = false,
                            error = error.message ?: "No se pudieron cargar los contactos."
                        )
                    }
                }
                .collect { (contactos, configuracion) ->
                    _estadoPantalla.update {
                        it.copy(
                            cargando = false,
                            listaContactos = contactos,
                            configuracionLlamada = configuracion,
                            error = null
                        )
                    }
                }
        }
    }

    fun seleccionarDestinoLlamada(usar112: Boolean, idContacto: String = "") {
        if (idUsuario.isBlank() || _estadoPantalla.value.escribiendo) return
        val configuracion = ConfiguracionLlamadaEmergencia(
            llamarA112 = usar112,
            idContactoSeleccionado = if (usar112) "" else idContacto.trim()
        )
        ejecutarEscritura {
            repositorio.guardarConfiguracionLlamada(idUsuario, configuracion)
        }
    }

    fun agregarContacto(nombre: String, telefono: String) {
        if (idUsuario.isBlank() || _estadoPantalla.value.escribiendo) return
        val nombreLimpio = nombre.trim()
        val telefonoLimpio = telefono.trim()
        if (nombreLimpio.isEmpty() || telefonoLimpio.isEmpty()) {
            _estadoPantalla.update {
                it.copy(error = "Nombre y teléfono son obligatorios.")
            }
            return
        }
        ejecutarEscritura {
            repositorio.agregarContacto(idUsuario, nombreLimpio, telefonoLimpio)
        }
    }

    fun eliminarContacto(idContacto: String) {
        if (idUsuario.isBlank() || idContacto.isBlank() || _estadoPantalla.value.escribiendo) return
        val configuracionActual = _estadoPantalla.value.configuracionLlamada
        ejecutarEscritura {
            repositorio.eliminarContacto(idUsuario, idContacto)
            if (!configuracionActual.llamarA112 &&
                configuracionActual.idContactoSeleccionado == idContacto
            ) {
                repositorio.guardarConfiguracionLlamada(
                    idUsuario,
                    configuracionActual.copy(idContactoSeleccionado = "")
                )
            }
        }
    }

    fun limpiarError() {
        _estadoPantalla.update { it.copy(error = null) }
    }

    private fun ejecutarEscritura(bloque: suspend () -> Unit) {
        viewModelScope.launch {
            _estadoPantalla.update { it.copy(escribiendo = true, error = null) }
            runCatching { bloque() }
                .onFailure { error ->
                    _estadoPantalla.update {
                        it.copy(
                            error = error.message ?: "No se pudo guardar en Firebase."
                        )
                    }
                }
            _estadoPantalla.update { it.copy(escribiendo = false) }
        }
    }
}

class ContactosEmergenciaViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ContactosEmergenciaViewModel::class.java)) {
            "ViewModel desconocido: ${modelClass.name}"
        }
        val idUsuario = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        return ContactosEmergenciaViewModel(idUsuario = idUsuario) as T
    }
}
