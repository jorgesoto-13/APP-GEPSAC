package com.gepsac.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.gepsac.app.data.model.MARCAS
import com.gepsac.app.data.model.Producto
import com.gepsac.app.databinding.DialogProductoBinding
import com.gepsac.app.databinding.FragmentAdminProductosBinding
import com.gepsac.app.di.Fabrica
import com.gepsac.app.ui.catalogo.ProductoAdapter
import com.gepsac.app.ui.common.Imagenes
import com.gepsac.app.ui.common.err
import com.gepsac.app.ui.common.mostrarProducto
import com.gepsac.app.util.Validar
import com.gepsac.app.util.observarEvento
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Módulo de Administración: gestión de productos. */
class AdminProductosFragment : Fragment() {
    private var _b: FragmentAdminProductosBinding? = null
    private val b get() = _b!!
    private val vm: AdminProductosViewModel by activityViewModels { Fabrica.adminProductos }
    private lateinit var adapter: ProductoAdapter

    // Imagen (Base64) elegida en el diálogo abierto, aún sin guardar, y su vista previa
    private var imagenNueva: String? = null
    private var dialogoActual: DialogProductoBinding? = null
    private val elegirImagen = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val d = dialogoActual ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        val base64 = Imagenes.codificar(requireContext(), uri)
        if (base64 == null) {
            Toast.makeText(requireContext(), "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        imagenNueva = base64
        d.imgProducto.mostrarProducto(base64)
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentAdminProductosBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        adapter = ProductoAdapter(true) { p -> dialogo(p) }
        b.rv.layoutManager = GridLayoutManager(requireContext(), 2)
        b.rv.adapter = adapter
        b.fab.setOnClickListener { dialogo(null) }
        vm.productos.observe(viewLifecycleOwner) { adapter.submit(it) }
        vm.mensaje.observarEvento(viewLifecycleOwner) { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
    }

    private fun dialogo(p: Producto?) {
        val d = DialogProductoBinding.inflate(layoutInflater)
        d.spMarca.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, MARCAS)
        imagenNueva = null
        dialogoActual = d
        d.imgProducto.mostrarProducto(p?.imagen ?: "")
        d.btnImagen.setOnClickListener { elegirImagen.launch("image/*") }
        if (p != null) {
            d.spMarca.setSelection(MARCAS.indexOf(p.marca).coerceAtLeast(0))
            d.etNombre.setText(p.nombre)
            d.etPres.setText(p.presentacion)
            d.etPrecio.setText(p.precio.toString())
            d.swActivo.isChecked = p.activo
        } else d.swActivo.isChecked = true

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (p == null) "Nuevo producto" else "Editar producto").setView(d.root)
            .setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null)
        if (p != null) builder.setNeutralButton("Eliminar", null)
        val dlg = builder.create()
        dlg.setOnDismissListener {
            imagenNueva = null
            dialogoActual = null
        }
        dlg.show()

        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val nombre = d.etNombre.text.toString().trim()
            val pres = d.etPres.text.toString().trim()
            val precioTxt = d.etPrecio.text.toString()
            val ok = listOf(
                d.tilNombre.err(Validar.nombreProducto(nombre)),
                d.tilPres.err(Validar.presentacion(pres)),
                d.tilPrecio.err(Validar.precio(precioTxt))
            ).all { it }
            if (!ok) return@setOnClickListener
            vm.guardar(
                Producto(
                    id = p?.id ?: vm.nuevoId(), nombre = nombre,
                    marca = MARCAS[d.spMarca.selectedItemPosition], presentacion = pres,
                    precio = precioTxt.trim().replace(",", ".").toDouble(), activo = d.swActivo.isChecked,
                    imagen = imagenNueva ?: p?.imagen ?: ""
                )
            )
            dlg.dismiss()
        }
        if (p != null) {
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage("¿Eliminar " + p.nombre + " " + p.presentacion + "?")
                    .setNegativeButton("No", null)
                    .setPositiveButton("Eliminar") { _, _ -> vm.eliminar(p.id); dlg.dismiss() }
                    .show()
            }
        }
    }

    override fun onDestroyView() { _b = null; super.onDestroyView() }
}
