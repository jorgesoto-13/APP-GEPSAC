package com.gepsac.app.model.firebase

import com.gepsac.app.model.Rol
import com.gepsac.app.model.Usuario
import com.gepsac.app.model.repository.AuthRepository
import com.gepsac.app.model.repository.ErrorApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException

/** Autenticación con Firebase Auth (correo y contraseña) + perfil en la colección `usuarios/{uid}`. */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : AuthRepository {

    @Volatile
    private var usuario: Usuario? = null
    override val usuarioActual: Usuario? get() = usuario

    override suspend fun ingresar(email: String, pass: String): Result<Usuario> = intentar {
        val resultado = auth.signInWithEmailAndPassword(email.trim(), pass).esperar()
        val uid = resultado.user?.uid ?: throw ErrorApp("No se pudo iniciar sesión")
        cargarPerfil(uid)
    }

    override suspend fun registrar(nombre: String, telefono: String, email: String, pass: String): Result<Usuario> =
        intentar {
            val correo = email.trim().lowercase()
            val user = auth.createUserWithEmailAndPassword(correo, pass).esperar().user
                ?: throw ErrorApp("No se pudo crear la cuenta")

            val nuevo = Usuario(
                id = user.uid,
                nombre = nombre.trim(),
                email = user.email ?: correo,
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
                db.collection(Coleccion.USUARIOS).document(user.uid).set(perfil).esperar()
            } catch (e: Exception) {
                // Sin perfil la cuenta no sirve: se borra para que el correo pueda volver a registrarse.
                runCatching { user.delete().esperar() }
                auth.signOut()
                throw e
            }
            usuario = nuevo
            nuevo
        }

    override suspend fun restaurarSesion(): Usuario? {
        usuario?.let { return it }
        val uid = auth.currentUser?.uid ?: return null
        return try {
            cargarPerfil(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override fun cerrarSesion() {
        auth.signOut()
        usuario = null
    }

    private suspend fun cargarPerfil(uid: String): Usuario {
        val perfil = db.collection(Coleccion.USUARIOS).document(uid).get().esperar().aUsuario()
        if (perfil == null) {
            auth.signOut()
            throw ErrorApp("Tu cuenta no tiene perfil en la tienda. Contacta al administrador.")
        }
        usuario = perfil
        return perfil
    }
}
