
package com.gepsac.app.model.repository

import com.gepsac.app.model.NuevoPedido
import com.gepsac.app.model.Pedido
import com.gepsac.app.model.Usuario
import kotlinx.coroutines.flow.Flow

interface PedidoRepository {
    fun observarTodos(): Flow<List<Pedido>>

    fun observarDeCliente(clienteId: String): Flow<List<Pedido>>

    fun observar(id: String): Flow<Pedido?>

    suspend fun crear(nuevo: NuevoPedido, creador: Usuario): Result<Long>

    suspend fun avanzar(id: String): Result<Unit>

    suspend fun retroceder(id: String): Result<Unit>

    /** Registra o reenvía un pago. Queda pendiente de revisión administrativa. */
    suspend fun pagar(
        id: String,
        metodo: String,
        referencia: String,
        comprobante: String = ""
    ): Result<Unit>

    /** El administrador aprueba el pago y mueve el pedido a "Pagado". */
    suspend fun aprobarPago(id: String): Result<Unit>

    /** El administrador observa el pago indicando el motivo. */
    suspend fun observarPago(id: String, motivo: String): Result<Unit>

    suspend fun eliminar(id: String): Result<Unit>
}
