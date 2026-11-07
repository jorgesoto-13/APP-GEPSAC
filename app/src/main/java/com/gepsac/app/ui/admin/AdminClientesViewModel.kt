package com.gepsac.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.gepsac.app.data.model.ESTADO_PAGADO
import com.gepsac.app.data.model.Usuario
import com.gepsac.app.data.repository.PedidoRepository
import com.gepsac.app.data.repository.UsuarioRepository
import com.gepsac.app.util.Evento
import com.gepsac.app.util.alFallar
import kotlinx.coroutines.flow.combine

/** Fila del listado de clientes: cuántos pedidos hizo y cuánto ha pagado. */
data class ResumenCliente(val cliente: Usuario, val pedidos: Int, val totalPagado: Double)

class AdminClientesViewModel(
    usuarios: UsuarioRepository,
    pedidos: PedidoRepository
) : ViewModel() {
    private val _error = MutableLiveData<Evento<String>>()
    val error: LiveData<Evento<String>> = _error

    /** Se recalcula solo cuando cambian los clientes o los pedidos. De mayor a menor total pagado. */
    val resumen: LiveData<List<ResumenCliente>> =
        combine(usuarios.observarClientes(), pedidos.observarTodos()) { clientes, todos ->
            clientes.map { c ->
                val mios = todos.filter { it.clienteId == c.id }
                ResumenCliente(c, mios.size, mios.filter { it.estado >= ESTADO_PAGADO }.sumOf { it.total })
            }.sortedByDescending { it.totalPagado }
        }
            .alFallar(emptyList()) { _error.value = Evento(it) }
            .asLiveData()
}
