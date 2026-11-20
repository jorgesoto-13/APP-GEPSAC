package com.gepsac.app.view.ventas

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.gepsac.app.R
import com.gepsac.app.databinding.FragmentVentasBinding
import com.gepsac.app.di.Fabrica
import com.gepsac.app.model.ESTADOS
import com.gepsac.app.util.observarEvento
import com.gepsac.app.view.common.ESTADO_COLORES
import com.gepsac.app.view.common.aparecer
import com.gepsac.app.view.common.soles
import com.gepsac.app.viewmodel.ResumenVentas
import com.gepsac.app.viewmodel.VentasViewModel

/** Panel de ventas del administrador: totales, ventas por día, productos más vendidos y pedidos por etapa. */
class VentasFragment : Fragment() {
    private var _b: FragmentVentasBinding? = null
    private val b get() = _b!!
    private val vm: VentasViewModel by viewModels { Fabrica.ventas }
    private var animado = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentVentasBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        vm.resumen.observe(viewLifecycleOwner) { pintar(it) }
        vm.error.observarEvento(viewLifecycleOwner) { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
    }

    private fun pintar(r: ResumenVentas) {
        b.tvHoy.text = r.hoy.soles()
        b.tvSemana.text = r.ultimos7Dias.soles()
        b.tvMes.text = r.mes.soles()
        b.tvPagados.text = r.pedidosPagados.toString()
        b.tvTicket.text = r.ticketPromedio.soles()
        b.tvPorCobrar.text = r.porCobrar.toString()

        pintarDias(r)
        pintarMasVendidos(r)
        pintarEtapas(r)

        // La primera vez, las tres tarjetas de gráficos entran con una animación escalonada.
        if (!animado) {
            animado = true
            listOf(b.llDias, b.llTop, b.llEtapas).forEachIndexed { i, vista ->
                (vista.parent as? View)?.aparecer(120L * (i + 1))
            }
        }
    }

    /** Columnas verticales: la altura de cada una es proporcional a lo vendido ese día. */
    private fun pintarDias(r: ResumenVentas) {
        val ctx = requireContext()
        val densidad = resources.displayMetrics.density
        val altoMax = (110 * densidad).toInt()
        val maximo = r.porDia.maxOfOrNull { it.total } ?: 0.0
        b.llDias.removeAllViews()
        r.porDia.forEach { dia ->
            val columna = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val valor = TextView(ctx).apply {
                text = if (dia.total > 0) String.format("%.0f", dia.total) else ""
                textSize = 10f
                setTextColor(ContextCompat.getColor(ctx, R.color.muted))
                gravity = Gravity.CENTER
            }
            val proporcion = if (maximo > 0) (dia.total / maximo).toFloat() else 0f
            val alto = maxOf((altoMax * proporcion).toInt(), (3 * densidad).toInt())
            val barra = View(ctx).apply {
                setBackgroundColor(ContextCompat.getColor(ctx, if (dia.total > 0) R.color.brown else R.color.line))
                layoutParams = LinearLayout.LayoutParams((22 * densidad).toInt(), alto).apply { topMargin = (4 * densidad).toInt() }
            }
            val etiqueta = TextView(ctx).apply {
                text = dia.etiqueta
                textSize = 11f
                setTextColor(ContextCompat.getColor(ctx, R.color.muted))
                gravity = Gravity.CENTER
                setPadding(0, (6 * densidad).toInt(), 0, 0)
            }
            columna.addView(valor)
            columna.addView(barra)
            columna.addView(etiqueta)
            b.llDias.addView(columna)
        }
    }

    private fun pintarMasVendidos(r: ResumenVentas) {
        b.llTop.removeAllViews()
        if (r.masVendidos.isEmpty()) {
            b.llTop.addView(textoVacio("Aún no hay ventas registradas."))
            return
        }
        val maximo = r.masVendidos.maxOf { it.unidades }
        r.masVendidos.forEach { p ->
            b.llTop.addView(filaBarra(p.nombre, p.unidades.toString() + " u.", p.unidades.toFloat() / maximo, R.color.green))
        }
    }

    private fun pintarEtapas(r: ResumenVentas) {
        b.llEtapas.removeAllViews()
        val maximo = (r.porEtapa.maxOrNull() ?: 0).coerceAtLeast(1)
        ESTADOS.forEachIndexed { i, nombre ->
            val cantidad = r.porEtapa.getOrElse(i) { 0 }
            b.llEtapas.addView(filaBarra(nombre, cantidad.toString(), cantidad.toFloat() / maximo, null, ESTADO_COLORES[i]))
        }
    }

    /** Fila «nombre · barra · valor». El color viene de un recurso o, si no, de un color directo. */
    private fun filaBarra(nombre: String, valor: String, proporcion: Float, colorRes: Int?, colorDirecto: Int? = null): View {
        val ctx = requireContext()
        val densidad = resources.displayMetrics.density
        val color = colorDirecto ?: ContextCompat.getColor(ctx, colorRes ?: R.color.brown)

        val fila = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = (10 * densidad).toInt() }
        }
        val cabecera = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val tvNombre = TextView(ctx).apply {
            text = nombre
            textSize = 13f
            maxLines = 1
            setTextColor(ContextCompat.getColor(ctx, R.color.ink))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvValor = TextView(ctx).apply {
            text = valor
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.muted))
        }
        cabecera.addView(tvNombre)
        cabecera.addView(tvValor)

        // Pista (fondo) y relleno (proporción) con pesos: no hace falta medir la pantalla.
        val pista = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.line))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (8 * densidad).toInt())
                .apply { topMargin = (4 * densidad).toInt() }
        }
        val p = proporcion.coerceIn(0f, 1f)
        pista.addView(View(ctx).apply {
            setBackgroundColor(color)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, p)
        })
        pista.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - p)
        })
        fila.addView(cabecera)
        fila.addView(pista)
        return fila
    }

    private fun textoVacio(msg: String) = TextView(requireContext()).apply {
        text = msg
        textSize = 14f
        setTextColor(ContextCompat.getColor(requireContext(), R.color.muted))
    }

    override fun onDestroyView() { _b = null; super.onDestroyView() }
}
