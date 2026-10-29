package com.gepsac.app.viewmodel

import androidx.lifecycle.ViewModel
import com.gepsac.app.data.Repository
import com.gepsac.app.data.Usuario

class AuthViewModel : ViewModel() {
    val usuario: Usuario? get() = Repository.usuarioActual

    /** Devuelve un mensaje de error, o null si todo salió bien. */
    fun ingresar(email: String, pass: String): String? = Repository.login(email, pass)

    fun registrar(nombre: String, tel: String, email: String, pass: String): String? =
        Repository.registrar(nombre.trim(), tel.trim(), email, pass)
}
