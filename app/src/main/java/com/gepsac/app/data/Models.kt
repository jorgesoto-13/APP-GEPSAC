package com.gepsac.app.data

val ESTADOS = listOf("Por registrar", "Registrado", "Pagado", "En preparación", "Despachado", "Entregado")
const val ESTADO_REGISTRADO = 1
const val ESTADO_PAGADO = 2
val ESTADO_COLORES = listOf(
    0xFF9C5B1E.toInt(), 0xFFB38600.toInt(), 0xFF2F7F86.toInt(),
    0xFF35618F.toInt(), 0xFF6A4C93.toInt(), 0xFF3F6B42.toInt()
)
val MARCAS = listOf("Caffe", "Kaphiy")
val METODOS_PAGO = listOf("Yape", "Plin", "Transferencia", "Tarjeta")

data class Usuario(
    val id: String, val nombre: String, val email: String,
    val telefono: String, val password: String, val esAdmin: Boolean = false
)

data class Producto(
    val id: String, val nombre: String, val marca: String,
    val presentacion: String, val precio: Double, val activo: Boolean = true,
    val imagen: String = ""   // ruta del archivo en almacenamiento interno (vacío = sin imagen)
)

data class ItemPedido(val productoId: String, val nombre: String, val precio: Double, val cantidad: Int)

data class Pedido(
    val id: String, val numero: Long, val clienteId: String, val clienteNombre: String,
    val clienteEmail: String, val telefono: String, val direccion: String,
    val fechaEntrega: String, val obs: String, val items: List<ItemPedido>,
    val total: Double, val estado: Int, val tiempos: List<Long>, val metodoPago: String = ""
)
