package com.gepsac.app.ui.admin

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.gepsac.app.data.ItemPedido
import com.gepsac.app.databinding.DialogNuevoPedidoBinding
import com.gepsac.app.databinding.ItemCantidadProductoBinding
import com.gepsac.app.ui.elegirFecha
import com.gepsac.app.ui.err
import com.gepsac.app.ui.soles
import com.gepsac.app.viewmodel.TiendaViewModel
import com.gepsac.app.viewmodel.Validar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** El admin crea un pedido a nombre de un cliente (casos en que el cliente no puede hacerlo). Entra ya "Registrado". */
fun Fragment.mostrarNuevoPedido(vm: TiendaViewModel) {
    val clientes = vm.clientes()
    val productos = vm.productosActivos()
    if (clientes.isEmpty() || productos.isEmpty()) {
        Toast.makeText(requireContext(), "Se necesita al menos un cliente y un producto activo", Toast.LENGTH_LONG).show()
        return
    }
    val d = DialogNuevoPedidoBinding.inflate(layoutInflater)
    d.spCliente.adapter = ArrayAdapter(
        requireContext(), android.R.layout.simple_spinner_dropdown_item,
        clientes.map { it.nombre + " (" + it.email + ")" }
    )
    d.spCliente.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
            d.etTel.setText(clientes[pos].telefono)
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
    val filas = productos.map { p ->
        val f = ItemCantidadProductoBinding.inflate(layoutInflater, d.llProductos, false)
        f.tvNombre.text = p.nombre + " " + p.presentacion + " · " + p.precio.soles()
        d.llProductos.addView(f.root)
        f
    }
    d.etFecha.setOnClickListener {
        requireContext().elegirFecha { f -> d.etFecha.setText(f); d.tilFecha.error = null }
    }

    val dlg = MaterialAlertDialogBuilder(requireContext())
        .setTitle("Nuevo pedido").setView(d.root)
        .setNegativeButton("Cancelar", null).setPositiveButton("Registrar pedido", null).create()
    dlg.show()
    dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val dir = d.etDir.text.toString().trim()
        val tel = d.etTel.text.toString().trim()
        val fecha = d.etFecha.text.toString().trim()
        val obs = d.etObs.text.toString().trim()
        val camposOk = listOf(
            d.tilDir.err(Validar.direccion(dir)),
            d.tilTel.err(Validar.telefono(tel)),
            d.tilFecha.err(Validar.fechaEntrega(fecha)),
            d.tilObs.err(Validar.observacion(obs))
        ).all { it }

        var cantidadesOk = true
        val items = mutableListOf<ItemPedido>()
        filas.forEachIndexed { i, f ->
            val txt = f.etCant.text.toString()
            val e = Validar.cantidad(txt)
            f.etCant.error = e
            if (e != null) cantidadesOk = false
            else {
                val n = txt.trim().toIntOrNull() ?: 0
                if (n > 0) {
                    val p = productos[i]
                    items.add(ItemPedido(p.id, (p.nombre + " " + p.presentacion).trim(), p.precio, n))
                }
            }
        }
        d.tvErrorItems.visibility = if (cantidadesOk && items.isEmpty()) View.VISIBLE else View.GONE
        if (!camposOk || !cantidadesOk || items.isEmpty()) return@setOnClickListener

        val p = vm.crearPedidoAdmin(clientes[d.spCliente.selectedItemPosition], items, dir, tel, fecha, obs)
        dlg.dismiss()
        Toast.makeText(requireContext(), "Pedido #" + p.numero + " registrado. Esperando el pago del cliente.", Toast.LENGTH_LONG).show()
    }
}
