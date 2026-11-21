
package com.gepsac.app.model.firebase

import com.gepsac.app.model.ESTADO_PAGADO
import com.gepsac.app.model.ESTADO_POR_REGISTRAR
import com.gepsac.app.model.ESTADO_REGISTRADO
import com.gepsac.app.model.PAGO_APROBADO
import com.gepsac.app.model.PAGO_OBSERVADO
import com.gepsac.app.model.PAGO_PENDIENTE
import com.gepsac.app.model.ULTIMO_ESTADO
import com.gepsac.app.model.NuevoPedido
import com.gepsac.app.model.Pedido
import com.gepsac.app.model.Usuario
import com.gepsac.app.model.repository.ErrorApp
import com.gepsac.app.model.repository.PedidoRepository
import com.gepsac.app.model.service.FirestoreService
import com.gepsac.app.model.service.intentar
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirestorePedidoRepository(
    private val firestore: FirestoreService
) : PedidoRepository {

    private fun pedido(id: String) =
        firestore.documento(Coleccion.PEDIDOS, id)

    private val contador
        get() = firestore.documento(
            Coleccion.CONTADORES,
            Coleccion.DOC_CONTADOR_PEDIDOS
        )

    // ---------- Lectura en tiempo real ----------

    override fun observarTodos(): Flow<List<Pedido>> =
        firestore.observarColeccion(Coleccion.PEDIDOS)
            .map { snap ->
                snap.documents.mapNotNull { it.aPedido() }
            }

    override fun observarDeCliente(clienteId: String): Flow<List<Pedido>> =
        firestore.observarColeccion(Coleccion.PEDIDOS) {
            it.whereEqualTo("clienteId", clienteId)
        }.map { snap ->
            snap.documents.mapNotNull { it.aPedido() }
        }

    override fun observar(id: String): Flow<Pedido?> =
        firestore.observarDocumento(Coleccion.PEDIDOS, id)
            .map { it.aPedido() }

    // ---------- Creación ----------

    override suspend fun crear(
        nuevo: NuevoPedido,
        creador: Usuario
    ): Result<Long> = intentar {
        val ref = firestore.documentoNuevo(Coleccion.PEDIDOS)
        val estadoInicial =
            if (creador.esAdmin) ESTADO_REGISTRADO
            else ESTADO_POR_REGISTRAR

        firestore.transaccion { tx ->
            val ultimo = tx.get(contador).getLong("ultimo")
                ?: throw ErrorApp(
                    "Falta configurar el contador de pedidos en Firebase (ver firebase/README.md)"
                )

            val numero = ultimo + 1
            tx.update(contador, "ultimo", numero)
            tx.set(ref, nuevo.aMapa(numero, creador.id, estadoInicial))
            numero
        }
    }

    // ---------- Avance y retroceso ----------

    override suspend fun avanzar(id: String): Result<Unit> = intentar {
        val ref = pedido(id)

        firestore.transaccion { tx ->
            val e = estadoDe(tx.get(ref))

            if (e == ESTADO_REGISTRADO) {
                throw ErrorApp(
                    "El pedido debe tener un pago aprobado antes de avanzar."
                )
            }

            if (e >= ULTIMO_ESTADO) {
                throw ErrorApp("El pedido ya está en la última etapa")
            }

            tx.update(
                ref,
                "estado", e + 1,
                "tiempos.${e + 1}", FieldValue.serverTimestamp()
            )
            Unit
        }
    }

    override suspend fun retroceder(id: String): Result<Unit> = intentar {
        val ref = pedido(id)

        firestore.transaccion { tx ->
            val e = estadoDe(tx.get(ref))

            if (e == ESTADO_POR_REGISTRAR) {
                throw ErrorApp("El pedido ya está en la primera etapa")
            }

            if (e == ESTADO_PAGADO) {
                throw ErrorApp("No se puede retroceder un pedido pagado")
            }

            tx.update(
                ref,
                "estado", e - 1,
                "tiempos.$e", FieldValue.delete()
            )
            Unit
        }
    }

    // ---------- Registro del pago por el cliente ----------

    override suspend fun pagar(
        id: String,
        metodo: String,
        referencia: String,
        comprobante: String
    ): Result<Unit> = intentar {
        val ref = pedido(id)

        firestore.transaccion { tx ->
            val snap = tx.get(ref)

            if (estadoDe(snap) != ESTADO_REGISTRADO) {
                throw ErrorApp("Este pedido no está listo para recibir un pago")
            }

            val pagoAnterior =
                snap.get("pago") as? Map<*, *>

            val estadoAnterior =
                pagoAnterior?.get("estadoRevision") as? String

            if (estadoAnterior == PAGO_PENDIENTE) {
                throw ErrorApp("Tu pago ya está pendiente de revisión")
            }

            if (estadoAnterior == PAGO_APROBADO) {
                throw ErrorApp("Este pedido ya tiene un pago aprobado")
            }

            val pago = mutableMapOf<String, Any>(
                "metodo" to metodo,
                "monto" to (snap.getDouble("total") ?: 0.0),
                "referencia" to referencia,
                "fecha" to FieldValue.serverTimestamp(),
                "estadoRevision" to PAGO_PENDIENTE,
                "observacionAdmin" to "",
                "fechaRevision" to 0L
            )

            if (comprobante.isNotEmpty()) {
                pago["comprobante"] = comprobante
            }

            tx.update(ref, "pago", pago)

            // IMPORTANTE:
            // El estado del pedido permanece en Registrado.
            // Solo el administrador podrá aprobar el pago.
            Unit
        }
    }

    // ---------- Revisión administrativa ----------

    override suspend fun aprobarPago(id: String): Result<Unit> = intentar {
        val ref = pedido(id)

        firestore.transaccion { tx ->
            val snap = tx.get(ref)

            if (estadoDe(snap) != ESTADO_REGISTRADO) {
                throw ErrorApp("El pedido ya no está esperando la revisión del pago")
            }

            val pago = snap.get("pago") as? Map<*, *>
                ?: throw ErrorApp("El cliente todavía no ha enviado un pago")

            val estadoRevision =
                pago["estadoRevision"] as? String ?: PAGO_PENDIENTE

            if (estadoRevision != PAGO_PENDIENTE) {
                throw ErrorApp("Este pago ya fue revisado")
            }

            tx.update(
                ref,
                "estado", ESTADO_PAGADO,
                "tiempos.$ESTADO_PAGADO", FieldValue.serverTimestamp(),
                "pago.estadoRevision", PAGO_APROBADO,
                "pago.observacionAdmin", "",
                "pago.fechaRevision", FieldValue.serverTimestamp()
            )

            Unit
        }
    }

    override suspend fun observarPago(
        id: String,
        motivo: String
    ): Result<Unit> = intentar {
        val motivoLimpio = motivo.trim()

        if (motivoLimpio.length < 5) {
            throw ErrorApp("Escribe un motivo de al menos 5 caracteres")
        }

        if (motivoLimpio.length > 200) {
            throw ErrorApp("El motivo no puede superar los 200 caracteres")
        }

        val ref = pedido(id)

        firestore.transaccion { tx ->
            val snap = tx.get(ref)

            if (estadoDe(snap) != ESTADO_REGISTRADO) {
                throw ErrorApp("El pedido ya no está esperando la revisión del pago")
            }

            val pago = snap.get("pago") as? Map<*, *>
                ?: throw ErrorApp("El cliente todavía no ha enviado un pago")

            val estadoRevision =
                pago["estadoRevision"] as? String ?: PAGO_PENDIENTE

            if (estadoRevision != PAGO_PENDIENTE) {
                throw ErrorApp("Este pago ya fue revisado")
            }

            tx.update(
                ref,
                "pago.estadoRevision", PAGO_OBSERVADO,
                "pago.observacionAdmin", motivoLimpio,
                "pago.fechaRevision", FieldValue.serverTimestamp()
            )

            Unit
        }
    }

    // ---------- Eliminación ----------

    override suspend fun eliminar(id: String): Result<Unit> = intentar {
        if (
            estadoDe(firestore.obtener(Coleccion.PEDIDOS, id)) >= ESTADO_PAGADO
        ) {
            throw ErrorApp("No se puede eliminar un pedido ya pagado")
        }

        firestore.eliminar(Coleccion.PEDIDOS, id)
    }

    private fun estadoDe(snap: DocumentSnapshot): Int {
        if (!snap.exists()) {
            throw ErrorApp("El pedido ya no existe")
        }

        return snap.getLong("estado")?.toInt()
            ?: ESTADO_POR_REGISTRAR
    }
}
