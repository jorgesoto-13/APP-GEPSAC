package com.gepsac.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.gepsac.app.model.Pedido
import com.gepsac.app.model.repository.AuthRepository
import com.gepsac.app.model.repository.PedidoRepository
import com.gepsac.app.util.Evento
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Seguimiento de un pedido: el administrador avanza/retrocede etapas y elimina; el cliente paga. */
class SeguimientoViewModel(
    private val repo: PedidoRepository,
    auth: AuthRepository
) : ViewModel() {
    val esAdmin: Boolean = auth.usuarioActual?.esAdmin == true

    private val idPedido = MutableLiveData<String>()

    private val _mensaje = MutableLiveData<Evento<String>>()
    val mensaje: LiveData<Evento<String>> = _mensaje

    /** Se actualiza solo cuando cambia en Firebase. Es null si el pedido se eliminó. */
    val pedido: LiveData<Pedido?> = idPedido.switchMap { id ->
        repo.observar(id)
            .catch { _mensaje.value = Evento(it.message ?: "No se pudo cargar el pedido") }
            .asLiveData()
    }

    fun cargar(id: String) {
        if (idPedido.value != id) idPedido.value = id
    }

    fun avanzar(id: String) = ejecutar { repo.avanzar(id) }

    fun retroceder(id: String) = ejecutar { repo.retroceder(id) }

    fun eliminar(id: String) = ejecutar { repo.eliminar(id) }

    fun pagar(id: String, metodo: String, referencia: String) =
        ejecutar("Pago recibido con $metodo. ¡Gracias!") { repo.pagar(id, metodo, referencia) }

    private fun ejecutar(exito: String? = null, accion: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            accion().fold(
                onSuccess = { exito?.let { _mensaje.value = Evento(it) } },
                onFailure = { _mensaje.value = Evento(it.message ?: "No se pudo completar la acción") }
            )
        }
    }
}
