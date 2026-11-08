package com.gepsac.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Conversión entre los textos de la pantalla (dd/MM/yyyy) y los milisegundos que guarda el modelo. */
object Fechas {
    private const val DIA = "dd/MM/yyyy"
    private const val DIA_HORA = "dd/MM/yyyy HH:mm"

    /** "25/10/2026" -> milisegundos (medianoche local). Null si el texto no es una fecha válida. */
    fun leerDia(texto: String): Long? = try {
        SimpleDateFormat(DIA, Locale.US).apply { isLenient = false }.parse(texto.trim())?.time
    } catch (e: Exception) {
        null
    }

    fun dia(ms: Long): String = if (ms == 0L) "" else SimpleDateFormat(DIA, Locale.US).format(Date(ms))

    fun diaHora(ms: Long): String =
        if (ms == 0L) "" else SimpleDateFormat(DIA_HORA, Locale.getDefault()).format(Date(ms))
}
