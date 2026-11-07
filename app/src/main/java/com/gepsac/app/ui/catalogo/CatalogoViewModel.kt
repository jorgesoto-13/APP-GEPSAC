package com.gepsac.app.ui.catalogo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.gepsac.app.data.model.Producto
import com.gepsac.app.data.repository.ProductoRepository
import com.gepsac.app.util.Evento
import com.gepsac.app.util.alFallar
import kotlinx.coroutines.flow.map

/** Catálogo para el cliente: solo productos activos, ordenados por marca y precio. */
class CatalogoViewModel(repo: ProductoRepository) : ViewModel() {
    private val _error = MutableLiveData<Evento<String>>()
    val error: LiveData<Evento<String>> = _error

    val productos: LiveData<List<Producto>> = repo.observarProductos()
        .map { l -> l.filter { it.activo }.sortedWith(compareBy({ it.marca }, { it.precio })) }
        .alFallar(emptyList()) { _error.value = Evento(it) }
        .asLiveData()
}
