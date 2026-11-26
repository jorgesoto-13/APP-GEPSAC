package com.gepsac.app.model

/**
 * Datos necesarios para crear un pedido.
 * El número, estado y fechas de registro los define Firebase.
 */
data class NuevoPedido(
    val cliente: Usuario,
    val items: List<ItemPedido>,
    val direccion: String,
    val telefono: String,
    val fechaEntrega: Long,
    val observacion: String,
    val latitud: Double? = null,
    val longitud: Double? = null
) {

    val total: Double
        get() = items.sumOf { it.precio * it.cantidad }
}