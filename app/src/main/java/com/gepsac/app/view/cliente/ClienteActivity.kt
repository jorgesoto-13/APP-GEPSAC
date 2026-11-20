package com.gepsac.app.view.cliente

import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import com.gepsac.app.R
import com.gepsac.app.di.Fabrica
import com.gepsac.app.view.base.NavActivity
import com.gepsac.app.view.carrito.CarritoFragment
import com.gepsac.app.view.catalogo.CatalogoFragment
import com.gepsac.app.view.pedidos.PedidosFragment
import com.gepsac.app.viewmodel.CarritoViewModel

class ClienteActivity : NavActivity() {
    override val titulo = "GEPSAC · Tienda"
    override val menuRes = R.menu.menu_cliente
    override val inicio = R.id.nav_catalogo

    // Es la misma instancia que usan el Catálogo y el Carrito (activityViewModels).
    private val carrito: CarritoViewModel by viewModels { Fabrica.carrito }

    override fun fragmentPara(id: Int): Fragment = when (id) {
        R.id.nav_catalogo -> CatalogoFragment()
        R.id.nav_carrito -> CarritoFragment()
        else -> PedidosFragment()
    }

    override fun alCrear() {
        // Globo con la cantidad de productos sobre la pestaña Carrito.
        carrito.cantidadTotal.observe(this) { n ->
            val globo = b.bottomNav.getOrCreateBadge(R.id.nav_carrito)
            globo.isVisible = n > 0
            globo.number = n
        }
    }
}
