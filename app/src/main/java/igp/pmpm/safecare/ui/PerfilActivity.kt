package igp.pmpm.safecare.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import igp.pmpm.safecare.R
import igp.pmpm.safecare.data.RepositorioAutenticacion
import igp.pmpm.safecare.data.RepositorioContactosEmergencia
import igp.pmpm.safecare.databinding.ActivityPerfilBinding
import igp.pmpm.safecare.databinding.DialogoAgregarContactoBinding
import igp.pmpm.safecare.model.ConfiguracionLlamadaEmergencia
import igp.pmpm.safecare.model.ContactoEmergencia
import igp.pmpm.safecare.model.Usuario
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PerfilActivity : AppCompatActivity() {

    private lateinit var vinculacion: ActivityPerfilBinding
    private val autenticacion: FirebaseAuth = FirebaseAuth.getInstance()
    private val repositorioAutenticacion = RepositorioAutenticacion()
    private val repositorioContactos = RepositorioContactosEmergencia()
    private var configuracionLlamadaActual = ConfiguracionLlamadaEmergencia()
    private var contactosDisponibles: List<ContactoEmergencia> = emptyList()
    private var perfilActual: Usuario? = null

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        enableEdgeToEdge()
        vinculacion = ActivityPerfilBinding.inflate(layoutInflater)
        setContentView(vinculacion.root)
        ViewCompat.setOnApplyWindowInsetsListener(vinculacion.contenedorPrincipalPerfil) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
        vinculacion.barraHerramientasPerfil.setNavigationOnClickListener { finish() }
        vinculacion.btnGuardarCambios.setOnClickListener { guardarCambios() }
        vinculacion.btnCambiarContrasena.setOnClickListener {
            startActivity(Intent(this, CambiarContrasenaActivity::class.java))
        }
        configurarCampoDestinoEmergencia()
        observarDatosPerfilYDestino()
    }

    private fun uidActual(): String? = autenticacion.currentUser?.uid

    private fun correoAutenticacion(): String = autenticacion.currentUser?.email.orEmpty()

    private fun configurarCampoDestinoEmergencia() {
        vinculacion.etContactoEmergencia.isFocusable = false
        vinculacion.etContactoEmergencia.isFocusableInTouchMode = false
        vinculacion.etContactoEmergencia.isClickable = true
        vinculacion.etContactoEmergencia.keyListener = null
        val abrirSelector = { mostrarDialogoSelectorDestino() }
        vinculacion.tilContactoEmergencia.setEndIconDrawable(R.drawable.ic_navegacion_atras)
        vinculacion.tilContactoEmergencia.setEndIconOnClickListener { abrirSelector() }
        vinculacion.etContactoEmergencia.setOnClickListener { abrirSelector() }
        vinculacion.tilContactoEmergencia.setOnClickListener { abrirSelector() }
    }

    private fun observarDatosPerfilYDestino() {
        val idUsuario = uidActual()
        if (idUsuario == null) {
            Toast.makeText(this, R.string.perfil_toast_sesion_invalida, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        vinculacion.indicadorCargaPerfil.visibility = View.VISIBLE
        vinculacion.tilNombreUsuario.isEnabled = false
        vinculacion.tilContactoEmergencia.isEnabled = false
        vinculacion.btnGuardarCambios.isEnabled = false
        vinculacion.btnCambiarContrasena.isEnabled = false
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repositorioAutenticacion.observarPerfil(idUsuario),
                    repositorioContactos.observarConfiguracionLlamada(idUsuario),
                    repositorioContactos.observarContactos(idUsuario)
                ) { perfil, configuracion, contactos ->
                    Triple(perfil, configuracion, contactos)
                }.collect { (perfil, configuracion, contactos) ->
                    perfilActual = perfil
                    configuracionLlamadaActual = configuracion
                    contactosDisponibles = contactos
                    pintarDatosEnPantalla(perfil, configuracionLlamadaActual, contactosDisponibles)
                }
            }
        }
    }

    private fun pintarDatosEnPantalla(
        perfilNube: Usuario?,
        configuracion: ConfiguracionLlamadaEmergencia,
        contactos: List<ContactoEmergencia>
    ) {
        val correoAuth = correoAutenticacion()
        if (vinculacion.etNombreUsuario.text.isNullOrBlank()) {
            vinculacion.etNombreUsuario.setText(
                perfilNube?.nombre.orEmpty().ifEmpty { autenticacion.currentUser?.displayName.orEmpty() }
            )
        }
        vinculacion.etCorreoUsuario.setText(perfilNube?.correoElectronico.orEmpty().ifEmpty { correoAuth })
        vinculacion.etContactoEmergencia.setText(textoDestino(configuracion, contactos))

        vinculacion.indicadorCargaPerfil.isVisible = false
        vinculacion.tilNombreUsuario.isEnabled = true
        vinculacion.tilContactoEmergencia.isEnabled = true
        vinculacion.btnGuardarCambios.isEnabled = true
        vinculacion.btnCambiarContrasena.isEnabled = true
    }

    private fun textoDestino(
        configuracion: ConfiguracionLlamadaEmergencia,
        contactos: List<ContactoEmergencia>
    ): String {
        if (configuracion.llamarA112) return getString(R.string.contacto_destino_112)
        val seleccionado = contactos.firstOrNull { it.id == configuracion.idContactoSeleccionado }
        return if (seleccionado != null) {
            "${seleccionado.nombre} · ${seleccionado.telefono}"
        } else {
            getString(R.string.perfil_destino_sin_configurar)
        }
    }

    private fun mostrarDialogoSelectorDestino() {
        val idUsuario = uidActual() ?: return
        val opciones = buildList {
            add(getString(R.string.contacto_destino_112))
            contactosDisponibles.forEach { contacto ->
                add("${contacto.nombre} · ${contacto.telefono}")
            }
        }
        val indiceActual = when {
            configuracionLlamadaActual.llamarA112 -> 0
            else -> contactosDisponibles.indexOfFirst { it.id == configuracionLlamadaActual.idContactoSeleccionado }
                .takeIf { it >= 0 }?.plus(1) ?: 0
        }
        var indiceSeleccionado = indiceActual
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.perfil_dialogo_destino_titulo)
            .setSingleChoiceItems(opciones.toTypedArray(), indiceActual) { _, which ->
                indiceSeleccionado = which
            }
            .setNeutralButton(R.string.perfil_dialogo_destino_anadir) { _, _ ->
                mostrarDialogoAgregarContactoDesdePerfil()
            }
            .setPositiveButton(R.string.contacto_dialogo_guardar) { _, _ ->
                lifecycleScope.launch {
                    if (indiceSeleccionado == 0) {
                        repositorioContactos.guardarConfiguracionLlamada(
                            idUsuario,
                            ConfiguracionLlamadaEmergencia(llamarA112 = true, idContactoSeleccionado = "")
                        )
                    } else {
                        val contacto = contactosDisponibles[indiceSeleccionado - 1]
                        repositorioContactos.guardarConfiguracionLlamada(
                            idUsuario,
                            ConfiguracionLlamadaEmergencia(
                                llamarA112 = false,
                                idContactoSeleccionado = contacto.id
                            )
                        )
                    }
                }
            }
            .show()
    }

    private fun mostrarDialogoAgregarContactoDesdePerfil() {
        val idUsuario = uidActual() ?: return
        val enlaceDialogo = DialogoAgregarContactoBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.contacto_dialogo_titulo)
            .setView(enlaceDialogo.root)
            .setNegativeButton(R.string.contacto_dialogo_cancelar, null)
            .setPositiveButton(R.string.contacto_dialogo_guardar, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val nombre = enlaceDialogo.campoNombreContacto.text?.toString().orEmpty().trim()
                        val telefono = enlaceDialogo.campoTelefonoContacto.text?.toString().orEmpty().trim()
                        if (nombre.isBlank() || telefono.isBlank()) {
                            Toast.makeText(
                                this@PerfilActivity,
                                R.string.contacto_campos_obligatorios,
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }
                        lifecycleScope.launch {
                            runCatching {
                                repositorioContactos.guardarContactoPerfil(idUsuario, nombre, telefono)
                                repositorioContactos.guardarConfiguracionLlamada(
                                    idUsuario,
                                    ConfiguracionLlamadaEmergencia(
                                        llamarA112 = false,
                                        idContactoSeleccionado = ContactoEmergencia.ID_CONTACTO_PERFIL
                                    )
                                )
                            }.onFailure {
                                Toast.makeText(
                                    this@PerfilActivity,
                                    R.string.perfil_toast_error_guardar,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        dismiss()
                    }
                }
                show()
            }
    }

    private fun guardarCambios() {
        val idUsuario = uidActual() ?: return
        val correo = correoAutenticacion()
        if (correo.isEmpty()) {
            Toast.makeText(this, R.string.perfil_toast_sesion_invalida, Toast.LENGTH_LONG).show()
            return
        }
        val nombre = vinculacion.etNombreUsuario.text?.toString().orEmpty().trim()
        val usuario = Usuario(
            idUsuario = idUsuario,
            nombre = nombre,
            correoElectronico = vinculacion.etCorreoUsuario.text?.toString().orEmpty().trim()
                .ifEmpty { correo }
        )
        vinculacion.btnGuardarCambios.isEnabled = false
        vinculacion.indicadorCargaPerfil.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                repositorioAutenticacion.guardarPerfilEnBaseDeDatos(usuario)
                Toast.makeText(
                    this@PerfilActivity,
                    R.string.perfil_toast_guardado_ok,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {
                Toast.makeText(
                    this@PerfilActivity,
                    R.string.perfil_toast_error_guardar,
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                vinculacion.indicadorCargaPerfil.visibility = View.GONE
                vinculacion.btnGuardarCambios.isEnabled = true
            }
        }
    }

}
