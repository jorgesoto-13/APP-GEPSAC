package com.gepsac.app.view.carrito

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gepsac.app.model.ItemPedido
import com.gepsac.app.databinding.ItemCarritoBinding
import com.gepsac.app.view.common.AnimadorLista
import com.gepsac.app.view.common.soles
import com.gepsac.app.view.common.mostrarProducto

class CarritoAdapter(
    private val onCambio: (String, Int) -> Unit,
    private val onEliminar: (String) -> Unit
) : RecyclerView.Adapter<CarritoAdapter.VH>() {
    private var data = listOf<ItemPedido>()
    private val animador = AnimadorLista()
    fun submit(l: List<ItemPedido>) { data = l; notifyDataSetChanged() }

    class VH(val b: ItemCarritoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemCarritoBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun getItemCount() = data.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val item = data[i]
        with(h.b) {
            tvNombre.text = item.nombre
            tvSub.text = "Subtotal: " + (item.precio * item.cantidad).soles()
            tvCant.text = item.cantidad.toString()
            img.mostrarProducto(item.imagen)
            btnMas.setOnClickListener { onCambio(item.productoId, 1) }
            btnMenos.setOnClickListener { onCambio(item.productoId, -1) }
            btnEliminar.setOnClickListener {
                onEliminar(item.productoId)
            }
        }
        animador.animar(h.itemView, i)
    }
}
