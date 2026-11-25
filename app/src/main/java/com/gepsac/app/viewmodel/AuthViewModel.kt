
package com.gepsac.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gepsac.app.model.Usuario
import com.gepsac.app.model.repository.AuthRepository
import com.gepsac.app.util.Evento
import kotlinx.coroutines.launch

sealed interface AuthEstado {
    data object Inactivo : AuthEstado
    data object Cargando : AuthEstado
    data class Listo(val usuario: Usuario) : AuthEstado
    data object RegistroExitoso : AuthEstado
    data class Fallo(val mensaje: String) : AuthEstado
}

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {

    private val _estado = MutableLiveData<AuthEstado>(AuthEstado.Inactivo)
    val estado: LiveData<AuthEstado> = _estado

    val usuario: Usuario? get() = repo.usuarioActual

    /** Restaura una sesión existente al abrir la aplicación. */
    fun restaurarSesion() {
        if (_estado.value != AuthEstado.Inactivo) return

        _estado.value = AuthEstado.Cargando

        viewModelScope.launch {
            val u = repo.restaurarSesion()
            _estado.value =
                if (u != null) AuthEstado.Listo(u)
                else AuthEstado.Inactivo
        }
    }

    /** Inicio de sesión normal. */
    fun ingresar(email: String, pass: String) {
        _estado.value = AuthEstado.Cargando

        viewModelScope.launch {
            repo.ingresar(email, pass).fold(
                onSuccess = {
                    _estado.value = AuthEstado.Listo(it)
                },
                onFailure = {
                    _estado.value = AuthEstado.Fallo(
                        it.message ?: "No se pudo ingresar"
                    )
                }
            )
        }
    }

    /** Registro: crea la cuenta, pero no abre el panel. */
    fun registrar(
        nombre: String,
        tel: String,
        email: String,
        pass: String
    ) {
        _estado.value = AuthEstado.Cargando

        viewModelScope.launch {
            repo.registrar(
                nombre.trim(),
                tel.trim(),
                email,
                pass
            ).fold(
                onSuccess = {
                    _estado.value = AuthEstado.RegistroExitoso
                },
                onFailure = {
                    _estado.value = AuthEstado.Fallo(
                        it.message ?: "No se pudo crear la cuenta"
                    )
                }
            )
        }
    }

    private val _recuperacion = MutableLiveData<Evento<String>>()
    val recuperacion: LiveData<Evento<String>> = _recuperacion

    fun recuperarContrasena(email: String) {
        viewModelScope.launch {
            repo.recuperarContrasena(email).fold(
                onSuccess = {
                    _recuperacion.value = Evento(
                        "Si el correo está registrado, te enviamos un enlace " +
                                "para cambiar tu contraseña. Revisa también el spam."
                    )
                },
                onFailure = {
                    _recuperacion.value = Evento(
                        it.message ?: "No se pudo enviar el correo"
                    )
                }
            )
        }
    }

    fun cerrarSesion() = repo.cerrarSesion()
}
