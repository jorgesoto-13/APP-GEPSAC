package com.gepsac.app.data.repository

import com.gepsac.app.data.model.Producto
import kotlinx.coroutines.flow.Flow

interface ProductoRepository {
    /** Todos los productos (activos e inactivos), en tiempo real. */
    fun observarProductos(): Flow<List<Producto>>

    /** Id nuevo para un producto que aún no existe. */
    fun nuevoId(): String

    suspend fun guardar(producto: Producto): Result<Unit>

    suspend fun eliminar(id: String): Result<Unit>
}
