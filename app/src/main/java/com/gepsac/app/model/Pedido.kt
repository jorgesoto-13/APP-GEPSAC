
package com.gepsac.app.model

val ESTADOS = listOf("Por registrar", "Registrado", "Pagado", "En preparación", "Despachado", "Entregado")
const val ESTADO_POR_REGISTRAR = 0
const val ESTADO_REGISTRADO = 1
const val ESTADO_PAGADO = 2
val ULTIMO_ESTADO = ESTADOS.size - 1

val METODOS_PAGO = listOf("Yape", "Plin", "Transferencia", "Tarjeta")

const val PAGO_PENDIENTE = "Pendiente"
const val PAGO_APROBADO = "Aprobado"
const val PAGO_OBSERVADO = "Observado"

/** Línea del pedido. Guarda nombre y precio del momento. */
data class ItemPedido(
    val productoId: String,
    val nombre: String,
    val precio: Double,
    val cantidad: Int,
    val imagen: String = ""
)

/**
 * Información del pago y su revisión administrativa.
 * Nunca guarda los datos completos de tarjeta.
 */
data class Pago(
    val metodo: String,
    val monto: Double,
    val referencia: String,
    val fecha: Long,
    val comprobante: String = "",
    val estadoRevision: String = PAGO_PENDIENTE,
    val observacionAdmin: String = "",
    val fechaRevision: Long = 0L
)

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
    /** Una posición por etapa. 0L = etapa aún no alcanzada. */
    val tiempos: List<Long>,
    val pago: Pago? = null,
    val creadoPor: String = "",
    /** Punto de entrega marcado en el mapa (opcional). */
    val latitud: Double? = null,
    val longitud: Double? = null
) {
    val tieneUbicacion: Boolean get() = latitud != null && longitud != null

    val estadoSeguro: Int get() = estado.coerceIn(0, ULTIMO_ESTADO)
    val metodoPago: String get() = pago?.metodo.orEmpty()

    /** Desde "Registrado" no se avanza hasta que el pago sea aprobado. */
    val puedeAvanzar: Boolean
        get() = estado != ESTADO_REGISTRADO && estado < ULTIMO_ESTADO

    /** Desde "Pagado" no se retrocede. */
    val puedeRetroceder: Boolean
        get() = estado > ESTADO_POR_REGISTRAR && estado != ESTADO_PAGADO

    /** El cliente puede pagar si aún no envió el pago o si fue observado. */
    val puedePagar: Boolean
        get() = estado == ESTADO_REGISTRADO &&
                (pago == null || pago.estadoRevision == PAGO_OBSERVADO)

    /** El administrador puede revisar un pago pendiente. */
    val puedeRevisarPago: Boolean
        get() = estado == ESTADO_REGISTRADO &&
                pago?.estadoRevision == PAGO_PENDIENTE

    /** Solo se elimina un pedido que aún no está pagado. */
    val puedeEliminar: Boolean get() = estado < ESTADO_PAGADO
}
