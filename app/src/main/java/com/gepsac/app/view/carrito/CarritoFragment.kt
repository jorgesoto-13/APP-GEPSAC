
package com.gepsac.app.view.carrito

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gepsac.app.R
import com.gepsac.app.databinding.DialogCheckoutBinding
import com.gepsac.app.databinding.FragmentCarritoBinding
import com.gepsac.app.di.Fabrica
import com.gepsac.app.util.Validar
import com.gepsac.app.util.observarEvento
import com.gepsac.app.view.cliente.ClienteActivity
import com.gepsac.app.view.common.elegirFecha
import com.gepsac.app.view.common.err
import com.gepsac.app.view.common.paraTelefono
import com.gepsac.app.view.common.soles
import com.gepsac.app.view.mapa.MapaActivity
import com.gepsac.app.view.seguimiento.mostrarPago
import com.gepsac.app.viewmodel.CarritoViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class CarritoFragment : Fragment() {

    private var _b: FragmentCarritoBinding? = null
    private val b get() = _b!!

    private val vm: CarritoViewModel by activityViewModels {
        Fabrica.carrito
    }

    private lateinit var adapter: CarritoAdapter

    /** Diálogo de checkout activo. */
    private var dialogoActual: DialogCheckoutBinding? = null

    /** Callback que recibe la URI del comprobante seleccionado. */
    private var callbackComprobante: ((Uri) -> Unit)? = null

    private var latitud: Double? = null
    private var longitud: Double? = null
    private var rellenandoDireccion = false

    private val selectorComprobante =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->

            if (uri != null) {
                callbackComprobante?.invoke(uri)
            }

            callbackComprobante = null
        }

    private val selectorMapa =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { resultado ->

            val datos = resultado.data

            if (
                resultado.resultCode == Activity.RESULT_OK &&
                datos != null
            ) {
                fijarDireccion(
                    datos.getStringExtra(
                        MapaActivity.EXTRA_DIRECCION
                    ).orEmpty(),
                    datos.getDoubleExtra(
                        MapaActivity.EXTRA_LATITUD,
                        0.0
                    ),
                    datos.getDoubleExtra(
                        MapaActivity.EXTRA_LONGITUD,
                        0.0
                    )
                )
            }
        }

    private val permisoUbicacion =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { concedido ->

            if (concedido) {
                usarUbicacion()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Sin permiso de ubicación. Escribe la dirección a mano.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _b = FragmentCarritoBinding.inflate(
            inflater,
            container,
            false
        )

        return b.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        adapter = CarritoAdapter(
            onCambio = { id, delta ->

                if (!vm.cambiarCantidad(id, delta)) {
                    Toast.makeText(
                        requireContext(),
                        "Máximo ${Validar.MAX_CANT} unidades por producto",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },

            onEliminar = { id ->
                confirmarEliminar(id)
            }
        )

        b.rv.layoutManager =
            LinearLayoutManager(requireContext())

        b.rv.adapter = adapter

        vm.carrito.observe(viewLifecycleOwner) { lista ->

            adapter.submit(lista)

            b.tvVacio.visibility =
                if (lista.isEmpty()) View.VISIBLE
                else View.GONE

            actualizarBoton()
        }

        vm.total.observe(viewLifecycleOwner) { total ->

            b.tvTotal.text = "Total: ${total.soles()}"
        }

        vm.enviando.observe(viewLifecycleOwner) {
            actualizarBoton()
        }

        vm.pedidoEnviado.observarEvento(viewLifecycleOwner) { numero ->

            Toast.makeText(
                requireContext(),
                "Pedido #$numero registrado. Tu pago está pendiente de revisión.",
                Toast.LENGTH_LONG
            ).show()

            (activity as? ClienteActivity)
                ?.irA(R.id.nav_pedidos)
        }

        vm.error.observarEvento(viewLifecycleOwner) { mensaje ->

            Toast.makeText(
                requireContext(),
                mensaje,
                Toast.LENGTH_LONG
            ).show()
        }

        b.btnConfirmar.setOnClickListener {
            checkout()
        }

        b.btnVaciar.setOnClickListener {
            confirmarVaciar()
        }
    }

    // --------------------------------------------------
    // Eliminar producto
    // --------------------------------------------------

    private fun confirmarEliminar(id: String) {

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar producto")
            .setMessage(
                "¿Deseas eliminar este producto del carrito?"
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->

                vm.eliminar(id)
            }
            .show()
    }

    // --------------------------------------------------
    // Vaciar carrito
    // --------------------------------------------------

    private fun confirmarVaciar() {

        if (vm.carrito.value.orEmpty().isEmpty()) {

            Toast.makeText(
                requireContext(),
                "El carrito ya está vacío",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Vaciar carrito")
            .setMessage(
                "¿Deseas eliminar todos los productos?"
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Vaciar") { _, _ ->

                vm.vaciar()
            }
            .show()
    }

    // --------------------------------------------------
    // Botón confirmar
    // --------------------------------------------------

    private fun actualizarBoton() {

        val hayItems =
            vm.carrito.value.orEmpty().isNotEmpty()

        b.btnConfirmar.isEnabled =
            hayItems && vm.enviando.value != true
    }

    // --------------------------------------------------
    // Checkout
    // --------------------------------------------------

    private fun checkout() {

        val d = DialogCheckoutBinding.inflate(
            layoutInflater
        )

        dialogoActual = d

        latitud = null
        longitud = null

        d.etTel.paraTelefono(d.tilTel)
        d.etTel.setText(vm.telefonoUsuario)

        // Si se modifica manualmente la dirección,
        // descartamos las coordenadas anteriores.
        d.etDir.doAfterTextChanged {

            if (!rellenandoDireccion) {
                latitud = null
                longitud = null
            }
        }

        d.etFecha.setOnClickListener {

            requireContext().elegirFecha { fecha ->

                d.etFecha.setText(fecha)
                d.tilFecha.error = null
            }
        }

        // Direcciones guardadas.
        val guardadas = vm.direcciones.value.orEmpty()

        if (guardadas.isNotEmpty()) {

            d.tvGuardadas.visibility = View.VISIBLE
            d.chipsDirecciones.visibility = View.VISIBLE

            guardadas.forEach { direccion ->

                val chip = Chip(requireContext()).apply {

                    text = if (direccion.length > 34) {
                        direccion.take(34) + "…"
                    } else {
                        direccion
                    }

                    isCheckable = false

                    setOnClickListener {

                        d.etDir.setText(direccion)
                        d.tilDir.error = null
                        d.cbGuardarDir.isChecked = false
                    }
                }

                d.chipsDirecciones.addView(chip)
            }
        }

        d.btnUbicacion.setOnClickListener {
            pedirUbicacion()
        }

        d.btnMapa.setOnClickListener {

            val intent = android.content.Intent(
                requireContext(),
                MapaActivity::class.java
            )

            latitud?.let {
                intent.putExtra(
                    MapaActivity.EXTRA_LATITUD,
                    it
                )
            }

            longitud?.let {
                intent.putExtra(
                    MapaActivity.EXTRA_LONGITUD,
                    it
                )
            }

            selectorMapa.launch(intent)
        }

        val dialogo = MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle("Datos de entrega")
            .setView(d.root)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar al pago", null)
            .create()

        dialogo.setOnDismissListener {
            dialogoActual = null
        }

        dialogo.show()

        dialogo.getButton(
            AlertDialog.BUTTON_POSITIVE
        ).setOnClickListener {

            val direccion =
                d.etDir.text.toString().trim()

            val telefono =
                d.etTel.text.toString().trim()

            val fecha =
                d.etFecha.text.toString().trim()

            val observacion =
                d.etObs.text.toString().trim()

            val valido = listOf(

                d.tilDir.err(
                    Validar.direccion(direccion)
                ),

                d.tilTel.err(
                    Validar.telefono(telefono)
                ),

                d.tilFecha.err(
                    Validar.fechaEntrega(fecha)
                ),

                d.tilObs.err(
                    Validar.observacion(observacion)
                )

            ).all { it }

            if (!valido) return@setOnClickListener

            if (d.cbGuardarDir.isChecked) {
                vm.guardarDireccion(direccion)
            }

            // Guardamos temporalmente los datos de entrega
            // para enviarlos cuando el pago sea confirmado.
            dialogo.dismiss()

            mostrarDialogoPago(
                direccion = direccion,
                telefono = telefono,
                fecha = fecha,
                observacion = observacion,
                lat = latitud,
                lng = longitud
            )
        }
    }

    // --------------------------------------------------
    // Pago y creación del pedido
    // --------------------------------------------------

    private fun mostrarDialogoPago(
        direccion: String,
        telefono: String,
        fecha: String,
        observacion: String,
        lat: Double?,
        lng: Double?
    ) {

        val monto = vm.total.value ?: 0.0

        (requireActivity() as androidx.appcompat.app.AppCompatActivity)
            .mostrarPago(
                monto = monto,

                elegirImagen = { callback ->

                    callbackComprobante = callback

                    selectorComprobante.launch("image/*")
                },

                onPagado = { metodo, referencia, comprobante ->

                    vm.confirmarPedido(
                        dir = direccion,
                        tel = telefono,
                        fecha = fecha,
                        obs = observacion,
                        metodoPago = metodo,
                        referenciaPago = referencia,
                        comprobante = comprobante,
                        latitud = lat,
                        longitud = lng
                    )
                }
            )
    }

    // --------------------------------------------------
    // Dirección obtenida del mapa o GPS
    // --------------------------------------------------

    private fun fijarDireccion(
        texto: String,
        lat: Double?,
        lng: Double?
    ) {

        val d = dialogoActual ?: return

        rellenandoDireccion = true

        d.etDir.setText(texto.take(120))

        rellenandoDireccion = false

        d.tilDir.error = null

        latitud = lat
        longitud = lng
    }

    // --------------------------------------------------
    // Ubicación actual
    // --------------------------------------------------

    private fun pedirUbicacion() {

        val concedido =
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (concedido) {
            usarUbicacion()
        } else {
            permisoUbicacion.launch(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun usarUbicacion() {

        val contexto =
            requireContext().applicationContext

        Toast.makeText(
            contexto,
            "Buscando tu ubicación…",
            Toast.LENGTH_SHORT
        ).show()

        viewLifecycleOwner.lifecycleScope.launch {

            try {

                val ubicacion =
                    LocationServices
                        .getFusedLocationProviderClient(contexto)
                        .getCurrentLocation(
                            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            null
                        )
                        .await()

                if (ubicacion == null) {

                    Toast.makeText(
                        contexto,
                        "No se pudo obtener la ubicación. Revisa que el GPS esté activo.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@launch
                }

                val direccion = withContext(
                    Dispatchers.IO
                ) {
                    direccionDe(
                        contexto,
                        ubicacion.latitude,
                        ubicacion.longitude
                    )
                }

                if (direccion == null) {

                    Toast.makeText(
                        contexto,
                        "Encontré tu ubicación, pero no pude convertirla en dirección. Escríbela a mano.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@launch
                }

                fijarDireccion(
                    direccion,
                    ubicacion.latitude,
                    ubicacion.longitude
                )

            } catch (e: Exception) {

                Toast.makeText(
                    contexto,
                    "No se pudo obtener la ubicación.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun direccionDe(
        contexto: Context,
        lat: Double,
        lng: Double
    ): String? {

        if (!Geocoder.isPresent()) return null

        return try {

            Geocoder(
                contexto,
                Locale("es", "PE")
            )
                .getFromLocation(lat, lng, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)

        } catch (e: IOException) {

            null
        }
    }

    override fun onDestroyView() {

        dialogoActual = null
        callbackComprobante = null
        _b = null

        super.onDestroyView()
    }
}
