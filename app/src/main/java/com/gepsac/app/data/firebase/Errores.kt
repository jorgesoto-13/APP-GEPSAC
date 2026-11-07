package com.gepsac.app.data.firebase

import com.gepsac.app.data.repository.ErrorApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code

private const val MENSAJE_GENERICO = "Ocurrió un error inesperado. Inténtalo de nuevo."

/** Traduce cualquier excepción de Firebase a un mensaje para el usuario. */
internal fun Throwable.aMensaje(): String {
    // Una excepción lanzada dentro de una transacción puede llegar envuelta: se busca nuestro mensaje en la cadena.
    generateSequence(this) { it.cause }.filterIsInstance<ErrorApp>().firstOrNull()?.let {
        return it.message ?: MENSAJE_GENERICO
    }
    return when (this) {
        is FirebaseAuthWeakPasswordException -> "La contraseña es muy débil"
        is FirebaseAuthUserCollisionException -> "Ese correo ya está registrado"
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos"
        is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera un momento e inténtalo otra vez"
        is FirebaseNetworkException -> "Sin conexión a internet"
        is FirebaseFirestoreException -> when (code) {
            Code.PERMISSION_DENIED -> "No tienes permiso para realizar esta acción"
            Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> "Sin conexión. Revisa tu internet e inténtalo de nuevo"
            Code.NOT_FOUND -> "El dato ya no existe"
            else -> MENSAJE_GENERICO
        }
        else -> MENSAJE_GENERICO
    }
}
