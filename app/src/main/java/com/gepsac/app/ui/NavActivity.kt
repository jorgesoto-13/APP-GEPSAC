package com.gepsac.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.gepsac.app.R
import com.gepsac.app.data.Repository
import com.gepsac.app.databinding.ActivityNavBinding
import com.gepsac.app.ui.auth.LoginActivity
import com.gepsac.app.ui.carrito.CarritoFragment
import com.gepsac.app.ui.catalogo.CatalogoFragment
import com.gepsac.app.ui.pedidos.PedidosFragment

abstract class NavActivity : AppCompatActivity() {
    protected lateinit var b: ActivityNavBinding

    abstract val titulo: String
    abstract val menuRes: Int
    abstract val inicio: Int
    abstract fun fragmentPara(id: Int): Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityNavBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.tvTitulo.text = titulo
        b.tvSalir.setOnClickListener {
            Repository.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        b.bottomNav.inflateMenu(menuRes)
        b.bottomNav.setOnItemSelectedListener {
            supportFragmentManager.beginTransaction().replace(R.id.container, fragmentPara(it.itemId)).commit()
            true
        }
        if (savedInstanceState == null) b.bottomNav.selectedItemId = inicio
    }

    fun irA(id: Int) { b.bottomNav.selectedItemId = id }
}

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
