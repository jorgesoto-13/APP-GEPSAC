package com.gepsac.app.data.model

enum class Rol(val clave: String) {
    CLIENTE("cliente"),
    ADMIN("admin");

    companion object {
        /** Cualquier valor desconocido se trata como cliente (el rol con menos permisos). */
        fun desde(clave: String?): Rol = entries.find { it.clave == clave } ?: CLIENTE
    }
}

/** Perfil de la app. Las credenciales (correo y contraseña) las gestiona Firebase Auth, no este objeto. */
data class Usuario(
    val id: String,
    val nombre: String,
    val email: String,
    val telefono: String,
    val rol: Rol = Rol.CLIENTE
) {
    val esAdmin: Boolean get() = rol == Rol.ADMIN
}
