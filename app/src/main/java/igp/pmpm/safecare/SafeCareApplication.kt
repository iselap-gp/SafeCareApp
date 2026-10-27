package igp.pmpm.safecare

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import igp.pmpm.safecare.notificaciones.EscuchaAlertasFirebase
import igp.pmpm.safecare.notificaciones.GestorNotificacionesAlertas

class SafeCareApplication : Application() {

    private lateinit var escuchaAlertas: EscuchaAlertasFirebase

    private val listenerAuth = FirebaseAuth.AuthStateListener { auth ->
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            escuchaAlertas.detener()
        } else {
            escuchaAlertas.iniciar(uid)
        }
    }

    override fun onCreate() {
        super.onCreate()
        activarPersistenciaFirebase()
        GestorNotificacionesAlertas.crearCanales(this)
        escuchaAlertas = EscuchaAlertasFirebase(this)
        FirebaseAuth.getInstance().addAuthStateListener(listenerAuth)
    }

    private fun activarPersistenciaFirebase() {
        runCatching {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        }.onFailure { error ->
            Log.w(TAG, "Persistencia Firebase: ${error.message}")
        }
    }

    private companion object {
        const val TAG = "SafeCareApplication"
    }
}
