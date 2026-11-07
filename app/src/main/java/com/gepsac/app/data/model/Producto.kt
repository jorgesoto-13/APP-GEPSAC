package com.gepsac.app.data.model

val MARCAS = listOf("Caffe", "Kaphiy")

data class Producto(
    val id: String,
    val nombre: String,
    val marca: String,
    val presentacion: String,
    val precio: Double,
    val activo: Boolean = true,
    /** Foto reducida en Base64 (JPEG). Vacío = sin imagen. Se guarda en el propio documento de Firestore. */
    val imagen: String = ""
) {
    val nombreCompleto: String get() = (nombre + " " + presentacion).trim()
}
