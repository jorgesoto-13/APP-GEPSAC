package com.gepsac.app.model.repository

import com.gepsac.app.model.NuevoPedido
import com.gepsac.app.model.Pedido
import com.gepsac.app.model.Usuario
import kotlinx.coroutines.flow.Flow

interface PedidoRepository {
    /** Todos los pedidos (administrador). */
    fun observarTodos(): Flow<List<Pedido>>

    /** Solo los pedidos de un cliente. */
    fun observarDeCliente(clienteId: String): Flow<List<Pedido>>

    /** Un pedido; emite null si se elimina o no existe. */
    fun observar(id: String): Flow<Pedido?>

    /**
     * Crea el pedido con el siguiente número. Si lo crea el administrador entra "Registrado",
     * si lo crea el cliente entra "Por registrar". Devuelve el número asignado.
     */
    suspend fun crear(nuevo: NuevoPedido, creador: Usuario): Result<Long>

    suspend fun avanzar(id: String): Result<Unit>

    suspend fun retroceder(id: String): Result<Unit>

    /** Registra el pago de un pedido "Registrado" y lo pasa a "Pagado". */
    suspend fun pagar(id: String, metodo: String, referencia: String): Result<Unit>

    /** Solo se puede eliminar un pedido que aún no está pagado. */
    suspend fun eliminar(id: String): Result<Unit>
}
