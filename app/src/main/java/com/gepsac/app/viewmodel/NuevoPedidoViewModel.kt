package com.gepsac.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.gepsac.app.model.ItemPedido
import com.gepsac.app.model.NuevoPedido
import com.gepsac.app.model.Producto
import com.gepsac.app.model.Usuario
import com.gepsac.app.model.repository.AuthRepository
import com.gepsac.app.model.repository.PedidoRepository
import com.gepsac.app.model.repository.ProductoRepository
import com.gepsac.app.model.repository.UsuarioRepository
import com.gepsac.app.util.Evento
import com.gepsac.app.util.Fechas
import com.gepsac.app.util.alFallar
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** El administrador crea un pedido a nombre de un cliente. Entra ya "Registrado". */
class NuevoPedidoViewModel(
    usuarios: UsuarioRepository,
    productosRepo: ProductoRepository,
    private val pedidos: PedidoRepository,
    private val auth: AuthRepository
) : ViewModel() {
    private val _mensaje = MutableLiveData<Evento<String>>()
    val mensaje: LiveData<Evento<String>> = _mensaje

    /** Número del pedido creado. */
    private val _creado = MutableLiveData<Evento<Long>>()
    val creado: LiveData<Evento<Long>> = _creado

    val clientes: LiveData<List<Usuario>> = usuarios.observarClientes()
        .alFallar(emptyList()) { _mensaje.value = Evento(it) }
        .asLiveData()

    val productos: LiveData<List<Producto>> = productosRepo.observarProductos()
        .map { l -> l.filter { it.activo }.sortedWith(compareBy({ it.marca }, { it.precio })) }
        .alFallar(emptyList()) { _mensaje.value = Evento(it) }
        .asLiveData()

    fun crear(cliente: Usuario, items: List<ItemPedido>, dir: String, tel: String, fecha: String, obs: String) {
        val admin = auth.usuarioActual ?: return
        val fechaMs = Fechas.leerDia(fecha) ?: run {
            _mensaje.value = Evento("Fecha de entrega no válida")
            return
        }
        viewModelScope.launch {
            val nuevo = NuevoPedido(cliente, items, dir.trim(), tel.replace(" ", ""), fechaMs, obs.trim())
            pedidos.crear(nuevo, admin).fold(
                onSuccess = { _creado.value = Evento(it) },
                onFailure = { _mensaje.value = Evento(it.message ?: "No se pudo crear el pedido") }
            )
        }
    }
}
