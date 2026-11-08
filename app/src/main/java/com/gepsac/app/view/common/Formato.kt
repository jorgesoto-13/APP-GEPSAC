package com.gepsac.app.view.common

import com.gepsac.app.util.Fechas
import java.util.Locale

fun Double.soles(): String = "S/ " + String.format(Locale.US, "%.2f", this)

/** "25/10/2026 14:30" (vacío si la etapa no se alcanzó). */
fun Long.fechaHora(): String = Fechas.diaHora(this)

/** "25/10/2026". */
fun Long.fecha(): String = Fechas.dia(this)
