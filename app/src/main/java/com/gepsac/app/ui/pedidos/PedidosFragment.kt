package com.gepsac.app.ui.pedidos

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gepsac.app.data.ESTADOS
import com.gepsac.app.data.ESTADO_COLORES
import com.gepsac.app.data.Pedido
import com.gepsac.app.databinding.FragmentPedidosBinding
import com.gepsac.app.databinding.ItemPedidoBinding
import com.gepsac.app.ui.admin.mostrarNuevoPedido
import com.gepsac.app.ui.crearChips
import com.gepsac.app.ui.fechaHora
import com.gepsac.app.ui.seguimiento.SeguimientoActivity
import com.gepsac.app.ui.soles
import com.gepsac.app.viewmodel.TiendaViewModel

/** Módulo de Pedidos: cliente ve los suyos, admin ve todos. */
class PedidosFragment : Fragment() {
    private var _b: FragmentPedidosBinding? = null
    private val b get() = _b!!
    private val vm: TiendaViewModel by activityViewModels()
    private var todos = listOf<Pedido>()
    private var filtro = -1
    private lateinit var adapter: PedidoAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentPedidosBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        adapter = PedidoAdapter(vm.esAdmin) { p ->
            startActivity(Intent(requireContext(), SeguimientoActivity::class.java).putExtra("id", p.id))
        }
        b.rv.layoutManager = LinearLayoutManager(requireContext())
        b.rv.adapter = adapter
        vm.pedidos.observe(viewLifecycleOwner) { l -> todos = l; aplicar() }
        crearChips(b.chips, listOf("Todos") + ESTADOS) { sel -> filtro = ESTADOS.indexOf(sel); aplicar() }
        b.etBuscar.doAfterTextChanged { aplicar() }
        b.fabNuevo.visibility = if (vm.esAdmin) View.VISIBLE else View.GONE
        b.fabNuevo.setOnClickListener { mostrarNuevoPedido(vm) }
    }

    private fun aplicar() {
        val q = b.etBuscar.text.toString().trim().lowercase()
        val l = todos.filter {
            (filtro < 0 || it.estado == filtro) &&
                (q.isEmpty() || it.clienteNombre.lowercase().contains(q) || it.numero.toString().contains(q))
        }
        adapter.submit(l)
        b.tvVacio.visibility = if (l.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() { _b = null; super.onDestroyView() }
}

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
        val e = p.estado.coerceIn(0, ESTADOS.size - 1)
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
