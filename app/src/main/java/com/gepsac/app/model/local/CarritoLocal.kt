package com.gepsac.app.model.local

import android.content.Context
import com.gepsac.app.model.ItemPedido
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Carrito guardado en el teléfono (SharedPreferences), uno por usuario. Así no se pierde al cerrar la app.
 * Es una única instancia compartida: cualquier pantalla que agregue productos (catálogo, «volver a pedir»)
 * actualiza el mismo carrito y la barra inferior lo refleja al instante.
 */
class CarritoLocal(contexto: Context) {
    private val prefs = contexto.getSharedPreferences("carrito", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow<List<ItemPedido>>(emptyList())
    val items: StateFlow<List<ItemPedido>> = _items.asStateFlow()

    private var dueno: String? = null

    /** Carga el carrito guardado de este usuario. Si ya estaba abierto para él, no hace nada. */
    fun abrir(usuarioId: String) {
        if (dueno == usuarioId) return
        dueno = usuarioId
        _items.value = leer(usuarioId)
    }

    fun guardar(lista: List<ItemPedido>) {
        _items.value = lista
        val id = dueno ?: return
        prefs.edit().putString(clave(id), aJson(lista)).apply()
    }

    /** Al cerrar sesión: se suelta el carrito de la memoria (el guardado en el teléfono se conserva). */
    fun cerrar() {
        dueno = null
        _items.value = emptyList()
    }

    private fun clave(id: String) = "items_$id"

    private fun leer(id: String): List<ItemPedido> {
        val texto = prefs.getString(clave(id), null) ?: return emptyList()
        return try {
            val arr = JSONArray(texto)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ItemPedido(
                    productoId = o.getString("id"),
                    nombre = o.getString("nombre"),
                    precio = o.getDouble("precio"),
                    cantidad = o.getInt("cantidad"),
                    imagen = o.optString("imagen","")
                )
            }.filter { it.cantidad > 0 }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private fun aJson(lista: List<ItemPedido>): String {
        val arr = JSONArray()
        lista.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.productoId)
                    .put("nombre", it.nombre)
                    .put("precio", it.precio)
                    .put("cantidad", it.cantidad)
                    .put("imagen",it.imagen)
            )
        }
        return arr.toString()
    }
}
