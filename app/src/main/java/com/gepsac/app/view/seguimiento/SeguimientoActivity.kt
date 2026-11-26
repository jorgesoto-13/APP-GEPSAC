
package com.gepsac.app.view.seguimiento

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gepsac.app.GepsacApp
import com.gepsac.app.R
import com.gepsac.app.databinding.ActivityPedidoDetalleBinding
import com.gepsac.app.databinding.ItemEtapaBinding
import com.gepsac.app.di.Fabrica
import com.gepsac.app.model.ESTADOS
import com.gepsac.app.model.ESTADO_PAGADO
import com.gepsac.app.model.ESTADO_REGISTRADO
import com.gepsac.app.model.PAGO_OBSERVADO
import com.gepsac.app.model.PAGO_PENDIENTE
import com.gepsac.app.model.Pedido
import com.gepsac.app.model.ULTIMO_ESTADO
import com.gepsac.app.util.observarEvento
import com.gepsac.app.view.auth.LoginActivity
import com.gepsac.app.view.common.ESTADO_COLORES
import com.gepsac.app.view.common.aparecer
import com.gepsac.app.view.common.emerger
import com.gepsac.app.view.common.fecha
import com.gepsac.app.view.common.fechaHora
import com.gepsac.app.view.common.mostrarProducto
import com.gepsac.app.view.common.soles
import com.gepsac.app.viewmodel.SeguimientoViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SeguimientoActivity : AppCompatActivity() {

    private lateinit var b: ActivityPedidoDetalleBinding

    private val vm: SeguimientoViewModel by viewModels {
        Fabrica.seguimiento
    }

    private var actual: Pedido? = null

    private var alElegirImagen: ((Uri) -> Unit)? = null

    private val selectorImagen =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (uri != null) {
                alElegirImagen?.invoke(uri)
            }

            alElegirImagen = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        b = ActivityPedidoDetalleBinding.inflate(layoutInflater)
        setContentView(b.root)

        val id = intent.getStringExtra("id")

        if (id == null) {
            finish()
            return
        }

        if (
            (application as GepsacApp)
                .contenedor
                .authRepository
                .usuarioActual == null
        ) {

            startActivity(
                Intent(this, LoginActivity::class.java)
                    .putExtra(LoginActivity.EXTRA_PEDIDO, id)
            )

            finish()
            return
        }

        vm.cargar(id)

        b.tvVolver.setOnClickListener {
            finish()
        }

        // --------------------------------------------------
        // Cliente: pagar o corregir pago observado
        // --------------------------------------------------

        b.btnPagar.setOnClickListener {

            val p = actual ?: return@setOnClickListener

            mostrarPago(
                p,

                elegirImagen = { alElegir ->

                    alElegirImagen = alElegir

                    selectorImagen.launch("image/*")
                },

                onPagado = { metodo, referencia, comprobante ->

                    vm.pagar(
                        p.id,
                        metodo,
                        referencia,
                        comprobante
                    )
                }
            )
        }

        // --------------------------------------------------
        // Administrador: aprobar pago
        // --------------------------------------------------

        b.btnAprobarPago.setOnClickListener {

            val p = actual ?: return@setOnClickListener

            confirmar(
                "¿Confirmas que el comprobante del pedido #${p.numero} es correcto y deseas aprobar el pago?"
            ) {
                vm.aprobarPago(p.id)
            }
        }

        // --------------------------------------------------
        // Administrador: observar pago
        // --------------------------------------------------

        b.btnObservarPago.setOnClickListener {

            val p = actual ?: return@setOnClickListener

            mostrarObservacionPago(p)
        }

        // --------------------------------------------------
        // Cliente: volver a pedir
        // --------------------------------------------------

        b.btnRepetir.setOnClickListener {

            val p = actual ?: return@setOnClickListener

            vm.volverAPedir(p)
        }

        // --------------------------------------------------
        // Administrador: avanzar pedido
        // --------------------------------------------------

        b.btnAvanzar.setOnClickListener {

            val p = actual ?: return@setOnClickListener

            if (!p.puedeAvanzar) {
                return@setOnClickListener
            }

            val siguiente = p.estadoSeguro + 1

            if (siguiente !in ESTADOS.indices) {
                return@setOnClickListener
            }

            val mensaje =
                "¿Seguro que quieres pasar el pedido #${p.numero} a «${ESTADOS[siguiente]}»?"

            confirmar(mensaje) {
                vm.avanzar(p.id)
            }
        }

        // --------------------------------------------------
        // Administrador: retroceder pedido
        // --------------------------------------------------

        b.btnRetroceder.setOnClickListener {

            val p = actual ?: return@setOnClickListener

            if (!p.puedeRetroceder) {
                return@setOnClickListener
            }

            val anterior = p.estadoSeguro - 1

            if (anterior !in ESTADOS.indices) {
                return@setOnClickListener
            }

            confirmar(
                "¿Seguro que quieres retroceder el pedido #${p.numero} a «${ESTADOS[anterior]}»? Se borrará la fecha de la etapa actual."
            ) {
                vm.retroceder(p.id)
            }
        }

        // --------------------------------------------------
        // Administrador: eliminar pedido
        // --------------------------------------------------

        b.btnEliminar.setOnClickListener {

            confirmar(
                "¿Eliminar este pedido? Esta acción no se puede deshacer."
            ) {
                vm.eliminar(id)
            }
        }

        // --------------------------------------------------
        // Escuchar cambios desde Firebase
        // --------------------------------------------------

        vm.pedido.observe(this) { p ->

            if (p == null) {

                finish()

            } else {

                actual = p
                pintar(p)
            }
        }

        vm.mensaje.observarEvento(this) {

            Toast.makeText(
                this,
                it,
                Toast.LENGTH_LONG
            ).show()
        }

        vm.agregadoAlCarrito.observarEvento(this) {
            finish()
        }
    }

    // --------------------------------------------------
    // Abrir ubicación de entrega
    // --------------------------------------------------

    private fun abrirMapa(p: Pedido) {

        val uri = if (p.tieneUbicacion) {

            Uri.parse(
                "geo:${p.latitud},${p.longitud}?q=${p.latitud},${p.longitud}(" +
                        Uri.encode("Entrega pedido #${p.numero}") + ")"
            )

        } else {

            Uri.parse(
                "geo:0,0?q=${Uri.encode(p.direccion)}"
            )
        }

        try {

            startActivity(
                Intent(Intent.ACTION_VIEW, uri)
            )

        } catch (e: ActivityNotFoundException) {

            Toast.makeText(
                this,
                "No hay una app de mapas instalada.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // --------------------------------------------------
    // Ver comprobante
    // --------------------------------------------------

    private fun verComprobante(base64: String) {

        val imagen = ImageView(this).apply {

            adjustViewBounds = true
            setPadding(24, 24, 24, 24)

            mostrarProducto(base64)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Comprobante de pago")
            .setView(imagen)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    // --------------------------------------------------
    // Confirmación general
    // --------------------------------------------------

    private fun confirmar(
        mensaje: String,
        onSi: () -> Unit
    ) {

        MaterialAlertDialogBuilder(this)
            .setMessage(mensaje)
            .setNegativeButton("No", null)
            .setPositiveButton("Sí") { _, _ ->
                onSi()
            }
            .show()
    }

    // --------------------------------------------------
    // Observar pago
    // --------------------------------------------------

    private fun mostrarObservacionPago(p: Pedido) {

        val campo = EditText(this).apply {

            hint = "Ejemplo: El comprobante no corresponde al monto del pedido."

            inputType =
                InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE

            minLines = 3
            maxLines = 5

            setPadding(40, 24, 40, 24)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Observar pago #${p.numero}")
            .setMessage(
                "Indica al cliente qué debe corregir en sus datos o comprobante."
            )
            .setView(campo)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Enviar observación") { _, _ ->

                val motivo =
                    campo.text.toString().trim()

                if (motivo.length < 5) {

                    Toast.makeText(
                        this,
                        "Escribe un motivo de al menos 5 caracteres",
                        Toast.LENGTH_LONG
                    ).show()

                } else {

                    vm.observarPago(
                        p.id,
                        motivo
                    )
                }
            }
            .show()
    }

    // --------------------------------------------------
    // Pintar información del pedido
    // --------------------------------------------------

    private fun pintar(p: Pedido) {

        val admin = vm.esAdmin
        val e = p.estadoSeguro
        val pago = p.pago

        // Información principal.
        b.tvNumero.text =
            "Pedido #${p.numero}"

        b.tvEstado.text =
            ESTADOS[e]

        b.tvEstado.backgroundTintList =
            ColorStateList.valueOf(ESTADO_COLORES[e])

        b.tvCliente.text =
            "Cliente: ${p.clienteNombre}"

        b.tvContacto.text =
            "Tel: ${p.telefono} · ${p.clienteEmail}"

        b.tvDireccion.text =
            "Dirección: ${p.direccion}" +
                    if (p.tieneUbicacion) {
                        "  (ver el punto exacto en el mapa)"
                    } else {
                        "  (ver en el mapa)"
                    }

        b.tvDireccion.paintFlags =
            b.tvDireccion.paintFlags or
                    Paint.UNDERLINE_TEXT_FLAG

        b.tvDireccion.setOnClickListener {
            abrirMapa(p)
        }

        b.tvEntrega.text =
            "Entrega solicitada: ${p.fechaEntrega.fecha()}"

        // --------------------------------------------------
        // Información del pago
        // --------------------------------------------------

        val estadoRevision =
            pago?.estadoRevision.orEmpty()

        b.tvPago.text = buildString {

            append("Método de pago: ${p.metodoPago}")

            pago?.referencia?.let {
                append("\nReferencia: $it")
            }

            if (estadoRevision.isNotEmpty()) {
                append("\nEstado del pago: $estadoRevision")
            }

            pago?.observacionAdmin
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    append("\nMotivo de observación: $it")
                }
        }

        b.tvPago.visibility =
            if (pago == null) {
                View.GONE
            } else {
                View.VISIBLE
            }

        // --------------------------------------------------
        // Comprobante
        // --------------------------------------------------

        val comprobante =
            pago?.comprobante.orEmpty()

        b.imgComprobante.visibility =
            if (comprobante.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        b.tvTituloComprobante.visibility =
            b.imgComprobante.visibility

        if (comprobante.isNotEmpty()) {

            b.imgComprobante.mostrarProducto(
                comprobante
            )

            b.imgComprobante.setOnClickListener {
                verComprobante(comprobante)
            }

        } else {

            b.imgComprobante.setOnClickListener(null)
        }

        // --------------------------------------------------
        // Observaciones generales
        // --------------------------------------------------

        b.tvObs.text =
            "Observaciones: ${p.obs}"

        b.tvObs.visibility =
            if (p.obs.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        // --------------------------------------------------
        // Productos y total
        // --------------------------------------------------

        b.tvItems.text =
            p.items.joinToString("\n") { item ->

                "${item.cantidad} × ${item.nombre} — " +
                        "${(item.precio * item.cantidad).soles()}"
            }

        b.tvTotal.text =
            "Total: ${p.total.soles()}"

        // --------------------------------------------------
        // Línea de tiempo
        // --------------------------------------------------

        val animar =
            b.llTimeline.childCount == 0

        b.llTimeline.removeAllViews()

        ESTADOS.forEachIndexed { i, nombre ->

            val v = ItemEtapaBinding.inflate(
                layoutInflater,
                b.llTimeline,
                false
            )

            val hecho = i <= e

            v.dot.backgroundTintList = ColorStateList.valueOf(
                if (hecho) {
                    ContextCompat.getColor(this, R.color.green)
                } else {
                    0xFFCFCBC2.toInt()
                }
            )

            v.linea.visibility =
                if (i == ESTADOS.lastIndex) {
                    View.INVISIBLE
                } else {
                    View.VISIBLE
                }

            v.linea.setBackgroundColor(
                if (i < e) {
                    ContextCompat.getColor(this, R.color.tracking_olive)
                } else {
                    ContextCompat.getColor(this, R.color.tracking_beige)
                }
            )

            v.tvEtapa.text = nombre

            v.tvEtapa.alpha =
                if (hecho) 1f else 0.45f

            v.tvHora.text =
                if (hecho) {
                    p.tiempos.getOrElse(i) { 0L }.fechaHora()
                } else {
                    "Pendiente"
                }

            v.tvHora.setTextColor(
                ContextCompat.getColor(this, R.color.tracking_beige)
            )

            b.llTimeline.addView(v.root)

            if (animar) {

                v.root.aparecer(i * 60L)

                if (i == e) {
                    v.dot.emerger(i * 60L + 150L)
                }
            }
        }

        // --------------------------------------------------
        // Avisos según estado y rol
        // --------------------------------------------------

        val aviso = when {

            // Cliente: pedido registrado sin pago.
            !admin &&
                    e == ESTADO_REGISTRADO &&
                    pago == null ->

                "Tu pedido está registrado. Completa el pago para que el administrador pueda revisarlo."

            // Cliente: pago pendiente de revisión.
            !admin &&
                    e == ESTADO_REGISTRADO &&
                    estadoRevision == PAGO_PENDIENTE ->

                "Tu comprobante fue enviado correctamente. Estamos esperando que el administrador revise tu pago."

            // Cliente: pago observado.
            !admin &&
                    e == ESTADO_REGISTRADO &&
                    estadoRevision == PAGO_OBSERVADO ->

                "Tu pago fue observado. Revisa el motivo indicado y vuelve a enviar tus datos y comprobante."

            // Cliente: pago aprobado.
            !admin &&
                    e == ESTADO_PAGADO ->

                "Tu pago fue aprobado. Tu pedido está listo para pasar a preparación."

            // Administrador: pago pendiente.
            admin &&
                    e == ESTADO_REGISTRADO &&
                    estadoRevision == PAGO_PENDIENTE ->

                "El cliente ha enviado su comprobante. Revísalo y aprueba el pago o indica qué debe corregir."

            // Administrador: pago observado.
            admin &&
                    e == ESTADO_REGISTRADO &&
                    estadoRevision == PAGO_OBSERVADO ->

                "El pago fue observado. El cliente debe corregir sus datos y enviar un nuevo comprobante."

            // Administrador: todavía no se envió pago.
            admin &&
                    e == ESTADO_REGISTRADO &&
                    pago == null ->

                "El pedido está registrado. Esperando que el cliente complete el pago."

            // Administrador: pago aprobado.
            admin &&
                    e == ESTADO_PAGADO ->

                "El pago fue aprobado. Puedes continuar con la preparación del pedido."

            else -> null
        }

        b.tvAviso.text = aviso

        b.tvAviso.visibility =
            if (aviso == null) {
                View.GONE
            } else {
                View.VISIBLE
            }

        // --------------------------------------------------
        // Botones del cliente
        // --------------------------------------------------

        b.btnPagar.visibility =
            if (!admin && p.puedePagar) {
                View.VISIBLE
            } else {
                View.GONE
            }

        b.btnPagar.text =
            if (estadoRevision == PAGO_OBSERVADO) {
                "Corregir y reenviar comprobante"
            } else {
                "Completar pago"
            }

        b.btnRepetir.visibility =
            if (!admin && e == ULTIMO_ESTADO) {
                View.VISIBLE
            } else {
                View.GONE
            }

        // --------------------------------------------------
        // Botones del administrador
        // --------------------------------------------------

        val pagoPendiente =
            admin && p.puedeRevisarPago

        b.btnAprobarPago.visibility =
            if (pagoPendiente) {
                View.VISIBLE
            } else {
                View.GONE
            }

        b.btnObservarPago.visibility =
            if (pagoPendiente) {
                View.VISIBLE
            } else {
                View.GONE
            }

        b.btnEliminar.visibility =
            if (admin && p.puedeEliminar) {
                View.VISIBLE
            } else {
                View.GONE
            }

        // El pedido ya nace registrado.
        // No existe el botón "Registrar pedido".
        b.btnAvanzar.visibility =
            if (admin && p.puedeAvanzar) {
                View.VISIBLE
            } else {
                View.GONE
            }

        if (p.puedeAvanzar) {

            val siguiente = e + 1

            if (siguiente in ESTADOS.indices) {
                b.btnAvanzar.text =
                    "Pasar a: ${ESTADOS[siguiente]}"
            }
        }

        b.btnRetroceder.visibility =
            if (admin && p.puedeRetroceder) {
                View.VISIBLE
            } else {
                View.GONE
            }

        if (p.puedeRetroceder) {

            val anterior = e - 1

            if (anterior in ESTADOS.indices) {
                b.btnRetroceder.text =
                    "Retroceder a: ${ESTADOS[anterior]}"
            }
        }
    }
}
