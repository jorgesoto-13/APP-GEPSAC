package com.gepsac.app.model.firebase

import com.gepsac.app.model.Producto
import com.gepsac.app.model.repository.ProductoRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirestoreProductoRepository(private val db: FirebaseFirestore) : ProductoRepository {
    private val productos get() = db.collection(Coleccion.PRODUCTOS)

    override fun observarProductos(): Flow<List<Producto>> =
        productos.cambios().map { snap -> snap.documents.mapNotNull { it.aProducto() } }

    override fun nuevoId(): String = productos.document().id

    override suspend fun guardar(producto: Producto): Result<Unit> = intentar {
        productos.document(producto.id).set(producto.aMapa()).esperar()
        Unit
    }

    override suspend fun eliminar(id: String): Result<Unit> = intentar {
        productos.document(id).delete().esperar()
        Unit
    }
}
