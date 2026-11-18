package com.gepsac.app.model.firebase

import com.gepsac.app.model.ESTADO_PAGADO
import com.gepsac.app.model.ESTADO_POR_REGISTRAR
import com.gepsac.app.model.ESTADO_REGISTRADO
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

class FirestorePedidoRepository(private val firestore: FirestoreService) : PedidoRepository {
    private fun pedido(id: String) = firestore.documento(Coleccion.PEDIDOS, id)
    private val contador get() = firestore.documento(Coleccion.CONTADORES, Coleccion.DOC_CONTADOR_PEDIDOS)

    // ---------- Lectura en tiempo real ----------

    override fun observarTodos(): Flow<List<Pedido>> =
        firestore.observarColeccion(Coleccion.PEDIDOS).map { snap -> snap.documents.mapNotNull { it.aPedido() } }

    // Sin orderBy a propósito: así no hace falta crear un índice compuesto. La app ordena la lista.
    override fun observarDeCliente(clienteId: String): Flow<List<Pedido>> =
        firestore.observarColeccion(Coleccion.PEDIDOS) { it.whereEqualTo("clienteId", clienteId) }
            .map { snap -> snap.documents.mapNotNull { it.aPedido() } }

    override fun observar(id: String): Flow<Pedido?> =
        firestore.observarDocumento(Coleccion.PEDIDOS, id).map { it.aPedido() }

    // ---------- Escritura ----------

    override suspend fun crear(nuevo: NuevoPedido, creador: Usuario): Result<Long> = intentar {
        val ref = firestore.documentoNuevo(Coleccion.PEDIDOS)
        val estadoInicial = if (creador.esAdmin) ESTADO_REGISTRADO else ESTADO_POR_REGISTRAR
        // El número sale de un contador incrementado en la misma transacción: nunca se repite.
        firestore.transaccion { tx ->
            val ultimo = tx.get(contador).getLong("ultimo")
                ?: throw ErrorApp("Falta configurar el contador de pedidos en Firebase (ver firebase/README.md)")
            val numero = ultimo + 1
            tx.update(contador, "ultimo", numero)
            tx.set(ref, nuevo.aMapa(numero, creador.id, estadoInicial))
            numero
        }
    }

    override suspend fun avanzar(id: String): Result<Unit> = intentar {
        val ref = pedido(id)
        firestore.transaccion { tx ->
            val e = estadoDe(tx.get(ref))
            if (e == ESTADO_REGISTRADO) throw ErrorApp("Esperando el pago del cliente. No se puede avanzar hasta que pague.")
            if (e >= ULTIMO_ESTADO) throw ErrorApp("El pedido ya está en la última etapa")
            tx.update(ref, "estado", e + 1, "tiempos.${e + 1}", FieldValue.serverTimestamp())
            Unit
        }
    }

    override suspend fun retroceder(id: String): Result<Unit> = intentar {
        val ref = pedido(id)
        firestore.transaccion { tx ->
            val e = estadoDe(tx.get(ref))
            if (e == ESTADO_POR_REGISTRAR) throw ErrorApp("El pedido ya está en la primera etapa")
            if (e == ESTADO_PAGADO) throw ErrorApp("No se puede retroceder un pedido pagado")
            tx.update(ref, "estado", e - 1, "tiempos.$e", FieldValue.delete())
            Unit
        }
    }

    override suspend fun pagar(id: String, metodo: String, referencia: String): Result<Unit> = intentar {
        val ref = pedido(id)
        firestore.transaccion { tx ->
            val snap = tx.get(ref)
            if (estadoDe(snap) != ESTADO_REGISTRADO) throw ErrorApp("Este pedido no está listo para pagar")
            val pago = mapOf(
                "metodo" to metodo,
                "monto" to (snap.getDouble("total") ?: 0.0),
                "referencia" to referencia,
                "fecha" to FieldValue.serverTimestamp()
            )
            tx.update(ref, "estado", ESTADO_PAGADO, "tiempos.$ESTADO_PAGADO", FieldValue.serverTimestamp(), "pago", pago)
            Unit
        }
    }

    override suspend fun eliminar(id: String): Result<Unit> = intentar {
        if (estadoDe(firestore.obtener(Coleccion.PEDIDOS, id)) >= ESTADO_PAGADO) {
            throw ErrorApp("No se puede eliminar un pedido ya pagado")
        }
        firestore.eliminar(Coleccion.PEDIDOS, id)
    }

    private fun estadoDe(snap: DocumentSnapshot): Int {
        if (!snap.exists()) throw ErrorApp("El pedido ya no existe")
        return snap.getLong("estado")?.toInt() ?: ESTADO_POR_REGISTRAR
    }
}
