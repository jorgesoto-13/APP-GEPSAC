package com.gepsac.app.model.repository

import com.gepsac.app.model.Usuario

/** Sesión del usuario. Los errores llegan como [ErrorApp] con el mensaje ya en español. */
interface AuthRepository {
    /** Usuario con sesión iniciada (null si no hay). */
    val usuarioActual: Usuario?

    suspend fun ingresar(email: String, pass: String): Result<Usuario>

    suspend fun registrar(nombre: String, telefono: String, email: String, pass: String): Result<Usuario>

    /** Recupera la sesión guardada por Firebase al abrir la app. Devuelve null si no hay o no se pudo leer. */
    suspend fun restaurarSesion(): Usuario?

    fun cerrarSesion()
}
