package com.gepsac.app.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/** Si el flujo falla (sin permisos, sin red...) avisa con el mensaje y emite [valorVacio] en lugar de cerrar la pantalla. */
fun <T> Flow<T>.alFallar(valorVacio: T, aviso: (String) -> Unit): Flow<T> = catch { e ->
    aviso(e.message ?: "No se pudieron cargar los datos")
    emit(valorVacio)
}
