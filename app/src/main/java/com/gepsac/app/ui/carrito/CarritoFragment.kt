package com.gepsac.app.ui.carrito

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gepsac.app.R
import com.gepsac.app.data.ItemPedido
import com.gepsac.app.databinding.DialogCheckoutBinding
import com.gepsac.app.databinding.FragmentCarritoBinding
import com.gepsac.app.databinding.ItemCarritoBinding
import com.gepsac.app.ui.ClienteActivity
import com.gepsac.app.ui.elegirFecha
import com.gepsac.app.ui.err
import com.gepsac.app.ui.soles
import com.gepsac.app.viewmodel.TiendaViewModel
import com.gepsac.app.viewmodel.Validar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CarritoFragment : Fragment() {
    private var _b: FragmentCarritoBinding? = null
    private val b get() = _b!!
    private val vm: TiendaViewModel by activityViewModels()
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
            b.btnConfirmar.isEnabled = l.isNotEmpty()
        }
        vm.total.observe(viewLifecycleOwner) { b.tvTotal.text = "Total: " + it.soles() }
        b.btnConfirmar.setOnClickListener { checkout() }
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
            val p = vm.confirmarPedido(dir, tel, fecha, obs)
            dlg.dismiss()
            if (p != null) {
                Toast.makeText(requireContext(), "Pedido #" + p.numero + " enviado. Está por registrar.", Toast.LENGTH_LONG).show()
                (activity as? ClienteActivity)?.irA(R.id.nav_pedidos)
            }
        }
    }

    override fun onDestroyView() { _b = null; super.onDestroyView() }
}

class CarritoAdapter(private val onCambio: (String, Int) -> Unit) : RecyclerView.Adapter<CarritoAdapter.VH>() {
    private var data = listOf<ItemPedido>()
    fun submit(l: List<ItemPedido>) { data = l; notifyDataSetChanged() }

    class VH(val b: ItemCarritoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemCarritoBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun getItemCount() = data.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val item = data[i]
        with(h.b) {
            tvNombre.text = item.nombre
            tvSub.text = item.precio.soles() + " c/u · " + (item.precio * item.cantidad).soles()
            tvCant.text = item.cantidad.toString()
            btnMas.setOnClickListener { onCambio(item.productoId, 1) }
            btnMenos.setOnClickListener { onCambio(item.productoId, -1) }
        }
    }
}
