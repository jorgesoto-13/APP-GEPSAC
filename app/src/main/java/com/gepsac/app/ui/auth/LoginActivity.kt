package com.gepsac.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.gepsac.app.databinding.ActivityLoginBinding
import com.gepsac.app.ui.ClienteActivity
import com.gepsac.app.ui.admin.AdminActivity
import com.gepsac.app.ui.err
import com.gepsac.app.viewmodel.AuthViewModel
import com.gepsac.app.viewmodel.Validar

class LoginActivity : AppCompatActivity() {
    private lateinit var b: ActivityLoginBinding
    private val vm: AuthViewModel by viewModels()
    private var registro = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        if (vm.usuario != null) { irAPanel(); return }

        b.btnAccion.setOnClickListener { if (registro) registrar() else ingresar() }
        b.tvCambiar.setOnClickListener { registro = !registro; actualizarUI() }
        b.btnDemoCliente.setOnClickListener { entrarDemo("cliente@gepsac.com", "cliente123") }
        b.btnDemoAdmin.setOnClickListener { entrarDemo("admin@gepsac.com", "admin123") }
        actualizarUI()
    }

    private fun ingresar() {
        val email = b.etEmail.text.toString()
        val pass = b.etPass.text.toString()
        val ok = listOf(b.tilEmail.err(Validar.email(email)), b.tilPass.err(Validar.password(pass))).all { it }
        if (!ok) return
        val error = vm.ingresar(email, pass)
        if (error == null) irAPanel() else mostrarError(error)
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
            b.tilPass.err(Validar.password(pass)),
            b.tilPass2.err(Validar.confirmar(pass, pass2))
        ).all { it }
        if (!ok) return
        val error = vm.registrar(nombre, tel, email, pass)
        if (error == null) irAPanel() else mostrarError(error)
    }

    private fun entrarDemo(email: String, pass: String) {
        val error = vm.ingresar(email, pass)
        if (error == null) irAPanel() else mostrarError(error)
    }

    private fun actualizarUI() {
        val reg = if (registro) View.VISIBLE else View.GONE
        b.tilNombre.visibility = reg
        b.tilTel.visibility = reg
        b.tilPass2.visibility = reg
        b.btnAccion.text = if (registro) "Crear cuenta" else "Ingresar"
        b.tvCambiar.text = if (registro) "¿Ya tienes cuenta? Ingresa" else "¿Eres nuevo? Crea tu cuenta de cliente"
        listOf(b.tilNombre, b.tilTel, b.tilEmail, b.tilPass, b.tilPass2).forEach { it.error = null }
        b.tvError.visibility = View.GONE
    }

    private fun mostrarError(msg: String) {
        b.tvError.text = msg
        b.tvError.visibility = View.VISIBLE
    }

    private fun irAPanel() {
        val destino = if (vm.usuario?.esAdmin == true) AdminActivity::class.java else ClienteActivity::class.java
        startActivity(Intent(this, destino))
        finish()
    }
}
