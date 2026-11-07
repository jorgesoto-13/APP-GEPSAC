package com.gepsac.app.ui.cliente

import androidx.fragment.app.Fragment
import com.gepsac.app.R
import com.gepsac.app.ui.base.NavActivity
import com.gepsac.app.ui.carrito.CarritoFragment
import com.gepsac.app.ui.catalogo.CatalogoFragment
import com.gepsac.app.ui.pedidos.PedidosFragment

class ClienteActivity : NavActivity() {
    override val titulo = "GEPSAC · Tienda"
    override val menuRes = R.menu.menu_cliente
    override val inicio = R.id.nav_catalogo
    override fun fragmentPara(id: Int): Fragment = when (id) {
        R.id.nav_catalogo -> CatalogoFragment()
        R.id.nav_carrito -> CarritoFragment()
        else -> PedidosFragment()
    }
}
