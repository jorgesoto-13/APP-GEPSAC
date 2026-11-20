package com.gepsac.app.view.common

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
import android.graphics.Color

/**
 * Las fotos de producto se reducen y se guardan como texto Base64 dentro del documento del producto en Firestore
 * (así no hace falta Firebase Storage, que exige el plan de pago).
 */
object Imagenes {
    private const val MAX_LADO = 480
    private const val MAX_BYTES = 150_000   // el documento de Firestore admite 1 MiB; esto deja mucho margen
    private const val LADO_COMPROBANTE = 1100
    private const val BYTES_COMPROBANTE = 180_000   // en Base64 ~240 000 caracteres: cabe en el límite de las reglas (250 000)
    private val cache = LruCache<String, Bitmap>(24)

    /**
     * Lee la imagen elegida, la reduce, corrige la rotación y la devuelve en Base64 (JPEG). Null si falla.
     * Si no cabe en [maxBytes], la va reduciendo hasta que quepa (las capturas de comprobante usan un lado mayor
     * para que el texto siga siendo legible).
     */
    fun codificar(ctx: Context, uri: Uri, maxLado: Int = MAX_LADO, maxBytes: Int = MAX_BYTES): String? {
        return try {
            val cr = ctx.contentResolver
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) }
            var muestreo = 1
            while (medidas.outWidth / (muestreo * 2) >= maxLado && medidas.outHeight / (muestreo * 2) >= maxLado) muestreo *= 2
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
            if (mayor > maxLado) {
                val f = maxLado.toFloat() / mayor
                bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
            }

            // Baja la calidad y, si hace falta, el tamaño, hasta que quepa.
            var resultado: ByteArray? = null
            for (intento in 0 until 4) {
                for (calidad in intArrayOf(85, 70, 55, 40)) {
                    val salida = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
                    val bytes = salida.toByteArray()
                    if (bytes.size <= maxBytes) { resultado = bytes; break }
                }
                if (resultado != null) break
                bmp = Bitmap.createScaledBitmap(
                    bmp, (bmp.width * 0.75f).toInt().coerceAtLeast(1), (bmp.height * 0.75f).toInt().coerceAtLeast(1), true
                )
            }
            resultado?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (e: Exception) {
            null
        }
    }

    /** Captura de un comprobante de pago: más resolución que las fotos de producto, para poder leer los montos. */
    fun codificarComprobante(ctx: Context, uri: Uri): String? = codificar(ctx, uri, LADO_COMPROBANTE, BYTES_COMPROBANTE)

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

    // Fondo blanco para los espacios laterales
    setBackgroundColor(Color.WHITE)

    // Respeta las esquinas redondeadas
    clipToOutline = true

    if (bmp != null) {
        setImageBitmap(bmp)
        scaleType = ImageView.ScaleType.FIT_CENTER
    } else {
        setImageResource(R.drawable.ic_image)
        scaleType = ImageView.ScaleType.CENTER
    }
}
