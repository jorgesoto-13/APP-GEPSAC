package com.gepsac.app.di

import com.gepsac.app.model.firebase.FirebaseAuthRepository
import com.gepsac.app.model.firebase.FirestorePedidoRepository
import com.gepsac.app.model.firebase.FirestoreProductoRepository
import com.gepsac.app.model.firebase.FirestoreUsuarioRepository
import com.gepsac.app.model.repository.AuthRepository
import com.gepsac.app.model.repository.PedidoRepository
import com.gepsac.app.model.repository.ProductoRepository
import com.gepsac.app.model.repository.UsuarioRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Único lugar donde se crean los repositorios (inyección de dependencias manual).
 * Las pantallas y los ViewModels solo conocen las interfaces, nunca Firebase.
 */
class AppContainer {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    val authRepository: AuthRepository by lazy { FirebaseAuthRepository(auth, db) }
    val usuarioRepository: UsuarioRepository by lazy { FirestoreUsuarioRepository(db) }
    val productoRepository: ProductoRepository by lazy { FirestoreProductoRepository(db) }
    val pedidoRepository: PedidoRepository by lazy { FirestorePedidoRepository(db) }
}
