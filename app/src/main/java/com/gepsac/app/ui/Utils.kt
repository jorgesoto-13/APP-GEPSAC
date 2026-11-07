package com.gepsac.app.ui

import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.gepsac.app.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun Double.soles(): String = "S/ " + String.format(Locale.US, "%.2f", this)

fun Long.fechaHora(): String =
    if (this == 0L) "" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(this))

/** Muestra el error en el campo. Devuelve true si el campo es válido (msg == null). */
fun TextInputLayout.err(msg: String?): Boolean {
    error = msg
    return msg == null
}

fun Context.elegirFecha(onFecha: (String) -> Unit) {
    val c = Calendar.getInstance()
    DatePickerDialog(this, { _, y, m, d ->
        onFecha(String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y))
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
        .apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
}

fun crearChips(g: ChipGroup, opciones: List<String>, onSel: (String) -> Unit) {
    g.isSingleSelection = true
    g.isSelectionRequired = true
    opciones.forEachIndexed { i, t ->
        val c = LayoutInflater.from(g.context).inflate(R.layout.chip_filter, g, false) as Chip
        c.text = t
        c.id = View.generateViewId()
        g.addView(c)
        c.setOnCheckedChangeListener { _, ok -> if (ok) onSel(t) }
        if (i == 0) c.isChecked = true
    }
}
