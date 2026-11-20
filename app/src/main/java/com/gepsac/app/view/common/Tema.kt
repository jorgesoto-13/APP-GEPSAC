package com.gepsac.app.view.common

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Tema de la app: claro, oscuro o automático (el mismo del teléfono). La elección se guarda en el teléfono y se
 * aplica al abrir la app y en el momento de cambiarla (las pantallas abiertas se redibujan solas).
 */
object Tema {
    const val AUTOMATICO = 0
    const val CLARO = 1
    const val OSCURO = 2

    private const val PREFS = "ajustes"
    private const val CLAVE = "tema"

    private val nombres = arrayOf("Automático (como el teléfono)", "Claro", "Oscuro")

    fun guardado(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(CLAVE, AUTOMATICO)

    /** Texto corto del tema actual, para mostrarlo junto al botón de ajustes. */
    fun nombreActual(ctx: Context): String = nombres[guardado(ctx).coerceIn(0, nombres.lastIndex)]

    /** Aplica el tema guardado. Se llama al iniciar la app. */
    fun aplicar(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(modo(guardado(ctx)))
    }

    private fun elegir(ctx: Context, tema: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(CLAVE, tema).apply()
        aplicar(ctx)
    }

    private fun modo(tema: Int) = when (tema) {
        CLARO -> AppCompatDelegate.MODE_NIGHT_NO
        OSCURO -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    /** Diálogo para elegir el tema. Al elegir uno, se aplica de inmediato. */
    fun mostrarSelector(actividad: AppCompatActivity) {
        MaterialAlertDialogBuilder(actividad)
            .setTitle("Tema de la app")
            .setSingleChoiceItems(nombres, guardado(actividad)) { dialogo, cual ->
                elegir(actividad.applicationContext, cual)
                dialogo.dismiss()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }
}
