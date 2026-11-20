package com.gepsac.app.view.mapa

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gepsac.app.databinding.ActivityMapaBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import java.io.File

/**
 * Elegir el punto de entrega en un mapa gratuito (OpenStreetMap). El marcador está fijo en el centro: el usuario
 * mueve el mapa (o toca un punto, o busca un lugar) y la dirección se actualiza sola. Al confirmar devuelve la
 * dirección y las coordenadas ([EXTRA_DIRECCION], [EXTRA_LATITUD], [EXTRA_LONGITUD]).
 */
class MapaActivity : AppCompatActivity() {
    private lateinit var b: ActivityMapaBinding
    private var consulta: Job? = null
    private var direccionActual: String? = null

    private val permisoUbicacion = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) irAMiUbicacion()
        else Toast.makeText(this, "Sin permiso de ubicación. Mueve el mapa a mano.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OpenStreetMap pide identificar la app; los mapas descargados se guardan en la caché (sin pedir permisos de almacenamiento).
        Configuration.getInstance().apply {
            userAgentValue = packageName
            val base = File(cacheDir, "osmdroid").apply { mkdirs() }
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
        }

        b = ActivityMapaBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.mapa.setTileSource(TileSourceFactory.MAPNIK)
        b.mapa.setMultiTouchControls(true)
        b.mapa.controller.setZoom(if (intent.hasExtra(EXTRA_LATITUD)) 17.0 else 14.0)
        b.mapa.controller.setCenter(
            GeoPoint(
                intent.getDoubleExtra(EXTRA_LATITUD, LIMA_LATITUD),
                intent.getDoubleExtra(EXTRA_LONGITUD, LIMA_LONGITUD)
            )
        )

        // Tocar un punto del mapa lo lleva al centro, bajo el marcador.
        b.mapa.overlays.add(0, MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let { b.mapa.controller.animateTo(it) }
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }))
        b.mapa.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { programarDireccion(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { programarDireccion(); return false }
        })

        b.btnBuscar.setOnClickListener { buscarLugar() }
        b.etBuscar.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_SEARCH) { buscarLugar(); true } else false
        }
        b.btnMiUbicacion.setOnClickListener {
            val concedido = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (concedido) irAMiUbicacion() else permisoUbicacion.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        b.btnConfirmar.setOnClickListener {
            val centro = b.mapa.mapCenter
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra(EXTRA_LATITUD, centro.latitude)
                    .putExtra(EXTRA_LONGITUD, centro.longitude)
                    .putExtra(EXTRA_DIRECCION, direccionActual.orEmpty().take(120))
            )
            finish()
        }

        programarDireccion()
    }

    /** Espera a que el mapa deje de moverse y entonces pregunta la dirección del centro (una consulta a la vez). */
    private fun programarDireccion() {
        b.tvDireccion.text = "Buscando la dirección…"
        b.btnConfirmar.isEnabled = false
        direccionActual = null
        consulta?.cancel()
        consulta = lifecycleScope.launch {
            delay(800)
            val centro = b.mapa.mapCenter
            val direccion = withContext(Dispatchers.IO) { Nominatim.direccion(centro.latitude, centro.longitude) }
            // Si no se pudo obtener el nombre de la calle, se deja una descripción que el cliente puede corregir luego.
            direccionActual = direccion ?: "Punto marcado en el mapa (" + "%.5f".format(centro.latitude) + ", " + "%.5f".format(centro.longitude) + ")"
            b.tvDireccion.text = direccionActual
            b.btnConfirmar.isEnabled = true
        }
    }

    private fun buscarLugar() {
        val texto = b.etBuscar.text.toString().trim()
        if (texto.length < 3) {
            Toast.makeText(this, "Escribe al menos 3 letras", Toast.LENGTH_SHORT).show()
            return
        }
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(b.etBuscar.windowToken, 0)
        lifecycleScope.launch {
            val lugar = withContext(Dispatchers.IO) { Nominatim.buscar(texto) }
            if (lugar == null) {
                Toast.makeText(this@MapaActivity, "No encontré ese lugar. Prueba con otro nombre.", Toast.LENGTH_LONG).show()
            } else {
                b.mapa.controller.setZoom(17.0)
                b.mapa.controller.animateTo(GeoPoint(lugar.latitud, lugar.longitud))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun irAMiUbicacion() {
        lifecycleScope.launch {
            try {
                val ubicacion = LocationServices.getFusedLocationProviderClient(this@MapaActivity)
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                if (ubicacion == null) {
                    Toast.makeText(this@MapaActivity, "No se pudo obtener tu ubicación. Revisa que el GPS esté activo.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                b.mapa.controller.setZoom(17.0)
                b.mapa.controller.animateTo(GeoPoint(ubicacion.latitude, ubicacion.longitude))
            } catch (e: Exception) {
                Toast.makeText(this@MapaActivity, "No se pudo obtener tu ubicación.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() { super.onResume(); b.mapa.onResume() }

    override fun onPause() { b.mapa.onPause(); super.onPause() }

    override fun onDestroy() { b.mapa.onDetach(); super.onDestroy() }

    companion object {
        const val EXTRA_LATITUD = "latitud"
        const val EXTRA_LONGITUD = "longitud"
        const val EXTRA_DIRECCION = "direccion"

        // Centro de Lima: punto de partida cuando todavía no hay ubicación.
        private const val LIMA_LATITUD = -12.0464
        private const val LIMA_LONGITUD = -77.0428
    }
}
