
package com.gepsac.app.model.firebase

import com.gepsac.app.model.ESTADO_PAGADO
import com.gepsac.app.model.ESTADO_REGISTRADO
import com.gepsac.app.model.PAGO_APROBADO
import com.gepsac.app.model.PAGO_OBSERVADO
import com.gepsac.app.model.PAGO_PENDIENTE
import com.gepsac.app.model.ULTIMO_ESTADO
import com.gepsac.app.model.VERSION_ESTADOS_PEDIDO
import com.gepsac.app.model.METODOS_PAGO
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

    // --------------------------------------------------
    // Lectura en tiempo real
    // --------------------------------------------------

    override fun observarTodos(): Flow<List<Pedido>> =
        firestore.observarColeccion(Coleccion.PEDIDOS)
            .map { snap ->
                snap.documents.mapNotNull { it.aPedido() }
            }

    override fun observarDeCliente(
        clienteId: String
    ): Flow<List<Pedido>> =
        firestore.observarColeccion(Coleccion.PEDIDOS) {
            it.whereEqualTo("clienteId", clienteId)
        }.map { snap ->
            snap.documents.mapNotNull { it.aPedido() }
        }

    override fun observar(id: String): Flow<Pedido?> =
        firestore.observarDocumento(Coleccion.PEDIDOS, id)
            .map { it.aPedido() }

    // --------------------------------------------------
    // Creación manual del pedido
    // --------------------------------------------------

    override suspend fun crear(
        nuevo: NuevoPedido,
        creador: Usuario
    ): Result<Long> = intentar {

        val ref = firestore.documentoNuevo(Coleccion.PEDIDOS)

        firestore.transaccion { tx ->

            val ultimo = tx.get(contador).getLong("ultimo")
                ?: throw ErrorApp(
                    "Falta configurar el contador de pedidos en Firebase."
                )

            val numero = ultimo + 1

            val mapa = nuevo.aMapa(
                numero = numero,
                creadorId = creador.id,
                estadoInicial = ESTADO_REGISTRADO
            ).toMutableMap()

            mapa["versionEstado"] = VERSION_ESTADOS_PEDIDO

            tx.update(contador, "ultimo", numero)
            tx.set(ref, mapa)

            numero
        }
    }

    // --------------------------------------------------
    // Crear pedido desde checkout + pago
    // --------------------------------------------------

    override suspend fun crearConPago(
        nuevo: NuevoPedido,
        creador: Usuario,
        metodo: String,
        referencia: String,
        comprobante: String
    ): Result<Long> = intentar {

        val metodoLimpio = metodo.trim()
        val referenciaLimpia = referencia.trim()
        val comprobanteLimpio = comprobante.trim()

        if (metodoLimpio !in METODOS_PAGO) {
            throw ErrorApp("Selecciona un método de pago válido.")
        }

        if (referenciaLimpia.isBlank()) {
            throw ErrorApp("Ingresa la referencia del pago.")
        }

        if (
            metodoLimpio != "Tarjeta" &&
            comprobanteLimpio.isBlank()
        ) {
            throw ErrorApp("Adjunta el comprobante de pago.")
        }

        val ref = firestore.documentoNuevo(Coleccion.PEDIDOS)

        firestore.transaccion { tx ->

            val ultimo = tx.get(contador).getLong("ultimo")
                ?: throw ErrorApp(
                    "Falta configurar el contador de pedidos en Firebase."
                )

            val numero = ultimo + 1

            val mapaPedido = nuevo.aMapa(
                numero = numero,
                creadorId = creador.id,
                estadoInicial = ESTADO_REGISTRADO
            ).toMutableMap()

            mapaPedido["versionEstado"] = VERSION_ESTADOS_PEDIDO

            val pago = mutableMapOf<String, Any>(
                "metodo" to metodoLimpio,
                "monto" to nuevo.total,
                "referencia" to referenciaLimpia,
                "fecha" to FieldValue.serverTimestamp(),
                "estadoRevision" to PAGO_PENDIENTE,
                "observacionAdmin" to "",
                "fechaRevision" to 0L
            )

            if (comprobanteLimpio.isNotEmpty()) {
                pago["comprobante"] = comprobanteLimpio
            }

            mapaPedido["pago"] = pago

            tx.update(contador, "ultimo", numero)
            tx.set(ref, mapaPedido)

            numero
        }
    }

    // --------------------------------------------------
    // Avanzar pedido
    // --------------------------------------------------

    override suspend fun avanzar(
        id: String
    ): Result<Unit> = intentar {

        val ref = pedido(id)

        firestore.transaccion { tx ->

            val snap = tx.get(ref)
            val estado = estadoDe(snap)

            if (estado == ESTADO_REGISTRADO) {
                throw ErrorApp(
                    "El pago debe ser aprobado antes de avanzar."
                )
            }

            if (estado >= ULTIMO_ESTADO) {
                throw ErrorApp("El pedido ya está en la última etapa.")
            }

            val siguiente = estado + 1
            val tiempos = tiemposNormalizados(snap)

            tiempos[siguiente.toString()] =
                FieldValue.serverTimestamp()

            tx.update(
                ref,
                "estado", siguiente,
                "tiempos", tiempos,
                "versionEstado", VERSION_ESTADOS_PEDIDO
            )

            Unit
        }
    }

    // --------------------------------------------------
    // Retroceder pedido
    // --------------------------------------------------

    override suspend fun retroceder(
        id: String
    ): Result<Unit> = intentar {

        val ref = pedido(id)

        firestore.transaccion { tx ->

            val snap = tx.get(ref)
            val estado = estadoDe(snap)

            if (estado <= ESTADO_PAGADO) {
                throw ErrorApp(
                    "No se puede retroceder un pedido en esta etapa."
                )
            }

            val anterior = estado - 1
            val tiempos = tiemposNormalizados(snap)

            tiempos.remove(estado.toString())

            tx.update(
                ref,
                "estado", anterior,
                "tiempos", tiempos,
                "versionEstado", VERSION_ESTADOS_PEDIDO
            )

            Unit
        }
    }

    // --------------------------------------------------
    // Reenviar pago después de una observación
    // --------------------------------------------------

    override suspend fun pagar(
        id: String,
        metodo: String,
        referencia: String,
        comprobante: String
    ): Result<Unit> = intentar {

        val metodoLimpio = metodo.trim()
        val referenciaLimpia = referencia.trim()
        val comprobanteLimpio = comprobante.trim()

        if (metodoLimpio !in METODOS_PAGO) {
            throw ErrorApp("Selecciona un método de pago válido.")
        }

        if (referenciaLimpia.isBlank()) {
            throw ErrorApp("Ingresa la referencia del pago.")
        }

        if (
            metodoLimpio != "Tarjeta" &&
            comprobanteLimpio.isBlank()
        ) {
            throw ErrorApp("Adjunta el comprobante de pago.")
        }

        val ref = pedido(id)

        firestore.transaccion { tx ->

            val snap = tx.get(ref)

            if (estadoDe(snap) != ESTADO_REGISTRADO) {
                throw ErrorApp(
                    "Este pedido no está esperando un pago."
                )
            }

            val pagoAnterior =
                snap.get("pago") as? Map<*, *>

            val estadoAnterior =
                pagoAnterior?.get("estadoRevision") as? String

            if (estadoAnterior == PAGO_PENDIENTE) {
                throw ErrorApp(
                    "Tu pago ya está pendiente de revisión."
                )
            }

            if (estadoAnterior == PAGO_APROBADO) {
                throw ErrorApp(
                    "Este pedido ya tiene un pago aprobado."
                )
            }

            val pago = mutableMapOf<String, Any>(
                "metodo" to metodoLimpio,
                "monto" to (snap.getDouble("total") ?: 0.0),
                "referencia" to referenciaLimpia,
                "fecha" to FieldValue.serverTimestamp(),
                "estadoRevision" to PAGO_PENDIENTE,
                "observacionAdmin" to "",
                "fechaRevision" to 0L
            )

            if (comprobanteLimpio.isNotEmpty()) {
                pago["comprobante"] = comprobanteLimpio
            }

            val tiempos = tiemposNormalizados(snap)

            tx.update(
                ref,
                "pago", pago,
                "tiempos", tiempos,
                "versionEstado", VERSION_ESTADOS_PEDIDO
            )

            Unit
        }
    }

    // --------------------------------------------------
    // Aprobar pago desde administración
    // --------------------------------------------------

    override suspend fun aprobarPago(
        id: String
    ): Result<Unit> = intentar {

        val ref = pedido(id)

        firestore.transaccion { tx ->

            val snap = tx.get(ref)

            if (estadoDe(snap) != ESTADO_REGISTRADO) {
                throw ErrorApp(
                    "El pedido ya no espera la revisión del pago."
                )
            }

            val pago = snap.get("pago") as? Map<*, *>
                ?: throw ErrorApp(
                    "El cliente todavía no ha enviado un pago."
                )

            val estadoRevision =
                pago["estadoRevision"] as? String
                    ?: PAGO_PENDIENTE

            if (estadoRevision != PAGO_PENDIENTE) {
                throw ErrorApp("Este pago ya fue revisado.")
            }

            val tiempos = tiemposNormalizados(snap)

            tiempos[ESTADO_PAGADO.toString()] =
                FieldValue.serverTimestamp()

            tx.update(
                ref,
                "estado", ESTADO_PAGADO,
                "tiempos", tiempos,
                "pago.estadoRevision", PAGO_APROBADO,
                "pago.observacionAdmin", "",
                "pago.fechaRevision", FieldValue.serverTimestamp(),
                "versionEstado", VERSION_ESTADOS_PEDIDO
            )

            Unit
        }
    }

    // --------------------------------------------------
    // Observar pago desde administración
    // --------------------------------------------------

    override suspend fun observarPago(
        id: String,
        motivo: String
    ): Result<Unit> = intentar {

        val motivoLimpio = motivo.trim()

        if (motivoLimpio.length < 5) {
            throw ErrorApp(
                "Escribe un motivo de al menos 5 caracteres."
            )
        }

        if (motivoLimpio.length > 200) {
            throw ErrorApp(
                "El motivo no puede superar los 200 caracteres."
            )
        }

        val ref = pedido(id)

        firestore.transaccion { tx ->

            val snap = tx.get(ref)

            if (estadoDe(snap) != ESTADO_REGISTRADO) {
                throw ErrorApp(
                    "El pedido ya no espera la revisión del pago."
                )
            }

            val pago = snap.get("pago") as? Map<*, *>
                ?: throw ErrorApp(
                    "El cliente todavía no ha enviado un pago."
                )

            val estadoRevision =
                pago["estadoRevision"] as? String
                    ?: PAGO_PENDIENTE

            if (estadoRevision != PAGO_PENDIENTE) {
                throw ErrorApp("Este pago ya fue revisado.")
            }

            val tiempos = tiemposNormalizados(snap)

            tx.update(
                ref,
                "pago.estadoRevision", PAGO_OBSERVADO,
                "pago.observacionAdmin", motivoLimpio,
                "pago.fechaRevision", FieldValue.serverTimestamp(),
                "tiempos", tiempos,
                "versionEstado", VERSION_ESTADOS_PEDIDO
            )

            Unit
        }
    }

    // --------------------------------------------------
    // Eliminar pedido
    // --------------------------------------------------

    override suspend fun eliminar(
        id: String
    ): Result<Unit> = intentar {

        val snap = firestore.obtener(Coleccion.PEDIDOS, id)
        val estado = estadoDe(snap)

        if (estado >= ESTADO_PAGADO) {
            throw ErrorApp(
                "No se puede eliminar un pedido que ya fue pagado."
            )
        }

        firestore.eliminar(Coleccion.PEDIDOS, id)
    }

    // --------------------------------------------------
    // Normalización de estados antiguos
    // --------------------------------------------------

    private fun estadoDe(
        snap: DocumentSnapshot
    ): Int {

        if (!snap.exists()) {
            throw ErrorApp("El pedido ya no existe.")
        }

        val estadoGuardado =
            snap.getLong("estado")?.toInt() ?: 0

        val version =
            snap.getLong("versionEstado")?.toInt() ?: 1

        val estadoNormalizado = if (
            version < VERSION_ESTADOS_PEDIDO
        ) {
            when (estadoGuardado) {
                0, 1 -> 0
                2 -> 1
                3 -> 2
                4 -> 3
                5 -> 4
                else -> 0
            }
        } else {
            estadoGuardado
        }

        return estadoNormalizado.coerceIn(
            0,
            ULTIMO_ESTADO
        )
    }

    // --------------------------------------------------
    // Normalización del historial de tiempos
    // --------------------------------------------------

    private fun tiemposNormalizados(
        snap: DocumentSnapshot
    ): MutableMap<String, Any> {

        val version =
            snap.getLong("versionEstado")?.toInt() ?: 1

        val originales =
            snap.get("tiempos") as? Map<*, *>
                ?: emptyMap<Any, Any>()

        val resultado = mutableMapOf<String, Any>()

        // Los documentos nuevos ya utilizan las posiciones 0..4.
        if (version >= VERSION_ESTADOS_PEDIDO) {

            originales.forEach { (clave, valor) ->
                if (clave is String && valor != null) {
                    resultado[clave] = valor
                }
            }

            return resultado
        }

        // Documentos antiguos:
        // 0 = Por registrar
        // 1 = Registrado
        // 2 = Pagado
        // 3 = En preparación
        // 4 = Despachado
        // 5 = Entregado

        val registrado = originales["1"] ?: originales["0"]

        if (registrado != null) {
            resultado["0"] = registrado
        }

        for (nuevoEstado in 1..ULTIMO_ESTADO) {

            val estadoAntiguo = nuevoEstado + 1
            val tiempo = originales[estadoAntiguo.toString()]

            if (tiempo != null) {
                resultado[nuevoEstado.toString()] = tiempo
            }
        }

        return resultado
    }
}
