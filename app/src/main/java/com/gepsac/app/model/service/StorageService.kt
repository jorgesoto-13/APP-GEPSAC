package com.gepsac.app.model.service

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata

/**
 * Servicio de Firebase Storage: subida y descarga de archivos (imágenes de productos, comprobantes).
 *
 * IMPORTANTE: Storage exige el plan Blaze (de pago) y un bucket creado en la consola. Hoy la app guarda las
 * imágenes en Base64 dentro del documento del producto, así que ningún repositorio usa este servicio todavía.
 * Está listo para cuando el proyecto pase a Blaze.
 */
class StorageService(private val storage: FirebaseStorage) {

    /** Sube [bytes] a [ruta] (por ejemplo `productos/abc.jpg`) y devuelve la URL de descarga. */
    suspend fun subir(ruta: String, bytes: ByteArray, tipo: String = "image/jpeg"): String {
        val ref = storage.reference.child(ruta)
        ref.putBytes(bytes, StorageMetadata.Builder().setContentType(tipo).build()).esperar()
        return ref.downloadUrl.esperar().toString()
    }

    suspend fun eliminar(ruta: String) {
        storage.reference.child(ruta).delete().esperar()
    }
}
