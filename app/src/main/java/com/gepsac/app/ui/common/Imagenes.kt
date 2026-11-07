package com.gepsac.app.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import android.widget.ImageView
import com.gepsac.app.R
import java.io.ByteArrayOutputStream

/**
 * Las fotos de producto se reducen y se guardan como texto Base64 dentro del documento del producto en Firestore
 * (así no hace falta Firebase Storage, que exige el plan de pago).
 */
object Imagenes {
    private const val MAX_LADO = 480
    private const val MAX_BYTES = 150_000   // el documento de Firestore admite 1 MiB; esto deja mucho margen
    private val cache = LruCache<String, Bitmap>(24)

    /** Lee la imagen elegida, la reduce, corrige la rotación y la devuelve en Base64 (JPEG). Null si falla. */
    fun codificar(ctx: Context, uri: Uri): String? {
        return try {
            val cr = ctx.contentResolver
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) }
            var muestreo = 1
            while (medidas.outWidth / (muestreo * 2) >= MAX_LADO && medidas.outHeight / (muestreo * 2) >= MAX_LADO) muestreo *= 2
            var bmp = cr.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestreo })
            } ?: return null

            // Corrige la orientación de las fotos
            val grados = cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (grados != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(grados) }, true)

            val mayor = maxOf(bmp.width, bmp.height)
            if (mayor > MAX_LADO) {
                val f = MAX_LADO.toFloat() / mayor
                bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
            }

            // Baja la calidad hasta que quepa en el tamaño máximo.
            var bytes = ByteArray(0)
            for (calidad in intArrayOf(85, 70, 55, 40)) {
                val salida = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
                bytes = salida.toByteArray()
                if (bytes.size <= MAX_BYTES) break
            }
            if (bytes.size > MAX_BYTES) null else Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun decodificar(base64: String): Bitmap? {
        val clave = base64.length.toString() + ":" + base64.hashCode()
        cache.get(clave)?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            cache.put(clave, bmp)
            bmp
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/** Muestra la foto del producto (Base64) o, si no tiene, el ícono de imagen. */
fun ImageView.mostrarProducto(base64: String) {
    val bmp = if (base64.isEmpty()) null else Imagenes.decodificar(base64)
    clipToOutline = true   // respeta las esquinas redondeadas del fondo
    if (bmp != null) {
        setImageBitmap(bmp)
        scaleType = ImageView.ScaleType.CENTER_CROP
    } else {
        setImageResource(R.drawable.ic_image)
        scaleType = ImageView.ScaleType.CENTER
    }
}
