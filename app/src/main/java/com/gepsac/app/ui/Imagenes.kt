package com.gepsac.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import com.gepsac.app.R
import java.io.File

object Imagenes {
    private const val MAX_LADO = 600
    private val cache = LruCache<String, Bitmap>(24)

    /** Copia la imagen elegida a filesDir/productos, reducida y con la rotación corregida. Devuelve la ruta o null. */
    fun guardar(ctx: Context, uri: Uri): String? {
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

            val dir = File(ctx.filesDir, "productos").apply { mkdirs() }
            val archivo = File(dir, "img_" + System.currentTimeMillis() + ".jpg")
            archivo.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            archivo.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun cargar(ruta: String): Bitmap? {
        cache.get(ruta)?.let { return it }
        if (!File(ruta).exists()) return null
        val bmp = BitmapFactory.decodeFile(ruta) ?: return null
        cache.put(ruta, bmp)
        return bmp
    }
}

fun ImageView.mostrarProducto(ruta: String) {
    val bmp = if (ruta.isEmpty()) null else Imagenes.cargar(ruta)
    clipToOutline = true   // respeta las esquinas redondeadas del fondo
    if (bmp != null) {
        setImageBitmap(bmp)
        scaleType = ImageView.ScaleType.CENTER_CROP
    } else {
        setImageResource(R.drawable.ic_image)
        scaleType = ImageView.ScaleType.CENTER
    }
}
