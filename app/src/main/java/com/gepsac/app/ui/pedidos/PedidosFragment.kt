package com.gepsac.app.ui.pedidos

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.gepsac.app.data.model.ESTADOS
import com.gepsac.app.data.model.Producto
import com.gepsac.app.data.model.Usuario
import com.gepsac.app.databinding.FragmentPedidosBinding
import com.gepsac.app.di.Fabrica
import com.gepsac.app.ui.admin.NuevoPedidoViewModel
import com.gepsac.app.ui.admin.mostrarNuevoPedido
import com.gepsac.app.ui.common.crearChips
import com.gepsac.app.ui.seguimiento.SeguimientoActivity
import com.gepsac.app.util.observarEvento

/** Módulo de Pedidos: cliente ve los suyos, admin ve todos. */
class PedidosFragment : Fragment() {
    private var _b: FragmentPedidosBinding? = null
    private val b get() = _b!!
    private val vm: PedidosViewModel by activityViewModels { Fabrica.pedidos }
    private val nuevoVm: NuevoPedidoViewModel by viewModels { Fabrica.nuevoPedido }   // solo se usa si es admin
    private lateinit var adapter: PedidoAdapter

    // Datos del diálogo "Nuevo pedido" (solo admin)
    private var clientes = listOf<Usuario>()
    private var productos = listOf<Producto>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentPedidosBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        adapter = PedidoAdapter(vm.esAdmin) { p ->
            startActivity(Intent(requireContext(), SeguimientoActivity::class.java).putExtra("id", p.id))
        }
        b.rv.layoutManager = LinearLayoutManager(requireContext())
        b.rv.adapter = adapter
        vm.pedidos.observe(viewLifecycleOwner) { l ->
            adapter.submit(l)
            b.tvVacio.visibility = if (l.isEmpty()) View.VISIBLE else View.GONE
        }
        vm.error.observarEvento(viewLifecycleOwner) { aviso(it) }

        crearChips(b.chips, listOf("Todos") + ESTADOS) { sel -> vm.filtrarPorEstado(ESTADOS.indexOf(sel)) }
        b.etBuscar.doAfterTextChanged { vm.buscar(it?.toString().orEmpty()) }

        b.fabNuevo.visibility = if (vm.esAdmin) View.VISIBLE else View.GONE
        if (vm.esAdmin) {
            nuevoVm.clientes.observe(viewLifecycleOwner) { clientes = it }
            nuevoVm.productos.observe(viewLifecycleOwner) { productos = it }
            nuevoVm.creado.observarEvento(viewLifecycleOwner) { numero ->
                aviso("Pedido #$numero registrado. Esperando el pago del cliente.")
            }
            nuevoVm.mensaje.observarEvento(viewLifecycleOwner) { aviso(it) }
            b.fabNuevo.setOnClickListener {
                mostrarNuevoPedido(clientes, productos) { cliente, items, dir, tel, fecha, obs ->
                    nuevoVm.crear(cliente, items, dir, tel, fecha, obs)
                }
            }
        }
    }

    private fun aviso(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

    override fun onDestroyView() { _b = null; super.onDestroyView() }
}
