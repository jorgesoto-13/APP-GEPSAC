package com.gepsac.app.view.catalogo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gepsac.app.model.Producto
import com.gepsac.app.databinding.ItemProductoBinding
import com.gepsac.app.view.common.mostrarProducto
import com.gepsac.app.view.common.soles

/** Tarjeta de producto. admin=true muestra "Editar" en lugar de "Agregar". */
class ProductoAdapter(
    private val admin: Boolean,
    private val onClick: (Producto) -> Unit
) : RecyclerView.Adapter<ProductoAdapter.VH>() {
    private var data = listOf<Producto>()
    fun submit(l: List<Producto>) { data = l; notifyDataSetChanged() }

    class VH(val b: ItemProductoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemProductoBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun getItemCount() = data.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val p = data[i]
        with(h.b) {
            tvMarca.text = p.marca.uppercase()
            tvNombre.text = p.nombre
            tvPres.text = if (p.activo) p.presentacion else p.presentacion + " · Inactivo"
            tvPrecio.text = p.precio.soles()
            img.mostrarProducto(p.imagen)
            btn.text = if (admin) "Editar" else "Agregar"
            btn.setOnClickListener { onClick(p) }
        }
    }
}
