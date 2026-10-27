package igp.pmpm.safecare.notificaciones

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import igp.pmpm.safecare.MainActivity
import igp.pmpm.safecare.R
import igp.pmpm.safecare.model.EstadoAlarma
import igp.pmpm.safecare.model.EstadoSistemaSeguridad
import igp.pmpm.safecare.model.EventoSistema

object GestorNotificacionesAlertas {

    const val CANAL_ALERTAS = "safecare_alertas"
    const val CANAL_PANICO = "safecare_panico"
    private const val GRUPO_ALERTAS = "safecare_grupo_alertas"

    fun crearCanales(contexto: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return

        val canalAlertas = NotificationChannel(
            CANAL_ALERTAS,
            contexto.getString(R.string.notificacion_canal_alertas_nombre),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = contexto.getString(R.string.notificacion_canal_alertas_descripcion)
            enableVibration(true)
        }

        val canalPanico = NotificationChannel(
            CANAL_PANICO,
            contexto.getString(R.string.notificacion_canal_panico_nombre),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = contexto.getString(R.string.notificacion_canal_panico_descripcion)
            enableVibration(true)
        }

        gestor.createNotificationChannel(canalAlertas)
        gestor.createNotificationChannel(canalPanico)
    }

    fun mostrarAlertaEvento(contexto: Context, evento: EventoSistema) {
        if (!NotificationManagerCompat.from(contexto).areNotificationsEnabled()) return

        val esPanico = evento.tipo == EventoSistema.TIPO_PANICO
        val titulo = resolverTitulo(contexto, evento)
        val cuerpo = evento.descripcion.ifBlank {
            contexto.getString(R.string.notificacion_cuerpo_generico)
        }
        val idNotificacion = (evento.id.hashCode() and Int.MAX_VALUE).takeIf { it != 0 }
            ?: System.currentTimeMillis().toInt()

        val intent = Intent(contexto, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            contexto,
            idNotificacion,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val constructor = NotificationCompat.Builder(
            contexto,
            if (esPanico) CANAL_PANICO else CANAL_ALERTAS
        )
            .setSmallIcon(R.drawable.ic_notification_safecare)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setPriority(
                if (esPanico) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(
                if (esPanico) NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_STATUS
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(GRUPO_ALERTAS)

        if (esPanico) {
            constructor.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        NotificationManagerCompat.from(contexto).notify(idNotificacion, constructor.build())
    }

    private fun resolverTitulo(contexto: Context, evento: EventoSistema): String {
        return when (evento.tipo) {
            EventoSistema.TIPO_PANICO -> contexto.getString(R.string.notificacion_titulo_panico)
            EventoSistema.TIPO_SENSOR -> resolverTituloSensor(contexto, evento.descripcion)
            else -> contexto.getString(R.string.notificacion_titulo_alerta)
        }
    }

    private fun resolverTituloSensor(contexto: Context, descripcion: String): String {
        val texto = descripcion.lowercase()
        return when {
            texto.contains("inactividad") ->
                contexto.getString(R.string.notificacion_titulo_inactividad)
            texto.contains("puerta") ->
                contexto.getString(R.string.notificacion_titulo_puerta)
            texto.contains("movimiento") ->
                contexto.getString(R.string.notificacion_titulo_movimiento)
            else -> contexto.getString(R.string.notificacion_titulo_alerta)
        }
    }

    fun debeNotificar(tipo: String): Boolean {
        return tipo == EventoSistema.TIPO_PANICO || tipo == EventoSistema.TIPO_SENSOR
    }

    fun debeNotificar(evento: EventoSistema): Boolean {
        if (!debeNotificar(evento.tipo)) return false
        if (evento.tipo == EventoSistema.TIPO_PANICO &&
            evento.descripcion.contains("desactivada", ignoreCase = true)
        ) {
            return false
        }
        return true
    }

    fun debeNotificar(evento: EventoSistema, sistema: EstadoSistemaSeguridad): Boolean {
        if (!debeNotificar(evento)) return false

        if (evento.tipo == EventoSistema.TIPO_SISTEMA || evento.tipo == EventoSistema.TIPO_ALARMA) {
            return false
        }

        val desc = evento.descripcion.lowercase()

        if (desc.contains("modo ") && (desc.contains("activado") || desc.contains("desactivado"))) {
            return false
        }
        if (desc.contains("sensores restablecidos") || desc.contains("sistema safecare inicializado")) {
            return false
        }
        if (desc.contains("simulación esp32") && desc.contains("sin movimiento")) {
            return false
        }

        val alarma = EstadoAlarma.desdeFirebase(sistema.estadoAlarma)
        if (evento.tipo == EventoSistema.TIPO_SENSOR && !alarma.estaArmado) {
            return false
        }

        if (desc.contains("inactividad") && !sistema.modoMayoresActivo) {
            return false
        }

        if (desc.contains("movimiento") && sistema.modoMascotasActivo && alarma.esModoFuera) {
            return false
        }

        return true
    }
}
