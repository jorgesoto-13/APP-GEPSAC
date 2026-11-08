package com.gepsac.app.view.admin

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.gepsac.app.databinding.ItemClienteBinding
import com.gepsac.app.view.common.soles
import com.gepsac.app.viewmodel.ResumenCliente

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
