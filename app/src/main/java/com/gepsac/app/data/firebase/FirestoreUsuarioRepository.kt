package com.gepsac.app.data.firebase

import com.gepsac.app.data.model.Rol
import com.gepsac.app.data.model.Usuario
import com.gepsac.app.data.repository.UsuarioRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirestoreUsuarioRepository(private val db: FirebaseFirestore) : UsuarioRepository {

    override fun observarClientes(): Flow<List<Usuario>> =
        db.collection(Coleccion.USUARIOS)
            .whereEqualTo("rol", Rol.CLIENTE.clave)
            .cambios()
            .map { snap -> snap.documents.mapNotNull { it.aUsuario() }.sortedBy { it.nombre.lowercase() } }
}
