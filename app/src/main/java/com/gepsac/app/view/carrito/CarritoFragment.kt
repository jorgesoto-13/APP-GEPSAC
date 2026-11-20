package com.gepsac.app.view.carrito

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gepsac.app.R
import com.gepsac.app.databinding.DialogCheckoutBinding
import com.gepsac.app.databinding.FragmentCarritoBinding
import com.gepsac.app.di.Fabrica
import com.gepsac.app.view.cliente.ClienteActivity
import com.gepsac.app.view.common.elegirFecha
import com.gepsac.app.view.common.err
import com.gepsac.app.view.common.soles
import com.gepsac.app.util.Validar
import com.gepsac.app.util.observarEvento
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gepsac.app.viewmodel.CarritoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class CarritoFragment : Fragment() {
    private var _b: FragmentCarritoBinding? = null
    private val b get() = _b!!
    private val vm: CarritoViewModel by activityViewModels { Fabrica.carrito }
    private lateinit var adapter: CarritoAdapter

    /** Diálogo de envío abierto (para rellenar la dirección cuando llega la ubicación). */
    private var dialogoActual: DialogCheckoutBinding? = null

    private val permisoUbicacion = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) usarUbicacion()
        else Toast.makeText(requireContext(), "Sin permiso de ubicación. Escribe la dirección a mano.", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCarritoBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        adapter = CarritoAdapter { id, delta ->
            if (!vm.cambiarCantidad(id, delta))
                Toast.makeText(requireContext(), "Máximo " + Validar.MAX_CANT + " unidades por producto", Toast.LENGTH_SHORT).show()
        }
        b.rv.layoutManager = LinearLayoutManager(requireContext())
        b.rv.adapter = adapter
        vm.carrito.observe(viewLifecycleOwner) { l ->
            adapter.submit(l)
            b.tvVacio.visibility = if (l.isEmpty()) View.VISIBLE else View.GONE
            actualizarBoton()
        }
        vm.total.observe(viewLifecycleOwner) { b.tvTotal.text = "Total: " + it.soles() }
        vm.enviando.observe(viewLifecycleOwner) { actualizarBoton() }
        vm.pedidoEnviado.observarEvento(viewLifecycleOwner) { numero ->
            Toast.makeText(requireContext(), "Pedido #$numero enviado. Está por registrar.", Toast.LENGTH_LONG).show()
            (activity as? ClienteActivity)?.irA(R.id.nav_pedidos)
        }
        vm.error.observarEvento(viewLifecycleOwner) { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
        b.btnConfirmar.setOnClickListener { checkout() }
    }

    private fun actualizarBoton() {
        val hayItems = vm.carrito.value.orEmpty().isNotEmpty()
        b.btnConfirmar.isEnabled = hayItems && vm.enviando.value != true
    }

    private fun checkout() {
        val d = DialogCheckoutBinding.inflate(layoutInflater)
        dialogoActual = d
        d.etTel.setText(vm.telefonoUsuario)
        d.etFecha.setOnClickListener {
            requireContext().elegirFecha { f -> d.etFecha.setText(f); d.tilFecha.error = null }
        }

        // Direcciones guardadas: un toque rellena el campo.
        val guardadas = vm.direcciones.value.orEmpty()
        if (guardadas.isNotEmpty()) {
            d.tvGuardadas.visibility = View.VISIBLE
            d.chipsDirecciones.visibility = View.VISIBLE
            guardadas.forEach { dir ->
                val chip = Chip(requireContext()).apply {
                    text = if (dir.length > 34) dir.take(34) + "…" else dir
                    isCheckable = false
                    setOnClickListener { d.etDir.setText(dir); d.tilDir.error = null; d.cbGuardarDir.isChecked = false }
                }
                d.chipsDirecciones.addView(chip)
            }
        }
        d.btnUbicacion.setOnClickListener { pedirUbicacion() }

        val dlg = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enviar pedido").setView(d.root)
            .setNegativeButton("Cancelar", null).setPositiveButton("Enviar pedido", null).create()
        dlg.setOnDismissListener { dialogoActual = null }
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val dir = d.etDir.text.toString().trim()
            val tel = d.etTel.text.toString().trim()
            val fecha = d.etFecha.text.toString().trim()
            val obs = d.etObs.text.toString().trim()
            val ok = listOf(
                d.tilDir.err(Validar.direccion(dir)),
                d.tilTel.err(Validar.telefono(tel)),
                d.tilFecha.err(Validar.fechaEntrega(fecha)),
                d.tilObs.err(Validar.observacion(obs))
            ).all { it }
            if (!ok) return@setOnClickListener
            if (d.cbGuardarDir.isChecked) vm.guardarDireccion(dir)
            vm.confirmarPedido(dir, tel, fecha, obs)
            dlg.dismiss()
        }
    }

    // ---------- Ubicación actual ----------

    private fun pedirUbicacion() {
        val concedido = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (concedido) usarUbicacion() else permisoUbicacion.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** Obtiene la ubicación y la convierte en una dirección legible (sin claves de API: usa el Geocoder del sistema). */
    @SuppressLint("MissingPermission")
    private fun usarUbicacion() {
        val ctx = requireContext().applicationContext
        Toast.makeText(ctx, "Buscando tu ubicación…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ubicacion = LocationServices.getFusedLocationProviderClient(ctx)
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                if (ubicacion == null) {
                    Toast.makeText(ctx, "No se pudo obtener la ubicación. Revisa que el GPS esté activo.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val direccion = withContext(Dispatchers.IO) { direccionDe(ctx, ubicacion.latitude, ubicacion.longitude) }
                if (direccion == null) {
                    Toast.makeText(ctx, "Encontré tu ubicación, pero no pude convertirla en dirección. Escríbela a mano.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                dialogoActual?.let { it.etDir.setText(direccion.take(120)); it.tilDir.error = null }
            } catch (e: Exception) {
                Toast.makeText(ctx, "No se pudo obtener la ubicación.", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun direccionDe(ctx: android.content.Context, lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            Geocoder(ctx, Locale("es", "PE")).getFromLocation(lat, lng, 1)?.firstOrNull()?.getAddressLine(0)
        } catch (e: IOException) {
            null
        }
    }

    override fun onDestroyView() { dialogoActual = null; _b = null; super.onDestroyView() }
}
