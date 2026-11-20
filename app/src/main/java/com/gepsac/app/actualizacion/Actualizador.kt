package com.gepsac.app.actualizacion

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.gepsac.app.BuildConfig
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualización dentro de la app. En cada compilación de main, GitHub Actions publica un Release con el APK
 * y con el número de compilación en la etiqueta («build-12»). Al abrir la app se compara con el de este APK:
 * si hay uno más nuevo se ofrece «Actualizar ahora» (descarga e instala) o «Más tarde».
 *
 * Solo se activa en los APK compilados por GitHub Actions ([BuildConfig.BUSCAR_ACTUALIZACIONES]);
 * las compilaciones locales de Android Studio no preguntan nada.
 */
object Actualizador {
    private class Version(val numero: Int, val nombre: String, val notas: String, val urlApk: String)

    private const val ARCHIVO = "gepsac-actualizacion.apk"

    /** Versión que espera a que el usuario dé el permiso de instalar; al volver a la app, la descarga sigue sola. */
    private var pendientePermiso: Version? = null

    /** Se pregunta una sola vez por apertura de la app: si el usuario elige «Más tarde», no se insiste. */
    private var consultado = false

    fun buscar(actividad: AppCompatActivity) {
        if (!BuildConfig.BUSCAR_ACTUALIZACIONES || consultado) return
        consultado = true
        actividad.lifecycleScope.launch {
            val ultima = withContext(Dispatchers.IO) { consultarUltimaVersion() } ?: return@launch
            if (ultima.numero > BuildConfig.NUMERO_COMPILACION) preguntar(actividad, ultima)
        }
    }

    /** Se llama al volver a una pantalla: si el usuario acababa de dar el permiso de instalar, continúa la descarga. */
    fun reanudar(actividad: AppCompatActivity) {
        val v = pendientePermiso ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !actividad.packageManager.canRequestPackageInstalls()) return
        pendientePermiso = null
        iniciarDescarga(actividad, v)
    }

    private fun consultarUltimaVersion(): Version? {
        return try {
            val url = URL("https://api.github.com/repos/" + BuildConfig.REPO_GITHUB + "/releases/latest")
            val conexion = url.openConnection() as HttpURLConnection
            conexion.connectTimeout = 8_000
            conexion.readTimeout = 8_000
            conexion.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (conexion.responseCode != HttpURLConnection.HTTP_OK) return null
                val json = JSONObject(conexion.inputStream.bufferedReader().use { it.readText() })
                val numero = json.getString("tag_name").removePrefix("build-").toIntOrNull() ?: return null
                val assets = json.getJSONArray("assets")
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
                Version(
                    numero = numero,
                    nombre = json.optString("name", "build $numero"),
                    notas = json.optString("body", "").trim().take(300),
                    urlApk = apk.getString("browser_download_url")
                )
            } finally {
                conexion.disconnect()
            }
        } catch (e: IOException) {
            null   // sin internet o GitHub no responde: no se molesta al usuario
        } catch (e: JSONException) {
            null
        }
    }

    private fun preguntar(actividad: AppCompatActivity, v: Version) {
        if (actividad.isFinishing || actividad.isDestroyed) return
        val mensaje = buildString {
            append("Hay una versión más nueva de GEPSAC (").append(v.nombre).append(").")
            if (v.notas.isNotEmpty()) append("\n\nNovedades:\n").append(v.notas)
        }
        MaterialAlertDialogBuilder(actividad)
            .setTitle("Actualización disponible")
            .setMessage(mensaje)
            .setPositiveButton("Actualizar ahora") { _, _ -> iniciarDescarga(actividad, v) }
            .setNegativeButton("Más tarde", null)
            .show()
    }

    private fun iniciarDescarga(actividad: AppCompatActivity, v: Version) {
        val app = actividad.applicationContext

        // Android 8+: instalar un APK exige permitirlo para esta app (una sola vez).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
            pendientePermiso = v
            Toast.makeText(app, "Activa «Permitir de esta fuente» y regresa a GEPSAC: la descarga seguirá sola.", Toast.LENGTH_LONG).show()
            val ajustes = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(ajustes)
            return
        }

        val carpeta = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        val archivo = File(carpeta, ARCHIVO)
        if (archivo.exists()) archivo.delete()

        val descargas = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val idDescarga = descargas.enqueue(
            DownloadManager.Request(Uri.parse(v.urlApk))
                .setTitle("Actualización de GEPSAC")
                .setDescription("Descargando " + v.nombre)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, ARCHIVO)
        )

        val receptor = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != idDescarga) return
                app.unregisterReceiver(this)
                instalar(app, archivo)
            }
        }
        ContextCompat.registerReceiver(
            app, receptor, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED
        )
        Toast.makeText(app, "Descargando la actualización…", Toast.LENGTH_SHORT).show()
    }

    private fun instalar(app: Context, archivo: File) {
        if (!archivo.exists() || archivo.length() == 0L) {
            Toast.makeText(app, "No se pudo descargar la actualización. Inténtalo más tarde.", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(app, app.packageName + ".fileprovider", archivo)
        val instalador = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

        // 1) Intento directo: funciona si GEPSAC está en pantalla.
        try {
            app.startActivity(instalador)
        } catch (e: Exception) {
            // Android puede bloquear abrir el instalador desde segundo plano: queda el aviso de abajo.
        }
        // 2) Aviso con botón por si lo anterior no pudo abrirse (por ejemplo, si el usuario salió de la app mientras descargaba).
        avisarDescargaLista(app, instalador)
    }

    private fun avisarDescargaLista(app: Context, instalador: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = android.app.NotificationChannel("actualizaciones", "Actualizaciones", android.app.NotificationManager.IMPORTANCE_HIGH)
            app.getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(canal)
        }
        val abrir = android.app.PendingIntent.getActivity(
            app, 0, instalador, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val aviso = androidx.core.app.NotificationCompat.Builder(app, "actualizaciones")
            .setSmallIcon(com.gepsac.app.R.drawable.ic_cup)
            .setContentTitle("Actualización lista")
            .setContentText("Toca para instalar la nueva versión de GEPSAC.")
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            androidx.core.app.NotificationManagerCompat.from(app).notify(4001, aviso)
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones: se ignora.
        }
    }
}
