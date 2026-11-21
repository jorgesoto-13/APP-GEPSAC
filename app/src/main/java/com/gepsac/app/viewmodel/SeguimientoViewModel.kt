package com.gepsac.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.gepsac.app.model.ItemPedido
import com.gepsac.app.model.Pedido
import com.gepsac.app.model.local.CarritoLocal
import com.gepsac.app.model.repository.AuthRepository
import com.gepsac.app.model.repository.PedidoRepository
import com.gepsac.app.model.repository.ProductoRepository
import com.gepsac.app.util.Evento
import com.gepsac.app.util.Validar
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Seguimiento de un pedido: el administrador avanza/retrocede etapas y elimina; el cliente paga o vuelve a pedir. */
class SeguimientoViewModel(
    private val repo: PedidoRepository,
    auth: AuthRepository,
    private val productos: ProductoRepository,
    private val carrito: CarritoLocal
) : ViewModel() {
    val esAdmin: Boolean = auth.usuarioActual?.esAdmin == true

    init {
        auth.usuarioActual?.let { carrito.abrir(it.id) }
    }

    private val idPedido = MutableLiveData<String>()

    private val _mensaje = MutableLiveData<Evento<String>>()
    val mensaje: LiveData<Evento<String>> = _mensaje

    /** Se emite cuando los productos del pedido ya quedaron en el carrito. */
    private val _agregadoAlCarrito = MutableLiveData<Evento<Unit>>()
    val agregadoAlCarrito: LiveData<Evento<Unit>> = _agregadoAlCarrito

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

    fun pagar(id: String, metodo: String, referencia: String, comprobante: String) =
        ejecutar("Pago recibido con $metodo. ¡Gracias!") { repo.pagar(id, metodo, referencia, comprobante) }

    // ---------- Revisión del pago por el administrador ----------

    fun aprobarPago(id: String) =
        ejecutar("Pago aprobado correctamente. El pedido puede pasar a preparación.") {
            repo.aprobarPago(id)
        }

    fun observarPago(id: String, motivo: String) =
        ejecutar("Pago observado. El cliente puede corregir y reenviar su comprobante.") {
            repo.observarPago(id, motivo)
        }

    /**
     * «Volver a pedir»: agrega al carrito los productos de este pedido con el precio y el nombre de hoy.
     * Los que ya no existen o están inactivos se omiten y se avisa.
     */
    fun volverAPedir(p: Pedido) {
        viewModelScope.launch {
            val vigentes = productos.observarProductos().catch { emit(emptyList()) }.first()
                .filter { it.activo }
                .associateBy { it.id }
            val disponibles = p.items.mapNotNull { i ->
                vigentes[i.productoId]?.let { ItemPedido(it.id, it.nombreCompleto, it.precio, i.cantidad) }
            }
            if (disponibles.isEmpty()) {
                _mensaje.value = Evento("Esos productos ya no están disponibles")
                return@launch
            }
            // Si algún producto ya estaba en el carrito, se suman las cantidades (hasta el máximo permitido).
            val mezclado = (carrito.items.value + disponibles)
                .groupBy { it.productoId }
                .map { (_, filas) -> filas.last().copy(cantidad = minOf(filas.sumOf { it.cantidad }, Validar.MAX_CANT)) }
            carrito.guardar(mezclado)
            val omitidos = p.items.size - disponibles.size
            _mensaje.value = Evento(
                if (omitidos == 0) "Productos agregados a tu carrito"
                else "Agregados a tu carrito. $omitidos ya no están disponibles"
            )
            _agregadoAlCarrito.value = Evento(Unit)
        }
    }

    private fun ejecutar(exito: String? = null, accion: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            accion().fold(
                onSuccess = { exito?.let { _mensaje.value = Evento(it) } },
                onFailure = { _mensaje.value = Evento(it.message ?: "No se pudo completar la acción") }
            )
        }
    }
}
