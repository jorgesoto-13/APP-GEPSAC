package com.gepsac.app.view.base

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.gepsac.app.GepsacApp
import com.gepsac.app.R
import com.gepsac.app.actualizacion.Actualizador
import com.gepsac.app.databinding.ActivityNavBinding
import com.gepsac.app.di.Fabrica
import com.gepsac.app.viewmodel.AuthViewModel
import com.gepsac.app.view.auth.LoginActivity

/** Pantalla base con barra inferior. La usan ClienteActivity y AdminActivity. */
abstract class NavActivity : AppCompatActivity() {
    protected lateinit var b: ActivityNavBinding
    private val auth: AuthViewModel by viewModels { Fabrica.auth }

    /** Permiso para mostrar avisos de pedidos (Android 13+). */
    private val permisoAvisos = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    abstract val titulo: String
    abstract val menuRes: Int
    abstract val inicio: Int
    abstract fun fragmentPara(id: Int): Fragment

    /** Para que cada panel añada lo suyo (por ejemplo, el globo del carrito) una vez creada la pantalla. */
    protected open fun alCrear() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // Si el proceso se reinició sin sesión en memoria, se vuelve al login (que restaura la sesión de Firebase).
        val contenedor = (application as GepsacApp).contenedor
        val usuario = contenedor.authRepository.usuarioActual
        super.onCreate(if (usuario != null) savedInstanceState else null)
        if (usuario == null) { irALogin(); return }

        b = ActivityNavBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.tvTitulo.text = titulo
        b.tvSalir.setOnClickListener {
            contenedor.notificador.detener()
            contenedor.carritoLocal.cerrar()
            auth.cerrarSesion()
            irALogin()
        }
        b.bottomNav.inflateMenu(menuRes)
        b.bottomNav.setOnItemSelectedListener {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.fragmento_entra, R.anim.fragmento_sale)
                .replace(R.id.container, fragmentPara(it.itemId))
                .commit()
            true
        }
        if (savedInstanceState == null) b.bottomNav.selectedItemId = inicio

        alCrear()
        contenedor.notificador.iniciar(usuario)
        pedirPermisoDeAvisos()
        Actualizador.buscar(this)
    }

    private fun pedirPermisoDeAvisos() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || yaPidioAvisos) return
        yaPidioAvisos = true
        val concedido = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!concedido) permisoAvisos.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun irA(id: Int) { b.bottomNav.selectedItemId = id }

    private fun irALogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private companion object {
        /** El permiso se pide una vez por apertura de la app. */
        var yaPidioAvisos = false
    }
}
