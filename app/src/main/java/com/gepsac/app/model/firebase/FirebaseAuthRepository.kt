package com.gepsac.app.model.firebase

import com.gepsac.app.model.Rol
import com.gepsac.app.model.Usuario
import com.gepsac.app.model.repository.AuthRepository
import com.gepsac.app.model.repository.ErrorApp
import com.gepsac.app.model.service.AuthService
import com.gepsac.app.model.service.FirestoreService
import com.gepsac.app.model.service.intentar
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CancellationException

/** Sesión con Firebase Auth (vía [AuthService]) + perfil en la colección `usuarios/{uid}` (vía [FirestoreService]). */
class FirebaseAuthRepository(
    private val auth: AuthService,
    private val firestore: FirestoreService
) : AuthRepository {

    @Volatile
    private var usuario: Usuario? = null
    override val usuarioActual: Usuario? get() = usuario

    override suspend fun ingresar(email: String, pass: String): Result<Usuario> = intentar {
        cargarPerfil(auth.iniciarSesion(email, pass).uid)
    }

    override suspend fun registrar(nombre: String, telefono: String, email: String, pass: String): Result<Usuario> =
        intentar {
            val correo = email.trim().lowercase()
            val cuenta = auth.crearCuenta(correo, pass)

            val nuevo = Usuario(
                id = cuenta.uid,
                nombre = nombre.trim(),
                email = cuenta.email ?: correo,
                telefono = telefono.replace(" ", ""),
                rol = Rol.CLIENTE
            )
            val perfil = mapOf(
                "nombre" to nuevo.nombre,
                "email" to nuevo.email,
                "telefono" to nuevo.telefono,
                "rol" to nuevo.rol.clave,
                "creadoEn" to FieldValue.serverTimestamp()
            )
            try {
                firestore.guardar(Coleccion.USUARIOS, cuenta.uid, perfil)
            } catch (e: Exception) {
                // Sin perfil la cuenta no sirve: se borra para que el correo pueda volver a registrarse.
                runCatching { auth.eliminarCuentaActual() }
                auth.cerrarSesion()
                throw e
            }
            auth.cerrarSesion()
            usuario = null

            nuevo
        }

    override suspend fun restaurarSesion(): Usuario? {
        usuario?.let { return it }
        val uid = auth.uidActual ?: return null
        return try {
            cargarPerfil(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun recuperarContrasena(email: String): Result<Unit> = intentar {
        try {
            auth.enviarRecuperacion(email.trim().lowercase())
        } catch (e: FirebaseAuthInvalidUserException) {
            // No se avisa si el correo no existe: así nadie puede averiguar quién tiene cuenta.
        }
    }

    override fun cerrarSesion() {
        auth.cerrarSesion()
        usuario = null
    }

    private suspend fun cargarPerfil(uid: String): Usuario {
        val perfil = firestore.obtener(Coleccion.USUARIOS, uid).aUsuario()
        if (perfil == null) {
            auth.cerrarSesion()
            throw ErrorApp("Tu cuenta no tiene perfil en la tienda. Contacta al administrador.")
        }
        usuario = perfil
        return perfil
    }
}
