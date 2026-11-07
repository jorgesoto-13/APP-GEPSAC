package com.gepsac.app.data.firebase

import com.gepsac.app.data.repository.ErrorApp
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private const val TIEMPO_ESPERA_MS = 15_000L

/**
 * Espera una operación de Firebase. Sin internet, Firestore deja las escrituras pendientes sin terminar nunca;
 * el tiempo máximo evita que la pantalla se quede cargando para siempre.
 */
internal suspend fun <T> Task<T>.esperar(): T =
    try {
        withTimeout(TIEMPO_ESPERA_MS) { await() }
    } catch (e: TimeoutCancellationException) {
        throw ErrorApp("Sin conexión. Revisa tu internet e inténtalo de nuevo.", e)
    }

/** Ejecuta [bloque] y devuelve éxito o un [ErrorApp] con mensaje amigable. */
internal suspend fun <T> intentar(bloque: suspend () -> T): Result<T> =
    try {
        Result.success(bloque())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(if (e is ErrorApp) e else ErrorApp(e.aMensaje(), e))
    }

/** Escucha una consulta en tiempo real. Se desconecta sola cuando nadie recoge el flujo. */
internal fun Query.cambios(): Flow<QuerySnapshot> = callbackFlow {
    val registro = addSnapshotListener { snap, error ->
        if (error != null) close(ErrorApp(error.aMensaje(), error))
        else if (snap != null) trySend(snap)
    }
    awaitClose { registro.remove() }
}

/** Escucha un documento en tiempo real. */
internal fun DocumentReference.cambios(): Flow<DocumentSnapshot> = callbackFlow {
    val registro = addSnapshotListener { snap, error ->
        if (error != null) close(ErrorApp(error.aMensaje(), error))
        else if (snap != null) trySend(snap)
    }
    awaitClose { registro.remove() }
}
