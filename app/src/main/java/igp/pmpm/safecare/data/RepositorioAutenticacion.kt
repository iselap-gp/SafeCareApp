package igp.pmpm.safecare.data

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import igp.pmpm.safecare.model.Usuario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RepositorioAutenticacion(
    private val autenticacionFirebase: FirebaseAuth = FirebaseAuth.getInstance(),
    private val baseDatos: FirebaseDatabase = FirebaseDatabase.getInstance()
) {

    private val referenciaUsuarios: DatabaseReference
        get() = baseDatos.reference.child(RutasFirebase.USUARIOS)

        suspend fun crearCuentaConCorreoYContrasena(
        correoElectronico: String,
        contrasena: String
    ): AuthResult = withContext(Dispatchers.Default) {
        val tarea: Task<AuthResult> =
            autenticacionFirebase.createUserWithEmailAndPassword(
                correoElectronico,
                contrasena
            )
        esperarTareaOExcepcion(tarea, TIEMPO_ESPERA_AUTENTICACION_SEG)
    }

        suspend fun guardarPerfilEnBaseDeDatos(usuario: Usuario) = withContext(Dispatchers.Default) {
        val camposPerfil = mapOf(
            "uid" to usuario.idUsuario,
            "nombre" to usuario.nombre,
            "email" to usuario.correoElectronico
        )
        val tarea: Task<Void> =
            referenciaUsuarios.child(usuario.idUsuario).updateChildren(camposPerfil)
        esperarTareaVaciaOExcepcion(tarea, TIEMPO_ESPERA_BASE_DATOS_SEG)
    }

        fun observarPerfil(idUsuario: String): Flow<Usuario?> = callbackFlow {
        val referencia = referenciaUsuarios.child(idUsuario)
        val listener = object : ValueEventListener {
            override fun onDataChange(instantanea: DataSnapshot) {
                val perfil = if (instantanea.exists()) {
                    instantanea.getValue(Usuario::class.java)
                } else {
                    null
                }
                trySend(perfil)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observarPerfil cancelado: ${error.message}")
                trySend(null)
            }
        }
        referencia.addValueEventListener(listener)
        awaitClose { referencia.removeEventListener(listener) }
    }

        suspend fun cargarPerfilDesdeNube(idUsuario: String): Usuario? = withContext(Dispatchers.Default) {
        val tarea: Task<DataSnapshot> = referenciaUsuarios.child(idUsuario).get()
        val instantanea = esperarTareaOExcepcion(tarea, TIEMPO_ESPERA_BASE_DATOS_SEG)
        if (!instantanea.exists()) return@withContext null
        instantanea.getValue(Usuario::class.java)
    }

    suspend fun actualizarContrasena(
        contrasenaActual: String,
        contrasenaNueva: String
    ) = withContext(Dispatchers.Default) {
        val usuario = autenticacionFirebase.currentUser
            ?: throw IllegalStateException("Sesión no válida. Vuelve a iniciar sesión.")
        val correo = usuario.email
            ?: throw IllegalStateException("No se pudo obtener el correo de la cuenta.")
        val credencial = com.google.firebase.auth.EmailAuthProvider.getCredential(
            correo,
            contrasenaActual
        )
        esperarTareaOExcepcion(
            usuario.reauthenticate(credencial),
            TIEMPO_ESPERA_AUTENTICACION_SEG
        )
        esperarTareaVaciaOExcepcion(
            usuario.updatePassword(contrasenaNueva),
            TIEMPO_ESPERA_AUTENTICACION_SEG
        )
    }

    suspend fun iniciarSesionConCorreoYContrasena(
        correoElectronico: String,
        contrasena: String
    ): AuthResult = withContext(Dispatchers.Default) {
        val tarea: Task<AuthResult> =
            autenticacionFirebase.signInWithEmailAndPassword(
                correoElectronico,
                contrasena
            )
        esperarTareaOExcepcion(tarea, TIEMPO_ESPERA_AUTENTICACION_SEG)
    }

        private fun <T> esperarTareaOExcepcion(
        tarea: Task<T>,
        segundosTimeout: Long
    ): T {
        return try {
            Tasks.await(tarea, segundosTimeout, TimeUnit.SECONDS)
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: TimeoutException) {
            throw IllegalStateException(
                "La operación de red con Firebase ha excedido el tiempo de espera. Inténtalo de nuevo.",
                error
            )
        }
    }

        private fun esperarTareaVaciaOExcepcion(
        tarea: Task<Void>,
        segundosTimeout: Long
    ) {
        try {
            Tasks.await(tarea, segundosTimeout, TimeUnit.SECONDS)
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: TimeoutException) {
            throw IllegalStateException(
                "No se pudo completar el guardado en la base de datos. Inténtalo de nuevo.",
                error
            )
        }
    }

    private companion object {
        const val TIEMPO_ESPERA_AUTENTICACION_SEG = 60L
        const val TIEMPO_ESPERA_BASE_DATOS_SEG = 60L
        const val TAG = "RepositorioAuth"
    }
}
