package com.gepsac.app.view.base

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.gepsac.app.GepsacApp
import com.gepsac.app.R
import com.gepsac.app.databinding.ActivityNavBinding
import com.gepsac.app.di.Fabrica
import com.gepsac.app.viewmodel.AuthViewModel
import com.gepsac.app.view.auth.LoginActivity

/** Pantalla base con barra inferior. La usan ClienteActivity y AdminActivity. */
abstract class NavActivity : AppCompatActivity() {
    protected lateinit var b: ActivityNavBinding
    private val auth: AuthViewModel by viewModels { Fabrica.auth }

    abstract val titulo: String
    abstract val menuRes: Int
    abstract val inicio: Int
    abstract fun fragmentPara(id: Int): Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        // Si el proceso se reinició sin sesión en memoria, se vuelve al login (que restaura la sesión de Firebase).
        val haySesion = (application as GepsacApp).contenedor.authRepository.usuarioActual != null
        super.onCreate(if (haySesion) savedInstanceState else null)
        if (!haySesion) { irALogin(); return }

        b = ActivityNavBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.tvTitulo.text = titulo
        b.tvSalir.setOnClickListener {
            auth.cerrarSesion()
            irALogin()
        }
        b.bottomNav.inflateMenu(menuRes)
        b.bottomNav.setOnItemSelectedListener {
            supportFragmentManager.beginTransaction().replace(R.id.container, fragmentPara(it.itemId)).commit()
            true
        }
        if (savedInstanceState == null) b.bottomNav.selectedItemId = inicio
    }

    fun irA(id: Int) { b.bottomNav.selectedItemId = id }

    private fun irALogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
