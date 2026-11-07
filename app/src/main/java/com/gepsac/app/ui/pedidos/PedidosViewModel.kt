package com.gepsac.app.ui.pedidos

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.gepsac.app.data.model.Pedido
import com.gepsac.app.data.repository.AuthRepository
import com.gepsac.app.data.repository.PedidoRepository
import com.gepsac.app.util.Evento
import com.gepsac.app.util.alFallar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Listado de pedidos: el cliente ve los suyos, el administrador ve todos. Incluye filtro por etapa y búsqueda. */
class PedidosViewModel(
    repo: PedidoRepository,
    auth: AuthRepository
) : ViewModel() {
    private val usuario = auth.usuarioActual
    val esAdmin: Boolean = usuario?.esAdmin == true

    private val _error = MutableLiveData<Evento<String>>()
    val error: LiveData<Evento<String>> = _error

    private val origen: Flow<List<Pedido>> = when {
        usuario == null -> flowOf(emptyList())
        usuario.esAdmin -> repo.observarTodos()
        else -> repo.observarDeCliente(usuario.id)
    }

    // Más recientes primero (la primera etapa guarda la fecha de creación).
    private val todos: LiveData<List<Pedido>> = origen
        .map { l -> l.sortedByDescending { it.tiempos.firstOrNull() ?: 0L } }
        .alFallar(emptyList()) { _error.value = Evento(it) }
        .asLiveData()

    private val filtroEstado = MutableLiveData(-1)
    private val busqueda = MutableLiveData("")

    /** Lista ya filtrada, lista para pintar. */
    val pedidos: LiveData<List<Pedido>> = MediatorLiveData<List<Pedido>>().apply {
        fun recalcular() {
            val base = todos.value ?: return   // aún no llegaron los datos: no mostrar "vacío" por error
            val estado = filtroEstado.value ?: -1
            val q = busqueda.value.orEmpty().trim().lowercase()
            value = base.filter {
                (estado < 0 || it.estado == estado) &&
                    (q.isEmpty() || it.clienteNombre.lowercase().contains(q) || it.numero.toString().contains(q))
            }
        }
        addSource(todos) { recalcular() }
        addSource(filtroEstado) { recalcular() }
        addSource(busqueda) { recalcular() }
    }

    /** -1 = todos. */
    fun filtrarPorEstado(estado: Int) { filtroEstado.value = estado }

    fun buscar(texto: String) { busqueda.value = texto }
}
