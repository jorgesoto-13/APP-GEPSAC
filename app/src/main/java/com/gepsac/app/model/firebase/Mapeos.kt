
package com.gepsac.app.model.firebase

import com.gepsac.app.model.ESTADOS
import com.gepsac.app.model.ESTADO_PAGADO
import com.gepsac.app.model.ESTADO_REGISTRADO
import com.gepsac.app.model.PAGO_APROBADO
import com.gepsac.app.model.PAGO_PENDIENTE
import com.gepsac.app.model.VERSION_ESTADOS_PEDIDO
import com.gepsac.app.model.ItemPedido
import com.gepsac.app.model.NuevoPedido
import com.gepsac.app.model.Pago
import com.gepsac.app.model.Pedido
import com.gepsac.app.model.Producto
import com.gepsac.app.model.Rol
import com.gepsac.app.model.Usuario
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import java.util.Date

// Conversión entre documentos de Firestore y los modelos de la app.

// --------------------------------------------------
// Usuario
// --------------------------------------------------

internal fun DocumentSnapshot.aUsuario(): Usuario? {

    if (!exists()) return null

    return Usuario(
        id = id,
        nombre = getString("nombre").orEmpty(),
        email = getString("email").orEmpty(),
        telefono = getString("telefono").orEmpty(),
        rol = Rol.desde(getString("rol")),
        direcciones = (get("direcciones") as? List<*>)
            .orEmpty()
            .filterIsInstance<String>()
    )
}

// --------------------------------------------------
// Producto
// --------------------------------------------------

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

// --------------------------------------------------
// Funciones auxiliares
// --------------------------------------------------

private fun Any?.aMs(): Long =
    (this as? Timestamp)?.toDate()?.time ?: 0L

private fun Any?.aTexto(): String =
    this as? String ?: ""

private fun Any?.aDouble(): Double =
    (this as? Number)?.toDouble() ?: 0.0

private fun Any?.aLong(): Long =
    (this as? Number)?.toLong() ?: 0L

/**
 * Convierte el número de estado antiguo al nuevo.
 *
 * Versión antigua:
 * 0 = Por registrar
 * 1 = Registrado
 * 2 = Pagado
 * 3 = En preparación
 * 4 = Despachado
 * 5 = Entregado
 *
 * Versión nueva:
 * 0 = Registrado
 * 1 = Pagado
 * 2 = En preparación
 * 3 = Despachado
 * 4 = Entregado
 */
private fun normalizarEstado(
    estadoAntiguo: Int,
    version: Int
): Int {

    if (version >= VERSION_ESTADOS_PEDIDO) {
        return estadoAntiguo.coerceIn(ESTADOS.indices)
    }

    val estadoNuevo = when (estadoAntiguo) {

        0, 1 -> ESTADO_REGISTRADO

        2 -> ESTADO_PAGADO

        3 -> 2

        4 -> 3

        5 -> 4

        else -> ESTADO_REGISTRADO
    }

    return estadoNuevo.coerceIn(ESTADOS.indices)
}

/**
 * Convierte los tiempos antiguos al orden de etapas actual.
 *
 * En la versión antigua existía una etapa adicional:
 * "Por registrar".
 */
private fun normalizarTiempos(
    mapaTiempos: Map<String, Any?>,
    version: Int
): List<Long> {

    return if (version >= VERSION_ESTADOS_PEDIDO) {

        List(ESTADOS.size) { i ->
            mapaTiempos[i.toString()].aMs()
        }

    } else {

        List(ESTADOS.size) { i ->

            val indiceAntiguo = i + 1

            val tiempoNuevo =
                mapaTiempos[indiceAntiguo.toString()].aMs()

            if (tiempoNuevo != 0L) {
                tiempoNuevo
            } else if (i == 0) {
                mapaTiempos["0"].aMs()
            } else {
                0L
            }
        }
    }
}

// --------------------------------------------------
// Pedido: Firestore -> modelo
// --------------------------------------------------

internal fun DocumentSnapshot.aPedido(): Pedido? {

    if (!exists()) return null

    val d = getData(ServerTimestampBehavior.ESTIMATE)
        ?: return null

    val version = (d["versionEstado"] as? Number)
        ?.toInt()
        ?: 1

    val estadoOriginal = (d["estado"] as? Number)
        ?.toInt()
        ?: 0

    val estado = normalizarEstado(
        estadoAntiguo = estadoOriginal,
        version = version
    )

    @Suppress("UNCHECKED_CAST")
    val mapaTiempos =
        d["tiempos"] as? Map<String, Any?>
            ?: emptyMap()

    val tiempos = normalizarTiempos(
        mapaTiempos = mapaTiempos,
        version = version
    )

    val items = (d["items"] as? List<*>)
        .orEmpty()
        .filterIsInstance<Map<*, *>>()
        .map { m ->

            ItemPedido(
                productoId = m["productoId"].aTexto(),
                nombre = m["nombre"].aTexto(),
                precio = m["precio"].aDouble(),
                cantidad = (m["cantidad"] as? Number)
                    ?.toInt()
                    ?: 0
            )
        }

    val pago = (d["pago"] as? Map<*, *>)?.let { m ->

        val estadoRevisionGuardado =
            m["estadoRevision"].aTexto()

        val estadoRevision = estadoRevisionGuardado.ifEmpty {

            if (estado >= ESTADO_PAGADO) {
                PAGO_APROBADO
            } else {
                PAGO_PENDIENTE
            }
        }

        Pago(
            metodo = m["metodo"].aTexto(),
            monto = m["monto"].aDouble(),
            referencia = m["referencia"].aTexto(),
            fecha = m["fecha"].aMs(),
            comprobante = m["comprobante"].aTexto(),
            estadoRevision = estadoRevision,
            observacionAdmin = m["observacionAdmin"].aTexto(),
            fechaRevision = m["fechaRevision"].aMs()
        )
    }

    return Pedido(
        id = id,
        numero = d["numero"].aLong(),
        clienteId = d["clienteId"].aTexto(),
        clienteNombre = d["clienteNombre"].aTexto(),
        clienteEmail = d["clienteEmail"].aTexto(),
        telefono = d["telefono"].aTexto(),
        direccion = d["direccion"].aTexto(),
        fechaEntrega = d["fechaEntrega"].aMs(),
        obs = d["observacion"].aTexto(),
        items = items,
        total = d["total"].aDouble(),
        estado = estado,
        tiempos = tiempos,
        pago = pago,
        creadoPor = d["creadoPor"].aTexto(),
        latitud = (d["latitud"] as? Number)?.toDouble(),
        longitud = (d["longitud"] as? Number)?.toDouble()
    )
}

// --------------------------------------------------
// Pedido nuevo: modelo -> Firestore
// --------------------------------------------------

/**
 * Documento de un pedido nuevo.
 * Las fechas las establece el servidor.
 */
internal fun NuevoPedido.aMapa(
    numero: Long,
    creadorId: String,
    estadoInicial: Int
): Map<String, Any> {

    val estadoSeguro =
        estadoInicial.coerceIn(ESTADOS.indices)

    val tiempos = (0..estadoSeguro).associate { indice ->
        indice.toString() to FieldValue.serverTimestamp()
    }

    val base: Map<String, Any> = mapOf(
        "numero" to numero,
        "clienteId" to cliente.id,
        "clienteNombre" to cliente.nombre,
        "clienteEmail" to cliente.email,
        "creadoPor" to creadorId,
        "telefono" to telefono,
        "direccion" to direccion,
        "fechaEntrega" to Timestamp(Date(fechaEntrega)),
        "observacion" to observacion,
        "items" to items.map { item ->

            mapOf(
                "productoId" to item.productoId,
                "nombre" to item.nombre,
                "precio" to item.precio,
                "cantidad" to item.cantidad
            )
        },
        "total" to total,
        "estado" to estadoSeguro,
        "versionEstado" to VERSION_ESTADOS_PEDIDO,
        "tiempos" to tiempos,
        "creadoEn" to FieldValue.serverTimestamp()
    )

    return if (latitud != null && longitud != null) {

        base + mapOf(
            "latitud" to latitud,
            "longitud" to longitud
        )

    } else {

        base
    }
}
