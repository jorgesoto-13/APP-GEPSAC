package com.gepsac.app.view.seguimiento

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.gepsac.app.model.ESTADOS
import com.gepsac.app.model.ESTADO_REGISTRADO
import com.gepsac.app.model.Pedido
import com.gepsac.app.model.ULTIMO_ESTADO
import com.gepsac.app.databinding.ActivityPedidoDetalleBinding
import com.gepsac.app.databinding.ItemEtapaBinding
import com.gepsac.app.di.Fabrica
import com.gepsac.app.view.common.ESTADO_COLORES
import com.gepsac.app.view.common.fecha
import com.gepsac.app.view.common.fechaHora
import com.gepsac.app.view.common.soles
import com.gepsac.app.util.observarEvento
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gepsac.app.viewmodel.SeguimientoViewModel

/** Módulo de Seguimiento: línea de tiempo del pedido. El admin avanza/retrocede etapas; el cliente paga. */
class SeguimientoActivity : AppCompatActivity() {
    private lateinit var b: ActivityPedidoDetalleBinding
    private val vm: SeguimientoViewModel by viewModels { Fabrica.seguimiento }
    private var actual: Pedido? = null

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
            mostrarPago(p) { metodo, referencia -> vm.pagar(p.id, metodo, referencia) }
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
        b.tvDireccion.text = "Dirección: " + p.direccion
        b.tvEntrega.text = "Entrega solicitada: " + p.fechaEntrega.fecha()
        b.tvPago.text = "Pago: " + p.metodoPago
        b.tvPago.visibility = if (p.metodoPago.isEmpty()) View.GONE else View.VISIBLE
        b.tvObs.text = "Observaciones: " + p.obs
        b.tvObs.visibility = if (p.obs.isEmpty()) View.GONE else View.VISIBLE
        b.tvItems.text = p.items.joinToString("\n") { x ->
            x.cantidad.toString() + " × " + x.nombre + "  —  " + (x.precio * x.cantidad).soles()
        }
        b.tvTotal.text = "Total: " + p.total.soles()

        b.llTimeline.removeAllViews()
        ESTADOS.forEachIndexed { i, nombre ->
            val v = ItemEtapaBinding.inflate(layoutInflater, b.llTimeline, false)
            val hecho = i <= e
            v.dot.backgroundTintList = ColorStateList.valueOf(if (hecho) ESTADO_COLORES[ULTIMO_ESTADO] else 0xFFCFCBC2.toInt())
            v.tvEtapa.text = nombre
            v.tvEtapa.alpha = if (hecho) 1f else 0.45f
            v.tvHora.text = if (hecho) p.tiempos.getOrElse(i) { 0L }.fechaHora() else "Pendiente"
            b.llTimeline.addView(v.root)
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

        // Cliente: pagar
        b.btnPagar.visibility = if (!admin && p.puedePagar) View.VISIBLE else View.GONE

        // Admin: registrar/avanzar, retroceder, eliminar
        b.btnEliminar.visibility = if (admin && p.puedeEliminar) View.VISIBLE else View.GONE
        b.btnAvanzar.visibility = if (admin && p.puedeAvanzar) View.VISIBLE else View.GONE
        if (p.puedeAvanzar) b.btnAvanzar.text = if (e == 0) "Registrar pedido" else "Pasar a: " + ESTADOS[e + 1]
        b.btnRetroceder.visibility = if (admin && p.puedeRetroceder) View.VISIBLE else View.GONE
        if (p.puedeRetroceder) b.btnRetroceder.text = "Retroceder a: " + ESTADOS[e - 1]
    }
}
