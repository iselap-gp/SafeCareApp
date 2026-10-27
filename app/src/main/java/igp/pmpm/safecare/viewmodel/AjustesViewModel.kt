package igp.pmpm.safecare.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import igp.pmpm.safecare.data.RepositorioSistemaSeguridad
import igp.pmpm.safecare.model.EstadoSistemaSeguridad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AjustesUiState(
    val cargando: Boolean = true,
    val modoMayoresActivo: Boolean = false,
    val modoMascotasActivo: Boolean = false,
    val modoNinosActivo: Boolean = false,
    val escribiendo: Boolean = false,
    val error: String? = null
)

class AjustesViewModel(
    private val idUsuario: String,
    private val repositorioSistema: RepositorioSistemaSeguridad = RepositorioSistemaSeguridad()
) : ViewModel() {

    private val _estadoPantalla = MutableStateFlow(AjustesUiState())
    val estadoPantalla: StateFlow<AjustesUiState> = _estadoPantalla.asStateFlow()

    init {
        if (idUsuario.isBlank()) {
            _estadoPantalla.update {
                it.copy(
                    cargando = false,
                    error = "Sesión no válida. Vuelve a iniciar sesión."
                )
            }
        } else {
            observarModosProteccionFamiliar()
        }
    }

    private fun observarModosProteccionFamiliar() {
        viewModelScope.launch {
            repositorioSistema.observarSistema(idUsuario)
                .catch { error ->
                    _estadoPantalla.update {
                        it.copy(
                            cargando = false,
                            error = error.message ?: "No se pudo cargar la configuración."
                        )
                    }
                }
                .collect { sistema -> actualizarDesdeSistema(sistema) }
        }
    }

    fun cambiarModoMayores(activo: Boolean) {
        if (idUsuario.isBlank() || _estadoPantalla.value.escribiendo) return
        if (_estadoPantalla.value.modoMayoresActivo == activo) return
        ejecutarEscritura { repositorioSistema.actualizarModoMayores(idUsuario, activo) }
    }

    fun cambiarModoMascotas(activo: Boolean) {
        if (idUsuario.isBlank() || _estadoPantalla.value.escribiendo) return
        if (_estadoPantalla.value.modoMascotasActivo == activo) return
        ejecutarEscritura { repositorioSistema.actualizarModoMascotas(idUsuario, activo) }
    }

    fun cambiarModoNinos(activo: Boolean) {
        if (idUsuario.isBlank() || _estadoPantalla.value.escribiendo) return
        if (_estadoPantalla.value.modoNinosActivo == activo) return
        ejecutarEscritura { repositorioSistema.actualizarModoNinos(idUsuario, activo) }
    }

    fun limpiarError() {
        _estadoPantalla.update { it.copy(error = null) }
    }

    private fun actualizarDesdeSistema(sistema: EstadoSistemaSeguridad) {
        val anterior = _estadoPantalla.value
        _estadoPantalla.value = AjustesUiState(
            cargando = false,
            modoMayoresActivo = sistema.modoMayoresActivo,
            modoMascotasActivo = sistema.modoMascotasActivo,
            modoNinosActivo = sistema.modoNinosActivo,
            escribiendo = anterior.escribiendo,
            error = anterior.error
        )
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

class AjustesViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AjustesViewModel::class.java)) {
            "ViewModel desconocido: ${modelClass.name}"
        }
        val idUsuario = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        return AjustesViewModel(idUsuario) as T
    }
}
