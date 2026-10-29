package com.gepsac.app.ui.admin

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.gepsac.app.databinding.FragmentAdminClientesBinding
import com.gepsac.app.databinding.ItemClienteBinding
import com.gepsac.app.ui.soles
import com.gepsac.app.viewmodel.ResumenCliente
import com.gepsac.app.viewmodel.TiendaViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Módulo de Clientes (admin): listado con ListView. Se actualiza solo cuando cambian los pedidos. */
class AdminClientesFragment : Fragment() {
    private var _b: FragmentAdminClientesBinding? = null
    private val b get() = _b!!
    private val vm: TiendaViewModel by activityViewModels()
    private lateinit var adapter: ClienteAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentAdminClientesBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        adapter = ClienteAdapter(requireContext())
        b.lv.adapter = adapter
        b.lv.emptyView = b.tvVacio
        b.lv.setOnItemClickListener { _, _, pos, _ -> detalle(adapter.getItem(pos)) }
        vm.pedidos.observe(viewLifecycleOwner) { adapter.submit(vm.resumenClientes()) }
    }

    private fun detalle(r: ResumenCliente) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(r.cliente.nombre)
            .setMessage(
                "Correo: " + r.cliente.email + "\nTeléfono: " + r.cliente.telefono +
                    "\nPedidos: " + r.pedidos + "\nTotal pagado: " + r.totalPagado.soles()
            )
            .setPositiveButton("Cerrar", null)
            .show()
    }

    override fun onDestroyView() { _b = null; super.onDestroyView() }
}

/** Adapter del ListView: reutiliza la vista de la fila (convertView) para que el scroll sea fluido. */
class ClienteAdapter(private val ctx: Context) : BaseAdapter() {
    private var data = listOf<ResumenCliente>()
    fun submit(l: List<ResumenCliente>) { data = l; notifyDataSetChanged() }

    override fun getCount() = data.size
    override fun getItem(position: Int): ResumenCliente = data[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val b = if (convertView == null) ItemClienteBinding.inflate(LayoutInflater.from(ctx), parent, false)
        else ItemClienteBinding.bind(convertView)
        val r = data[position]
        b.tvNombre.text = r.cliente.nombre
        b.tvContacto.text = r.cliente.email + " · " + r.cliente.telefono
        b.tvPedidos.text = if (r.pedidos == 1) "1 pedido" else r.pedidos.toString() + " pedidos"
        b.tvTotal.text = r.totalPagado.soles()
        return b.root
    }
}
