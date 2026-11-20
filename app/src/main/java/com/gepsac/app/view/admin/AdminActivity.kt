package com.gepsac.app.view.admin

import androidx.fragment.app.Fragment
import com.gepsac.app.R
import com.gepsac.app.view.base.NavActivity
import com.gepsac.app.view.pedidos.PedidosFragment
import com.gepsac.app.view.ventas.VentasFragment

class AdminActivity : NavActivity() {
    override val titulo = "GEPSAC · Administrador"
    override val menuRes = R.menu.menu_admin
    override val inicio = R.id.nav_pedidos
    override fun fragmentPara(id: Int): Fragment = when (id) {
        R.id.nav_productos -> AdminProductosFragment()
        R.id.nav_clientes -> AdminClientesFragment()
        R.id.nav_ventas -> VentasFragment()
        else -> PedidosFragment()
    }
}
