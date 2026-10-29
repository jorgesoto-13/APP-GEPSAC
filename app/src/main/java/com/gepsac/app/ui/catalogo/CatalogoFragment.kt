package com.gepsac.app.ui.catalogo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gepsac.app.data.MARCAS
import com.gepsac.app.data.Producto
import com.gepsac.app.databinding.FragmentCatalogoBinding
import com.gepsac.app.databinding.ItemProductoBinding
import com.gepsac.app.ui.crearChips
import com.gepsac.app.ui.mostrarProducto
import com.gepsac.app.ui.soles
import com.gepsac.app.viewmodel.TiendaViewModel
import com.gepsac.app.viewmodel.Validar

class CatalogoFragment : Fragment() {
    private var _b: FragmentCatalogoBinding? = null
    private val b get() = _b!!
    private val vm: TiendaViewModel by activityViewModels()
    private var todos = listOf<Producto>()
    private var filtro: String? = null
    private lateinit var adapter: ProductoAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCatalogoBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        adapter = ProductoAdapter(false) { p ->
            val ok = vm.agregar(p)
            val msg = if (ok) p.nombre + " " + p.presentacion + " agregado al carrito"
            else "Máximo " + Validar.MAX_CANT + " unidades por producto"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
        b.rv.layoutManager = GridLayoutManager(requireContext(), 2)
        b.rv.adapter = adapter
        vm.productos.observe(viewLifecycleOwner) { l -> todos = l.filter { it.activo }; aplicar() }
        crearChips(b.chips, listOf("Todas") + MARCAS) { sel -> filtro = if (sel == "Todas") null else sel; aplicar() }
    }

    private fun aplicar() {
        val l = todos.filter { filtro == null || it.marca == filtro }
        adapter.submit(l)
        b.tvVacio.visibility = if (l.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() { _b = null; super.onDestroyView() }
}

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
