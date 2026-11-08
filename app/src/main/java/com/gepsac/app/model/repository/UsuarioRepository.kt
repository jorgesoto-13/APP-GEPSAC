package com.gepsac.app.model.repository

import com.gepsac.app.model.Usuario
import kotlinx.coroutines.flow.Flow

interface UsuarioRepository {
    /** Clientes registrados, en tiempo real (solo el administrador puede leerlos). */
    fun observarClientes(): Flow<List<Usuario>>
}
