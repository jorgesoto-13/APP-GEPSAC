package com.gepsac.app.view.seguimiento

import android.net.Uri
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.gepsac.app.model.METODOS_PAGO
import com.gepsac.app.model.Pedido
import com.gepsac.app.databinding.DialogPagoBinding
import com.gepsac.app.view.common.Imagenes
import com.gepsac.app.view.common.err
import com.gepsac.app.view.common.formatoTarjeta
import com.gepsac.app.view.common.formatoVencimiento
import com.gepsac.app.view.common.limitar
import com.gepsac.app.view.common.mostrarProducto
import com.gepsac.app.view.common.nombrePersona
import com.gepsac.app.view.common.soles
import com.gepsac.app.view.common.soloDigitos
import com.gepsac.app.util.Validar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Pago SIMULADO (no cobra nada). Entrega el método, una referencia y la captura del comprobante.
 *  - Yape / Plin / Transferencia: la captura del pago es OBLIGATORIA. Se guarda gratis dentro del pedido y es lo
 *    que el administrador revisa antes de seguir con la preparación.
 *  - Tarjeta: no lleva comprobante; solo se guardan los últimos 4 dígitos. Los datos completos nunca salen de aquí.
 *
 * [elegirImagen] abre la galería y devuelve la foto elegida por el callback que recibe; lo provee la pantalla
 * porque el selector de la galería debe registrarse al crear la actividad, no al abrir el diálogo.
 */
fun AppCompatActivity.mostrarPago(
    p: Pedido,
    elegirImagen: (alElegir: (Uri) -> Unit) -> Unit,
    onPagado: (metodo: String, referencia: String, comprobante: String) -> Unit
) {
    val d = DialogPagoBinding.inflate(layoutInflater)
    d.tvMonto.text = "Monto a pagar: " + p.total.soles()
    d.spMetodo.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, METODOS_PAGO)
    var comprobante = ""

    fun metodo(): String = METODOS_PAGO[d.spMetodo.selectedItemPosition]
    fun esTarjeta() = metodo() == "Tarjeta"
    fun esBilletera() = metodo() == "Yape" || metodo() == "Plin"

    // Observadores permanentes: solo actúan cuando el método elegido corresponde.
    d.etC1.formatoTarjeta { esTarjeta() }
    d.etVence.formatoVencimiento { esTarjeta() }
    d.etC1.doAfterTextChanged { t ->
        // Yape/Plin: el celular debe empezar con 9 (se avisa apenas se escribe).
        val s = t?.toString().orEmpty()
        if (esBilletera()) d.tilC1.error = if (s.isNotEmpty() && s[0] != '9') "Debe empezar con 9" else null
    }

    fun aplicarMetodo() {
        val m = metodo()
        listOf(d.etC1, d.etC2, d.etVence, d.etCvv).forEach { it.text = null }
        listOf(d.tilC1, d.tilC2, d.tilVence, d.tilCvv).forEach { it.error = null }
        d.tvErrorComprobante.visibility = View.GONE
        val tarjeta = m == "Tarjeta"
        d.tilC2.visibility = if (m == "Transferencia") View.GONE else View.VISIBLE
        d.llTarjeta.visibility = if (tarjeta) View.VISIBLE else View.GONE

        // El comprobante aplica a Yape, Plin y transferencia; con tarjeta no hay.
        comprobante = ""
        d.imgComprobante.visibility = View.GONE
        d.btnComprobante.text = "Adjuntar captura del comprobante"
        d.btnComprobante.visibility = if (tarjeta) View.GONE else View.VISIBLE
        d.tvAyudaComprobante.visibility = if (tarjeta) View.GONE else View.VISIBLE

        // Qué se puede escribir en cada campo según el método.
        d.etC1.inputType = InputType.TYPE_CLASS_NUMBER
        when (m) {
            "Yape", "Plin" -> {
                d.etC1.soloDigitos(9)
                d.etC2.soloDigitos(6)
                d.etC2.inputType = InputType.TYPE_CLASS_NUMBER
                d.tilC1.hint = "Celular de " + m + " (9 dígitos)"
                d.tilC2.hint = "Código de operación (6 dígitos)"
            }
            "Transferencia" -> {
                d.etC1.soloDigitos(12)
                d.tilC1.hint = "N° de operación (6 a 12 dígitos)"
            }
            else -> {
                d.etC1.limitar(19)              // el formato en grupos de 4 lo pone el observador
                d.etC1.keyListener = DigitsKeyListener.getInstance("0123456789 ")   // deja pasar los espacios del formato
                d.etC2.nombrePersona(40)
                d.etC2.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                d.etVence.limitar(5)
                d.etCvv.soloDigitos(3)
                d.tilC1.hint = "N° de tarjeta (16 dígitos)"
                d.tilC2.hint = "Nombre del titular"
            }
        }
    }

    d.btnComprobante.setOnClickListener {
        elegirImagen { uri ->
            val base64 = Imagenes.codificarComprobante(this, uri)
            if (base64 == null) {
                Toast.makeText(this, "No se pudo usar esa imagen. Prueba con otra captura.", Toast.LENGTH_LONG).show()
            } else {
                comprobante = base64
                d.tvErrorComprobante.visibility = View.GONE
                d.imgComprobante.visibility = View.VISIBLE
                d.imgComprobante.mostrarProducto(base64)
                d.btnComprobante.text = "Cambiar captura"
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

        // La captura del pago es obligatoria salvo con tarjeta.
        val faltaComprobante = !esTarjeta() && comprobante.isEmpty()
        d.tvErrorComprobante.visibility = if (faltaComprobante) View.VISIBLE else View.GONE

        if (!ok || faltaComprobante) return@setOnClickListener
        val m = metodo()
        val referencia = when (m) {
            "Yape", "Plin" -> c2.trim()
            "Transferencia" -> c1.trim()
            else -> "Tarjeta ****" + c1.filter { it.isDigit() }.takeLast(4)
        }
        dlg.dismiss()
        onPagado(m, referencia, if (m == "Tarjeta") "" else comprobante)
    }
}
