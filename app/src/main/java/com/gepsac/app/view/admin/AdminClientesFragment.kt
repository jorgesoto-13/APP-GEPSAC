package com.gepsac.app.view.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.gepsac.app.databinding.FragmentAdminClientesBinding
import com.gepsac.app.di.Fabrica
import com.gepsac.app.view.common.soles
import com.gepsac.app.util.observarEvento
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gepsac.app.viewmodel.AdminClientesViewModel
import com.gepsac.app.viewmodel.ResumenCliente

/** Módulo de Clientes (admin): listado con ListView. Se actualiza solo cuando cambian los clientes o los pedidos. */
class AdminClientesFragment : Fragment() {
    private var _b: FragmentAdminClientesBinding? = null
    private val b get() = _b!!
    private val vm: AdminClientesViewModel by activityViewModels { Fabrica.adminClientes }
    private lateinit var adapter: ClienteAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentAdminClientesBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        adapter = ClienteAdapter(requireContext())
        b.lv.adapter = adapter
        b.lv.emptyView = b.tvVacio
        b.lv.setOnItemClickListener { _, _, pos, _ -> detalle(adapter.getItem(pos)) }
        vm.resumen.observe(viewLifecycleOwner) { adapter.submit(it) }
        vm.error.observarEvento(viewLifecycleOwner) { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
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
