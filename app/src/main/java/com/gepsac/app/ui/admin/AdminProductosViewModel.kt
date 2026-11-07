package com.gepsac.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.gepsac.app.data.model.Producto
import com.gepsac.app.data.repository.ProductoRepository
import com.gepsac.app.util.Evento
import com.gepsac.app.util.alFallar
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Gestión de productos (administrador): ve todos, incluidos los inactivos. */
class AdminProductosViewModel(private val repo: ProductoRepository) : ViewModel() {
    private val _mensaje = MutableLiveData<Evento<String>>()
    val mensaje: LiveData<Evento<String>> = _mensaje

    val productos: LiveData<List<Producto>> = repo.observarProductos()
        .map { l -> l.sortedWith(compareBy({ it.marca }, { it.precio })) }
        .alFallar(emptyList()) { _mensaje.value = Evento(it) }
        .asLiveData()

    fun nuevoId(): String = repo.nuevoId()

    fun guardar(p: Producto) {
        viewModelScope.launch {
            repo.guardar(p).fold(
                onSuccess = { _mensaje.value = Evento("Producto guardado") },
                onFailure = { _mensaje.value = Evento(it.message ?: "No se pudo guardar el producto") }
            )
        }
    }

    fun eliminar(id: String) {
        viewModelScope.launch {
            repo.eliminar(id).fold(
                onSuccess = { _mensaje.value = Evento("Producto eliminado") },
                onFailure = { _mensaje.value = Evento(it.message ?: "No se pudo eliminar el producto") }
            )
        }
    }
}
