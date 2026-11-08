package com.gepsac.app.view.carrito

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gepsac.app.viewmodel.CarritoViewModel

class CarritoFragment : Fragment() {
    private var _b: FragmentCarritoBinding? = null
    private val b get() = _b!!
    private val vm: CarritoViewModel by activityViewModels { Fabrica.carrito }
    private lateinit var adapter: CarritoAdapter

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
        d.etTel.setText(vm.telefonoUsuario)
        d.etFecha.setOnClickListener {
            requireContext().elegirFecha { f -> d.etFecha.setText(f); d.tilFecha.error = null }
        }
        val dlg = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enviar pedido").setView(d.root)
            .setNegativeButton("Cancelar", null).setPositiveButton("Enviar pedido", null).create()
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
            vm.confirmarPedido(dir, tel, fecha, obs)
            dlg.dismiss()
        }
    }

    override fun onDestroyView() { _b = null; super.onDestroyView() }
}
