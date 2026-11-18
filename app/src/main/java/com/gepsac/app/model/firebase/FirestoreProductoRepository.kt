package com.gepsac.app.model.firebase

import com.gepsac.app.model.Producto
import com.gepsac.app.model.repository.ProductoRepository
import com.gepsac.app.model.service.FirestoreService
import com.gepsac.app.model.service.intentar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirestoreProductoRepository(private val firestore: FirestoreService) : ProductoRepository {

    override fun observarProductos(): Flow<List<Producto>> =
        firestore.observarColeccion(Coleccion.PRODUCTOS).map { snap -> snap.documents.mapNotNull { it.aProducto() } }

    override fun nuevoId(): String = firestore.documentoNuevo(Coleccion.PRODUCTOS).id

    override suspend fun guardar(producto: Producto): Result<Unit> = intentar {
        firestore.guardar(Coleccion.PRODUCTOS, producto.id, producto.aMapa())
    }

    override suspend fun eliminar(id: String): Result<Unit> = intentar {
        firestore.eliminar(Coleccion.PRODUCTOS, id)
    }
}
