package com.gepsac.app.view.seguimiento

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gepsac.app.R
import com.gepsac.app.model.ESTADOS
import com.gepsac.app.model.ESTADO_REGISTRADO
import com.gepsac.app.model.Pedido
import com.gepsac.app.model.ULTIMO_ESTADO
import com.gepsac.app.databinding.ActivityPedidoDetalleBinding
import com.gepsac.app.databinding.ItemEtapaBinding
import com.gepsac.app.di.Fabrica
import com.gepsac.app.view.common.ESTADO_COLORES
import com.gepsac.app.view.common.aparecer
import com.gepsac.app.view.common.emerger
import com.gepsac.app.view.common.fecha
import com.gepsac.app.view.common.fechaHora
import com.gepsac.app.view.common.mostrarProducto
import com.gepsac.app.view.common.soles
import com.gepsac.app.util.observarEvento
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gepsac.app.viewmodel.SeguimientoViewModel

/** Módulo de Seguimiento: línea de tiempo del pedido. El admin avanza/retrocede etapas; el cliente paga. */
class SeguimientoActivity : AppCompatActivity() {
    private lateinit var b: ActivityPedidoDetalleBinding
    private val vm: SeguimientoViewModel by viewModels { Fabrica.seguimiento }
    private var actual: Pedido? = null

    // El selector de la galería se registra aquí (al crear la actividad); el diálogo de pago lo usa por callback.
    private var alElegirImagen: ((Uri) -> Unit)? = null
    private val selectorImagen = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) alElegirImagen?.invoke(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPedidoDetalleBinding.inflate(layoutInflater)
        setContentView(b.root)
        val id = intent.getStringExtra("id")
        if (id == null) { finish(); return }
        vm.cargar(id)

        b.tvVolver.setOnClickListener { finish() }

        b.btnPagar.setOnClickListener {
            val p = actual ?: return@setOnClickListener
            mostrarPago(
                p,
                elegirImagen = { alElegir -> alElegirImagen = alElegir; selectorImagen.launch("image/*") },
                onPagado = { metodo, referencia, comprobante -> vm.pagar(p.id, metodo, referencia, comprobante) }
            )
        }
        b.btnRepetir.setOnClickListener {
            val p = actual ?: return@setOnClickListener
            vm.volverAPedir(p)
        }
        b.btnAvanzar.setOnClickListener {
            val p = actual ?: return@setOnClickListener
            val msg = if (p.estado == 0) "¿Registrar el pedido #" + p.numero + "?"
            else "¿Seguro que quieres pasar el pedido #" + p.numero + " a «" + ESTADOS[p.estado + 1] + "»?"
            confirmar(msg) { vm.avanzar(p.id) }
        }
        b.btnRetroceder.setOnClickListener {
            val p = actual ?: return@setOnClickListener
            confirmar("¿Seguro que quieres retroceder el pedido #" + p.numero + " a «" + ESTADOS[p.estado - 1] +
                "»? Se borrará la fecha de la etapa actual.") { vm.retroceder(p.id) }
        }
        b.btnEliminar.setOnClickListener {
            confirmar("¿Eliminar este pedido? Esta acción no se puede deshacer.") { vm.eliminar(id) }
        }

        // Si el pedido se elimina, Firebase emite null y la pantalla se cierra sola.
        vm.pedido.observe(this) { p ->
            if (p == null) finish() else { actual = p; pintar(p) }
        }
        vm.mensaje.observarEvento(this) { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        // «Volver a pedir»: ya están en el carrito; se regresa a la tienda, donde la pestaña Carrito muestra el globo.
        vm.agregadoAlCarrito.observarEvento(this) { finish() }
    }

    /** Abre la dirección en la app de mapas del teléfono (sin claves de API). */
    private fun abrirMapa(direccion: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(direccion))))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No hay una app de mapas instalada.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Muestra el comprobante a pantalla completa dentro de un diálogo. */
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
            .setPositiveButton("Sí") { _, _ -> onSi() }
            .show()
    }

    private fun pintar(p: Pedido) {
        val admin = vm.esAdmin
        val e = p.estadoSeguro

        b.tvNumero.text = "Pedido #" + p.numero
        b.tvEstado.text = ESTADOS[e]
        b.tvEstado.backgroundTintList = ColorStateList.valueOf(ESTADO_COLORES[e])
        b.tvCliente.text = "Cliente: " + p.clienteNombre
        b.tvContacto.text = "Tel: " + p.telefono + " · " + p.clienteEmail
        // La dirección se subraya y, al tocarla, se abre en la app de mapas.
        b.tvDireccion.text = "Dirección: " + p.direccion + "  (ver en el mapa)"
        b.tvDireccion.paintFlags = b.tvDireccion.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        b.tvDireccion.setOnClickListener { abrirMapa(p.direccion) }
        b.tvEntrega.text = "Entrega solicitada: " + p.fechaEntrega.fecha()
        b.tvPago.text = "Pago: " + p.metodoPago + (p.pago?.referencia?.let { " · " + it } ?: "")
        b.tvPago.visibility = if (p.metodoPago.isEmpty()) View.GONE else View.VISIBLE

        // Captura del comprobante (si el cliente la adjuntó): toca para ampliar.
        val comprobante = p.pago?.comprobante.orEmpty()
        b.imgComprobante.visibility = if (comprobante.isEmpty()) View.GONE else View.VISIBLE
        if (comprobante.isNotEmpty()) {
            b.imgComprobante.mostrarProducto(comprobante)
            b.imgComprobante.setOnClickListener { verComprobante(comprobante) }
        }
        b.tvObs.text = "Observaciones: " + p.obs
        b.tvObs.visibility = if (p.obs.isEmpty()) View.GONE else View.VISIBLE
        b.tvItems.text = p.items.joinToString("\n") { x ->
            x.cantidad.toString() + " × " + x.nombre + "  —  " + (x.precio * x.cantidad).soles()
        }
        b.tvTotal.text = "Total: " + p.total.soles()

        // La animación de entrada solo se hace la primera vez; después, los cambios en vivo no la repiten.
        val animar = b.llTimeline.childCount == 0
        b.llTimeline.removeAllViews()
        ESTADOS.forEachIndexed { i, nombre ->
            val v = ItemEtapaBinding.inflate(layoutInflater, b.llTimeline, false)
            val hecho = i <= e
            v.dot.backgroundTintList = ColorStateList.valueOf(if (hecho) ESTADO_COLORES[ULTIMO_ESTADO] else 0xFFCFCBC2.toInt())
            // Conector hacia la etapa siguiente: verde si esa etapa ya se alcanzó.
            v.linea.visibility = if (i == ESTADOS.lastIndex) View.INVISIBLE else View.VISIBLE
            v.linea.setBackgroundColor(if (i < e) ESTADO_COLORES[ULTIMO_ESTADO] else ContextCompat.getColor(this, R.color.line))
            v.tvEtapa.text = nombre
            v.tvEtapa.alpha = if (hecho) 1f else 0.45f
            v.tvHora.text = if (hecho) p.tiempos.getOrElse(i) { 0L }.fechaHora() else "Pendiente"
            b.llTimeline.addView(v.root)
            if (animar) {
                v.root.aparecer(i * 60L)
                if (i == e) v.dot.emerger(i * 60L + 150L)   // la etapa actual «salta» al aparecer
            }
        }

        // Avisos
        val aviso = when {
            !admin && e == 0 -> "Aún no se ha registrado tu pedido. Cuando el administrador lo registre, podrás realizar el pago."
            !admin && e == ESTADO_REGISTRADO -> "Tu pedido ya fue registrado. Realiza el pago para que empiece la preparación."
            admin && e == ESTADO_REGISTRADO -> "Esperando el pago del cliente. No se puede avanzar hasta que pague."
            else -> null
        }
        b.tvAviso.text = aviso
        b.tvAviso.visibility = if (aviso == null) View.GONE else View.VISIBLE

        // Cliente: pagar; y, si ya recibió el pedido, volver a pedirlo.
        b.btnPagar.visibility = if (!admin && p.puedePagar) View.VISIBLE else View.GONE
        b.btnRepetir.visibility = if (!admin && e == ULTIMO_ESTADO) View.VISIBLE else View.GONE

        // Admin: registrar/avanzar, retroceder, eliminar
        b.btnEliminar.visibility = if (admin && p.puedeEliminar) View.VISIBLE else View.GONE
        b.btnAvanzar.visibility = if (admin && p.puedeAvanzar) View.VISIBLE else View.GONE
        if (p.puedeAvanzar) b.btnAvanzar.text = if (e == 0) "Registrar pedido" else "Pasar a: " + ESTADOS[e + 1]
        b.btnRetroceder.visibility = if (admin && p.puedeRetroceder) View.VISIBLE else View.GONE
        if (p.puedeRetroceder) b.btnRetroceder.text = "Retroceder a: " + ESTADOS[e - 1]
    }
}
