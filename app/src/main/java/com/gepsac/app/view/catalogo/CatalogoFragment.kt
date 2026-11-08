package com.gepsac.app.view.catalogo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.gepsac.app.model.MARCAS
import com.gepsac.app.model.Producto
import com.gepsac.app.databinding.FragmentCatalogoBinding
import com.gepsac.app.di.Fabrica
import com.gepsac.app.viewmodel.CarritoViewModel
import com.gepsac.app.view.common.crearChips
import com.gepsac.app.util.Validar
import com.gepsac.app.util.observarEvento
import com.gepsac.app.viewmodel.CatalogoViewModel

class CatalogoFragment : Fragment() {
    private var _b: FragmentCatalogoBinding? = null
    private val b get() = _b!!
    private val vm: CatalogoViewModel by activityViewModels { Fabrica.catalogo }
    private val carrito: CarritoViewModel by activityViewModels { Fabrica.carrito }
    private var todos = listOf<Producto>()
    private var filtro: String? = null
    private lateinit var adapter: ProductoAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCatalogoBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        adapter = ProductoAdapter(false) { p ->
            val ok = carrito.agregar(p)
            val msg = if (ok) p.nombre + " " + p.presentacion + " agregado al carrito"
            else "Máximo " + Validar.MAX_CANT + " unidades por producto"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
        b.rv.layoutManager = GridLayoutManager(requireContext(), 2)
        b.rv.adapter = adapter
        vm.productos.observe(viewLifecycleOwner) { l -> todos = l; aplicar() }
        vm.error.observarEvento(viewLifecycleOwner) { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
        crearChips(b.chips, listOf("Todas") + MARCAS) { sel -> filtro = if (sel == "Todas") null else sel; aplicar() }
    }

    private fun aplicar() {
        val l = todos.filter { filtro == null || it.marca == filtro }
        adapter.submit(l)
        b.tvVacio.visibility = if (l.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() { _b = null; super.onDestroyView() }
}
