package com.gepsac.app.ui.carrito

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gepsac.app.data.model.ItemPedido
import com.gepsac.app.databinding.ItemCarritoBinding
import com.gepsac.app.ui.common.soles

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
