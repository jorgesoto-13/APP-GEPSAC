package com.gepsac.app.view.admin

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.gepsac.app.model.ItemPedido
import com.gepsac.app.model.Producto
import com.gepsac.app.model.Usuario
import com.gepsac.app.databinding.DialogNuevoPedidoBinding
import com.gepsac.app.databinding.ItemCantidadProductoBinding
import com.gepsac.app.view.common.elegirFecha
import com.gepsac.app.view.common.err
import com.gepsac.app.view.common.soles
import com.gepsac.app.util.Validar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * El admin crea un pedido a nombre de un cliente (casos en que el cliente no puede hacerlo). Entra ya "Registrado".
 * El diálogo solo recoge y valida los datos; quien lo llama (ViewModel) guarda el pedido.
 */
fun Fragment.mostrarNuevoPedido(
    clientes: List<Usuario>,
    productos: List<Producto>,
    onConfirmar: (cliente: Usuario, items: List<ItemPedido>, dir: String, tel: String, fecha: String, obs: String) -> Unit
) {
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
                    items.add(ItemPedido(p.id, p.nombreCompleto, p.precio, n))
                }
            }
        }
        d.tvErrorItems.visibility = if (cantidadesOk && items.isEmpty()) View.VISIBLE else View.GONE
        if (!camposOk || !cantidadesOk || items.isEmpty()) return@setOnClickListener

        dlg.dismiss()
        onConfirmar(clientes[d.spCliente.selectedItemPosition], items, dir, tel, fecha, obs)
    }
}
