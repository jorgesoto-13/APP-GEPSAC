
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
 * Carrito compartido entre Catálogo y Carrito.
 *
 * El contenido se conserva localmente y se comparte
 * entre las pantallas que utilizan activityViewModels.
 */
class CarritoViewModel(
    private val pedidos: PedidoRepository,
    private val auth: AuthRepository,
    private val usuarios: UsuarioRepository,
    private val local: CarritoLocal
) : ViewModel() {

    init {
        auth.usuarioActual?.let {
            local.abrir(it.id)
        }
    }

    val carrito: LiveData<List<ItemPedido>> =
        local.items.asLiveData()

    val total: LiveData<Double> =
        carrito.map { lista ->
            lista.sumOf { it.precio * it.cantidad }
        }

    /** Cantidad total de unidades del carrito. */
    val cantidadTotal: LiveData<Int> =
        carrito.map { lista ->
            lista.sumOf { it.cantidad }
        }

    private val _enviando = MutableLiveData(false)
    val enviando: LiveData<Boolean> = _enviando

    /** Número del pedido recién creado. */
    private val _pedidoEnviado =
        MutableLiveData<Evento<Long>>()

    val pedidoEnviado: LiveData<Evento<Long>> =
        _pedidoEnviado

    private val _error =
        MutableLiveData<Evento<String>>()

    val error: LiveData<Evento<String>> = _error

    val telefonoUsuario: String
        get() = auth.usuarioActual?.telefono.orEmpty()

    /** Direcciones guardadas del cliente. */
    val direcciones: LiveData<List<String>> = run {

        val usuario = auth.usuarioActual

        val origen: Flow<Usuario?> =
            if (usuario != null) {
                usuarios.observarUsuario(usuario.id)
            } else {
                flowOf(null)
            }

        origen
            .mapFlow { it?.direcciones.orEmpty() }
            .catch { emit(emptyList()) }
            .asLiveData()
    }

    // --------------------------------------------------
    // Agregar producto
    // --------------------------------------------------

    fun agregar(p: Producto): Boolean {

        val lista = local.items.value

        val existente = lista.find {
            it.productoId == p.id
        }

        if (
            existente != null &&
            existente.cantidad >= Validar.MAX_CANT
        ) {
            return false
        }

        local.guardar(
            if (existente != null) {
                lista.map {
                    if (it.productoId == p.id) {
                        it.copy(
                            cantidad = it.cantidad + 1
                        )
                    } else {
                        it
                    }
                }
            } else {
                lista + ItemPedido(
                    productoId = p.id,
                    nombre = p.nombreCompleto,
                    precio = p.precio,
                    cantidad = 1,
                    imagen = p.imagen
                )
            }
        )

        return true
    }

    // --------------------------------------------------
    // Cambiar cantidad
    // --------------------------------------------------

    fun cambiarCantidad(
        productoId: String,
        delta: Int
    ): Boolean {

        val lista = local.items.value

        val existente = lista.find {
            it.productoId == productoId
        }

        if (
            existente != null &&
            delta > 0 &&
            existente.cantidad >= Validar.MAX_CANT
        ) {
            return false
        }

        local.guardar(
            lista.map {
                if (it.productoId == productoId) {
                    it.copy(
                        cantidad = it.cantidad + delta
                    )
                } else {
                    it
                }
            }.filter {
                it.cantidad > 0
            }
        )

        return true
    }

    // --------------------------------------------------
    // Eliminar producto
    // --------------------------------------------------

    fun eliminar(productoId: String) {

        local.guardar(
            local.items.value.filterNot {
                it.productoId == productoId
            }
        )
    }

    // --------------------------------------------------
    // Vaciar carrito
    // --------------------------------------------------

    fun vaciar() {
        local.guardar(emptyList())
    }

    // --------------------------------------------------
    // Guardar dirección
    // --------------------------------------------------

    fun guardarDireccion(dir: String) {

        val usuario = auth.usuarioActual ?: return
        val limpia = dir.trim()

        if (limpia.isEmpty()) return

        val nuevas = (
                listOf(limpia) +
                        direcciones.value.orEmpty()
                )
            .distinct()
            .take(Usuario.MAX_DIRECCIONES)

        viewModelScope.launch {
            usuarios.guardarDirecciones(
                usuario.id,
                nuevas
            )
        }
    }

    // --------------------------------------------------
    // Confirmar pedido con pago
    // --------------------------------------------------

    fun confirmarPedido(
        dir: String,
        tel: String,
        fecha: String,
        obs: String,
        metodoPago: String,
        referenciaPago: String,
        comprobante: String = "",
        latitud: Double? = null,
        longitud: Double? = null
    ) {

        val items = local.items.value
        val usuario = auth.usuarioActual

        if (_enviando.value == true) return

        if (items.isEmpty()) {
            _error.value = Evento(
                "Tu carrito está vacío."
            )
            return
        }

        if (usuario == null) {
            _error.value = Evento(
                "Debes iniciar sesión para realizar tu pedido."
            )
            return
        }

        if (dir.trim().isEmpty()) {
            _error.value = Evento(
                "Ingresa una dirección de entrega."
            )
            return
        }

        if (tel.trim().isEmpty()) {
            _error.value = Evento(
                "Ingresa un teléfono de contacto."
            )
            return
        }

        if (metodoPago.trim().isEmpty()) {
            _error.value = Evento(
                "Selecciona un método de pago."
            )
            return
        }

        if (referenciaPago.trim().isEmpty()) {
            _error.value = Evento(
                "Ingresa la referencia del pago."
            )
            return
        }

        if (
            metodoPago != "Tarjeta" &&
            comprobante.trim().isEmpty()
        ) {
            _error.value = Evento(
                "Adjunta el comprobante de pago."
            )
            return
        }

        val fechaMs = Fechas.leerDia(fecha)
            ?: run {
                _error.value = Evento(
                    "Fecha de entrega no válida."
                )
                return
            }

        _enviando.value = true

        viewModelScope.launch {

            try {

                val nuevo = NuevoPedido(
                    usuario,
                    items,
                    dir.trim(),
                    tel.replace(" ", ""),
                    fechaMs,
                    obs.trim(),
                    latitud,
                    longitud
                )

                pedidos.crearConPago(
                    nuevo = nuevo,
                    creador = usuario,
                    metodo = metodoPago,
                    referencia = referenciaPago,
                    comprobante = comprobante
                ).fold(

                    onSuccess = { numero ->

                        // El carrito se vacía únicamente
                        // después de crear correctamente
                        // el pedido en Firebase.

                        local.guardar(emptyList())

                        _pedidoEnviado.value =
                            Evento(numero)
                    },

                    onFailure = { error ->

                        _error.value = Evento(
                            error.message
                                ?: "No se pudo registrar el pedido."
                        )
                    }
                )

            } catch (e: Exception) {

                _error.value = Evento(
                    e.message
                        ?: "Ocurrió un error al confirmar el pedido."
                )

            } finally {

                _enviando.value = false
            }
        }
    }
}
