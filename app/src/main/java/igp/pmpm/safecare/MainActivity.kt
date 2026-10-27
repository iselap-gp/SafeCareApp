package igp.pmpm.safecare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.firebase.auth.FirebaseAuth
import igp.pmpm.safecare.databinding.ActivityMainBinding
import igp.pmpm.safecare.ui.AjustesFragment
import igp.pmpm.safecare.ui.CamarasFragment
import igp.pmpm.safecare.ui.HistorialFragment
import igp.pmpm.safecare.ui.LoginActivity
import igp.pmpm.safecare.ui.PanelFragment

class MainActivity : AppCompatActivity() {

    private lateinit var vinculacion: ActivityMainBinding

    private val solicitudPermisoNotificaciones = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* El usuario puede denegarlo; las alertas solo funcionarán si lo concede. */ }

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        solicitarPermisoNotificacionesSiFalta()
        enableEdgeToEdge()
        vinculacion = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vinculacion.root)
        ViewCompat.setOnApplyWindowInsetsListener(vinculacion.contenedorPanelControl) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, 0)
            insets
        }
        if (estado == null) {
            reemplazarFragmento(PanelFragment())
        }
        sincronizarPestañaInferiorConFragmento()
        vinculacion.barraNavegacionInferior.setOnItemSelectedListener { elemento ->
            reemplazarFragmentoParaPestana(elemento.itemId)
            true
        }
    }

        fun navegarAPestana(idElementoMenu: Int) {
        when (idElementoMenu) {
            R.id.navegacion_panel,
            R.id.navegacion_camaras,
            R.id.navegacion_historial,
            R.id.navegacion_ajustes -> {
                if (vinculacion.barraNavegacionInferior.selectedItemId != idElementoMenu) {
                    vinculacion.barraNavegacionInferior.selectedItemId = idElementoMenu
                } else {
                    reemplazarFragmentoParaPestana(idElementoMenu)
                }
            }
        }
    }

    private fun reemplazarFragmentoParaPestana(idElementoMenu: Int) {
        when (idElementoMenu) {
            R.id.navegacion_panel -> reemplazarFragmento(PanelFragment())
            R.id.navegacion_camaras -> reemplazarFragmento(CamarasFragment())
            R.id.navegacion_historial -> reemplazarFragmento(HistorialFragment())
            R.id.navegacion_ajustes -> reemplazarFragmento(AjustesFragment())
        }
    }

    private fun sincronizarPestañaInferiorConFragmento() {
        vinculacion.barraNavegacionInferior.selectedItemId = when (
            supportFragmentManager.findFragmentById(R.id.contenedorFragmentos)
        ) {
            is AjustesFragment -> R.id.navegacion_ajustes
            is CamarasFragment -> R.id.navegacion_camaras
            is HistorialFragment -> R.id.navegacion_historial
            else -> R.id.navegacion_panel
        }
    }

        private fun reemplazarFragmento(fragmento: Fragment) {
        supportFragmentManager.commit {
            replace(R.id.contenedorFragmentos, fragmento)
        }
    }

    private fun solicitarPermisoNotificacionesSiFalta() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        solicitudPermisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
