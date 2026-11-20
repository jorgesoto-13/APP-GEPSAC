package com.gepsac.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.gepsac.app.model.ESTADOS
import com.gepsac.app.model.ESTADO_PAGADO
import com.gepsac.app.model.ESTADO_REGISTRADO
import com.gepsac.app.model.Pedido
import com.gepsac.app.model.repository.PedidoRepository
import com.gepsac.app.util.Evento
import com.gepsac.app.util.alFallar
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Locale

data class DiaVenta(val etiqueta: String, val total: Double)
data class ProductoVendido(val nombre: String, val unidades: Int)

/** Todo lo que muestra el panel de ventas. Una «venta» es un pedido ya pagado (etapa Pagado o posterior). */
data class ResumenVentas(
    val hoy: Double,
    val ultimos7Dias: Double,
    val mes: Double,
    val pedidosPagados: Int,
    val ticketPromedio: Double,
    /** Pedidos registrados que esperan el pago del cliente. */
    val porCobrar: Int,
    val porDia: List<DiaVenta>,
    val masVendidos: List<ProductoVendido>,
    /** Cantidad de pedidos en cada etapa (mismo orden que [ESTADOS]). */
    val porEtapa: List<Int>
)

/** Panel de ventas del administrador, calculado en tiempo real con los pedidos. */
class VentasViewModel(pedidos: PedidoRepository) : ViewModel() {
    private val _error = MutableLiveData<Evento<String>>()
    val error: LiveData<Evento<String>> = _error

    val resumen: LiveData<ResumenVentas> = pedidos.observarTodos()
        .map { calcular(it) }
        .alFallar(calcular(emptyList())) { _error.value = Evento(it) }
        .asLiveData()

    private fun calcular(todos: List<Pedido>): ResumenVentas {
        val ventas = todos.filter { it.estado >= ESTADO_PAGADO }

        val inicioHoy = inicioDelDia(System.currentTimeMillis())
        val hace6Dias = diasAtras(inicioHoy, 6)
        val inicioMes = Calendar.getInstance().apply {
            timeInMillis = inicioHoy
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

        fun suma(desde: Long) = ventas.filter { fechaVenta(it) >= desde }.sumOf { it.total }

        val porDia = (6 downTo 0).map { atras ->
            val ini = diasAtras(inicioHoy, atras)
            val fin = diasAtras(inicioHoy, atras - 1)
            DiaVenta(nombreDia(ini), ventas.filter { fechaVenta(it) in ini until fin }.sumOf { it.total })
        }

        val masVendidos = ventas.flatMap { it.items }
            .groupBy { it.nombre }
            .map { (nombre, filas) -> ProductoVendido(nombre, filas.sumOf { it.cantidad }) }
            .sortedByDescending { it.unidades }
            .take(5)

        val porEtapa = ESTADOS.indices.map { e -> todos.count { it.estadoSeguro == e } }
        val totalVentas = ventas.sumOf { it.total }

        return ResumenVentas(
            hoy = suma(inicioHoy),
            ultimos7Dias = suma(hace6Dias),
            mes = suma(inicioMes),
            pedidosPagados = ventas.size,
            ticketPromedio = if (ventas.isEmpty()) 0.0 else totalVentas / ventas.size,
            porCobrar = todos.count { it.estado == ESTADO_REGISTRADO },
            porDia = porDia,
            masVendidos = masVendidos,
            porEtapa = porEtapa
        )
    }

    /** Momento del pago; si falta, el de la etapa «Pagado», y como último recurso el de creación. */
    private fun fechaVenta(p: Pedido): Long {
        val pago = p.pago?.fecha ?: 0L
        if (pago > 0) return pago
        val etapa = p.tiempos.getOrElse(ESTADO_PAGADO) { 0L }
        return if (etapa > 0) etapa else p.tiempos.firstOrNull() ?: 0L
    }

    private fun inicioDelDia(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Inicio del día que está [n] días antes (o después si n es negativo) de [inicio]. */
    private fun diasAtras(inicio: Long, n: Int): Long = Calendar.getInstance().apply {
        timeInMillis = inicio
        add(Calendar.DAY_OF_YEAR, -n)
    }.timeInMillis

    private fun nombreDia(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale("es", "PE"))
            ?.take(3)?.replaceFirstChar { it.uppercase() } ?: ""
    }
}
