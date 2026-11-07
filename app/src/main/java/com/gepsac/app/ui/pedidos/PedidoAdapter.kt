package com.gepsac.app.ui.pedidos

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gepsac.app.data.model.ESTADOS
import com.gepsac.app.data.model.Pedido
import com.gepsac.app.databinding.ItemPedidoBinding
import com.gepsac.app.ui.common.ESTADO_COLORES
import com.gepsac.app.ui.common.fechaHora
import com.gepsac.app.ui.common.soles

class PedidoAdapter(
    private val admin: Boolean,
    private val onClick: (Pedido) -> Unit
) : RecyclerView.Adapter<PedidoAdapter.VH>() {
    private var data = listOf<Pedido>()
    fun submit(l: List<Pedido>) { data = l; notifyDataSetChanged() }

    class VH(val b: ItemPedidoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemPedidoBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun getItemCount() = data.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val p = data[i]
        val e = p.estadoSeguro
        with(h.b) {
            tvNumero.text = "Pedido #" + p.numero
            tvCliente.text = if (admin) p.clienteNombre
            else p.items.joinToString(", ") { x -> x.cantidad.toString() + "× " + x.nombre }
            tvFecha.text = p.tiempos.getOrElse(0) { 0L }.fechaHora()
            tvTotal.text = p.total.soles()
            tvEstado.text = ESTADOS[e]
            tvEstado.backgroundTintList = ColorStateList.valueOf(ESTADO_COLORES[e])
            root.setOnClickListener { onClick(p) }
        }
    }
}
