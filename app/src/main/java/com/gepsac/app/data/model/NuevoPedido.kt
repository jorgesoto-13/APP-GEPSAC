package com.gepsac.app.data.model

/** Datos que hacen falta para crear un pedido. El número, las fechas y el estado los define el repositorio. */
data class NuevoPedido(
    val cliente: Usuario,
    val items: List<ItemPedido>,
    val direccion: String,
    val telefono: String,
    /** Fecha de entrega solicitada, en milisegundos. */
    val fechaEntrega: Long,
    val observacion: String
) {
    val total: Double get() = items.sumOf { it.precio * it.cantidad }
}
