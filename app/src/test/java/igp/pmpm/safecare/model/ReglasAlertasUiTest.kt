package igp.pmpm.safecare.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasAlertasUiTest {

    @Test
    fun puertaAbierta_enModoCasa_sinNinos_debeDispararAlerta() {
        val resultado = ReglasAlertasUi.evaluarAlertaPuerta(
            alarmaArmada = true,
            esModoFuera = false,
            puertaAbierta = true,
            modoNinosActivo = false
        )
        assertTrue(resultado)
    }

    @Test
    fun puertaAbierta_enModoFuera_debeDispararAlerta() {
        val resultado = ReglasAlertasUi.evaluarAlertaPuerta(
            alarmaArmada = true,
            esModoFuera = true,
            puertaAbierta = true,
            modoNinosActivo = false
        )
        assertTrue(resultado)
    }

    @Test
    fun puertaAbierta_enModoCasa_conNinos_debeDispararAlerta() {
        val resultado = ReglasAlertasUi.evaluarAlertaPuerta(
            alarmaArmada = true,
            esModoFuera = false,
            puertaAbierta = true,
            modoNinosActivo = true
        )
        assertTrue(resultado)
    }

    @Test
    fun sensorMovimiento_enModoFuera_sinMascotas_debeDispararAlerta() {
        val resultado = ReglasAlertasUi.evaluarAlertaMovimiento(
            alarmaArmada = true,
            esModoFuera = true,
            movimientoDetectado = true,
            modoMascotasActivo = false
        )
        assertTrue(resultado)
    }

    @Test
    fun sensorMovimiento_enModoFuera_conMascotas_noDebeDispararAlerta() {
        val resultado = ReglasAlertasUi.evaluarAlertaMovimiento(
            alarmaArmada = true,
            esModoFuera = true,
            movimientoDetectado = true,
            modoMascotasActivo = true
        )
        assertFalse(resultado)
    }

    @Test
    fun inactividad_conModoMayores_debeDispararAlerta() {
        val resultado = ReglasAlertasUi.alertaInactividadMayores(
            alarmaArmada = true,
            modoMayoresActivo = true,
            alertaInactividadPorZona = mapOf("salon" to true)
        )
        assertTrue(resultado)
    }
}
