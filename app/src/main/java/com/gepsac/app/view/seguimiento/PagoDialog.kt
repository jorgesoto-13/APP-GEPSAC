
package com.gepsac.app.view.seguimiento

import android.net.Uri
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.gepsac.app.R
import com.gepsac.app.databinding.DialogPagoBinding
import com.gepsac.app.model.METODOS_PAGO
import com.gepsac.app.model.Pedido
import com.gepsac.app.util.Validar
import com.gepsac.app.view.common.Imagenes
import com.gepsac.app.view.common.err
import com.gepsac.app.view.common.mostrarProducto
import com.gepsac.app.view.common.soles
import com.gepsac.app.view.common.soloDigitos
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Diálogo de pago simulado.
 *
 * Métodos disponibles:
 * - Yape
 * - Plin
 * - Transferencia
 * - Tarjeta (simulada)
 *
 * No se solicitan ni almacenan datos reales de tarjetas.
 *
 * Para Yape, Plin y Transferencia se solicita una captura
 * del comprobante que revisará el administrador.
 */

// --------------------------------------------------
// Versión utilizada desde Seguimiento
// --------------------------------------------------

fun AppCompatActivity.mostrarPago(
    p: Pedido,
    elegirImagen: (alElegir: (Uri) -> Unit) -> Unit,
    onPagado: (
        metodo: String,
        referencia: String,
        comprobante: String
    ) -> Unit
) {
    mostrarPagoInterno(
        monto = p.total,
        titulo = "Pagar pedido #${p.numero}",
        elegirImagen = elegirImagen,
        onPagado = onPagado
    )
}

// --------------------------------------------------
// Versión utilizada desde Checkout
// --------------------------------------------------

fun AppCompatActivity.mostrarPago(
    monto: Double,
    elegirImagen: (alElegir: (Uri) -> Unit) -> Unit,
    onPagado: (
        metodo: String,
        referencia: String,
        comprobante: String
    ) -> Unit
) {
    mostrarPagoInterno(
        monto = monto,
        titulo = "Completar pago",
        elegirImagen = elegirImagen,
        onPagado = onPagado
    )
}

// --------------------------------------------------
// Implementación compartida
// --------------------------------------------------

private fun AppCompatActivity.mostrarPagoInterno(
    monto: Double,
    titulo: String,
    elegirImagen: (alElegir: (Uri) -> Unit) -> Unit,
    onPagado: (
        metodo: String,
        referencia: String,
        comprobante: String
    ) -> Unit
) {

    val d = DialogPagoBinding.inflate(layoutInflater)

    d.tvMonto.text = "Monto a pagar: ${monto.soles()}"

    d.spMetodo.adapter = ArrayAdapter(
        this,
        android.R.layout.simple_spinner_dropdown_item,
        METODOS_PAGO
    )

    var comprobante = ""

    fun metodo(): String {
        val posicion = d.spMetodo.selectedItemPosition
        return METODOS_PAGO.getOrElse(posicion) {
            METODOS_PAGO.first()
        }
    }

    fun esTarjeta(): Boolean =
        metodo() == "Tarjeta"

    fun esBilletera(): Boolean =
        metodo() == "Yape" || metodo() == "Plin"

    // --------------------------------------------------
    // Validación visual del celular
    // --------------------------------------------------

    d.etC1.doAfterTextChanged { texto ->

        val valor = texto?.toString().orEmpty()

        if (esBilletera()) {

            d.tilC1.error =
                if (valor.isNotEmpty() && valor[0] != '9') {
                    "Debe empezar con 9"
                } else {
                    null
                }
        }
    }

    // --------------------------------------------------
    // Cambiar campos según método
    // --------------------------------------------------

    fun aplicarMetodo() {

        val seleccionado = metodo()

        d.etC1.text = null
        d.etC2.text = null
        d.etVence.text = null
        d.etCvv.text = null

        d.tilC1.error = null
        d.tilC2.error = null
        d.tilVence.error = null
        d.tilCvv.error = null

        d.tvErrorComprobante.visibility = View.GONE

        val tarjeta = seleccionado == "Tarjeta"
        val transferencia = seleccionado == "Transferencia"

        // Los campos de tarjeta no se utilizan.
        // No se solicitan números reales ni CVV.
        d.llTarjeta.visibility = View.GONE

        d.tilC1.visibility =
            if (tarjeta) View.GONE else View.VISIBLE

        d.tilC2.visibility =
            if (tarjeta || transferencia) {
                View.GONE
            } else {
                View.VISIBLE
            }

        d.tilVence.visibility = View.GONE
        d.tilCvv.visibility = View.GONE

        // Reiniciar comprobante al cambiar de método.
        comprobante = ""

        d.imgComprobante.visibility = View.GONE

        d.btnComprobante.text =
            "Adjuntar captura del comprobante"

        d.btnComprobante.visibility =
            if (tarjeta) View.GONE else View.VISIBLE

        d.tvAyudaComprobante.visibility =
            View.VISIBLE

        when (seleccionado) {

            "Yape", "Plin" -> {

                d.tvAyudaComprobante.text =
                    "Ingresa el celular utilizado y el código de operación. Adjunta la captura del comprobante."

                d.etC1.inputType =
                    InputType.TYPE_CLASS_NUMBER

                d.etC1.soloDigitos(9)
                d.etC2.soloDigitos(3)

                d.etC2.inputType =
                    InputType.TYPE_CLASS_NUMBER

                d.tilC1.hint =
                    "Celular de $seleccionado (9 dígitos)"

                d.tilC2.hint =
                    "Código de operación (3 dígitos)"
            }

            "Transferencia" -> {

                d.tvAyudaComprobante.text =
                    "Ingresa el número de operación de tu transferencia y adjunta la captura del comprobante."

                d.etC1.inputType =
                    InputType.TYPE_CLASS_NUMBER

                d.etC1.soloDigitos(12)

                d.tilC1.hint =
                    "N° de operación (6 a 12 dígitos)"
            }

            "Tarjeta" -> {

                d.tvAyudaComprobante.text =
                    "Esta opción es una demostración académica. No se realizará ningún cobro ni debes ingresar datos de una tarjeta real."
            }
        }
    }

    // --------------------------------------------------
    // Seleccionar comprobante
    // --------------------------------------------------

    d.btnComprobante.setOnClickListener {

        elegirImagen { uri ->

            val base64 =
                Imagenes.codificarComprobante(this, uri)

            if (base64 == null) {

                Toast.makeText(
                    this,
                    "No se pudo usar esa imagen. Prueba con otra captura.",
                    Toast.LENGTH_LONG
                ).show()

            } else {

                comprobante = base64

                d.tvErrorComprobante.visibility =
                    View.GONE

                d.imgComprobante.visibility =
                    View.VISIBLE

                d.imgComprobante.mostrarProducto(base64)

                d.btnComprobante.text =
                    "Cambiar captura"
            }
        }
    }

    // --------------------------------------------------
    // Escuchar cambio de método
    // --------------------------------------------------

    d.spMetodo.onItemSelectedListener =
        object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                aplicarMetodo()
            }

            override fun onNothingSelected(
                parent: AdapterView<*>?
            ) {
            }
        }

    // --------------------------------------------------
    // Crear diálogo
    // --------------------------------------------------

    val dialogo = MaterialAlertDialogBuilder(this)
        .setTitle(titulo)
        .setView(d.root)
        .setNegativeButton("Cancelar", null)
        .setPositiveButton("Confirmar pago", null)
        .create()

    dialogo.show()

    dialogo.getButton(
        AlertDialog.BUTTON_POSITIVE
    ).setOnClickListener {

        val c1 = d.etC1.text.toString().trim()
        val c2 = d.etC2.text.toString().trim()

        val seleccionado = metodo()

        val valido = when (seleccionado) {

            "Yape", "Plin" -> {

                listOf(
                    d.tilC1.err(
                        Validar.celularPago(c1)
                    ),

                    d.tilC2.err(
                        Validar.codigo(c2, 3, 3)
                    )
                ).all { it }
            }

            "Transferencia" -> {

                d.tilC1.err(
                    Validar.codigo(c1, 6, 12)
                )
            }

            "Tarjeta" -> {

                // Pago ficticio para la demostración.
                // No se recopilan datos bancarios.
                true
            }

            else -> false
        }

        val faltaComprobante =
            seleccionado != "Tarjeta" &&
                    comprobante.isEmpty()

        d.tvErrorComprobante.visibility =
            if (faltaComprobante) {
                View.VISIBLE
            } else {
                View.GONE
            }

        if (!valido || faltaComprobante) {
            return@setOnClickListener
        }

        val referencia = when (seleccionado) {

            "Yape", "Plin" -> c2

            "Transferencia" -> c1

            "Tarjeta" -> "SIMULADO-TARJETA"

            else -> ""
        }

        dialogo.dismiss()

        onPagado(
            seleccionado,
            referencia,
            if (seleccionado == "Tarjeta") {
                ""
            } else {
                comprobante
            }
        )
    }
}
