package com.gepsac.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.gepsac.app.model.Producto
import com.gepsac.app.model.repository.ProductoRepository
import com.gepsac.app.util.Evento
import com.gepsac.app.util.alFallar
import kotlinx.coroutines.flow.map

/** Formas de ordenar el catálogo. */
enum class OrdenProducto(val texto: String) {
    RECOMENDADO("Recomendado"),
    MENOR_PRECIO("Menor precio"),
    MAYOR_PRECIO("Mayor precio"),
    NOMBRE("A-Z")
}

/** Catálogo para el cliente: solo productos activos, con filtro por marca, búsqueda por texto y orden. */
class CatalogoViewModel(repo: ProductoRepository) : ViewModel() {
    private val _error = MutableLiveData<Evento<String>>()
    val error: LiveData<Evento<String>> = _error

    // Por defecto: por marca y luego por precio.
    private val activos: LiveData<List<Producto>> = repo.observarProductos()
        .map { l -> l.filter { it.activo }.sortedWith(compareBy({ it.marca }, { it.precio })) }
        .alFallar(emptyList()) { _error.value = Evento(it) }
        .asLiveData()

    private val marca = MutableLiveData<String?>(null)
    private val texto = MutableLiveData("")
    private val orden = MutableLiveData(OrdenProducto.RECOMENDADO)

    /** Lista ya filtrada y ordenada, lista para pintar. */
    val productos: LiveData<List<Producto>> = MediatorLiveData<List<Producto>>().apply {
        fun recalcular() {
            val base = activos.value ?: return   // aún no llegaron los datos
            val m = marca.value
            val q = texto.value.orEmpty().trim().lowercase()
            val filtrados = base.filter { p ->
                (m == null || p.marca == m) &&
                    (q.isEmpty() || p.nombreCompleto.lowercase().contains(q) || p.marca.lowercase().contains(q))
            }
            value = when (orden.value ?: OrdenProducto.RECOMENDADO) {
                OrdenProducto.RECOMENDADO -> filtrados
                OrdenProducto.MENOR_PRECIO -> filtrados.sortedBy { it.precio }
                OrdenProducto.MAYOR_PRECIO -> filtrados.sortedByDescending { it.precio }
                OrdenProducto.NOMBRE -> filtrados.sortedBy { it.nombreCompleto.lowercase() }
            }
        }
        addSource(activos) { recalcular() }
        addSource(marca) { recalcular() }
        addSource(texto) { recalcular() }
        addSource(orden) { recalcular() }
    }

    /** null = todas las marcas. */
    fun filtrarMarca(nombre: String?) { marca.value = nombre }

    fun buscar(consulta: String) { texto.value = consulta }

    fun ordenarPor(nuevo: OrdenProducto) { orden.value = nuevo }
}
