
package com.gepsac.app.model

/**
 * Estados del pedido.
 *
 * El pedido se registra cuando el cliente confirma
 * el checkout y envía sus datos de pago.
 */
val ESTADOS = listOf(
    "Registrado",
    "Pagado",
    "En preparación",
    "Despachado",
    "Entregado"
)

const val ESTADO_REGISTRADO = 0
const val ESTADO_PAGADO = 1

val ULTIMO_ESTADO = ESTADOS.lastIndex

/**
 * Métodos de pago disponibles en la aplicación.
 *
 * El pago es simulado. No se realiza un cobro real
 * ni se almacenan datos completos de tarjeta.
 */
val METODOS_PAGO = listOf(
    "Yape",
    "Plin",
    "Transferencia",
    "Tarjeta"
)

const val PAGO_PENDIENTE = "Pendiente"
const val PAGO_APROBADO = "Aprobado"
const val PAGO_OBSERVADO = "Observado"

/**
 * Versión del esquema de estados.
 * Se utilizará al guardar nuevos pedidos en Firestore.
 */
const val VERSION_ESTADOS_PEDIDO = 2

/**
 * Línea de un pedido.
 *
 * Se conserva el nombre y precio del producto
 * al momento de realizar la compra.
 */
data class ItemPedido(
    val productoId: String,
    val nombre: String,
    val precio: Double,
    val cantidad: Int,
    val imagen: String = ""
)

/**
 * Información del pago y su revisión administrativa.
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

/**
 * Información completa del pedido.
 */
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

    /** Una posición por etapa. 0L significa que aún no se alcanzó. */
    val tiempos: List<Long>,

    val pago: Pago? = null,
    val creadoPor: String = "",

    /** Coordenadas opcionales del punto de entrega. */
    val latitud: Double? = null,
    val longitud: Double? = null
) {

    val tieneUbicacion: Boolean
        get() = latitud != null && longitud != null

    val estadoSeguro: Int
        get() = estado.coerceIn(0, ULTIMO_ESTADO)

    val metodoPago: String
        get() = pago?.metodo.orEmpty()

    /**
     * El pedido no puede avanzar desde Registrado.
     * Primero debe aprobarse el pago.
     */
    val puedeAvanzar: Boolean
        get() = estado > ESTADO_REGISTRADO &&
                estado < ULTIMO_ESTADO

    /**
     * Se permite retroceder desde preparación o despacho,
     * pero no desde Registrado ni desde Pagado.
     */
    val puedeRetroceder: Boolean
        get() = estado > ESTADO_PAGADO &&
                estado <= ULTIMO_ESTADO

    /**
     * El cliente puede enviar el pago si:
     * - el pedido está Registrado y aún no tiene pago;
     * - o el pago anterior fue observado.
     */
    val puedePagar: Boolean
        get() = estado == ESTADO_REGISTRADO &&
                (
                        pago == null ||
                                pago.estadoRevision == PAGO_OBSERVADO
                        )

    /**
     * El administrador puede revisar un pago pendiente.
     */
    val puedeRevisarPago: Boolean
        get() = estado == ESTADO_REGISTRADO &&
                pago?.estadoRevision == PAGO_PENDIENTE

    /**
     * Solo se puede eliminar un pedido que todavía
     * no haya sido marcado como Pagado.
     */
    val puedeEliminar: Boolean
        get() = estado < ESTADO_PAGADO
}
