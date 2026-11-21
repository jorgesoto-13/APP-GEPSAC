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
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) alElegirImagen?.invoke(uri)
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

        if ((application as GepsacApp).contenedor.authRepository.usuarioActual == null) {
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

        // Cliente: pagar o reenviar el comprobante si fue observado.
        b.btnPagar.setOnClickListener {
            val p = actual ?: return@setOnClickListener

            mostrarPago(
                p,
                elegirImagen = { alElegir ->
                    alElegirImagen = alElegir
                    selectorImagen.launch("image/*")
                },
                onPagado = { metodo, referencia, comprobante ->
                    vm.pagar(p.id, metodo, referencia, comprobante)
                }
            )
        }

        // Administrador: aprobar el pago.
        b.btnAprobarPago.setOnClickListener {
            val p = actual ?: return@setOnClickListener

            confirmar(
                "¿Confirmas que el comprobante del pedido #${p.numero} es correcto y deseas aprobar el pago?"
            ) {
                vm.aprobarPago(p.id)
            }
        }

        // Administrador: observar el pago y solicitar una corrección.
        b.btnObservarPago.setOnClickListener {
            val p = actual ?: return@setOnClickListener
            mostrarObservacionPago(p)
        }

        b.btnRepetir.setOnClickListener {
            val p = actual ?: return@setOnClickListener
            vm.volverAPedir(p)
        }

        // Administrador: registrar o avanzar el pedido.
        b.btnAvanzar.setOnClickListener {
            val p = actual ?: return@setOnClickListener

            val msg = if (p.estado == 0) {
                "¿Registrar el pedido #${p.numero}?"
            } else {
                "¿Seguro que quieres pasar el pedido #${p.numero} a «${ESTADOS[p.estado + 1]}»?"
            }

            confirmar(msg) {
                vm.avanzar(p.id)
            }
        }

        b.btnRetroceder.setOnClickListener {
            val p = actual ?: return@setOnClickListener

            confirmar(
                "¿Seguro que quieres retroceder el pedido #${p.numero} a «${ESTADOS[p.estado - 1]}»? Se borrará la fecha de la etapa actual."
            ) {
                vm.retroceder(p.id)
            }
        }

        b.btnEliminar.setOnClickListener {
            confirmar("¿Eliminar este pedido? Esta acción no se puede deshacer.") {
                vm.eliminar(id)
            }
        }

        // Escuchar los cambios del pedido en Firebase.
        vm.pedido.observe(this) { p ->
            if (p == null) {
                finish()
            } else {
                actual = p
                pintar(p)
            }
        }

        vm.mensaje.observarEvento(this) {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
        }

        vm.agregadoAlCarrito.observarEvento(this) {
            finish()
        }
    }

    private fun abrirMapa(p: Pedido) {
        val uri = if (p.tieneUbicacion) {
            Uri.parse(
                "geo:${p.latitud},${p.longitud}?q=${p.latitud},${p.longitud}(" +
                        Uri.encode("Entrega pedido #${p.numero}") + ")"
            )
        } else {
            Uri.parse("geo:0,0?q=${Uri.encode(p.direccion)}")
        }

        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "No hay una app de mapas instalada.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

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

    private fun confirmar(msg: String, onSi: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setMessage(msg)
            .setNegativeButton("No", null)
            .setPositiveButton("Sí") { _, _ ->
                onSi()
            }
            .show()
    }

    private fun mostrarObservacionPago(p: Pedido) {
        val campo = EditText(this).apply {
            hint = "Ejemplo: El comprobante no corresponde al monto del pedido."
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 5
            setPadding(40, 24, 40, 24)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Observar pago #${p.numero}")
            .setMessage("Indica al cliente qué debe corregir en sus datos o comprobante.")
            .setView(campo)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Enviar observación") { _, _ ->
                val motivo = campo.text.toString().trim()

                if (motivo.length < 5) {
                    Toast.makeText(
                        this,
                        "Escribe un motivo de al menos 5 caracteres",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    vm.observarPago(p.id, motivo)
                }
            }
            .show()
    }

    private fun pintar(p: Pedido) {
        val admin = vm.esAdmin
        val e = p.estadoSeguro
        val pago = p.pago

        // Información principal.
        b.tvNumero.text = "Pedido #${p.numero}"
        b.tvEstado.text = ESTADOS[e]
        b.tvEstado.backgroundTintList =
            ColorStateList.valueOf(ESTADO_COLORES[e])

        b.tvCliente.text = "Cliente: ${p.clienteNombre}"
        b.tvContacto.text = "Tel: ${p.telefono} · ${p.clienteEmail}"

        b.tvDireccion.text = "Dirección: ${p.direccion}" +
                if (p.tieneUbicacion) {
                    "  (ver el punto exacto en el mapa)"
                } else {
                    "  (ver en el mapa)"
                }

        b.tvDireccion.paintFlags =
            b.tvDireccion.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        b.tvDireccion.setOnClickListener {
            abrirMapa(p)
        }

        b.tvEntrega.text = "Entrega solicitada: ${p.fechaEntrega.fecha()}"

        // Información del pago: método, referencia, estado y observación.
        val estadoRevision = pago?.estadoRevision.orEmpty()

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
            if (pago == null) View.GONE else View.VISIBLE

        // Comprobante.
        val comprobante = pago?.comprobante.orEmpty()

        b.imgComprobante.visibility =
            if (comprobante.isEmpty()) View.GONE else View.VISIBLE

        b.tvTituloComprobante.visibility = b.imgComprobante.visibility

        if (comprobante.isNotEmpty()) {
            b.imgComprobante.mostrarProducto(comprobante)

            b.imgComprobante.setOnClickListener {
                verComprobante(comprobante)
            }
        } else {
            b.imgComprobante.setOnClickListener(null)
        }

        // Observaciones generales del pedido.
        b.tvObs.text = "Observaciones: ${p.obs}"
        b.tvObs.visibility =
            if (p.obs.isEmpty()) View.GONE else View.VISIBLE

        // Productos y total.
        b.tvItems.text = p.items.joinToString("\n") { item ->
            "${item.cantidad} × ${item.nombre}  —  ${(item.precio * item.cantidad).soles()}"
        }

        b.tvTotal.text = "Total: ${p.total.soles()}"

        // Línea de tiempo.
        val animar = b.llTimeline.childCount == 0
        b.llTimeline.removeAllViews()

        ESTADOS.forEachIndexed { i, nombre ->
            val v = ItemEtapaBinding.inflate(
                layoutInflater,
                b.llTimeline,
                false
            )

            val hecho = i <= e

            v.dot.backgroundTintList = ColorStateList.valueOf(
                if (hecho) ESTADO_COLORES[ULTIMO_ESTADO]
                else 0xFFCFCBC2.toInt()
            )

            v.linea.visibility =
                if (i == ESTADOS.lastIndex) View.INVISIBLE else View.VISIBLE

            v.linea.setBackgroundColor(
                if (i < e) ESTADO_COLORES[ULTIMO_ESTADO]
                else ContextCompat.getColor(this, R.color.line)
            )

            v.tvEtapa.text = nombre
            v.tvEtapa.alpha = if (hecho) 1f else 0.45f

            v.tvHora.text = if (hecho) {
                p.tiempos.getOrElse(i) { 0L }.fechaHora()
            } else {
                "Pendiente"
            }

            b.llTimeline.addView(v.root)

            if (animar) {
                v.root.aparecer(i * 60L)

                if (i == e) {
                    v.dot.emerger(i * 60L + 150L)
                }
            }
        }

        // Avisos para cliente y administrador.
        val aviso = when {
            !admin && e == 0 ->
                "Aún no se ha registrado tu pedido. Cuando el administrador lo registre, podrás realizar el pago."

            !admin && e == ESTADO_REGISTRADO &&
                    estadoRevision == PAGO_PENDIENTE ->
                "Tu comprobante fue enviado correctamente. Estamos esperando que el administrador revise tu pago."

            !admin && e == ESTADO_REGISTRADO &&
                    estadoRevision == PAGO_OBSERVADO ->
                "Tu pago fue observado. Revisa el motivo indicado y vuelve a enviar tus datos y comprobante."

            !admin && e == ESTADO_REGISTRADO && pago == null ->
                "Tu pedido ya fue registrado. Realiza el pago para que empiece la preparación."

            admin && e == ESTADO_REGISTRADO &&
                    estadoRevision == PAGO_PENDIENTE ->
                "El cliente ha enviado su comprobante. Revísalo y aprueba el pago o indica qué debe corregir."

            admin && e == ESTADO_REGISTRADO &&
                    estadoRevision == PAGO_OBSERVADO ->
                "El pago fue observado. El cliente debe corregir sus datos y enviar un nuevo comprobante."

            admin && e == ESTADO_REGISTRADO && pago == null ->
                "Esperando que el cliente registre su pago."

            admin && e == ESTADO_PAGADO ->
                "El pago fue aprobado. Puedes continuar con la preparación del pedido."

            else -> null
        }

        b.tvAviso.text = aviso
        b.tvAviso.visibility =
            if (aviso == null) View.GONE else View.VISIBLE

        // Cliente: pagar o reenviar el pago observado.
        b.btnPagar.visibility =
            if (!admin && p.puedePagar) View.VISIBLE else View.GONE

        b.btnPagar.text =
            if (estadoRevision == PAGO_OBSERVADO) {
                "Corregir y reenviar comprobante"
            } else {
                "Pagar pedido"
            }

        b.btnRepetir.visibility =
            if (!admin && e == ULTIMO_ESTADO) View.VISIBLE else View.GONE

        // Administrador: revisar el pago pendiente.
        val pagoPendiente = admin && p.puedeRevisarPago

        b.btnAprobarPago.visibility =
            if (pagoPendiente) View.VISIBLE else View.GONE

        b.btnObservarPago.visibility =
            if (pagoPendiente) View.VISIBLE else View.GONE

        // Administrador: registrar, avanzar, retroceder o eliminar.
        b.btnEliminar.visibility =
            if (admin && p.puedeEliminar) View.VISIBLE else View.GONE

        b.btnAvanzar.visibility =
            if (admin && p.puedeAvanzar) View.VISIBLE else View.GONE

        if (p.puedeAvanzar) {
            b.btnAvanzar.text = when (e) {
                0 -> "Registrar pedido"
                else -> "Pasar a: ${ESTADOS[e + 1]}"
            }
        }

        b.btnRetroceder.visibility =
            if (admin && p.puedeRetroceder) View.VISIBLE else View.GONE

        if (p.puedeRetroceder) {
            b.btnRetroceder.text = "Retroceder a: ${ESTADOS[e - 1]}"
        }
    }
}