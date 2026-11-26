
package com.gepsac.app.model.repository

import com.gepsac.app.model.NuevoPedido
import com.gepsac.app.model.Pedido
import com.gepsac.app.model.Usuario
import kotlinx.coroutines.flow.Flow

interface PedidoRepository {

    /**
     * Observa todos los pedidos.
     * Normalmente se utiliza desde la administración.
     */
    fun observarTodos(): Flow<List<Pedido>>

    /**
     * Observa únicamente los pedidos de un cliente.
     */
    fun observarDeCliente(clienteId: String): Flow<List<Pedido>>

    /**
     * Observa un pedido específico.
     */
    fun observar(id: String): Flow<Pedido?>

    /**
     * Crea un pedido manualmente.
     *
     * Se conserva para las operaciones administrativas
     * que todavía puedan necesitar esta función.
     */
    suspend fun crear(
        nuevo: NuevoPedido,
        creador: Usuario
    ): Result<Long>

    /**
     * Crea el pedido junto con la información del pago.
     *
     * Esta operación se utilizará desde el checkout.
     *
     * El pedido se crea como Registrado y el pago queda
     * Pendiente de revisión administrativa.
     */
    suspend fun crearConPago(
        nuevo: NuevoPedido,
        creador: Usuario,
        metodo: String,
        referencia: String,
        comprobante: String = ""
    ): Result<Long>

    /**
     * Avanza el pedido a la siguiente etapa.
     */
    suspend fun avanzar(id: String): Result<Unit>

    /**
     * Retrocede el pedido a la etapa anterior.
     */
    suspend fun retroceder(id: String): Result<Unit>

    /**
     * Registra un pago o reenvía uno observado.
     *
     * El pago queda pendiente de revisión.
     */
    suspend fun pagar(
        id: String,
        metodo: String,
        referencia: String,
        comprobante: String = ""
    ): Result<Unit>

    /**
     * El administrador aprueba el pago.
     *
     * El pedido pasa a Pagado.
     */
    suspend fun aprobarPago(id: String): Result<Unit>

    /**
     * El administrador observa el pago e indica
     * al cliente qué debe corregir.
     */
    suspend fun observarPago(
        id: String,
        motivo: String
    ): Result<Unit>

    /**
     * Elimina un pedido permitido por las reglas
     * del repositorio.
     */
    suspend fun eliminar(id: String): Result<Unit>
}
