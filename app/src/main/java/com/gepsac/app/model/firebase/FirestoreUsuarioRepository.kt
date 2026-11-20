package com.gepsac.app.model.firebase

import com.gepsac.app.model.Rol
import com.gepsac.app.model.Usuario
import com.gepsac.app.model.repository.UsuarioRepository
import com.gepsac.app.model.service.FirestoreService
import com.gepsac.app.model.service.intentar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirestoreUsuarioRepository(private val firestore: FirestoreService) : UsuarioRepository {

    override fun observarClientes(): Flow<List<Usuario>> =
        firestore.observarColeccion(Coleccion.USUARIOS) { it.whereEqualTo("rol", Rol.CLIENTE.clave) }
            .map { snap -> snap.documents.mapNotNull { it.aUsuario() }.sortedBy { it.nombre.lowercase() } }

    override fun observarUsuario(id: String): Flow<Usuario?> =
        firestore.observarDocumento(Coleccion.USUARIOS, id).map { it.aUsuario() }

    override suspend fun guardarDirecciones(id: String, direcciones: List<String>): Result<Unit> = intentar {
        firestore.actualizar(Coleccion.USUARIOS, id, mapOf("direcciones" to direcciones))
    }
}
