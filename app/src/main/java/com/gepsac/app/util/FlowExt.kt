
package com.gepsac.app.util

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Si el flujo falla, avisa con el mensaje y emite valorVacio.
 * No muestra errores de permisos cuando la sesión ya está cerrada.
 */
fun <T> Flow<T>.alFallar(
    valorVacio: T,
    aviso: (String) -> Unit
): Flow<T> = catch { e ->

    val errorPermiso = generateSequence(e) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .any { it.code == FirebaseFirestoreException.Code.PERMISSION_DENIED }

    val sesionCerrada = FirebaseAuth.getInstance().currentUser == null

    if (!(errorPermiso && sesionCerrada)) {
        aviso(e.message ?: "No se pudieron cargar los datos")
    }

    emit(valorVacio)
}
