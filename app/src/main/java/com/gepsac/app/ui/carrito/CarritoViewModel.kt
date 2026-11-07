package com.gepsac.app.ui.carrito

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.gepsac.app.data.model.ItemPedido
import com.gepsac.app.data.model.NuevoPedido
import com.gepsac.app.data.model.Producto
import com.gepsac.app.data.repository.AuthRepository
import com.gepsac.app.data.repository.PedidoRepository
import com.gepsac.app.util.Evento
import com.gepsac.app.util.Fechas
import com.gepsac.app.util.Validar
import kotlinx.coroutines.launch

/**
 * Carrito del cliente. Se comparte entre el Catálogo (agrega) y el Carrito (confirma),
 * por eso las pantallas lo piden con activityViewModels.
 */
class CarritoViewModel(
    private val pedidos: PedidoRepository,
    private val auth: AuthRepository
) : ViewModel() {
    private val _carrito = MutableLiveData<List<ItemPedido>>(emptyList())
    val carrito: LiveData<List<ItemPedido>> = _carrito
    val total: LiveData<Double> = _carrito.map { l -> l.sumOf { it.precio * it.cantidad } }

    private val _enviando = MutableLiveData(false)
    val enviando: LiveData<Boolean> = _enviando

    /** Número del pedido recién enviado. */
    private val _pedidoEnviado = MutableLiveData<Evento<Long>>()
    val pedidoEnviado: LiveData<Evento<Long>> = _pedidoEnviado

    private val _error = MutableLiveData<Evento<String>>()
    val error: LiveData<Evento<String>> = _error

    val telefonoUsuario: String get() = auth.usuarioActual?.telefono.orEmpty()

    /** false si ya se alcanzó el máximo por producto. */
    fun agregar(p: Producto): Boolean {
        val l = _carrito.value.orEmpty()
        val e = l.find { it.productoId == p.id }
        if (e != null && e.cantidad >= Validar.MAX_CANT) return false
        _carrito.value =
            if (e != null) l.map { if (it.productoId == p.id) it.copy(cantidad = it.cantidad + 1) else it }
            else l + ItemPedido(p.id, p.nombreCompleto, p.precio, 1)
        return true
    }

    fun cambiarCantidad(productoId: String, delta: Int): Boolean {
        val e = _carrito.value.orEmpty().find { it.productoId == productoId }
        if (e != null && delta > 0 && e.cantidad >= Validar.MAX_CANT) return false
        _carrito.value = _carrito.value.orEmpty()
            .map { if (it.productoId == productoId) it.copy(cantidad = it.cantidad + delta) else it }
            .filter { it.cantidad > 0 }
        return true
    }

    fun confirmarPedido(dir: String, tel: String, fecha: String, obs: String) {
        val items = _carrito.value.orEmpty()
        val usuario = auth.usuarioActual
        if (items.isEmpty() || usuario == null || _enviando.value == true) return
        val fechaMs = Fechas.leerDia(fecha) ?: run {
            _error.value = Evento("Fecha de entrega no válida")
            return
        }
        _enviando.value = true
        viewModelScope.launch {
            val nuevo = NuevoPedido(usuario, items, dir.trim(), tel.replace(" ", ""), fechaMs, obs.trim())
            pedidos.crear(nuevo, usuario).fold(
                onSuccess = { numero ->
                    _carrito.value = emptyList()
                    _pedidoEnviado.value = Evento(numero)
                },
                onFailure = { _error.value = Evento(it.message ?: "No se pudo enviar el pedido") }
            )
            _enviando.value = false
        }
    }
}
