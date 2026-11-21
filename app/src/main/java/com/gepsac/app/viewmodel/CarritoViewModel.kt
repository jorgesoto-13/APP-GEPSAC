package com.gepsac.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.gepsac.app.model.ItemPedido
import com.gepsac.app.model.NuevoPedido
import com.gepsac.app.model.Producto
import com.gepsac.app.model.Usuario
import com.gepsac.app.model.local.CarritoLocal
import com.gepsac.app.model.repository.AuthRepository
import com.gepsac.app.model.repository.PedidoRepository
import com.gepsac.app.model.repository.UsuarioRepository
import com.gepsac.app.util.Evento
import com.gepsac.app.util.Fechas
import com.gepsac.app.util.Validar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map as mapFlow
import kotlinx.coroutines.launch

/**
 * Carrito del cliente. Se comparte entre el Catálogo (agrega) y el Carrito (confirma),
 * por eso las pantallas lo piden con activityViewModels. El contenido vive en [CarritoLocal]:
 * se conserva al cerrar la app y lo comparten también otras pantallas (por ejemplo «volver a pedir»).
 */
class CarritoViewModel(
    private val pedidos: PedidoRepository,
    private val auth: AuthRepository,
    private val usuarios: UsuarioRepository,
    private val local: CarritoLocal
) : ViewModel() {

    init {
        auth.usuarioActual?.let { local.abrir(it.id) }
    }

    val carrito: LiveData<List<ItemPedido>> = local.items.asLiveData()
    val total: LiveData<Double> = carrito.map { l -> l.sumOf { it.precio * it.cantidad } }

    /** Cantidad total de unidades (para el globo de la pestaña Carrito). */
    val cantidadTotal: LiveData<Int> = carrito.map { l -> l.sumOf { it.cantidad } }

    private val _enviando = MutableLiveData(false)
    val enviando: LiveData<Boolean> = _enviando

    /** Número del pedido recién enviado. */
    private val _pedidoEnviado = MutableLiveData<Evento<Long>>()
    val pedidoEnviado: LiveData<Evento<Long>> = _pedidoEnviado

    private val _error = MutableLiveData<Evento<String>>()
    val error: LiveData<Evento<String>> = _error

    val telefonoUsuario: String get() = auth.usuarioActual?.telefono.orEmpty()

    /** Direcciones de entrega guardadas del cliente, en tiempo real. */
    val direcciones: LiveData<List<String>> = run {
        val usuario = auth.usuarioActual
        val origen: Flow<Usuario?> = if (usuario != null) usuarios.observarUsuario(usuario.id) else flowOf(null)
        origen.mapFlow { it?.direcciones.orEmpty() }
            .catch { emit(emptyList()) }
            .asLiveData()
    }

    /** false si ya se alcanzó el máximo por producto. */
    fun agregar(p: Producto): Boolean {
        val l = local.items.value
        val e = l.find { it.productoId == p.id }
        if (e != null && e.cantidad >= Validar.MAX_CANT) return false
        local.guardar(
            if (e != null) l.map { if (it.productoId == p.id) it.copy(cantidad = it.cantidad + 1) else it }
            else l + ItemPedido(
                productoId = p.id,
                nombre = p.nombreCompleto,
                precio = p.precio,
                cantidad = 1,
                imagen = p.imagen
            )
        )
        return true
    }

    fun cambiarCantidad(productoId: String, delta: Int): Boolean {
        val l = local.items.value
        val e = l.find { it.productoId == productoId }
        if (e != null && delta > 0 && e.cantidad >= Validar.MAX_CANT) return false
        local.guardar(
            l.map { if (it.productoId == productoId) it.copy(cantidad = it.cantidad + delta) else it }
                .filter { it.cantidad > 0 }
        )
        return true
    }

    fun eliminar(productoId: String) {
        local.guardar(
            local.items.value.filterNot {
                it.productoId == productoId
            }
        )
    }


    fun vaciar() {
        local.guardar(emptyList())
    }

    /** Guarda [dir] entre las direcciones del cliente (sin repetir, la más reciente primero, máximo 5). */
    fun guardarDireccion(dir: String) {
        val u = auth.usuarioActual ?: return
        val limpia = dir.trim()
        if (limpia.isEmpty()) return
        val nuevas = (listOf(limpia) + direcciones.value.orEmpty()).distinct().take(Usuario.MAX_DIRECCIONES)
        viewModelScope.launch { usuarios.guardarDirecciones(u.id, nuevas) }
    }

    fun confirmarPedido(dir: String, tel: String, fecha: String, obs: String, latitud: Double? = null, longitud: Double? = null) {
        val items = local.items.value
        val usuario = auth.usuarioActual
        if (items.isEmpty() || usuario == null || _enviando.value == true) return
        val fechaMs = Fechas.leerDia(fecha) ?: run {
            _error.value = Evento("Fecha de entrega no válida")
            return
        }
        _enviando.value = true
        viewModelScope.launch {
            val nuevo = NuevoPedido(usuario, items, dir.trim(), tel.replace(" ", ""), fechaMs, obs.trim(), latitud, longitud)
            pedidos.crear(nuevo, usuario).fold(
                onSuccess = { numero ->
                    local.guardar(emptyList())
                    _pedidoEnviado.value = Evento(numero)
                },
                onFailure = { _error.value = Evento(it.message ?: "No se pudo enviar el pedido") }
            )
            _enviando.value = false
        }
    }
}
