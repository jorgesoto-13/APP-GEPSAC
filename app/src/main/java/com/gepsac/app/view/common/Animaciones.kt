package com.gepsac.app.view.common

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/*
 * Animaciones de la interfaz. Todas usan ViewPropertyAnimator (transform y opacity), que respeta la opción
 * del sistema «Escala de duración de animación»: si el usuario la desactiva, no se anima nada.
 */

private const val DESPLAZAMIENTO_DP = 24f
private const val RETRASO_FILA_MS = 45L
private const val FILAS_ESCALONADAS = 8

/**
 * Entrada escalonada de las filas de una lista (RecyclerView). Cada fila se anima solo la primera vez que aparece:
 * al volver a hacer scroll o al actualizarse los datos no vuelve a parpadear.
 */
class AnimadorLista {
    private var ultimo = -1

    fun animar(v: View, posicion: Int) {
        v.animate().cancel()
        if (posicion <= ultimo) {
            v.alpha = 1f
            v.translationY = 0f
            return
        }
        ultimo = posicion
        v.alpha = 0f
        v.translationY = DESPLAZAMIENTO_DP * v.resources.displayMetrics.density
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(if (posicion < FILAS_ESCALONADAS) posicion * RETRASO_FILA_MS else 0L)
            .setDuration(300L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}

/** Aparece desde abajo con fundido. */
fun View.aparecer(retrasoMs: Long = 0L) {
    alpha = 0f
    translationY = DESPLAZAMIENTO_DP * resources.displayMetrics.density
    animate()
        .alpha(1f)
        .translationY(0f)
        .setStartDelay(retrasoMs)
        .setDuration(380L)
        .setInterpolator(DecelerateInterpolator())
        .start()
}

/** Crece desde cero con un pequeño rebote (logo, punto de la etapa actual). */
fun View.emerger(retrasoMs: Long = 0L) {
    scaleX = 0f
    scaleY = 0f
    animate()
        .scaleX(1f)
        .scaleY(1f)
        .setStartDelay(retrasoMs)
        .setDuration(420L)
        .setInterpolator(OvershootInterpolator(1.6f))
        .start()
}
