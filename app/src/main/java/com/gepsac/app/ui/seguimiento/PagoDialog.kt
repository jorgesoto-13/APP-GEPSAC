package com.gepsac.app.ui.seguimiento

import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gepsac.app.data.METODOS_PAGO
import com.gepsac.app.data.Pedido
import com.gepsac.app.databinding.DialogPagoBinding
import com.gepsac.app.ui.err
import com.gepsac.app.ui.soles
import com.gepsac.app.viewmodel.Validar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Pago SIMULADO (no cobra nada). Con Firebase se puede cambiar por un voucher que verifique el admin. */
fun AppCompatActivity.mostrarPago(p: Pedido, onPagado: (String) -> Unit) {
    val d = DialogPagoBinding.inflate(layoutInflater)
    d.tvMonto.text = "Monto a pagar: " + p.total.soles()
    d.spMetodo.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, METODOS_PAGO)

    fun metodo(): String = METODOS_PAGO[d.spMetodo.selectedItemPosition]

    fun aplicarMetodo() {
        val m = metodo()
        listOf(d.etC1, d.etC2, d.etVence, d.etCvv).forEach { it.text = null }
        listOf(d.tilC1, d.tilC2, d.tilVence, d.tilCvv).forEach { it.error = null }
        val tarjeta = m == "Tarjeta"
        d.tilC2.visibility = if (m == "Transferencia") View.GONE else View.VISIBLE
        d.llTarjeta.visibility = if (tarjeta) View.VISIBLE else View.GONE
        d.etC1.inputType = InputType.TYPE_CLASS_NUMBER
        d.etC2.inputType =
            if (tarjeta) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS else InputType.TYPE_CLASS_NUMBER
        when (m) {
            "Yape", "Plin" -> {
                d.tilC1.hint = "Celular de " + m + " (9 dígitos)"
                d.tilC2.hint = "Código de operación (6 dígitos)"
            }
            "Transferencia" -> d.tilC1.hint = "N° de operación (6 a 12 dígitos)"
            else -> {
                d.tilC1.hint = "N° de tarjeta (16 dígitos)"
                d.tilC2.hint = "Nombre del titular"
            }
        }
    }

    d.spMetodo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) { aplicarMetodo() }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    val dlg = MaterialAlertDialogBuilder(this)
        .setTitle("Pagar pedido #" + p.numero).setView(d.root)
        .setNegativeButton("Cancelar", null).setPositiveButton("Pagar", null).create()
    dlg.show()
    dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val c1 = d.etC1.text.toString()
        val c2 = d.etC2.text.toString()
        val ok = when (metodo()) {
            "Yape", "Plin" -> listOf(d.tilC1.err(Validar.celularPago(c1)), d.tilC2.err(Validar.codigo(c2, 6, 6)))
            "Transferencia" -> listOf(d.tilC1.err(Validar.codigo(c1, 6, 12)))
            else -> listOf(
                d.tilC1.err(Validar.tarjeta(c1)),
                d.tilC2.err(Validar.titular(c2)),
                d.tilVence.err(Validar.vencimiento(d.etVence.text.toString())),
                d.tilCvv.err(Validar.cvv(d.etCvv.text.toString()))
            )
        }.all { it }
        if (!ok) return@setOnClickListener
        val m = metodo()
        dlg.dismiss()
        onPagado(m)
    }
}
