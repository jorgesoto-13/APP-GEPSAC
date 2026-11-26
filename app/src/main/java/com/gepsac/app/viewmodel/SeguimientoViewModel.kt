
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

/**
 * Seguimiento de pedidos.
 *
 * Cliente:
 * - Consulta su pedido.
 * - Paga o reenvía un pago observado.
 * - Puede volver a pedir productos.
 *
 * Administrador:
 * - Aprueba u observa pagos.
 * - Avanza o retrocede etapas.
 * - Elimina pedidos permitidos.
 */
class SeguimientoViewModel(
    private val repo: PedidoRepository,
    auth: AuthRepository,
    private val productos: ProductoRepository,
    private val carrito: CarritoLocal
) : ViewModel() {

    val esAdmin: Boolean =
        auth.usuarioActual?.esAdmin == true

    init {
        auth.usuarioActual?.let {
            carrito.abrir(it.id)
        }
    }

    private val idPedido =
        MutableLiveData<String>()

    private val _mensaje =
        MutableLiveData<Evento<String>>()

    val mensaje: LiveData<Evento<String>> =
        _mensaje

    private val _agregadoAlCarrito =
        MutableLiveData<Evento<Unit>>()

    val agregadoAlCarrito: LiveData<Evento<Unit>> =
        _agregadoAlCarrito

    /**
     * Se actualiza automáticamente cuando cambia el pedido en Firebase.
     */
    val pedido: LiveData<Pedido?> =
        idPedido.switchMap { id ->

            repo.observar(id)
                .catch { error ->

                    _mensaje.value = Evento(
                        error.message
                            ?: "No se pudo cargar el pedido"
                    )
                }
                .asLiveData()
        }

    fun cargar(id: String) {

        if (idPedido.value != id) {
            idPedido.value = id
        }
    }

    // --------------------------------------------------
    // Avanzar y retroceder
    // --------------------------------------------------

    fun avanzar(id: String) =
        ejecutar {
            repo.avanzar(id)
        }

    fun retroceder(id: String) =
        ejecutar {
            repo.retroceder(id)
        }

    // --------------------------------------------------
    // Eliminar pedido
    // --------------------------------------------------

    fun eliminar(id: String) =
        ejecutar {
            repo.eliminar(id)
        }

    // --------------------------------------------------
    // Registrar o reenviar pago
    // --------------------------------------------------

    fun pagar(
        id: String,
        metodo: String,
        referencia: String,
        comprobante: String
    ) = ejecutar(
        "Pago recibido con $metodo. ¡Gracias!"
    ) {
        repo.pagar(
            id = id,
            metodo = metodo,
            referencia = referencia,
            comprobante = comprobante
        )
    }

    // --------------------------------------------------
    // Administrador: aprobar pago
    // --------------------------------------------------

    fun aprobarPago(id: String) =
        ejecutar(
            "Pago aprobado correctamente. El pedido puede pasar a preparación."
        ) {
            repo.aprobarPago(id)
        }

    // --------------------------------------------------
    // Administrador: observar pago
    // --------------------------------------------------

    fun observarPago(
        id: String,
        motivo: String
    ) = ejecutar(
        "Pago observado. El cliente puede corregir y reenviar su comprobante."
    ) {
        repo.observarPago(
            id = id,
            motivo = motivo
        )
    }

    // --------------------------------------------------
    // Cliente: volver a pedir
    // --------------------------------------------------

    /**
     * Agrega al carrito los productos de un pedido anterior.
     * Utiliza el nombre y precio actuales de cada producto.
     * Los productos que ya no existen o están inactivos se omiten.
     */
    fun volverAPedir(p: Pedido) {

        viewModelScope.launch {

            val vigentes = productos
                .observarProductos()
                .catch {
                    emit(emptyList())
                }
                .first()
                .filter { it.activo }
                .associateBy { it.id }

            val disponibles = p.items.mapNotNull { item ->

                vigentes[item.productoId]?.let { producto ->

                    ItemPedido(
                        productoId = producto.id,
                        nombre = producto.nombreCompleto,
                        precio = producto.precio,
                        cantidad = item.cantidad
                    )
                }
            }

            if (disponibles.isEmpty()) {

                _mensaje.value = Evento(
                    "Esos productos ya no están disponibles"
                )

                return@launch
            }

            // Sumar cantidades si el producto ya estaba en el carrito.
            val mezclado = (carrito.items.value + disponibles)
                .groupBy { it.productoId }
                .map { (_, filas) ->

                    filas.last().copy(
                        cantidad = minOf(
                            filas.sumOf { it.cantidad },
                            Validar.MAX_CANT
                        )
                    )
                }

            carrito.guardar(mezclado)

            val omitidos =
                p.items.size - disponibles.size

            _mensaje.value = Evento(
                if (omitidos == 0) {
                    "Productos agregados a tu carrito"
                } else {
                    "Agregados a tu carrito. $omitidos ya no están disponibles"
                }
            )

            _agregadoAlCarrito.value =
                Evento(Unit)
        }
    }

    // --------------------------------------------------
    // Ejecutar operaciones del repositorio
    // --------------------------------------------------

    private fun ejecutar(
        exito: String? = null,
        accion: suspend () -> Result<Unit>
    ) {

        viewModelScope.launch {

            accion().fold(

                onSuccess = {
                    exito?.let { mensaje ->
                        _mensaje.value = Evento(mensaje)
                    }
                },

                onFailure = { error ->

                    _mensaje.value = Evento(
                        error.message
                            ?: "No se pudo completar la acción"
                    )
                }
            )
        }
    }
}
