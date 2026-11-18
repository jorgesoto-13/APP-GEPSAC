package com.gepsac.app.model.service

import com.google.firebase.functions.FirebaseFunctions

/**
 * Servicio de Cloud Functions: invoca funciones del servidor (pagos protegidos, cambios de estado, reglas de negocio).
 *
 * IMPORTANTE: desplegar Cloud Functions exige el plan Blaze (de pago). Hoy no hay ninguna función publicada: el
 * pago se simula y las reglas de negocio se validan en el código de la app y en `firebase/firestore.rules`. Ningún
 * repositorio usa este servicio todavía; queda listo para cuando existan funciones desplegadas.
 */
class CloudFunctionsService(private val functions: FirebaseFunctions) {

    /** Llama a la función `nombre` con [datos] y devuelve lo que responda el servidor. */
    suspend fun llamar(nombre: String, datos: Map<String, Any?> = emptyMap()): Any? =
        functions.getHttpsCallable(nombre).call(datos).esperar().data
}
