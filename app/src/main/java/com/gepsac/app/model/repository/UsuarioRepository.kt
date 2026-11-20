package com.gepsac.app.model.repository

import com.gepsac.app.model.Usuario
import kotlinx.coroutines.flow.Flow

interface UsuarioRepository {
    /** Clientes registrados, en tiempo real (solo el administrador puede leerlos). */
    fun observarClientes(): Flow<List<Usuario>>

    /** Un usuario en tiempo real (el propio perfil); emite null si no existe. */
    fun observarUsuario(id: String): Flow<Usuario?>

    /** Reemplaza las direcciones de entrega guardadas del usuario. */
    suspend fun guardarDirecciones(id: String, direcciones: List<String>): Result<Unit>
}
