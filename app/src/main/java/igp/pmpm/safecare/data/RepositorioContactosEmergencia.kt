package igp.pmpm.safecare.data

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import igp.pmpm.safecare.model.ConfiguracionLlamadaEmergencia
import igp.pmpm.safecare.model.ContactoEmergencia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RepositorioContactosEmergencia(
    private val baseDatos: FirebaseDatabase = FirebaseDatabase.getInstance()
) {

    fun observarContactos(idUsuario: String): Flow<List<ContactoEmergencia>> = callbackFlow {
        val referencia = referenciaContactos(idUsuario)
        val listener = object : ValueEventListener {
            override fun onDataChange(instantanea: DataSnapshot) {
                val contactos = instantanea.children
                    .mapNotNull { hijo -> mapearInstantaneaAContacto(hijo) }
                    .sortedBy { it.nombre.lowercase() }
                trySend(contactos)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observarContactos cancelado: ${error.message}")
                trySend(emptyList())
            }
        }
        referencia.addValueEventListener(listener)
        awaitClose { referencia.removeEventListener(listener) }
    }

    suspend fun agregarContacto(
        idUsuario: String,
        nombre: String,
        telefono: String
    ) = withContext(Dispatchers.IO) {
        val referenciaNueva = referenciaContactos(idUsuario).push()
        val idContacto = referenciaNueva.key.orEmpty()
        val contacto = ContactoEmergencia(
            id = idContacto,
            nombre = nombre.trim(),
            telefono = telefono.trim()
        )
        esperarTareaVacia(referenciaNueva.setValue(contacto.aMapaFirebase()))
    }

    suspend fun guardarContactoPerfil(
        idUsuario: String,
        nombre: String,
        telefono: String
    ) = withContext(Dispatchers.IO) {
        val contacto = ContactoEmergencia(
            id = ContactoEmergencia.ID_CONTACTO_PERFIL,
            nombre = nombre.trim(),
            telefono = telefono.trim()
        )
        esperarTareaVacia(
            referenciaContactos(idUsuario)
                .child(ContactoEmergencia.ID_CONTACTO_PERFIL)
                .setValue(contacto.aMapaFirebase())
        )
    }

    suspend fun eliminarContacto(idUsuario: String, idContacto: String) =
        withContext(Dispatchers.IO) {
            if (idContacto.isBlank()) return@withContext
            esperarTareaVacia(
                referenciaContactos(idUsuario).child(idContacto).removeValue()
            )
        }

    fun observarConfiguracionLlamada(idUsuario: String): Flow<ConfiguracionLlamadaEmergencia> =
        callbackFlow {
            val referencia = referenciaConfiguracionLlamada(idUsuario)
            val listener = object : ValueEventListener {
                override fun onDataChange(instantanea: DataSnapshot) {
                    val configuracion = instantanea.getValue(ConfiguracionLlamadaEmergencia::class.java)
                        ?: ConfiguracionLlamadaEmergencia()
                    trySend(configuracion)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "observarConfiguracionLlamada cancelado: ${error.message}")
                    trySend(ConfiguracionLlamadaEmergencia())
                }
            }
            referencia.addValueEventListener(listener)
            awaitClose { referencia.removeEventListener(listener) }
        }

    suspend fun guardarConfiguracionLlamada(
        idUsuario: String,
        configuracion: ConfiguracionLlamadaEmergencia
    ) = withContext(Dispatchers.IO) {
        esperarTareaVacia(
            referenciaConfiguracionLlamada(idUsuario).setValue(configuracion.aMapaFirebase())
        )
    }

    private fun referenciaContactos(idUsuario: String) =
        baseDatos.reference.child(RutasFirebase.CONTACTOS).child(idUsuario)

    private fun referenciaConfiguracionLlamada(idUsuario: String) =
        baseDatos.reference.child(RutasFirebase.AJUSTES_LLAMADA).child(idUsuario)

    private fun mapearInstantaneaAContacto(hijo: DataSnapshot): ContactoEmergencia? {
        val contacto = hijo.getValue(ContactoEmergencia::class.java) ?: return null
        return contacto.copy(id = hijo.key ?: contacto.id)
    }

    private fun <T> esperarTarea(tarea: Task<T>): T {
        return try {
            Tasks.await(tarea, TIEMPO_ESPERA_SEG, TimeUnit.SECONDS)
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: TimeoutException) {
            throw IllegalStateException(
                "La operación con Firebase ha excedido el tiempo de espera.",
                error
            )
        }
    }

    private fun esperarTareaVacia(tarea: Task<Void>) {
        esperarTarea(tarea)
    }

    private companion object {
        const val TIEMPO_ESPERA_SEG = 30L
        const val TAG = "RepositorioContactos"
    }
}
