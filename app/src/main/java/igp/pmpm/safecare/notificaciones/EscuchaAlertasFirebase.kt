package igp.pmpm.safecare.notificaciones

import android.content.Context
import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import igp.pmpm.safecare.data.RepositorioSistemaSeguridad
import igp.pmpm.safecare.data.RutasFirebase
import igp.pmpm.safecare.model.EventoSistema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class EscuchaAlertasFirebase(
    private val contexto: Context,
    private val repositorioSistema: RepositorioSistemaSeguridad = RepositorioSistemaSeguridad(),
    private val baseDatos: FirebaseDatabase = FirebaseDatabase.getInstance()
) {

    private var idUsuarioActivo: String? = null
    private var listenerEventos: ChildEventListener? = null
    private var listenerSincronizacion: ValueEventListener? = null

    private val idsEventosConocidos = mutableSetOf<String>()
    private var sincronizacionInicialCompleta = false

    fun iniciar(idUsuario: String) {
        if (idUsuario.isBlank()) return
        if (idUsuario == idUsuarioActivo && listenerEventos != null) return

        detener()
        idUsuarioActivo = idUsuario
        idsEventosConocidos.clear()
        sincronizacionInicialCompleta = false

        val referencia = baseDatos.reference
            .child(RutasFirebase.EVENTOS)
            .child(idUsuario)

        listenerEventos = object : ChildEventListener {
            override fun onChildAdded(instantanea: DataSnapshot, previousChildName: String?) {
                val idEvento = instantanea.key ?: return
                if (!sincronizacionInicialCompleta) {
                    idsEventosConocidos.add(idEvento)
                    return
                }
                if (idEvento in idsEventosConocidos) return
                idsEventosConocidos.add(idEvento)
                procesarNuevoEvento(instantanea)
            }

            override fun onChildChanged(instantanea: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(instantanea: DataSnapshot) = Unit
            override fun onChildMoved(instantanea: DataSnapshot, previousChildName: String?) = Unit

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Escucha de alertas cancelada: ${error.message}")
            }
        }
        referencia.addChildEventListener(listenerEventos!!)

        listenerSincronizacion = object : ValueEventListener {
            override fun onDataChange(instantanea: DataSnapshot) {
                instantanea.children.forEach { hijo ->
                    hijo.key?.let { idsEventosConocidos.add(it) }
                }
                sincronizacionInicialCompleta = true
                listenerSincronizacion?.let { referencia.removeEventListener(it) }
                listenerSincronizacion = null
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Sincronización inicial cancelada: ${error.message}")
                sincronizacionInicialCompleta = true
                listenerSincronizacion?.let { referencia.removeEventListener(it) }
                listenerSincronizacion = null
            }
        }
        referencia.addValueEventListener(listenerSincronizacion!!)
    }

    fun detener() {
        val idUsuario = idUsuarioActivo ?: return
        val referencia = baseDatos.reference
            .child(RutasFirebase.EVENTOS)
            .child(idUsuario)

        listenerEventos?.let { referencia.removeEventListener(it) }
        listenerSincronizacion?.let { referencia.removeEventListener(it) }

        listenerEventos = null
        listenerSincronizacion = null
        idUsuarioActivo = null
        idsEventosConocidos.clear()
        sincronizacionInicialCompleta = false
    }

    private fun procesarNuevoEvento(instantanea: DataSnapshot) {
        val evento = instantanea.getValue(EventoSistema::class.java)?.copy(
            id = instantanea.key.orEmpty()
        ) ?: return

        val idUsuario = idUsuarioActivo ?: return
        val sistema = runBlocking(Dispatchers.IO) {
            runCatching { repositorioSistema.obtenerEstadoSistema(idUsuario) }.getOrNull()
        } ?: return

        if (!GestorNotificacionesAlertas.debeNotificar(evento, sistema)) return

        GestorNotificacionesAlertas.mostrarAlertaEvento(contexto.applicationContext, evento)
    }

    private companion object {
        const val TAG = "EscuchaAlertasFirebase"
    }
}
