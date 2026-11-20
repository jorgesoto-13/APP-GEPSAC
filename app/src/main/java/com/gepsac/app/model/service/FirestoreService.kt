package com.gepsac.app.model.service

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Servicio de Cloud Firestore: lectura y escritura de colecciones. Es lo único que toca `FirebaseFirestore`.
 * No sabe qué es un pedido o un producto; recibe el nombre de la colección y los datos ya armados.
 */
class FirestoreService(private val db: FirebaseFirestore) {

    fun documento(coleccion: String, id: String): DocumentReference = db.collection(coleccion).document(id)

    /** Referencia a un documento nuevo con id generado por Firestore (todavía no se escribe nada). */
    fun documentoNuevo(coleccion: String): DocumentReference = db.collection(coleccion).document()

    suspend fun obtener(coleccion: String, id: String): DocumentSnapshot =
        documento(coleccion, id).get().esperar()

    suspend fun guardar(coleccion: String, id: String, datos: Map<String, Any?>) {
        documento(coleccion, id).set(datos).esperar()
    }

    /** Cambia solo los campos indicados (el resto del documento queda igual). */
    suspend fun actualizar(coleccion: String, id: String, datos: Map<String, Any?>) {
        documento(coleccion, id).update(datos).esperar()
    }

    suspend fun eliminar(coleccion: String, id: String) {
        documento(coleccion, id).delete().esperar()
    }

    /** Escucha una colección en tiempo real; [filtro] permite agregar condiciones a la consulta. */
    fun observarColeccion(coleccion: String, filtro: (Query) -> Query = { it }): Flow<QuerySnapshot> =
        filtro(db.collection(coleccion)).cambios()

    fun observarDocumento(coleccion: String, id: String): Flow<DocumentSnapshot> =
        documento(coleccion, id).cambios()

    /** Ejecuta varias lecturas y escrituras como una sola operación: o se aplican todas o ninguna. */
    suspend fun <T> transaccion(bloque: (Transaction) -> T): T =
        db.runTransaction { tx -> bloque(tx) }.esperar()
}
