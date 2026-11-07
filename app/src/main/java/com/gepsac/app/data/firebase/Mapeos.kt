package com.gepsac.app.data.firebase

import com.gepsac.app.data.model.ESTADOS
import com.gepsac.app.data.model.ItemPedido
import com.gepsac.app.data.model.NuevoPedido
import com.gepsac.app.data.model.Pago
import com.gepsac.app.data.model.Pedido
import com.gepsac.app.data.model.Producto
import com.gepsac.app.data.model.Rol
import com.gepsac.app.data.model.Usuario
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import java.util.Date

// Conversión entre documentos de Firestore y los modelos de la app. Ver docs/MODELO_FIRESTORE.md.

// ---------- Usuario ----------

internal fun DocumentSnapshot.aUsuario(): Usuario? {
    if (!exists()) return null
    return Usuario(
        id = id,
        nombre = getString("nombre").orEmpty(),
        email = getString("email").orEmpty(),
        telefono = getString("telefono").orEmpty(),
        rol = Rol.desde(getString("rol"))
    )
}

// ---------- Producto ----------

internal fun DocumentSnapshot.aProducto(): Producto? {
    if (!exists()) return null
    return Producto(
        id = id,
        nombre = getString("nombre").orEmpty(),
        marca = getString("marca").orEmpty(),
        presentacion = getString("presentacion").orEmpty(),
        precio = getDouble("precio") ?: 0.0,
        activo = getBoolean("activo") ?: true,
        imagen = getString("imagen").orEmpty()
    )
}

internal fun Producto.aMapa(): Map<String, Any> = mapOf(
    "nombre" to nombre,
    "marca" to marca,
    "presentacion" to presentacion,
    "precio" to precio,
    "activo" to activo,
    "imagen" to imagen,
    "actualizadoEn" to FieldValue.serverTimestamp()
)

// ---------- Pedido ----------

private fun Any?.aMs(): Long = (this as? Timestamp)?.toDate()?.time ?: 0L
private fun Any?.aTexto(): String = this as? String ?: ""
private fun Any?.aDouble(): Double = (this as? Number)?.toDouble() ?: 0.0

internal fun DocumentSnapshot.aPedido(): Pedido? {
    if (!exists()) return null
    // ESTIMATE: mientras el servidor no confirma una fecha, se usa la hora del teléfono (así no llega null).
    val d = getData(ServerTimestampBehavior.ESTIMATE) ?: return null

    @Suppress("UNCHECKED_CAST")
    val mapaTiempos = d["tiempos"] as? Map<String, Any?> ?: emptyMap()
    val tiempos = List(ESTADOS.size) { i -> mapaTiempos[i.toString()].aMs() }

    val items = (d["items"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { m ->
        ItemPedido(
            productoId = m["productoId"].aTexto(),
            nombre = m["nombre"].aTexto(),
            precio = m["precio"].aDouble(),
            cantidad = (m["cantidad"] as? Number)?.toInt() ?: 0
        )
    }

    val pago = (d["pago"] as? Map<*, *>)?.let { m ->
        Pago(
            metodo = m["metodo"].aTexto(),
            monto = m["monto"].aDouble(),
            referencia = m["referencia"].aTexto(),
            fecha = m["fecha"].aMs()
        )
    }

    return Pedido(
        id = id,
        numero = (d["numero"] as? Number)?.toLong() ?: 0L,
        clienteId = d["clienteId"].aTexto(),
        clienteNombre = d["clienteNombre"].aTexto(),
        clienteEmail = d["clienteEmail"].aTexto(),
        telefono = d["telefono"].aTexto(),
        direccion = d["direccion"].aTexto(),
        fechaEntrega = d["fechaEntrega"].aMs(),
        obs = d["observacion"].aTexto(),
        items = items,
        total = d["total"].aDouble(),
        estado = (d["estado"] as? Number)?.toInt() ?: 0,
        tiempos = tiempos,
        pago = pago,
        creadoPor = d["creadoPor"].aTexto()
    )
}

/** Documento de un pedido nuevo. Las fechas las pone el servidor. */
internal fun NuevoPedido.aMapa(numero: Long, creadorId: String, estadoInicial: Int): Map<String, Any> {
    // Una clave por etapa alcanzada: "0" = Por registrar, "1" = Registrado, ...
    val tiempos = (0..estadoInicial).associate { it.toString() to FieldValue.serverTimestamp() }
    return mapOf(
        "numero" to numero,
        "clienteId" to cliente.id,
        "clienteNombre" to cliente.nombre,
        "clienteEmail" to cliente.email,
        "creadoPor" to creadorId,
        "telefono" to telefono,
        "direccion" to direccion,
        "fechaEntrega" to Timestamp(Date(fechaEntrega)),
        "observacion" to observacion,
        "items" to items.map {
            mapOf(
                "productoId" to it.productoId,
                "nombre" to it.nombre,
                "precio" to it.precio,
                "cantidad" to it.cantidad
            )
        },
        "total" to total,
        "estado" to estadoInicial,
        "tiempos" to tiempos,
        "creadoEn" to FieldValue.serverTimestamp()
    )
}
