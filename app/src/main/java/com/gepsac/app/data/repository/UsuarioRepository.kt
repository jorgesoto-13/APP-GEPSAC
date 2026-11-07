package com.gepsac.app.data.repository

import com.gepsac.app.data.model.Usuario
import kotlinx.coroutines.flow.Flow

interface UsuarioRepository {
    /** Clientes registrados, en tiempo real (solo el administrador puede leerlos). */
    fun observarClientes(): Flow<List<Usuario>>
}
