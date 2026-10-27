package igp.pmpm.safecare.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import igp.pmpm.safecare.R
import igp.pmpm.safecare.databinding.ActivityContactosEmergenciaBinding
import igp.pmpm.safecare.databinding.DialogoAgregarContactoBinding
import igp.pmpm.safecare.databinding.ItemContactoConfianzaBinding
import igp.pmpm.safecare.model.ConfiguracionLlamadaEmergencia
import igp.pmpm.safecare.model.ContactoEmergencia
import igp.pmpm.safecare.viewmodel.ContactosEmergenciaUiState
import igp.pmpm.safecare.viewmodel.ContactosEmergenciaViewModel
import igp.pmpm.safecare.viewmodel.ContactosEmergenciaViewModelFactory
import kotlinx.coroutines.launch

class ContactosEmergenciaActivity : AppCompatActivity() {

    private lateinit var vinculacion: ActivityContactosEmergenciaBinding

    private val viewModel: ContactosEmergenciaViewModel by viewModels {
        ContactosEmergenciaViewModelFactory()
    }

    private val adaptador = AdaptadorContactosEmergencia(
        alPulsarSeleccionar = { idContacto ->
            viewModel.seleccionarDestinoLlamada(usar112 = false, idContacto = idContacto)
        },
        alPulsarLlamar = { telefono -> abrirMarcadorTelefono(telefono) },
        alPulsarEliminar = { id -> viewModel.eliminarContacto(id) }
    )

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        enableEdgeToEdge()
        vinculacion = ActivityContactosEmergenciaBinding.inflate(layoutInflater)
        setContentView(vinculacion.root)
        ViewCompat.setOnApplyWindowInsetsListener(vinculacion.contenedorPrincipalContactosEmergencia) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
        vinculacion.barraHerramientasContactosEmergencia.setNavigationOnClickListener { finish() }

        vinculacion.listaContactosConfianza.layoutManager = LinearLayoutManager(this)
        vinculacion.listaContactosConfianza.isNestedScrollingEnabled = false
        vinculacion.listaContactosConfianza.setHasFixedSize(false)
        vinculacion.listaContactosConfianza.adapter = adaptador

        configurarSelectorDestino()
        vinculacion.btnAnadirContacto.setOnClickListener { mostrarDialogoAgregarContacto() }
        observarEstadoContactos()
    }

    private fun configurarSelectorDestino() {
        vinculacion.tarjetaDestino112.setOnClickListener {
            viewModel.seleccionarDestinoLlamada(usar112 = true)
        }
        vinculacion.tarjetaDestinoContacto.setOnClickListener {
            viewModel.seleccionarDestinoLlamada(usar112 = false)
        }
    }

    private fun observarEstadoContactos() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estadoPantalla.collect { estado ->
                    actualizarSelectorDestino(estado.configuracionLlamada)
                    actualizarListaContactos(estado)
                    vinculacion.btnAnadirContacto.isEnabled =
                        !estado.configuracionLlamada.llamarA112 && !estado.escribiendo
                    vinculacion.btnAnadirContacto.isVisible =
                        !estado.configuracionLlamada.llamarA112
                    estado.error?.let { mensaje ->
                        Toast.makeText(this@ContactosEmergenciaActivity, mensaje, Toast.LENGTH_LONG)
                            .show()
                        viewModel.limpiarError()
                    }
                }
            }
        }
    }

    private fun actualizarSelectorDestino(configuracion: ConfiguracionLlamadaEmergencia) {
        val usar112 = configuracion.llamarA112
        vinculacion.radioDestino112.isChecked = usar112
        vinculacion.radioDestinoContacto.isChecked = !usar112
        marcarTarjetaSeleccionada(vinculacion.tarjetaDestino112, seleccionada = usar112)
        marcarTarjetaSeleccionada(vinculacion.tarjetaDestinoContacto, seleccionada = !usar112)
        vinculacion.contenedorListaContactos.alpha = if (usar112) 0.45f else 1f
        vinculacion.contenedorListaContactos.isEnabled = !usar112
    }

    private fun marcarTarjetaSeleccionada(tarjeta: MaterialCardView, seleccionada: Boolean) {
        val colorBorde = ContextCompat.getColor(
            this,
            if (seleccionada) R.color.safecare_primary else R.color.safecare_divider
        )
        tarjeta.strokeColor = colorBorde
        tarjeta.strokeWidth = if (seleccionada) {
            resources.getDimensionPixelSize(R.dimen.contacto_destino_borde_seleccion)
        } else {
            resources.getDimensionPixelSize(R.dimen.contacto_destino_borde_normal)
        }
    }

    private fun actualizarListaContactos(estado: ContactosEmergenciaUiState) {
        val modoContacto = !estado.configuracionLlamada.llamarA112
        val idSeleccionado = estado.configuracionLlamada.idContactoSeleccionado
        val filas = estado.listaContactos.map { contacto ->
            ContactoEmergenciaFila(
                contacto = contacto,
                esDestinoSeleccionado = modoContacto && contacto.id == idSeleccionado,
                modoSeleccionContacto = modoContacto
            )
        }
        adaptador.submitList(filas)

        val hayContactos = estado.listaContactos.isNotEmpty()
        vinculacion.listaContactosConfianza.isVisible = hayContactos
        vinculacion.tvEstadoVacioContactos.isVisible =
            modoContacto && !estado.cargando && !hayContactos
        vinculacion.tvAvisoSinDestinoContacto.isVisible =
            modoContacto && hayContactos && idSeleccionado.isBlank()
    }

    private fun mostrarDialogoAgregarContacto() {
        val enlaceDialogo = DialogoAgregarContactoBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.contacto_dialogo_titulo)
            .setView(enlaceDialogo.root)
            .setNegativeButton(R.string.contacto_dialogo_cancelar) { dialogo, _ ->
                dialogo.dismiss()
            }
            .setPositiveButton(R.string.contacto_dialogo_guardar, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val nombre = enlaceDialogo.campoNombreContacto.text?.toString().orEmpty()
                        val telefono = enlaceDialogo.campoTelefonoContacto.text?.toString().orEmpty()
                        if (nombre.isBlank() || telefono.isBlank()) {
                            Toast.makeText(
                                this@ContactosEmergenciaActivity,
                                R.string.contacto_campos_obligatorios,
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }
                        viewModel.agregarContacto(nombre, telefono)
                        dismiss()
                    }
                }
                show()
            }
    }

    private fun abrirMarcadorTelefono(telefono: String) {
        val digitos = telefono.filter { it.isDigit() || it == '+' }
        if (!esTelefonoMarcable(digitos)) {
            Toast.makeText(
                this,
                getString(R.string.panel_panico_contacto_invalido, telefono),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        Toast.makeText(
            this,
            getString(R.string.contacto_toast_llamando, telefono),
            Toast.LENGTH_SHORT
        ).show()
        startActivity(Intent(Intent.ACTION_DIAL, "tel:$digitos".toUri()))
    }

    private fun esTelefonoMarcable(numero: String): Boolean {
        val soloDigitos = numero.filter { it.isDigit() }
        return soloDigitos in TELEFONOS_EMERGENCIA || soloDigitos.length >= 6
    }

    private companion object {
        val TELEFONOS_EMERGENCIA = setOf("112", "911")
    }
}

private data class ContactoEmergenciaFila(
    val contacto: ContactoEmergencia,
    val esDestinoSeleccionado: Boolean,
    val modoSeleccionContacto: Boolean
)

private class AdaptadorContactosEmergencia(
    private val alPulsarSeleccionar: (String) -> Unit,
    private val alPulsarLlamar: (String) -> Unit,
    private val alPulsarEliminar: (String) -> Unit
) : ListAdapter<ContactoEmergenciaFila, SoporteVistaContactoEmergencia>(DIF_CONTACTO_EMERGENCIA) {

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int): SoporteVistaContactoEmergencia {
        val inflador = LayoutInflater.from(padre.context)
        return SoporteVistaContactoEmergencia(
            ItemContactoConfianzaBinding.inflate(inflador, padre, false),
            alPulsarSeleccionar,
            alPulsarLlamar,
            alPulsarEliminar
        )
    }

    override fun onBindViewHolder(soporte: SoporteVistaContactoEmergencia, posicion: Int) {
        soporte.atar(getItem(posicion))
    }
}

private class SoporteVistaContactoEmergencia(
    private val v: ItemContactoConfianzaBinding,
    private val alPulsarSeleccionar: (String) -> Unit,
    private val alPulsarLlamar: (String) -> Unit,
    private val alPulsarEliminar: (String) -> Unit
) : RecyclerView.ViewHolder(v.root) {

    fun atar(fila: ContactoEmergenciaFila) {
        val contacto = fila.contacto
        v.tvNombreContacto.text = contacto.nombre
        v.tvParentescoContacto.text = contacto.telefono
        v.tvInicialAvatar.text = contacto.inicialAvatar

        v.radioDestinoEmergencia.isEnabled = fila.modoSeleccionContacto
        v.radioDestinoEmergencia.isChecked = fila.esDestinoSeleccionado
        v.radioDestinoEmergencia.setOnClickListener {
            if (fila.modoSeleccionContacto) {
                alPulsarSeleccionar(contacto.id)
            }
        }

        val tarjeta = v.root as MaterialCardView
        val colorBorde = ContextCompat.getColor(
            v.root.context,
            if (fila.esDestinoSeleccionado) R.color.safecare_exito else R.color.safecare_divider
        )
        tarjeta.strokeColor = colorBorde
        tarjeta.strokeWidth = if (fila.esDestinoSeleccionado) 2 else 1

        v.root.setOnClickListener {
            if (fila.modoSeleccionContacto) {
                alPulsarSeleccionar(contacto.id)
            }
        }

        v.btnLlamadaRapida.isEnabled = fila.modoSeleccionContacto
        v.btnLlamadaRapida.alpha = if (fila.modoSeleccionContacto) 1f else 0.5f
        v.btnLlamadaRapida.setOnClickListener { alPulsarLlamar(contacto.telefono) }

        v.btnEliminarContacto.isVisible = true
        v.btnEliminarContacto.isEnabled = fila.modoSeleccionContacto
        v.btnEliminarContacto.alpha = if (fila.modoSeleccionContacto) 1f else 0.5f
        v.btnEliminarContacto.setOnClickListener { alPulsarEliminar(contacto.id) }
    }
}

private val DIF_CONTACTO_EMERGENCIA = object : DiffUtil.ItemCallback<ContactoEmergenciaFila>() {
    override fun areItemsTheSame(anterior: ContactoEmergenciaFila, nuevo: ContactoEmergenciaFila) =
        anterior.contacto.id == nuevo.contacto.id && anterior.contacto.id.isNotEmpty()

    override fun areContentsTheSame(anterior: ContactoEmergenciaFila, nuevo: ContactoEmergenciaFila) =
        anterior == nuevo
}
