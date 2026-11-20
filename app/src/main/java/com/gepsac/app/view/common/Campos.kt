package com.gepsac.app.view.common

import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout

/*
 * Control de lo que se puede ESCRIBIR en cada campo. Así el usuario no puede meter de más (por ejemplo, más de
 * 9 dígitos en un celular) y los errores se evitan antes de llegar a la validación final (util/Validar.kt).
 */

/** Filtro que deja pasar solo los caracteres para los que [permitido] es verdadero. */
fun filtroDeCaracteres(permitido: (Char) -> Boolean) = InputFilter { fuente, inicio, fin, _, _, _ ->
    val texto = fuente.subSequence(inicio, fin)
    if (texto.all(permitido)) null else texto.filter(permitido)
}

/** Reemplaza los filtros del campo: los dados más un máximo de caracteres. */
fun EditText.limitar(max: Int, vararg filtros: InputFilter) {
    this.filters = arrayOf(*filtros, InputFilter.LengthFilter(max))
}

fun EditText.soloDigitos(max: Int) = limitar(max, filtroDeCaracteres { it.isDigit() })

fun EditText.sinEspacios(max: Int) = limitar(max, filtroDeCaracteres { !it.isWhitespace() })

/** Letras y espacios sueltos: sin espacio al inicio ni dos espacios seguidos. */
private val filtroNombre = InputFilter { fuente, inicio, fin, dest, dInicio, _ ->
    val salida = StringBuilder()
    var anterior = if (dInicio > 0) dest[dInicio - 1] else ' '   // al inicio cuenta como espacio: no se admite
    for (i in inicio until fin) {
        val c = fuente[i]
        if (c.isLetter() || (c == ' ' && anterior != ' ')) {
            salida.append(c)
            anterior = c
        }
    }
    if (salida.length == fin - inicio) null else salida.toString()
}

fun EditText.nombrePersona(max: Int) = limitar(max, filtroNombre)

/** Nombre de producto: letras, números, espacios y . , - & ' ( ) / */
fun EditText.nombreProducto(max: Int) =
    limitar(max, filtroDeCaracteres { it.isLetterOrDigit() || it in " .,-&'()/" })

/** Presentación (ej. «250 gr», «1 kg»): letras, números, espacios y punto. */
fun EditText.presentacion(max: Int) =
    limitar(max, filtroDeCaracteres { it.isLetterOrDigit() || it == ' ' || it == '.' })

/** Precio: hasta 4 enteros y 2 decimales (máximo 9999.99). */
private val filtroPrecio = InputFilter { fuente, inicio, fin, dest, dInicio, dFin ->
    val resultado = dest.subSequence(0, dInicio).toString() + fuente.subSequence(inicio, fin) + dest.subSequence(dFin, dest.length)
    if (Regex("""^\d{0,4}([.,]\d{0,2})?$""").matches(resultado)) null else ""
}

fun EditText.precio() = limitar(7, filtroPrecio)

/**
 * Celular peruano: solo dígitos, máximo 9, y avisa de inmediato si no empieza con 9.
 * Se llama una sola vez por campo (agrega un observador de texto).
 */
fun EditText.paraTelefono(til: TextInputLayout) {
    soloDigitos(9)
    doAfterTextChanged { t ->
        val s = t?.toString().orEmpty()
        til.error = if (s.isNotEmpty() && s[0] != '9') "Debe empezar con 9" else null
    }
}

/** Da formato al texto mientras se escribe: [transformar] recibe lo escrito y devuelve cómo debe verse. */
private fun EditText.formatear(activo: () -> Boolean, transformar: (String) -> String) {
    addTextChangedListener(object : TextWatcher {
        private var editando = false
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (editando || s == null || !activo()) return
            editando = true
            val nuevo = transformar(s.toString())
            if (nuevo != s.toString()) s.replace(0, s.length, nuevo)
            editando = false
        }
    })
}

/** Número de tarjeta en grupos de 4 («1234 5678 9012 3456»), máximo 16 dígitos. */
fun EditText.formatoTarjeta(activo: () -> Boolean = { true }) =
    formatear(activo) { t -> t.filter { it.isDigit() }.take(16).chunked(4).joinToString(" ") }

/** Vencimiento «MM/AA»: pone la barra sola. */
fun EditText.formatoVencimiento(activo: () -> Boolean = { true }) =
    formatear(activo) { t ->
        val d = t.filter { it.isDigit() }.take(4)
        if (d.length > 2) d.take(2) + "/" + d.drop(2) else d
    }
