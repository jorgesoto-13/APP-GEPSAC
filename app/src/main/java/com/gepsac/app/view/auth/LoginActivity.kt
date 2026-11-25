package com.gepsac.app.view.auth

import android.content.Intent
import android.os.Bundle
import android.transition.TransitionManager
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gepsac.app.BuildConfig
import com.gepsac.app.R
import com.gepsac.app.view.common.Tema
import com.gepsac.app.view.common.aparecer
import com.gepsac.app.view.common.nombrePersona
import com.gepsac.app.view.common.paraTelefono
import com.gepsac.app.view.common.sinEspacios
import com.gepsac.app.view.common.emerger
import com.gepsac.app.model.Usuario
import com.gepsac.app.databinding.ActivityLoginBinding
import com.gepsac.app.di.Fabrica
import com.gepsac.app.view.admin.AdminActivity
import com.gepsac.app.view.cliente.ClienteActivity
import com.gepsac.app.view.common.err
import com.gepsac.app.view.seguimiento.SeguimientoActivity
import com.gepsac.app.util.Validar
import com.gepsac.app.util.observarEvento
import com.gepsac.app.viewmodel.AuthViewModel
import com.gepsac.app.viewmodel.AuthEstado

class LoginActivity : AppCompatActivity() {
    private lateinit var b: ActivityLoginBinding
    private val vm: AuthViewModel by viewModels { Fabrica.auth }
    private var registro = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        // La cabecera de marca llega hasta arriba: barra de estado del mismo color y con íconos claros.
        window.statusBarColor = 0xFF432611.toInt()   // el marrón oscuro del degradado, de día y de noche
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        b.btnAccion.setOnClickListener { if (registro) registrar() else ingresar() }
        b.tvCambiar.setOnClickListener {
            registro = !registro
            TransitionManager.beginDelayedTransition(b.form)   // los campos aparecen/desaparecen con animación
            actualizarUI()
        }
        // Qué se puede escribir en cada campo (el celular, por ejemplo, solo admite 9 dígitos).
        b.etNombre.nombrePersona(50)
        b.etTel.paraTelefono(b.tilTel)
        b.etEmail.sinEspacios(60)

        b.tvVersion.text ="GEPSAC · versión " + BuildConfig.VERSION_NAME +
            if (BuildConfig.NUMERO_COMPILACION > 0) " (compilación " + BuildConfig.NUMERO_COMPILACION + ")" else " (local)"
        b.tvOlvide.setOnClickListener { recuperar() }
        b.tvTema.text = "Tema: " + Tema.nombreActual(this)
        b.tvTema.setOnClickListener { Tema.mostrarSelector(this) }
        vm.recuperacion.observarEvento(this) { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        actualizarUI()
        if (savedInstanceState == null) {
            b.imgLogo.emerger(100)
            b.form.aparecer(220)
        }


        vm.estado.observe(this) { estado ->
            cargando(estado == AuthEstado.Cargando)

            when (estado) {
                is AuthEstado.Listo -> {
                    irAPanel(estado.usuario)
                }

                AuthEstado.RegistroExitoso -> {
                    // Volver al formulario de inicio de sesión.
                    registro = false
                    actualizarUI()

                    // Mantener el correo para que el usuario pueda ingresar.
                    b.etPass.text?.clear()
                    b.etPass2.text?.clear()

                    Toast.makeText(
                        this,
                        "¡Registrado correctamente! Ahora inicia sesión.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                is AuthEstado.Fallo -> {
                    mostrarError(estado.mensaje)
                }

                AuthEstado.Cargando,
                AuthEstado.Inactivo -> {}
            }
        }

        vm.restaurarSesion()
    }

    /** «¿Olvidaste tu contraseña?»: usa el correo que ya escribió en el campo. */
    private fun recuperar() {
        val email = b.etEmail.text.toString()
        if (!b.tilEmail.err(Validar.email(email))) {
            Toast.makeText(this, "Escribe tu correo arriba y vuelve a tocar «¿Olvidaste tu contraseña?»", Toast.LENGTH_LONG).show()
            return
        }
        vm.recuperarContrasena(email)
    }

    private fun ingresar() {
        val email = b.etEmail.text.toString()
        val pass = b.etPass.text.toString()
        val ok = listOf(b.tilEmail.err(Validar.email(email)), b.tilPass.err(Validar.password(pass))).all { it }
        if (!ok) return
        b.tvError.visibility = View.GONE
        vm.ingresar(email, pass)
    }

    private fun registrar() {
        val nombre = b.etNombre.text.toString()
        val tel = b.etTel.text.toString()
        val email = b.etEmail.text.toString()
        val pass = b.etPass.text.toString()
        val pass2 = b.etPass2.text.toString()
        val ok = listOf(
            b.tilNombre.err(Validar.nombre(nombre)),
            b.tilTel.err(Validar.telefono(tel)),
            b.tilEmail.err(Validar.email(email)),
            b.tilPass.err(Validar.passwordNueva(pass)),
            b.tilPass2.err(Validar.confirmar(pass, pass2))
        ).all { it }
        if (!ok) return
        b.tvError.visibility = View.GONE
        vm.registrar(nombre, tel, email, pass)
    }

    private fun actualizarUI() {
        val reg = if (registro) View.VISIBLE else View.GONE
        b.tilNombre.visibility = reg
        b.tilTel.visibility = reg
        b.tilPass2.visibility = reg
        b.tvOlvide.visibility = if (registro) View.GONE else View.VISIBLE
        b.tvBienvenida.text = if (registro) "Crea tu cuenta" else "Bienvenido"
        b.tvSubtitulo.text = if (registro) "Regístrate como cliente para hacer tus pedidos." else "Inicia sesión para continuar."
        b.btnAccion.text = if (registro) "Crear cuenta" else "Ingresar"
        b.tvCambiar.text = if (registro) "¿Ya tienes cuenta? Ingresa" else "¿Eres nuevo? Crea tu cuenta de cliente"
        listOf(b.tilNombre, b.tilTel, b.tilEmail, b.tilPass, b.tilPass2).forEach { it.error = null }
        b.tvError.visibility = View.GONE
    }

    private fun cargando(si: Boolean) {
        b.progreso.visibility = if (si) View.VISIBLE else View.GONE
        b.btnAccion.isEnabled = !si
        b.tvCambiar.isEnabled = !si
    }

    private fun mostrarError(msg: String) {
        b.tvError.text = msg
        b.tvError.visibility = View.VISIBLE
    }

    companion object {
        /** Id del pedido que se debe abrir después de iniciar sesión (lo envían los avisos). */
        const val EXTRA_PEDIDO = "pedidoId"
    }

    private fun irAPanel(usuario: Usuario) {
        val destino = if (usuario.esAdmin) AdminActivity::class.java else ClienteActivity::class.java
        startActivity(Intent(this, destino))
        // Si se llegó desde un aviso de pedido, tras abrir el panel se abre ese pedido.
        intent.getStringExtra(EXTRA_PEDIDO)?.let {
            startActivity(Intent(this, SeguimientoActivity::class.java).putExtra("id", it))
        }
        finish()
    }
}
