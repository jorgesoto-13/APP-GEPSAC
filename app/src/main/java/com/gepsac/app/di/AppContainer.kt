package com.gepsac.app.di

import com.gepsac.app.model.firebase.FirebaseAuthRepository
import com.gepsac.app.model.firebase.FirestorePedidoRepository
import com.gepsac.app.model.firebase.FirestoreProductoRepository
import com.gepsac.app.model.firebase.FirestoreUsuarioRepository
import com.gepsac.app.model.repository.AuthRepository
import com.gepsac.app.model.repository.PedidoRepository
import com.gepsac.app.model.repository.ProductoRepository
import com.gepsac.app.model.repository.UsuarioRepository
import com.gepsac.app.model.service.AuthService
import com.gepsac.app.model.service.CloudFunctionsService
import com.gepsac.app.model.service.FirestoreService
import com.gepsac.app.model.service.StorageService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage

/**
 * Único lugar donde se crean los servicios y repositorios (inyección de dependencias manual).
 * Las pantallas y los ViewModels solo conocen las interfaces de los repositorios, nunca Firebase.
 *
 * Cadena: ViewModel → Repository → Service → SDK de Firebase.
 */
class AppContainer {
    // ---------- Servicios (hablan con el SDK de Firebase) ----------
    private val authService: AuthService by lazy { AuthService(FirebaseAuth.getInstance()) }
    private val firestoreService: FirestoreService by lazy { FirestoreService(FirebaseFirestore.getInstance()) }

    // Storage y Cloud Functions requieren el plan Blaze: existen en el código pero ningún repositorio los usa aún.
    val storageService: StorageService by lazy { StorageService(FirebaseStorage.getInstance()) }
    val cloudFunctionsService: CloudFunctionsService by lazy { CloudFunctionsService(FirebaseFunctions.getInstance()) }

    // ---------- Repositorios ----------
    val authRepository: AuthRepository by lazy { FirebaseAuthRepository(authService, firestoreService) }
    val usuarioRepository: UsuarioRepository by lazy { FirestoreUsuarioRepository(firestoreService) }
    val productoRepository: ProductoRepository by lazy { FirestoreProductoRepository(firestoreService) }
    val pedidoRepository: PedidoRepository by lazy { FirestorePedidoRepository(firestoreService) }
}
