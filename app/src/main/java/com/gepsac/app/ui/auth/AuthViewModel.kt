package com.gepsac.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gepsac.app.data.model.Usuario
import com.gepsac.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

sealed interface AuthEstado {
    data object Inactivo : AuthEstado
    data object Cargando : AuthEstado
    data class Listo(val usuario: Usuario) : AuthEstado
    data class Fallo(val mensaje: String) : AuthEstado
}

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _estado = MutableLiveData<AuthEstado>(AuthEstado.Inactivo)
    val estado: LiveData<AuthEstado> = _estado

    val usuario: Usuario? get() = repo.usuarioActual

    /** Al abrir la app: si Firebase ya tiene una sesión guardada, entra directo al panel. */
    fun restaurarSesion() {
        if (_estado.value != AuthEstado.Inactivo) return
        _estado.value = AuthEstado.Cargando
        viewModelScope.launch {
            val u = repo.restaurarSesion()
            _estado.value = if (u != null) AuthEstado.Listo(u) else AuthEstado.Inactivo
        }
    }

    fun ingresar(email: String, pass: String) {
        _estado.value = AuthEstado.Cargando
        viewModelScope.launch {
            repo.ingresar(email, pass).fold(
                onSuccess = { _estado.value = AuthEstado.Listo(it) },
                onFailure = { _estado.value = AuthEstado.Fallo(it.message ?: "No se pudo ingresar") }
            )
        }
    }

    fun registrar(nombre: String, tel: String, email: String, pass: String) {
        _estado.value = AuthEstado.Cargando
        viewModelScope.launch {
            repo.registrar(nombre.trim(), tel.trim(), email, pass).fold(
                onSuccess = { _estado.value = AuthEstado.Listo(it) },
                onFailure = { _estado.value = AuthEstado.Fallo(it.message ?: "No se pudo crear la cuenta") }
            )
        }
    }

    fun cerrarSesion() = repo.cerrarSesion()
}
