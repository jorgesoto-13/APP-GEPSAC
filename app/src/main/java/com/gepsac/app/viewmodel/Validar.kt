package com.gepsac.app.viewmodel

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Reglas de validación. Cada función devuelve el mensaje de error, o null si el dato es válido. */
object Validar {
    const val MAX_CANT = 100
    private val soloLetras = Regex("""^[\p{L} ]+$""")

    fun nombre(s: String, campo: String = "tu nombre"): String? {
        val t = s.trim()
        return when {
            t.isEmpty() -> "Ingresa $campo"
            t.length < 3 -> "Mínimo 3 letras"
            t.length > 50 -> "Máximo 50 caracteres"
            !soloLetras.matches(t) -> "Solo letras y espacios"
            else -> null
        }
    }

    fun telefono(s: String): String? {
        val t = s.replace(" ", "")
        return when {
            t.isEmpty() -> "Ingresa el teléfono"
            !Regex("""^9\d{8}$""").matches(t) -> "Debe tener 9 dígitos y empezar con 9"
            else -> null
        }
    }

    fun email(s: String): String? {
        val t = s.trim()
        return when {
            t.isEmpty() -> "Ingresa tu correo"
            !Regex("""^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$""").matches(t) -> "Correo no válido"
            else -> null
        }
    }

    fun password(s: String): String? = when {
        s.isEmpty() -> "Ingresa tu contraseña"
        s.length < 6 -> "Mínimo 6 caracteres"
        else -> null
    }

    fun confirmar(p1: String, p2: String): String? = if (p1 != p2) "Las contraseñas no coinciden" else null

    fun direccion(s: String): String? {
        val t = s.trim()
        return when {
            t.isEmpty() -> "Ingresa la dirección de entrega"
            t.length < 8 -> "Escribe una dirección completa (mínimo 8 caracteres)"
            t.length > 120 -> "Máximo 120 caracteres"
            else -> null
        }
    }

    fun fechaEntrega(s: String): String? {
        val t = s.trim()
        if (t.isEmpty()) return "Elige la fecha de entrega"
        val f = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { isLenient = false }
        val d = try { f.parse(t) } catch (e: Exception) { null } ?: return "Fecha no válida"
        val hoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        return if (d.before(hoy)) "La fecha no puede ser pasada" else null
    }

    fun observacion(s: String): String? = if (s.trim().length > 150) "Máximo 150 caracteres" else null

    /** Vacío cuenta como 0. */
    fun cantidad(s: String): String? {
        val t = s.trim()
        if (t.isEmpty()) return null
        val n = t.toIntOrNull() ?: return "Número no válido"
        return if (n < 0 || n > MAX_CANT) "De 0 a $MAX_CANT" else null
    }

    fun nombreProducto(s: String): String? {
        val t = s.trim()
        return when {
            t.isEmpty() -> "Ingresa el nombre del producto"
            t.length < 3 -> "Mínimo 3 caracteres"
            t.length > 40 -> "Máximo 40 caracteres"
            else -> null
        }
    }

    fun presentacion(s: String): String? {
        val t = s.trim()
        return when {
            t.isEmpty() -> "Ingresa la presentación (ej. 250 gr)"
            t.length > 20 -> "Máximo 20 caracteres"
            else -> null
        }
    }

    fun precio(s: String): String? {
        val v = s.trim().replace(",", ".").toDoubleOrNull() ?: return "Ingresa un precio válido"
        return when {
            v <= 0 -> "Debe ser mayor a 0"
            v > 9999 -> "Máximo S/ 9999"
            else -> null
        }
    }

    // ---------- Pago ----------
    fun celularPago(s: String): String? = telefono(s)

    fun codigo(s: String, min: Int, max: Int): String? {
        val t = s.trim()
        return when {
            t.isEmpty() -> "Campo obligatorio"
            !t.all { it.isDigit() } -> "Solo números"
            t.length < min || t.length > max ->
                if (min == max) "Debe tener $min dígitos" else "Debe tener de $min a $max dígitos"
            else -> null
        }
    }

    fun tarjeta(s: String): String? = codigo(s.replace(" ", ""), 16, 16)

    fun vencimiento(s: String): String? {
        val m = Regex("""^(0[1-9]|1[0-2])/(\d{2})$""").matchEntire(s.trim()) ?: return "Formato MM/AA"
        val mes = m.groupValues[1].toInt()
        val anio = 2000 + m.groupValues[2].toInt()
        val c = Calendar.getInstance()
        val actual = c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
        return if (anio * 12 + (mes - 1) < actual) "Tarjeta vencida" else null
    }

    fun cvv(s: String): String? = codigo(s, 3, 3)

    fun titular(s: String): String? = nombre(s, "el nombre del titular")
}
