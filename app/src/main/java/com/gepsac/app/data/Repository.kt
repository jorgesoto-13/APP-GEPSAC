package com.gepsac.app.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Repository en MEMORIA (sin base de datos). Cuando conectes Firebase, solo se cambia este archivo:
 * Authentication -> login/registrar, Cloud Firestore -> productos/pedidos, Storage -> imágenes.
 * Los datos se reinician al cerrar la app.
 */
object Repository {
    private val usuarios = mutableListOf(
        Usuario("u0", "Administrador GEPSAC", "admin@gepsac.com", "999 000 111", "admin123", true),
        Usuario("u1", "Cliente Demo", "cliente@gepsac.com", "987 654 321", "cliente123")
    )
    var usuarioActual: Usuario? = null
        private set

    private val _productos = MutableLiveData(
        listOf(
            Producto("p1", "Café Caffe Blend", "Caffe", "250 gr", 12.0),
            Producto("p2", "Café Caffe Blend", "Caffe", "500 gr", 26.0),
            Producto("p3", "Café Kaphiy Blend", "Kaphiy", "500 gr", 27.0)
        )
    )
    val productos: LiveData<List<Producto>> = _productos

    private val _pedidos = MutableLiveData(pedidosDemo())
    val pedidos: LiveData<List<Pedido>> = _pedidos

    // ---------- Autenticación ----------
    fun login(email: String, pass: String): String? {
        val u = usuarios.find { it.email.equals(email.trim(), true) && it.password == pass }
            ?: return "Correo o contraseña incorrectos"
        usuarioActual = u
        return null
    }

    fun registrar(nombre: String, tel: String, email: String, pass: String): String? {
        if (usuarios.any { it.email.equals(email.trim(), true) }) return "Ese correo ya está registrado"
        val u = Usuario("u" + System.currentTimeMillis(), nombre, email.trim(), tel, pass)
        usuarios.add(u)
        usuarioActual = u
        return null
    }

    fun logout() { usuarioActual = null }

    fun clientes(): List<Usuario> = usuarios.filter { !it.esAdmin }

    // ---------- Productos ----------
    fun guardarProducto(p: Producto) {
        val l = _productos.value.orEmpty().toMutableList()
        val i = l.indexOfFirst { it.id == p.id }
        if (i >= 0) l[i] = p else l.add(p)
        _productos.value = l
    }

    fun eliminarProducto(id: String) {
        _productos.value.orEmpty().find { it.id == id }?.imagen?.takeIf { it.isNotEmpty() }?.let { java.io.File(it).delete() }
        _productos.value = _productos.value.orEmpty().filter { it.id != id }
    }

    // ---------- Pedidos ----------
    /** Pedido que envía el cliente: queda "Por registrar". */
    fun crearPedido(items: List<ItemPedido>, dir: String, tel: String, fecha: String, obs: String): Pedido =
        nuevoPedido(usuarioActual!!, items, dir, tel, fecha, obs, false)

    /** Pedido que crea el admin a nombre de un cliente: entra ya "Registrado". */
    fun crearPedidoComoAdmin(cliente: Usuario, items: List<ItemPedido>, dir: String, tel: String, fecha: String, obs: String): Pedido =
        nuevoPedido(cliente, items, dir, tel, fecha, obs, true)

    private fun nuevoPedido(u: Usuario, items: List<ItemPedido>, dir: String, tel: String, fecha: String, obs: String, registrado: Boolean): Pedido {
        val now = System.currentTimeMillis()
        val t = MutableList(ESTADOS.size) { 0L }
        t[0] = now
        if (registrado) t[ESTADO_REGISTRADO] = now
        val p = Pedido(
            id = "o$now", numero = (now / 1000) % 1000000, clienteId = u.id, clienteNombre = u.nombre,
            clienteEmail = u.email, telefono = tel, direccion = dir, fechaEntrega = fecha, obs = obs,
            items = items, total = items.sumOf { it.precio * it.cantidad },
            estado = if (registrado) ESTADO_REGISTRADO else 0, tiempos = t.toList()
        )
        _pedidos.value = listOf(p) + _pedidos.value.orEmpty()
        return p
    }

    /** Avanza una etapa. Desde "Registrado" no avanza: hay que esperar el pago del cliente. */
    fun avanzarEstado(id: String) = cambiar(id) { p ->
        if (p.estado == ESTADO_REGISTRADO || p.estado >= ESTADOS.size - 1) p else mover(p, p.estado + 1)
    }

    /** Retrocede una etapa. Desde "Pagado" no se puede (anularía el pago). */
    fun retrocederEstado(id: String) = cambiar(id) { p ->
        if (p.estado == 0 || p.estado == ESTADO_PAGADO) p
        else {
            val t = p.tiempos.toMutableList()
            t[p.estado] = 0L
            p.copy(estado = p.estado - 1, tiempos = t)
        }
    }

    fun pagar(id: String, metodo: String) = cambiar(id) { p ->
        if (p.estado != ESTADO_REGISTRADO) p else mover(p, ESTADO_PAGADO).copy(metodoPago = metodo)
    }

    /** Solo se puede eliminar un pedido que aún NO ha sido pagado (Por registrar / Registrado). Devuelve true si se eliminó. */
    fun eliminarPedido(id: String): Boolean {
        val p = _pedidos.value.orEmpty().find { it.id == id } ?: return false
        if (p.estado >= ESTADO_PAGADO) return false
        _pedidos.value = _pedidos.value.orEmpty().filter { it.id != id }
        return true
    }

    private fun mover(p: Pedido, n: Int): Pedido {
        val t = p.tiempos.toMutableList()
        t[n] = System.currentTimeMillis()
        return p.copy(estado = n, tiempos = t)
    }

    private fun cambiar(id: String, f: (Pedido) -> Pedido) {
        _pedidos.value = _pedidos.value.orEmpty().map { if (it.id == id) f(it) else it }
    }

    // ---------- Datos de ejemplo para ver las pantallas ----------
    private fun pedidosDemo(): List<Pedido> {
        fun hace(h: Int) = System.currentTimeMillis() - h * 3_600_000L
        val c250 = { n: Int -> ItemPedido("p1", "Café Caffe Blend 250 gr", 12.0, n) }
        val c500 = { n: Int -> ItemPedido("p2", "Café Caffe Blend 500 gr", 26.0, n) }
        val k500 = { n: Int -> ItemPedido("p3", "Café Kaphiy Blend 500 gr", 27.0, n) }
        val dirDemo = "Av. Los Olivos 123, Lima"
        return listOf(
            Pedido("d6", 100236, "u1", "Cliente Demo", "cliente@gepsac.com", "987 654 321", dirDemo,
                "25/10/2026", "", listOf(k500(4)), 108.0, 0, listOf(hace(1), 0L, 0L, 0L, 0L, 0L)),
            Pedido("d5", 100235, "u1", "Cliente Demo", "cliente@gepsac.com", "987 654 321", dirDemo,
                "22/10/2026", "", listOf(c500(6)), 156.0, 1, listOf(hace(5), hace(4), 0L, 0L, 0L, 0L)),
            Pedido("d4", 100234, "ext2", "Minimarket Lucía", "lucia@correo.com", "955 111 222", "Jr. Unión 450, Lima",
                "20/10/2026", "", listOf(c250(6), c500(6)), 228.0, 3,
                listOf(hace(30), hace(28), hace(26), hace(10), 0L, 0L), "Yape"),
            Pedido("d3", 100233, "ext1", "Bodega Don Pepe", "pepe@correo.com", "944 333 444", "Av. Arequipa 800, Lima",
                "18/10/2026", "Entregar en la mañana", listOf(c250(20)), 240.0, 2,
                listOf(hace(8), hace(7), hace(2), 0L, 0L, 0L), "Transferencia"),
            Pedido("d2", 100232, "u1", "Cliente Demo", "cliente@gepsac.com", "987 654 321", dirDemo,
                "15/10/2026", "", listOf(c500(8)), 208.0, 4,
                listOf(hace(50), hace(48), hace(46), hace(30), hace(6), 0L), "Plin"),
            Pedido("d1", 100231, "u1", "Cliente Demo", "cliente@gepsac.com", "987 654 321", dirDemo,
                "10/10/2026", "", listOf(c250(10), c500(5)), 250.0, 5,
                listOf(hace(100), hace(98), hace(96), hace(80), hace(60), hace(30)), "Tarjeta")
        )
    }
}
