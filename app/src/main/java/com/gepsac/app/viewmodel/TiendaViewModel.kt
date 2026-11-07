package com.gepsac.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.gepsac.app.data.ESTADO_PAGADO
import com.gepsac.app.data.ItemPedido
import com.gepsac.app.data.Pedido
import com.gepsac.app.data.Producto
import com.gepsac.app.data.Repository
import com.gepsac.app.data.Usuario

/** Fila del listado de clientes: cuántos pedidos hizo y cuánto ha pagado. */
data class ResumenCliente(val cliente: Usuario, val pedidos: Int, val totalPagado: Double)

/** ViewModel compartido: Catálogo, Carrito, Pedidos, Seguimiento y Administración. */
class TiendaViewModel : ViewModel() {
    val esAdmin: Boolean get() = Repository.usuarioActual?.esAdmin == true
    val telefonoUsuario: String get() = Repository.usuarioActual?.telefono ?: ""

    // Catálogo
    val productos: LiveData<List<Producto>> =
        Repository.productos.map { l -> l.sortedWith(compareBy({ it.marca }, { it.precio })) }

    fun productosActivos(): List<Producto> =
        Repository.productos.value.orEmpty().filter { it.activo }.sortedWith(compareBy({ it.marca }, { it.precio }))

    // Carrito
    private val _carrito = MutableLiveData<List<ItemPedido>>(emptyList())
    val carrito: LiveData<List<ItemPedido>> = _carrito
    val total: LiveData<Double> = _carrito.map { l -> l.sumOf { it.precio * it.cantidad } }

    /** false si ya se alcanzó el máximo por producto. */
    fun agregar(p: Producto): Boolean {
        val l = _carrito.value.orEmpty()
        val e = l.find { it.productoId == p.id }
        if (e != null && e.cantidad >= Validar.MAX_CANT) return false
        _carrito.value =
            if (e != null) l.map { if (it.productoId == p.id) it.copy(cantidad = it.cantidad + 1) else it }
            else l + ItemPedido(p.id, (p.nombre + " " + p.presentacion).trim(), p.precio, 1)
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

    fun confirmarPedido(dir: String, tel: String, fecha: String, obs: String): Pedido? {
        val items = _carrito.value.orEmpty()
        if (items.isEmpty()) return null
        val p = Repository.crearPedido(items, dir, tel, fecha, obs)
        _carrito.value = emptyList()
        return p
    }

    // Pedidos (cliente: los suyos / admin: todos)
    val pedidos: LiveData<List<Pedido>> = Repository.pedidos.map { l ->
        val u = Repository.usuarioActual
        l.filter { u?.esAdmin == true || it.clienteId == u?.id }
            .sortedByDescending { it.tiempos.firstOrNull() ?: 0L }
    }

    // Seguimiento
    fun pedido(id: String): LiveData<Pedido?> = Repository.pedidos.map { l -> l.find { it.id == id } }
    fun pagar(id: String, metodo: String) = Repository.pagar(id, metodo)

    // Administración
    fun avanzarEstado(id: String) = Repository.avanzarEstado(id)
    fun retrocederEstado(id: String) = Repository.retrocederEstado(id)
    fun eliminarPedido(id: String): Boolean = Repository.eliminarPedido(id)
    fun guardarProducto(p: Producto) = Repository.guardarProducto(p)
    fun eliminarProducto(id: String) = Repository.eliminarProducto(id)
    fun clientes(): List<Usuario> = Repository.clientes()

    /** Clientes registrados con su cantidad de pedidos y lo que han pagado (de mayor a menor). */
    fun resumenClientes(): List<ResumenCliente> {
        val todos = Repository.pedidos.value.orEmpty()
        return Repository.clientes().map { c ->
            val mios = todos.filter { it.clienteId == c.id }
            ResumenCliente(c, mios.size, mios.filter { it.estado >= ESTADO_PAGADO }.sumOf { it.total })
        }.sortedByDescending { it.totalPagado }
    }
    fun crearPedidoAdmin(c: Usuario, items: List<ItemPedido>, dir: String, tel: String, fecha: String, obs: String): Pedido =
        Repository.crearPedidoComoAdmin(c, items, dir, tel, fecha, obs)
}
