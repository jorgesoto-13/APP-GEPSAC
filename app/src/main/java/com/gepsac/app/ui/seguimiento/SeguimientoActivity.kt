package com.gepsac.app.ui.seguimiento

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.gepsac.app.data.ESTADOS
import com.gepsac.app.data.ESTADO_COLORES
import com.gepsac.app.data.ESTADO_PAGADO
import com.gepsac.app.data.ESTADO_REGISTRADO
import com.gepsac.app.data.Pedido
import com.gepsac.app.databinding.ActivityPedidoDetalleBinding
import com.gepsac.app.databinding.ItemEtapaBinding
import com.gepsac.app.ui.fechaHora
import com.gepsac.app.ui.soles
import com.gepsac.app.viewmodel.TiendaViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Módulo de Seguimiento: línea de tiempo del pedido. El admin avanza/retrocede etapas; el cliente paga. */
class SeguimientoActivity : AppCompatActivity() {
    private lateinit var b: ActivityPedidoDetalleBinding
    private val vm: TiendaViewModel by viewModels()
    private var actual: Pedido? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPedidoDetalleBinding.inflate(layoutInflater)
        setContentView(b.root)
        val id = intent.getStringExtra("id")
        if (id == null) { finish(); return }

        b.tvVolver.setOnClickListener { finish() }

        b.btnPagar.setOnClickListener {
            val p = actual ?: return@setOnClickListener
            mostrarPago(p) { metodo ->
                vm.pagar(p.id, metodo)
                Toast.makeText(this, "Pago recibido con " + metodo + ". ¡Gracias!", Toast.LENGTH_LONG).show()
            }
        }
        b.btnAvanzar.setOnClickListener {
            val p = actual ?: return@setOnClickListener
            val msg = if (p.estado == 0) "¿Registrar el pedido #" + p.numero + "?"
            else "¿Seguro que quieres pasar el pedido #" + p.numero + " a «" + ESTADOS[p.estado + 1] + "»?"
            confirmar(msg) { vm.avanzarEstado(p.id) }
        }
        b.btnRetroceder.setOnClickListener {
            val p = actual ?: return@setOnClickListener
            confirmar("¿Seguro que quieres retroceder el pedido #" + p.numero + " a «" + ESTADOS[p.estado - 1] +
                "»? Se borrará la fecha de la etapa actual.") { vm.retrocederEstado(p.id) }
        }
        b.btnEliminar.setOnClickListener {
            confirmar("¿Eliminar este pedido? Esta acción no se puede deshacer.") {
                if (!vm.eliminarPedido(id))
                    Toast.makeText(this, "No se puede eliminar un pedido ya pagado", Toast.LENGTH_LONG).show()
            }
        }

        vm.pedido(id).observe(this) { p ->
            if (p == null) finish() else { actual = p; pintar(p) }
        }
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
        val e = p.estado.coerceIn(0, ESTADOS.size - 1)
        val ultimo = ESTADOS.size - 1

        b.tvNumero.text = "Pedido #" + p.numero
        b.tvEstado.text = ESTADOS[e]
        b.tvEstado.backgroundTintList = ColorStateList.valueOf(ESTADO_COLORES[e])
        b.tvCliente.text = "Cliente: " + p.clienteNombre
        b.tvContacto.text = "Tel: " + p.telefono + " · " + p.clienteEmail
        b.tvDireccion.text = "Dirección: " + p.direccion
        b.tvEntrega.text = "Entrega solicitada: " + p.fechaEntrega
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
            v.dot.backgroundTintList = ColorStateList.valueOf(if (hecho) ESTADO_COLORES[ultimo] else 0xFFCFCBC2.toInt())
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
        b.btnPagar.visibility = if (!admin && e == ESTADO_REGISTRADO) View.VISIBLE else View.GONE

        // Admin: registrar/avanzar, retroceder, eliminar
        b.btnEliminar.visibility = if (admin && e < ESTADO_PAGADO) View.VISIBLE else View.GONE
        val puedeAvanzar = admin && e != ESTADO_REGISTRADO && e < ultimo
        val puedeRetroceder = admin && e > 0 && e != ESTADO_PAGADO
        b.btnAvanzar.visibility = if (puedeAvanzar) View.VISIBLE else View.GONE
        if (puedeAvanzar) b.btnAvanzar.text = if (e == 0) "Registrar pedido" else "Pasar a: " + ESTADOS[e + 1]
        b.btnRetroceder.visibility = if (puedeRetroceder) View.VISIBLE else View.GONE
        if (puedeRetroceder) b.btnRetroceder.text = "Retroceder a: " + ESTADOS[e - 1]
    }
}
