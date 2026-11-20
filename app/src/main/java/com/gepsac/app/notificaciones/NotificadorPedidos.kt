package com.gepsac.app.notificaciones

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gepsac.app.R
import com.gepsac.app.model.ESTADOS
import com.gepsac.app.model.ESTADO_PAGADO
import com.gepsac.app.model.Pedido
import com.gepsac.app.model.Usuario
import com.gepsac.app.model.repository.PedidoRepository
import com.gepsac.app.view.seguimiento.SeguimientoActivity
import com.gepsac.app.view.common.soles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/**
 * Avisos de pedidos sin servidor propio (el plan gratuito no permite Cloud Functions):
 *  - Cliente: «Tu pedido #N pasó a …» cuando el administrador cambia la etapa.
 *  - Administrador: «Nuevo pedido #N» y «Pedido #N pagado».
 *
 * Escucha los pedidos en tiempo real y compara con la última etapa vista de cada uno. Funciona mientras el proceso
 * de la app siga vivo (abierta o en segundo plano). Si Android la cierra del todo, los avisos esperan a que se vuelva a abrir.
 */
class NotificadorPedidos(
    private val contexto: Context,
    private val pedidos: PedidoRepository
) {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var trabajo: Job? = null
    private val prefs = contexto.getSharedPreferences("notificaciones", Context.MODE_PRIVATE)

    fun iniciar(usuario: Usuario) {
        trabajo?.cancel()
        crearCanal()
        val origen = if (usuario.esAdmin) pedidos.observarTodos() else pedidos.observarDeCliente(usuario.id)
        trabajo = alcance.launch {
            origen.catch { /* sin red o sin permisos: se reintenta al volver a abrir la app */ }
                .collect { lista -> revisar(usuario, lista) }
        }
    }

    fun detener() {
        trabajo?.cancel()
        trabajo = null
    }

    private fun revisar(usuario: Usuario, lista: List<Pedido>) {
        val clave = "estados_" + usuario.id
        val previos = leer(clave)
        // La primera vez solo se «siembra» el estado actual: no se avisa de todo lo que ya existía.
        if (previos != null) lista.forEach { comparar(usuario, it, previos[it.id]) }
        guardar(clave, lista.associate { it.id to it.estado })
    }

    private fun comparar(usuario: Usuario, p: Pedido, anterior: Int?) {
        val idAviso = p.id.hashCode()
        if (usuario.esAdmin) {
            when {
                anterior == null && p.creadoPor != usuario.id ->
                    notificar(idAviso, p.id, "Nuevo pedido #" + p.numero, p.clienteNombre + " hizo un pedido por " + p.total.soles())
                anterior != null && anterior < ESTADO_PAGADO && p.estado >= ESTADO_PAGADO ->
                    notificar(
                        idAviso, p.id, "Pedido #" + p.numero + " pagado",
                        p.clienteNombre + " pagó " + p.total.soles() +
                            if (p.pago?.comprobante.orEmpty().isNotEmpty()) ". Toca para revisar el comprobante." else "."
                    )
            }
        } else {
            when {
                anterior == null && p.creadoPor != usuario.id ->
                    notificar(idAviso, p.id, "Pedido #" + p.numero, "Se registró un pedido a tu nombre.")
                // Pagar lo hace el propio cliente: no hace falta avisarle.
                anterior != null && p.estado > anterior && p.estado != ESTADO_PAGADO ->
                    notificar(idAviso, p.id, "Pedido #" + p.numero, "Ahora está: «" + ESTADOS[p.estadoSeguro] + "»")
            }
        }
    }

    // ---------- Almacenamiento de la última etapa vista ----------

    private fun leer(clave: String): Map<String, Int>? {
        val texto = prefs.getString(clave, null) ?: return null
        return try {
            val o = JSONObject(texto)
            o.keys().asSequence().associateWith { o.getInt(it) }
        } catch (e: JSONException) {
            null
        }
    }

    private fun guardar(clave: String, estados: Map<String, Int>) {
        val o = JSONObject()
        estados.forEach { (id, estado) -> o.put(id, estado) }
        prefs.edit().putString(clave, o.toString()).apply()
    }

    // ---------- Notificación del sistema ----------

    private fun crearCanal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(CANAL, "Pedidos", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Cambios de etapa, pedidos nuevos y pagos"
        }
        contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    private fun notificar(id: Int, pedidoId: String, titulo: String, texto: String) {
        // Desde Android 13 hace falta el permiso de notificaciones (se pide al abrir el panel).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        // Al tocar el aviso se abre ese pedido (si la sesión ya no está en memoria, pasa antes por el login).
        val abrir = PendingIntent.getActivity(
            contexto, id,
            Intent(contexto, SeguimientoActivity::class.java).putExtra("id", pedidoId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val aviso = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_cup)
            .setColor(ContextCompat.getColor(contexto, R.color.brown))
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(contexto).notify(id, aviso)
        } catch (e: SecurityException) {
            // El usuario quitó el permiso justo ahora: se ignora.
        }
    }

    private companion object {
        const val CANAL = "pedidos"
    }
}
