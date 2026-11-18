package com.gepsac.app.model.service

import com.gepsac.app.model.repository.ErrorApp
import com.google.firebase.auth.FirebaseAuth

/** Datos mínimos de una cuenta de Firebase Authentication. */
class CuentaFirebase(val uid: String, val email: String?)

/**
 * Servicio de Firebase Authentication: es lo único que toca el SDK de `FirebaseAuth`.
 * No conoce usuarios de la tienda ni roles; eso lo resuelve el repositorio.
 */
class AuthService(private val auth: FirebaseAuth) {

    /** Uid de la sesión abierta en el dispositivo, o null si no hay ninguna. */
    val uidActual: String? get() = auth.currentUser?.uid

    suspend fun iniciarSesion(email: String, pass: String): CuentaFirebase {
        val user = auth.signInWithEmailAndPassword(email.trim(), pass).esperar().user
            ?: throw ErrorApp("No se pudo iniciar sesión")
        return CuentaFirebase(user.uid, user.email)
    }

    suspend fun crearCuenta(email: String, pass: String): CuentaFirebase {
        val user = auth.createUserWithEmailAndPassword(email, pass).esperar().user
            ?: throw ErrorApp("No se pudo crear la cuenta")
        return CuentaFirebase(user.uid, user.email)
    }

    /** Borra la cuenta con la sesión abierta (se usa si falla guardar el perfil al registrarse). */
    suspend fun eliminarCuentaActual() {
        auth.currentUser?.delete()?.esperar()
    }

    fun cerrarSesion() = auth.signOut()
}
