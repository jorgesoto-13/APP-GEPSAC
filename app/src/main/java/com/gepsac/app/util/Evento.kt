package com.gepsac.app.util

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData

/** Mensaje de un solo uso para LiveData (toasts, avisos): no se repite al rotar la pantalla. */
class Evento<out T>(private val contenido: T) {
    private var consumido = false

    fun obtener(): T? {
        if (consumido) return null
        consumido = true
        return contenido
    }
}

fun <T> LiveData<Evento<T>>.observarEvento(owner: LifecycleOwner, alRecibir: (T) -> Unit) {
    observe(owner) { it.obtener()?.let(alRecibir) }
}
