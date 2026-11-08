package com.gepsac.app.model

val ESTADOS = listOf("Por registrar", "Registrado", "Pagado", "En preparación", "Despachado", "Entregado")
const val ESTADO_POR_REGISTRAR = 0
const val ESTADO_REGISTRADO = 1
const val ESTADO_PAGADO = 2
val ULTIMO_ESTADO = ESTADOS.size - 1

val METODOS_PAGO = listOf("Yape", "Plin", "Transferencia", "Tarjeta")

/** Línea del pedido. Guarda nombre y precio del momento: si luego cambia el producto, el pedido no se altera. */
data class ItemPedido(val productoId: String, val nombre: String, val precio: Double, val cantidad: Int)

/** Pago (simulado). Nunca incluye datos de tarjeta: solo método, monto y una referencia. */
data class Pago(val metodo: String, val monto: Double, val referencia: String, val fecha: Long)

data class Pedido(
    val id: String,
    val numero: Long,
    val clienteId: String,
    val clienteNombre: String,
    val clienteEmail: String,
    val telefono: String,
    val direccion: String,
    /** Fecha de entrega solicitada, en milisegundos. */
    val fechaEntrega: Long,
    val obs: String,
    val items: List<ItemPedido>,
    val total: Double,
    val estado: Int,
    /** Una posición por etapa (mismo orden que [ESTADOS]). 0L = etapa aún no alcanzada. */
    val tiempos: List<Long>,
    val pago: Pago? = null,
    val creadoPor: String = ""
) {
    val estadoSeguro: Int get() = estado.coerceIn(0, ULTIMO_ESTADO)
    val metodoPago: String get() = pago?.metodo.orEmpty()

    // ---- Reglas del flujo (las mismas que aplican las reglas de seguridad de Firestore) ----

    /** Desde "Registrado" no se avanza: hay que esperar el pago del cliente. */
    val puedeAvanzar: Boolean get() = estado != ESTADO_REGISTRADO && estado < ULTIMO_ESTADO

    /** Desde "Pagado" no se retrocede (anularía el pago). */
    val puedeRetroceder: Boolean get() = estado > ESTADO_POR_REGISTRAR && estado != ESTADO_PAGADO

    val puedePagar: Boolean get() = estado == ESTADO_REGISTRADO

    /** Solo se elimina un pedido que aún no se pagó. */
    val puedeEliminar: Boolean get() = estado < ESTADO_PAGADO
}
